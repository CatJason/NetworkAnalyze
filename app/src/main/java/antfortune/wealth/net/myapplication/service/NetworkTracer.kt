package antfortune.wealth.net.myapplication.service

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 通过ping模拟traceroute过程
 *
 * @author panghui
 */
class NetworkTracer private constructor() {
    private var listener: NetworkTraceListener? = null

    companion object {
        private var instance: NetworkTracer? = null

        fun getInstance(): NetworkTracer {
            if (instance == null) {
                instance = NetworkTracer()
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
        val trace = TraceRouteStep(host, 1)
        executeTraceRoute(trace)
    }

    fun resetInstance() {
        instance = null
    }

    private fun executePingCommand(ping: PingCommand): String {
        var process: Process? = null
        var result = ""
        var reader: BufferedReader? = null
        try {
            process = Runtime.getRuntime().exec("ping -c 1 " + ping.host)
            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result += line
            }
            process.waitFor()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            try {
                reader?.close()
                process?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }
    private fun executeTraceRoute(trace: TraceRouteStep) {
        val log = StringBuilder()  // 用于存储所有日志
        val patternTrace = Pattern.compile(MATCH_TRACE_IP)
        val patternIp = Pattern.compile(MATCH_PING_IP)
        val patternTime = Pattern.compile(MATCH_PING_TIME)

        var process: Process? = null
        var reader: BufferedReader? = null
        var finish = false
        var timeoutCount = 0
        var successfulHops = 0
        val rttTimes = mutableListOf<Long>()
        var totalHops = 0

        try {
            while (!finish && trace.hop < 30) {
                var str = ""
                val command = "ping -c 1 -t ${trace.hop} ${trace.host}"

                process = Runtime.getRuntime().exec(command)
                reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    str += line
                }
                process.waitFor()

                val m: Matcher = patternTrace.matcher(str)
                if (m.find()) {
                    val pingIp = m.group()
                    val pingTask = PingCommand(pingIp)

                    val status = executePingCommand(pingTask)
                    if (status.isEmpty()) {
                        log.append("非法主机或网络错误\n")
                        finish = true
                    } else {
                        val matcherTime: Matcher = patternTime.matcher(status)
                        if (matcherTime.find()) {
                            val time = matcherTime.group().replace(" ms", "").toLongOrNull() ?: 0L
                            val displayTime = if (time == 0L) "< 1ms\n" else "$time ms\n"

                            if (time >= 0L) {
                                rttTimes.add(time)
                            }

                            log.append(trace.hop).append("\t\t")
                                .append(pingIp).append("\t\t")
                                .append(displayTime).append("\t")
                            successfulHops++
                        } else {
                            log.append(trace.hop).append("\t\t")
                                .append(pingIp).append("\t\t 响应超时 \t")
                            timeoutCount++
                        }
                        trace.hop++
                    }
                } else {
                    val matchPingIp: Matcher = patternIp.matcher(str)
                    if (matchPingIp.find()) {
                        val pingIp = matchPingIp.group()
                        val matcherTime: Matcher = patternTime.matcher(str)
                        if (matcherTime.find()) {
                            val time = matcherTime.group().replace(" ms", "").toLongOrNull() ?: 0L
                            val displayTime = if (time == 0L) "< 1ms\n" else "$time ms\n"

                            if (time > 0L) {
                                rttTimes.add(time)
                            }

                            log.append(trace.hop).append("\t\t")
                                .append(pingIp).append("\t\t")
                                .append(displayTime).append("\t")
                            successfulHops++
                            finish = true
                        }
                    } else {
                        if (str.isEmpty()) {
                            log.append("非法主机或网络错误\t")
                            finish = true
                        } else {
                            log.append(trace.hop).append("\t\t 响应超时 \t")
                            timeoutCount++
                            trace.hop++
                        }
                    }
                }
                totalHops++
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            try {
                reader?.close()
                process?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 调用分析报告生成函数并将报告合并到日志中
        val analysisReport = generateAnalysisReport(successfulHops, timeoutCount, rttTimes, totalHops)
        log.append("TraceRoute IP: ${trace.host}\n")
        log.append(analysisReport).append("\n")

        // 一次性打印所有日志
        listener?.onTraceRouteUpdate(log.toString())
        listener?.onTraceRouteComplete()
    }

    // 生成 Traceroute 结果的分析报告
    private fun generateAnalysisReport(successfulHops: Int, timeoutCount: Int, rttTimes: List<Long>, totalHops: Int): String {
        val log = StringBuilder("\nTraceroute 分析报告:\n")

        // 成功的跳数和超时的跳数
        log.append("成功跳数: ").append(successfulHops).append("/").append(totalHops).append("\n")
        log.append("超时跳数: ").append(timeoutCount).append("/").append(totalHops).append("\n")

        // 计算超时率
        val timeoutRate = (timeoutCount.toDouble() / totalHops) * 100
        log.append("超时率: ").append(String.format("%.2f", timeoutRate)).append("%\n")

        // 最大RTT、最小RTT、平均RTT
        if (rttTimes.isNotEmpty()) {
            val maxRtt = rttTimes.maxOrNull() ?: 0L
            val minRtt = rttTimes.minOrNull() ?: 0L
            val avgRtt = rttTimes.average()

            if (maxRtt == 0L) {
                log.append("最大往返时间: < 1 ms\n")
            } else {
                log.append("最大往返时间: ").append(maxRtt).append(" ms\n")
            }

            if (minRtt == 0L) {
                log.append("最小往返时间: < 1 ms\n")
            } else {
                log.append("最小往返时间: ").append(minRtt).append(" ms\n")
            }
            if(avgRtt == 0.0) {
                log.append("平均往返时间: ").append("< 1ms").append(" ms\n")
            } else {
                log.append("平均往返时间: ").append(String.format("%.2f", avgRtt)).append(" ms\n")
            }

        } else {
            log.append("没有可用的往返时间数据\n")
        }

        log.append("网络连接超时率分析: ")
        if (successfulHops == totalHops && timeoutCount == 0) {
            log.append("网络连接非常稳定，无丢包和超时。\n")
        } else if (timeoutRate < 10) {
            log.append("网络连接稳定，但存在少量超时，可能是中间节点的防火墙设置或ICMP流量限制导致的。\n")
        } else if (timeoutRate in 10.0..50.0) {
            log.append("网络存在一定的延迟和超时，可能会影响连接稳定性。某些中间设备可能对ICMP消息进行了限制。\n")
        } else if (timeoutRate > 50) {
            log.append("网络连接不稳定，超时率较高，可能有大量丢包或网络设备配置导致响应失败。\n")
        }

        log.append("超时的具体情况分析:\n")
        if (timeoutCount > 0 && successfulHops > 0) {
            if (timeoutCount == totalHops - 1) {
                log.append("注意：Traceroute 最后一跳响应成功，可能是中间路由器配置了防火墙或ICMP消息过滤，影响中间跳的响应。\n")
            } else {
                log.append("注意：某些中间路由器跳出现超时，这可能是因为路由器对ICMP消息的限制，但最终目标仍然可以到达。\n")
            }
        } else if (timeoutCount == totalHops) {
            log.append("注意：整个路径存在超时，可能网络完全不可达，或者目标主机或网络设备屏蔽了Traceroute的请求。\n")
        }

        log.append("网络延迟分析:\n")
        if (rttTimes.isNotEmpty()) {
            val maxRtt = rttTimes.maxOrNull() ?: 0L
            if (maxRtt > 500) {
                log.append("网络延迟较高，可能存在较大的网络拥塞或跨国连接。\n")
            } else if (maxRtt in 101..500) {
                log.append("网络延迟在正常范围内，适合常规的网络连接使用。\n")
            } else {
                log.append("网络延迟很低，适合高实时性要求的网络应用。\n")
            }
        }

        return log.toString()
    }


    interface NetworkTraceListener {
        fun onTraceRouteUpdate(log: String)
        fun onTraceRouteComplete()
    }

    private inner class PingCommand(var host: String) {
        init {
            val p = Pattern.compile("(?<=\\().*?(?=\\))")
            val m: Matcher = p.matcher(host)
            if (m.find()) {
                this.host = m.group()
            }
        }
    }

    private class TraceRouteStep(val host: String, var hop: Int)
}
