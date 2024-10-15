package antfortune.wealth.net.myapplication.tester

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class NetworkPingTester(private val pinListener: LDNetPingListener, private val mSendCount: Int) {

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
        var packetLoss = 0
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

        // 成功 Ping 次数作为基础分 60 分
        val successfulPingPercentage = (successfulPings.toDouble() / (successfulPings + failedPings)) * 100
        val pingBaseScore = (60 * (successfulPingPercentage / 100)).toInt()
        analysis.append("成功 Ping 次数得分：$pingBaseScore / 60\n")

        // 丢包率评分
        val packetLossScore = when (packetLoss) {
            0 -> 10
            in 1..10 -> 10 - packetLoss // 动态扣分，丢包率越高分数越低
            in 11..30 -> 5 // 中等丢包率，固定分数
            else -> 0 // 丢包率过高，最低分
        }
        analysis.append("丢包率得分：$packetLossScore / 10 (丢包率: $packetLoss%)\n")

        // 平均 RTT 评分
        val avgRttScore = when {
            avgRtt < 50 -> 15
            avgRtt in 50.0..100.0 -> 10 + (100 - avgRtt).toInt() / 10
            avgRtt in 100.0..200.0 -> 5 + (200 - avgRtt).toInt() / 20
            else -> 0
        }
        analysis.append("平均 RTT 得分：$avgRttScore / 15 (平均 RTT: $avgRtt ms)\n")

        // RTT 波动评分
        val mdevRttScore = when {
            mdevRtt < 10 -> 15
            mdevRtt in 10.0..30.0 -> 10 + (30 - mdevRtt).toInt() / 2
            else -> 0
        }
        analysis.append("RTT 波动得分：$mdevRttScore / 15 (RTT 波动: $mdevRtt ms)\n")

        // 计算总分
        val totalScore = pingBaseScore + packetLossScore + avgRttScore + mdevRttScore
        analysis.append("网络总评分：$totalScore 分 (满分 100 分)\n")

        return analysis.toString()
    }
}
