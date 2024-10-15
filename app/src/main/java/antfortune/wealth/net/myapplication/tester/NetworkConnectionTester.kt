package antfortune.wealth.net.myapplication.tester

import antfortune.wealth.net.myapplication.NetworkAnalyzeListener
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class NetworkConnectionTester private constructor() {

    private var listener: NetworkAnalyzeListener? = null
    private var timeOut = 6000
    private var remoteInet: List<InetAddress>? = null
    private var remoteIpList: List<String>? = null
    private val rttTimes = LongArray(CONN_TIMES) // 用于存储每次测试中每次的RTT值

    companion object {
        private const val PORT = 80
        private const val CONN_TIMES = 4
        private const val TIMEOUT = "DNS解析正常，连接超时，TCP建立失败"
        private const val IOERR = "DNS解析正常，IO异常，TCP建立失败"
        private const val HOSTERR = "DNS解析失败，主机地址不可达"
        var instance: NetworkConnectionTester? = null
            get() {
                if (field == null) {
                    field = NetworkConnectionTester()
                }
                return field
            }
            private set
    }

    /**
     * 初始化测试并启动
     */
    fun initAndStartTest(
        remoteInet: List<InetAddress>?,
        remoteIpList: List<String>?,
        listener: NetworkAnalyzeListener,
        timeOut: Int = 6000
    ): Boolean {
        this.remoteInet = remoteInet
        this.remoteIpList = remoteIpList
        this.listener = listener
        this.timeOut = timeOut

        // 检查是否有有效的IP列表
        if (remoteInet.isNullOrEmpty() || remoteIpList.isNullOrEmpty()) {
            logConnectionDetails(HOSTERR)
            return false
        }

        // 开始连接测试
        return testConnection()
    }

    /**
     * 通过connect函数测试TCP的RTT时延
     */
    private fun testConnection(): Boolean {
        return testConnectionWithJava()
    }

    /**
     * 使用Java多线程执行connected
     */
    private fun testConnectionWithJava(): Boolean {
        val inetList = remoteInet
        val ipList = remoteIpList

        if (inetList == null || ipList == null) {
            logConnectionDetails(HOSTERR)
            return false
        }

        // 创建一个固定大小的线程池
        val executorService: ExecutorService = Executors.newFixedThreadPool(ipList.size)

        // 保存每个任务的 Future 结果
        val tasks: MutableList<Future<Boolean>> = mutableListOf()
        // 提交每个 IP 地址的连接任务
        for (i in inetList.indices) {
            val future: Future<Boolean> = executorService.submit(Callable {
                val isSuccessful = testSingleIPConnection(inetList[i], ipList[i])
                isSuccessful
            })
            tasks.add(future)
        }

        // 等待所有任务完成并检查是否有成功的连接
        var connectionSuccessful = false
        for (task in tasks) {
            try {
                if (task.get()) {
                    connectionSuccessful = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 在所有任务完成后，计算平均分数
        val successfulRttTimes = rttTimes.filter { it > 0 }
        if (successfulRttTimes.isNotEmpty()) {
            val score = calculateConnectionScore(
                successfulConnections = successfulRttTimes.size,
                totalConnections = CONN_TIMES,
                rttTimes = successfulRttTimes,
                timeouts = rttTimes.count { it == -1L },
                ioErrors = rttTimes.count { it == -2L },
                reportLog = StringBuilder() // 可以将报告保存或打印
            )

            // 调用回调将分数返回
            listener?.onTcpTestScoreReceived(score)
        } else {
            listener?.onTcpTestScoreReceived(0) // 若无成功连接，则返回0分
        }

        // 关闭线程池
        executorService.shutdown()
        listener?.onTcpTestCompleted()

        return connectionSuccessful
    }

    private fun testSingleIPConnection(inetAddress: InetAddress?, ip: String?): Boolean {
        var isConnected = true
        val log = StringBuilder()

        if (inetAddress == null || ip == null) {
            log.append("无效的 inetAddress 或 IP\n")
            logConnectionDetails(log.toString()) // 一次性打印日志
            return false
        }

        val socketAddress = InetSocketAddress(inetAddress, PORT)
        var timeOutIncrement = timeOut
        var connectionStatusFlag = 0
        val successfulRttTimes = mutableListOf<Long>()

        log.append("正在连接主机: $ip...\n")

        for (i in 0 until CONN_TIMES) {
            testSocketConnection(socketAddress, timeOutIncrement, i)
            when (rttTimes[i]) {
                -1L -> {
                    log.append("第${i + 1}次, 耗时: $TIMEOUT\n")
                    timeOutIncrement += 4000
                    if (i > 0 && rttTimes[i - 1] == -1L) {
                        connectionStatusFlag = -1
                        break
                    }
                }
                -2L -> {
                    log.append("第${i + 1}次, 耗时: $IOERR\n")
                    if (i > 0 && rttTimes[i - 1] == -2L) {
                        connectionStatusFlag = -2
                        break
                    }
                }
                else -> {
                    log.append("第${i + 1}次, 耗时: ${rttTimes[i]}ms\n")
                    successfulRttTimes.add(rttTimes[i])
                }
            }
        }

        if (connectionStatusFlag == 0 && successfulRttTimes.isNotEmpty()) {
            val averageTime = successfulRttTimes.average().toLong()
            log.append("平均耗时: ").append(averageTime).append("ms")
        } else {
            isConnected = false
        }


        // 一次性打印所有日志
        if (isConnected) {
            log.append("\n").append("连接成功\n")
        } else {
            log.append("\n").append("连接失败\n")
        }

        val report = generateTcpConnectionReport(ip)
        logConnectionDetails(log.append(report).toString())

        return isConnected
    }

    private fun testSocketConnection(socketAddress: InetSocketAddress, timeOut: Int, attemptIndex: Int) {
        var socket: Socket? = null
        val start: Long
        val end: Long
        try {
            socket = Socket()
            start = System.currentTimeMillis()
            socket.connect(socketAddress, timeOut)
            end = System.currentTimeMillis()
            rttTimes[attemptIndex] = end - start
        } catch (e: SocketTimeoutException) {
            rttTimes[attemptIndex] = -1
            logConnectionDetails("第${attemptIndex + 1}次连接超时")
        } catch (e: IOException) {
            rttTimes[attemptIndex] = -2
            logConnectionDetails("第${attemptIndex + 1}次发生IO异常")
        } finally {
            socket?.close()
        }
    }

    private fun logConnectionDetails(log: String) {
        listener?.onTcpTestUpdated(log)
    }

    private fun generateTcpConnectionReport(ip: String?): String {
        val log = StringBuilder("\nTCP 连接耗时分析报告 (IP: $ip):\n")

        // 成功连接的次数
        val successfulConnections = rttTimes.count { it > 0 }
        log.append("成功连接次数: ").append(successfulConnections).append("/").append(CONN_TIMES).append("\n")

        // 连接超时和 IO 异常的统计
        val timeouts = rttTimes.count { it == -1L }
        val ioErrors = rttTimes.count { it == -2L }
        log.append("连接超时次数: ").append(timeouts).append("/").append(CONN_TIMES).append("\n")
        log.append("IO异常次数: ").append(ioErrors).append("/").append(CONN_TIMES).append("\n")

        // 成功连接的 RTT 计算
        val successfulRttTimes = rttTimes.filter { it > 0 }
        if (successfulRttTimes.isNotEmpty()) {
            val averageRtt = successfulRttTimes.average().toLong()
            log.append("平均连接耗时: ").append(averageRtt).append(" ms\n")
        } else {
            log.append("无成功的连接，无法计算平均连接时间。\n")
        }

        // 计算网络评分
        val score = calculateConnectionScore(successfulConnections, CONN_TIMES, successfulRttTimes, timeouts, ioErrors, log)
        log.append("网络评分: ").append(score).append("/100\n")

        // 根据评分结果输出网络状态
        when {
            successfulConnections == CONN_TIMES -> log.append("网络连接状态：优秀\n\n")
            successfulConnections > 0 -> log.append("网络连接状态：一般，存在部分超时或异常。\n\n")
            else -> log.append("网络连接状态：较差，所有连接均失败。\n\n")
        }
        return log.toString()
    }

    /**
     * 计算网络评分，最高 100 分，并打印哪些部分没有达到满分以及原因
     */
    /**
     * 计算网络评分，最高 100 分，基于连接成功率、RTT 和超时/IO 异常表现
     * 如果没有成功连接，分数为 0，并打印每一项打分的理由
     */
    private fun calculateConnectionScore(
        successfulConnections: Int,
        totalConnections: Int,
        rttTimes: List<Long>,
        timeouts: Int,
        ioErrors: Int,
        reportLog: StringBuilder
    ): Int {
        var score = 0

        // 1. 基础分数：成功连接比例，最高 60 分
        if (successfulConnections == 0) {
            // 无成功连接的情况下，分数为 0
            reportLog.append("无成功连接，评分为 0。\n")
            score = 0
            logConnectionDetails(reportLog.toString())
            return score
        } else {
            val connectionScore = (successfulConnections.toDouble() / totalConnections * 60).toInt()
            score += connectionScore
            reportLog.append("连接成功率为 ").append(successfulConnections).append("/").append(totalConnections)
                .append("，获得 ").append(connectionScore).append(" 分。\n")
        }

        // 2. RTT 分数：根据 RTT 时间，最高 20 分
        if (rttTimes.isNotEmpty()) {
            val averageRtt = rttTimes.average().toLong()
            val rttScore = when {
                averageRtt < 100 -> 20  // RTT < 100ms，加满分 20 分
                averageRtt in 100..300 -> 10  // RTT 100-300ms，加 10 分
                averageRtt in 300..500 -> 5  // RTT 300-500ms，加 5 分
                else -> 0  // RTT > 500ms，0 分
            }
            score += rttScore
            reportLog.append("RTT 平均值为 ").append(averageRtt).append(" ms，获得 ").append(rttScore).append(" 分。\n")
        } else {
            reportLog.append("无成功连接，无法计算 RTT 分数。\n")
        }

        // 3. 超时和 IO 异常表现，最多 20 分
        val stableConnectionScore = when {
            (timeouts + ioErrors) == 0 -> 20  // 没有超时和 IO 异常，满分 20 分
            (timeouts + ioErrors) < totalConnections / 2 -> 10  // 少量超时和 IO 异常，加 10 分
            else -> 5  // 较多超时和 IO 异常，加 5 分
        }
        score += stableConnectionScore
        reportLog.append("超时次数: ").append(timeouts).append("，IO 异常次数: ").append(ioErrors)
            .append("，获得 ").append(stableConnectionScore).append(" 分。\n")

        // 确保评分在 0 到 100 分之间
        return score.coerceIn(0, 100)
    }
}

