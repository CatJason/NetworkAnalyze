package antfortune.wealth.net.myapplication.service

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

        // 关闭线程池
        executorService.shutdown()

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
        val successfulConnections = rttTimes.count { it > 0 }
        log.append("成功连接次数: ").append(successfulConnections).append("/").append(CONN_TIMES).append("\n")

        val timeouts = rttTimes.count { it == -1L }
        val ioErrors = rttTimes.count { it == -2L }
        log.append("连接超时次数: ").append(timeouts).append("/").append(CONN_TIMES).append("\n")
        log.append("IO异常次数: ").append(ioErrors).append("/").append(CONN_TIMES).append("\n")

        val successfulRttTimes = rttTimes.filter { it > 0 }
        if (successfulRttTimes.isNotEmpty()) {
            val averageRtt = successfulRttTimes.average().toLong()
            log.append("平均连接耗时: ").append(averageRtt).append(" ms\n")
        } else {
            log.append("无成功的连接，无法计算平均连接时间。\n")
        }

        if (successfulConnections == CONN_TIMES) {
            log.append("网络连接状态：优秀\n\n")
        } else if (successfulConnections > 0) {
            log.append("网络连接状态：一般，存在部分超时或异常。\n\n")
        } else {
            log.append("网络连接状态：较差，所有连接均失败。\n\n")
        }

        return log.toString()
    }
}
