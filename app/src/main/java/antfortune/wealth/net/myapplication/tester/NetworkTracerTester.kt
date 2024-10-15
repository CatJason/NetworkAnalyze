package antfortune.wealth.net.myapplication.tester

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 通过 ping 模拟 traceroute 过程
 *
 * @author panghui
 */
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
        private const val MATCH_PING_IP = "(?<=from ).*(?=: icmp_seq=1 ttl=)"
        private const val MATCH_PING_TIME = "(?<=time=).*?ms"
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
        val sendCount = 1 // 发送次数
        var process: Process? = null
        val result = StringBuilder()

        return try {
            // 构建 ping 命令
            val fullCmd = "${cmd}${sendCount} $host"

            // 执行命令
            process = Runtime.getRuntime().exec(fullCmd)

            // 创建一个线程来等待进程完成
            val waitThread = Thread {
                try {
                    process.waitFor()
                } catch (e: InterruptedException) {
                    // 如果线程被中断，销毁进程
                    process.destroy()
                }
            }
            waitThread.start()

            // 在主线程中等待指定的超时时间
            waitThread.join(1000) // 1000 毫秒

            if (waitThread.isAlive) {
                // 超时，销毁进程并中断线程
                process.destroy()
                waitThread.interrupt()
                return "$host 超时" // 返回 IP 地址 + "超时"
            }

            // 读取命令输出
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line).append(System.lineSeparator())
                }
            }

            result.toString().trim() // 去除多余的换行符
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
        val log = StringBuilder()  // 用于存储所有日志
        val patternTrace = Pattern.compile(MATCH_TRACE_IP)

        // 检查 host 是否为空
        if (trace.host.isEmpty()) {
            log.append("主机地址为空，无法执行 Traceroute\n")
            listener?.onTraceRouteUpdate(log.toString())
            return
        }

        // 在最前面打印 TraceRoute 的目标 IP 地址
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
        var validHops = 0 // 新增变量，统计有效的跳数
        val validIpList = mutableListOf<String>() // 新增变量，存储有效的 IP

        try {
            while (!finish && trace.hop <= 15) {
                var str = ""
                val command = "ping -c 1 -t ${trace.hop} ${trace.host}"

                val process = Runtime.getRuntime().exec(command)

                // 创建一个线程来等待进程完成
                val waitThread = Thread {
                    try {
                        process.waitFor()
                    } catch (e: InterruptedException) {
                        process.destroy()
                    }
                }
                waitThread.start()

                // 在主线程中等待指定的超时时间
                waitThread.join(460) // 460 毫秒

                if (waitThread.isAlive) {
                    // 超时，销毁进程并中断线程
                    process.destroy()
                    waitThread.interrupt()

                    val logLine = String.format("%-4d %-16s %s\n", trace.hop, "*", "响应超时")
                    log.append(logLine)
                    trace.hop++
                    totalHops++
                    continue
                }

                // 读取命令输出
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
                        // pingIp 为空，跳过本次循环
                        trace.hop++
                        continue
                    }

                    validHops++ // 仅在成功获取到 IP 时，增加有效跳数
                    validIpList.add(pingIp) // 添加有效 IP 到列表

                    val status = runPingCommand(pingIp, false)
                    if (status.isEmpty()) {
                        log.append("非法主机或网络错误\n")
                        finish = true
                    } else if (status.endsWith("超时")) {
                        // 处理超时的情况
                        val logLine = String.format("%-4d %-16s %s\n", trace.hop, pingIp, "超时")
                        log.append(logLine)
                        timeoutCount++ // 超时次数仅在有效跳数中统计
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
                        } else {
                            val logLine = String.format("%-4d %-16s %s\n", trace.hop, pingIp, "响应超时")
                            log.append(logLine)
                            timeoutCount++ // 超时次数仅在有效跳数中统计
                        }
                    }
                    trace.hop++
                    totalHops++
                } else {
                    // 未找到 IP，跳过，不计入有效跳数和超时次数
                    val logLine = String.format("%-4d %-16s %s\n", trace.hop, "*", "响应超时")
                    log.append(logLine)
                    trace.hop++
                    totalHops++
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // 添加打印有效 IP 的一行
        if (validIpList.isNotEmpty()) {
            log.append("\n有效的 IP 列表: ").append(validIpList.joinToString(", ")).append("\n")
        } else {
            log.append("\n没有有效的 IP 地址\n")
        }

        // 调用分析报告生成函数并将报告合并到日志中
        val analysisReport = generateAnalysisReport(successfulHops, timeoutCount, rttTimes, validHops)
        log.append("TraceRoute IP: ${trace.host}\n")
        log.append(analysisReport).append("\n")

        // 一次性打印所有日志
        listener?.onTraceRouteUpdate(log.toString())
    }

    // 生成 Traceroute 结果的分析报告
    private fun generateAnalysisReport(
        successfulHops: Int,
        timeoutCount: Int,
        rttTimes: List<Double>,
        validHops: Int
    ): String {
        val log = StringBuilder("\nTraceroute 分析报告:\n")

        // 成功的跳数和超时的跳数
        log.append("成功跳数: ").append(successfulHops).append("/").append(validHops).append("\n")
        log.append("超时跳数: ").append(timeoutCount).append("/").append(validHops).append("\n")

        // 计算超时率，只基于有效跳数
        val timeoutRate = if (validHops > 0) {
            (timeoutCount.toDouble() / validHops) * 100
        } else {
            null
        }
        if (timeoutRate != null) {
            log.append("超时率: ").append(String.format("%.2f", timeoutRate)).append("%\n")
        } else {
            log.append("超时率: 无法计算（有效跳数为零）\n")
        }

        // 最大RTT、最小RTT、平均RTT
        if (rttTimes.isNotEmpty()) {
            val maxRtt = rttTimes.maxOrNull() ?: 0.0
            val minRtt = rttTimes.minOrNull() ?: 0.0
            val avgRtt = rttTimes.average()

            fun formatRtt(name: String, rtt: Double): String {
                val threshold = 1.0
                return if (rtt < threshold) {
                    "$name: < 1 ms\n"
                } else {
                    "$name: ${String.format("%.2f", rtt)} ms\n"
                }
            }

            log.append(formatRtt("最大往返时间", maxRtt))
            log.append(formatRtt("最小往返时间", minRtt))
            log.append(formatRtt("平均往返时间", avgRtt))
        } else {
            log.append("没有可用的往返时间数据\n")
        }

        // 网络连接超时率分析
        log.append("\n网络连接超时率分析:\n")
        if (timeoutRate != null) {
            when {
                successfulHops == validHops && timeoutCount == 0 -> {
                    log.append("网络连接非常稳定，无丢包和超时。\n")
                }
                timeoutRate < 10 -> {
                    log.append("网络连接稳定，但存在少量超时，可能是中间节点的防火墙设置或 ICMP 流量限制导致的。\n")
                }
                timeoutRate in 10.0..50.0 -> {
                    log.append("网络存在一定的延迟和超时，可能会影响连接稳定性。某些中间设备可能对 ICMP 消息进行了限制。\n")
                }
                else -> {
                    log.append("网络连接不稳定，超时率较高，可能有大量丢包或网络设备配置导致响应失败。\n")
                }
            }
        } else {
            log.append("无法进行超时率分析（有效跳数为零）。\n")
        }

        // 超时的具体情况分析
        log.append("\n超时的具体情况分析:\n")
        when {
            timeoutCount > 0 && successfulHops > 0 -> {
                if (timeoutCount == validHops - 1) {
                    log.append("注意：Traceroute 最后一跳响应成功，可能是中间路由器配置了防火墙或 ICMP 消息过滤，影响中间跳的响应。\n")
                } else {
                    log.append("注意：某些中间路由器跳出现超时，这可能是因为路由器对 ICMP 消息的限制，但最终目标仍然可以到达。\n")
                }
            }
            timeoutCount == validHops -> {
                log.append("注意：有效跳数中所有请求都超时，可能网络完全不可达，或者目标主机或网络设备屏蔽了 Traceroute 的请求。\n")
            }
            else -> {
                log.append("没有超时的情况，网络连接良好。\n")
            }
        }

        // 网络延迟分析
        log.append("\n网络延迟分析:\n")
        if (rttTimes.isNotEmpty()) {
            val avgRtt = rttTimes.average()
            when {
                avgRtt > 500 -> {
                    log.append("网络延迟较高，可能存在网络拥塞或长距离连接。\n")
                }
                avgRtt in 100.0..500.0 -> {
                    log.append("网络延迟适中，适合一般网络应用。\n")
                }
                else -> {
                    log.append("网络延迟较低，适合对实时性要求高的应用。\n")
                }
            }
        } else {
            log.append("无法进行网络延迟分析（没有 RTT 数据）。\n")
        }

        return log.toString()
    }


    interface NetworkTraceListener {
        fun onTraceRouteUpdate(log: String)
    }

    private class TraceRouteStep(val host: String, var hop: Int)
}
