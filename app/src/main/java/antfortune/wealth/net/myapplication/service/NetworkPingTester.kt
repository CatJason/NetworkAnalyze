package antfortune.wealth.net.myapplication.service

import antfortune.wealth.net.myapplication.utils.LDPingParse
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

class NetworkPingTester(private val pinListener: LDNetPingListener, private val mSendCount: Int) {

    companion object {
        const val MATCH_PING_IP = "(?<=from ).*(?=: icmp_seq=1 ttl=)"
    }

    private var successfulPings = 0
    private var failedPings = 0

    /**
     * 监控NetPing的日志输出到Service
     * @author panghui
     */
    interface LDNetPingListener {
        fun onNetPingFinished(log: String)
    }

    /**
     * 执行 ping 命令，返回控制台输出。
     *
     * @param host 要 ping 的主机地址
     * @param isNeedL 是否需要指定数据包大小
     * @return 返回 ping 命令的控制台输出
     */
    private fun runPingCommand(host: String, isNeedL: Boolean): String {
        val cmd = if (isNeedL) {
            "ping -s 8185 -c "
        } else {
            "ping -c "
        }
        var process: Process? = null
        val result = StringBuilder()

        return try {
            // 构建完整的 ping 命令
            val fullCmd = "$cmd$mSendCount $host"

            // 执行命令
            process = Runtime.getRuntime().exec(fullCmd)

            // 读取控制台输出
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line).append(System.lineSeparator())

                    // 记录成功和失败的 ping 计数
                    if (line!!.contains("bytes from")) {
                        successfulPings++ // 成功的 ping
                    } else if (line!!.contains("Request timeout")) {
                        failedPings++ // 失败的 ping
                    }
                }
            }

            // 等待进程执行完成
            process.waitFor()
            result.toString().trim() // 去除多余的换行符
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // 重新设置线程中断状态
            e.printStackTrace()
            ""
        } finally {
            process?.destroy()
        }
    }
    fun startTraceroutePing(preString: String, ip: String, isNeedL: Boolean) {
        val log = StringBuilder(256)

        val rttTimes = mutableListOf<Long>() // 用于存储每次 ping 的 RTT

        // 执行 ping 命令并获取输出状态
        val startTime = System.currentTimeMillis() // 记录开始时间
        val status = runPingCommand(ip, isNeedL)
        val endTime = System.currentTimeMillis() // 记录结束时间

        // 计算往返时间（RTT）
        val rtt = endTime - startTime
        if (rtt > 0) {
            rttTimes.add(rtt)
        }

        // 检查 ping 结果中是否匹配指定的 IP 格式
        if (Pattern.compile(MATCH_PING_IP).matcher(status).find()) {
            log.append("\t").append(status)
            successfulPings++ // 增加成功的 ping 计数
        } else {
            // 处理 ping 失败的逻辑
            when {
                status.isEmpty() -> {
                    log.append("非法主机或网络错误\n")
                    failedPings++ // 未知主机或网络错误时，增加失败的 ping 计数
                }
                else -> {
                    log.append("响应超时\n")
                    failedPings++ // ping 超时时增加失败的 ping 计数
                }
            }
        }

        // 计算丢包率
        val totalPings = successfulPings + failedPings
        val lossRate = if (totalPings > 0) {
            (failedPings * 100) / totalPings
        } else {
            100 // 如果总的 ping 数为 0，默认丢包率为 100%（意味着完全无法连接）
        }

        // 记录 ping 结果和丢包率
        val logStr = LDPingParse.getFormattingStr(ip, log.toString())
        logStr.append(preString).append("丢包率: $lossRate%\n")

        // 计算抖动（极差或标准差）
        var jitter: Long = 0L
        if (rttTimes.size > 1) {
            jitter = calculateJitter(rttTimes)
            logStr.append("网络抖动: $jitter ms\n")
        } else {
            logStr.append("未出现网络抖动\n")
        }

        // 对 Traceroute 结果进行总结分析
        val summary = analyzeTracerouteResult(lossRate, jitter)
        logStr.append("网络诊断总结: $summary\n\n")
        pinListener.onNetPingFinished(logStr.toString())

        // 重置计数器
        successfulPings = 0
        failedPings = 0
    }

    // 计算 RTT 列表的抖动 (标准差或极差)
    private fun calculateJitter(rttTimes: List<Long>): Long {
        if (rttTimes.size < 2) return 0L

        // 计算极差（最大RTT减去最小RTT）
        val maxRtt = rttTimes.maxOrNull() ?: 0L
        val minRtt = rttTimes.minOrNull() ?: 0L
        return maxRtt - minRtt
    }

    // 分析 Traceroute 结果，根据丢包率和抖动给出总结
    private fun analyzeTracerouteResult(lossRate: Int, jitter: Long): String {
        return when {
            lossRate == 100 -> {
                "网络连接极差或无法连接"
            }
            lossRate > 30 -> {
                "网络连接较差，丢包率高达 $lossRate%，可能存在严重的网络问题"
            }
            jitter > 100 -> {
                "网络连接一般，抖动较高 ($jitter ms)，可能导致延迟不稳定"
            }
            lossRate in 1..30 -> {
                "网络连接较好，丢包率为 $lossRate%，但仍有少量丢包"
            }
            else -> {
                "网络连接非常好，丢包率接近 0%，抖动较小 ($jitter ms)"
            }
        }
    }

}
