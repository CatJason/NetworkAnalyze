package antfortune.wealth.net.myapplication.tester

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.regex.Matcher
import java.util.regex.Pattern

class NetworkTracerTester private constructor() {
    private var listener: NetworkTraceListener? = null

    companion object {
        private var instance: NetworkTracerTester? = null

        fun getInstance(): NetworkTracerTester {
            if (instance == null) {
                instance = NetworkTracerTester()
            }
            return instance!!
        }

        private const val MATCH_TRACE_IP = "(?<=From )(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"
    }

    fun setTraceRouteListener(listener: NetworkTraceListener) {
        this.listener = listener
    }

    fun beginTraceRoute(host: String) {
        val trace = TraceRouteStep(host.trim(), 1)
        executeTraceRoute(trace)
    }

    fun resetInstance() {
        instance = null
    }

    private fun runPingCommand(host: String, isNeedL: Boolean): String {
        val cmd = if (isNeedL) {
            "ping -s 8185 -c "
        } else {
            "ping -c "
        }
        val sendCount = 1
        var process: Process? = null
        val result = StringBuilder()

        return try {
            val fullCmd = "${cmd}${sendCount} $host"
            process = Runtime.getRuntime().exec(fullCmd)
            val waitThread = Thread {
                try {
                    process.waitFor()
                } catch (e: InterruptedException) {
                    process.destroy()
                }
            }
            waitThread.start()
            waitThread.join(1000)

            if (waitThread.isAlive) {
                process.destroy()
                waitThread.interrupt()
                return "$host 超时"
            }

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line).append(System.lineSeparator())
                }
            }
            result.toString().trim()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            e.printStackTrace()
            ""
        } finally {
            process?.destroy()
        }
    }

    private fun executeTraceRoute(trace: TraceRouteStep) {
        val log = StringBuilder()
        val patternTrace = Pattern.compile(MATCH_TRACE_IP)

        if (trace.host.isEmpty()) {
            log.append("主机地址为空，无法执行 Traceroute\n")
            listener?.onTraceRouteUpdate(log.toString())
            return
        }

        try {
            val targetIp = InetAddress.getByName(trace.host).hostAddress
            log.append("TraceRoute to IP: ").append(targetIp).append("\n")
        } catch (e: UnknownHostException) {
            log.append("无法解析主机: ").append(trace.host).append("\n")
            listener?.onTraceRouteUpdate(log.toString())
            return
        }

        var finish = false
        var timeoutCount = 0
        var successfulHops = 0
        val rttTimes = mutableListOf<Double>()
        var totalHops = 0
        var validHops = 0
        val validIpList = mutableListOf<String>()
        val ipScores = mutableMapOf<String, Int>()

        try {
            while (!finish && trace.hop <= 15) {
                var str = ""
                val command = "ping -c 1 -t ${trace.hop} ${trace.host}"
                val process = Runtime.getRuntime().exec(command)

                val waitThread = Thread {
                    try {
                        process.waitFor()
                    } catch (e: InterruptedException) {
                        process.destroy()
                    }
                }
                waitThread.start()
                waitThread.join(460)

                if (waitThread.isAlive) {
                    process.destroy()
                    waitThread.interrupt()
                    val logLine = String.format("%-4d %-16s %s\n", trace.hop, "*", "响应超时")
                    trace.hop++
                    totalHops++
                    continue
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    str += line
                }
                reader.close()
                process.destroy()

                val m: Matcher = patternTrace.matcher(str)
                if (m.find()) {
                    val pingIp = m.group()
                    if (pingIp.isNullOrEmpty()) {
                        trace.hop++
                        continue
                    }

                    validHops++
                    validIpList.add(pingIp)

                    val status = runPingCommand(pingIp, false)
                    if (status.isEmpty()) {
                        log.append("非法主机或网络错误\n")
                        finish = true
                    } else if (status.endsWith("超时")) {
                        val logLine = String.format("%-4d %-16s %s\n", trace.hop, pingIp, "超时")
                        log.append(logLine)
                        timeoutCount++
                    } else {
                        val patternTime = Pattern.compile("(\\d+(\\.\\d+)?) ms")
                        val matcherTime: Matcher = patternTime.matcher(status)
                        if (matcherTime.find()) {
                            val timeStr = matcherTime.group(1)
                            val time = timeStr.toDoubleOrNull() ?: 0.0
                            val displayTime = if (time == 0.0) "< 1 ms" else "$time ms"

                            if (time >= 0.0) {
                                rttTimes.add(time)
                            }

                            val logLine = String.format("%-4d %-16s %s\n", trace.hop, pingIp, displayTime)
                            log.append(logLine)
                            successfulHops++

                            // 计算IP的评分
                            val ipScore = calculateScoreForIp(time, timeoutCount, validHops)
                            ipScores[pingIp] = ipScore
                        } else {
                            timeoutCount++
                        }
                    }
                    trace.hop++
                    totalHops++
                } else {
                    val logLine = String.format("%-4d %-16s %s\n", trace.hop, "*", "响应超时")
                    trace.hop++
                    totalHops++
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        if (validIpList.isNotEmpty()) {
            log.append("有效的 IP 列表: ").append(validIpList.joinToString(", ")).append("\n")
        } else {
            log.append("没有有效的 IP 地址\n")
        }

        val analysisReport = generateAnalysisReport(successfulHops, timeoutCount, rttTimes, validHops, ipScores)
        log.append("TraceRoute IP: ${trace.host}\n")
        log.append(analysisReport).append("\n")

        // 添加总分计算和打印
        val totalScore = if (ipScores.isNotEmpty()) {
            ipScores.values.average().toInt() // 计算所有 IP 评分的平均值作为总分
        } else {
            0 // 如果没有有效 IP，默认总分为 0
        }
        log.append("总评分: ").append(totalScore).append("/100")

        listener?.onTraceRouteUpdate(log.toString())
    }

    private fun calculateScoreForIp(rtt: Double, timeoutCount: Int, validHops: Int): Int {
        var score = 100
        // 根据 RTT 时间来扣分
        score -= when {
            rtt < 100 -> 0
            rtt < 300 -> 10
            else -> 20
        }

        // 根据超时次数来扣分
        if (timeoutCount > 0) {
            score -= 10 * timeoutCount / validHops
        }

        return score.coerceIn(0, 100) // 确保分数在 0 到 100 之间
    }

    private fun generateAnalysisReport(
        successfulHops: Int,
        timeoutCount: Int,
        rttTimes: List<Double>,
        validHops: Int,
        ipScores: Map<String, Int>
    ): String {
        val log = StringBuilder("\nTraceroute 分析报告:\n")
        log.append("成功跳数: ").append(successfulHops).append("/").append(validHops).append("\n")
        log.append("超时跳数: ").append(timeoutCount).append("/").append(validHops).append("\n")

        val timeoutRate = if (validHops > 0) {
            (timeoutCount.toDouble() / validHops) * 100
        } else {
            null
        }
        timeoutRate?.let {
            log.append("超时率: ").append(String.format("%.2f", it)).append("%\n")
        }

        if (rttTimes.isNotEmpty()) {
            val maxRtt = rttTimes.maxOrNull() ?: 0.0
            val minRtt = rttTimes.minOrNull() ?: 0.0
            val avgRtt = rttTimes.average()

            log.append("最大往返时间: ").append(String.format("%.2f ms", maxRtt)).append("\n")
            log.append("最小往返时间: ").append(String.format("%.2f ms", minRtt)).append("\n")
            log.append("平均往返时间: ").append(String.format("%.2f ms", avgRtt)).append("\n")
        }

        log.append("\nIP评分:\n")
        for ((ip, score) in ipScores) {
            log.append("$ip -> 评分: $score\n")
        }

        return log.toString()
    }

    interface NetworkTraceListener {
        fun onTraceRouteUpdate(log: String)
    }

    private class TraceRouteStep(val host: String, var hop: Int)
}