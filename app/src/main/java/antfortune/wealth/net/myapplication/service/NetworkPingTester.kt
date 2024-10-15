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
        var transmittedPackets = 0
        var receivedPackets = 0
        var packetLoss = 0
        var totalTime = 0L
        var minRtt = 0.0
        var avgRtt = 0.0
        var maxRtt = 0.0
        var mdevRtt = 0.0

        // 执行 ping 命令并获取输出状态
        val status = runPingCommand(ip, isNeedL)

        // 解析 ping 输出，提取 RTT 信息
        val pingLines = status.split("\n")
        for (line in pingLines) {
            when {
                line.contains("time=") -> {
                    // 找到包含 RTT 时间的行，类似于: 64 bytes from 183.146.26.187: icmp_seq=1 ttl=56 time=6.47 ms
                    val timeIndex = line.indexOf("time=")
                    val rttString = line.substring(timeIndex + 5).split(" ")[0] // 提取时间值
                    val rtt = rttString.toDoubleOrNull() // 将 RTT 转换为 Double
                    if (rtt != null) {
                        rttTimes.add(rtt.toLong()) // 转换为 Long 并加入列表
                    }
                }
                line.contains("packets transmitted") -> {
                    // 解析传输的包数量
                    val parts = line.split(", ")
                    transmittedPackets = parts[0].split(" ")[0].toInt()
                    receivedPackets = parts[1].split(" ")[0].toInt()
                    packetLoss = parts[2].split("%")[0].toInt()
                }
                line.contains("rtt min/avg/max/mdev") -> {
                    // 解析 RTT 的统计数据
                    val parts = line.split(" = ")[1].split("/")
                    minRtt = parts[0].toDouble()
                    avgRtt = parts[1].toDouble()
                    maxRtt = parts[2].toDouble()
                    mdevRtt = parts[3].split(" ")[0].toDouble()
                }
            }
        }

        log.append(preString).append("\n")
        val analysisResult = analyzeNetworkStatus(ip, packetLoss, avgRtt, minRtt, maxRtt, mdevRtt)
        log.append(analysisResult)

        // 输出最终日志
        pinListener.onNetPingFinished(log.toString())
    }

    private fun analyzeNetworkStatus(ip: String, packetLoss: Int, avgRtt: Double, minRtt: Double, maxRtt: Double, mdevRtt: Double): String {
        val analysis = StringBuilder()

        // 分析丢包率
        when (packetLoss) {
            0 -> {
                analysis.append("$ip 的网络连接稳定，无丢包。\n")
            }
            in 1..10 -> {
                analysis.append("$ip 的网络连接有轻微丢包 (丢包率: $packetLoss%)，但不会显著影响网络性能。\n")
            }
            in 11..30 -> {
                analysis.append("$ip 的网络连接有中等丢包 (丢包率: $packetLoss%)，可能会影响应用的正常使用。\n")
            }
            else -> {
                analysis.append("$ip 的网络连接不稳定，丢包率高达 $packetLoss%，建议立即检查网络。\n")
            }
        }

        // 分析往返时间 (RTT)
        analysis.append("往返时间 (RTT) 分析:\n")
        analysis.append("最小 RTT: $minRtt ms\n")
        analysis.append("平均 RTT: $avgRtt ms\n")
        analysis.append("最大 RTT: $maxRtt ms\n")
        analysis.append("RTT 波动 (标准差): $mdevRtt ms\n")

        // 根据 RTT 数据判断网络延迟
        if (avgRtt < 50) {
            analysis.append("结论: 网络延迟非常低，适合实时应用（如语音、视频通话、在线游戏）。\n")
        } else if (avgRtt in 50.0..100.0) {
            analysis.append("结论: 网络延迟较低，大多数应用不会受到明显影响。\n")
        } else if (avgRtt in 100.0..200.0) {
            analysis.append("结论: 网络延迟较高，可能会影响实时应用的体验。\n")
        } else {
            analysis.append("结论: 网络延迟非常高，可能导致明显的卡顿和延迟，建议检查网络连接。\n")
        }

        // 根据 RTT 的波动情况分析网络的稳定性
        if (mdevRtt < 10) {
            analysis.append("RTT 波动较小，网络连接稳定。\n")
        } else if (mdevRtt in 10.0..30.0) {
            analysis.append("RTT 波动中等，可能会偶尔出现网络抖动。\n")
        } else {
            analysis.append("RTT 波动较大，网络不稳定，可能会频繁出现延迟变化。\n")
        }

        return analysis.toString()
    }

}
