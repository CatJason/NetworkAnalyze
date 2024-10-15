package antfortune.wealth.net.myapplication.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.text.TextUtils
import antfortune.wealth.net.myapplication.utils.LDNetUtil
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class NetworkAnalyzeService(
    private val context: Context,
    private val carrierName: String,
    private val listener: NetworkAnalyzeListener
) : NetworkPingTester.LDNetPingListener, NetworkAnalyzeListener {
    companion object {
        const val SERVER_URL = "www.antgroup.com"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isNetConnected = false
    private var isDomainParseOk = false
    private var isSocketConnected = false
    private var netType: String? = null
    private var localIp: String = "未知"
    private var gateWay: String = "未知"
    private var remoteInet: ArrayList<InetAddress> = ArrayList()
    private val remoteIpList: MutableList<String> = ArrayList()
    private val logInfo = StringBuilder(256)
    private var netSocker: NetworkConnectionTester? = null
    private var netPinger: NetworkPingTester? = null
    private var traceRouter: NetworkTracer? = null
    private var isRunning = false
    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
    private var sExecutor: ThreadPoolExecutor? = null

    init {
        // 核心线程数
        val corePoolSize = 4
        // 最大线程数
        val maximumPoolSize = 8
        // 线程空闲保持存活的时间
        val keepAliveTime = 1L
        // 时间单位
        val timeUnit = TimeUnit.MINUTES
        // 任务队列
        val workQueue = LinkedBlockingQueue<Runnable>()

        sExecutor = ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            timeUnit,
            workQueue
        )
    }

    private fun onPostExecute() {
        this.stopNetworkDiagnosis()
    }

    private fun stopNetworkDiagnosis() {
        if (isRunning) {
            netSocker = null
            netPinger = null
            traceRouter?.resetInstance()
            traceRouter = null
            sExecutor?.takeIf { !it.isShutdown }?.shutdown()
            isRunning = false
        }
    }

    private fun logStepInfo(stepInfo: String) {
        logInfo.append(stepInfo).append("\n")
        onDeviceInfoUpdated(stepInfo + "\n")
    }

    @SuppressLint("PermissionApiAndConstantDetector", "HardwareIds")
    private fun logAppVersionDetails() {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        logStepInfo("诊断时间: ${simpleDateFormat.format(Date())}")
        logStepInfo("机器类型:\t${Build.MANUFACTURER}:${Build.BRAND}:${Build.MODEL}")
        logStepInfo("系统版本:\t${Build.VERSION.RELEASE}")

        val result = LDNetUtil.checkSimState(context)
        if (result.isReady) {
            if (telephonyManager != null) {
                val tmp: String = telephonyManager.networkOperator
                if (!TextUtils.isEmpty(tmp)) {
                    if (tmp.length >= 3) {
                        val mobileCountryName = when (tmp.substring(0, 3)) {
                            "460" -> "中国 🇨🇳"
                            "454" -> "中国香港 🇨🇳"
                            "466" -> "中国台湾 🇨🇳"
                            "455" -> "中国澳门 🇨🇳"
                            "" -> "被隐藏"
                            else -> "境外"
                        }
                        logStepInfo("您所在的国家:\t$mobileCountryName")
                    }
                }
            }

            if (carrierName.isEmpty()) {
                val operatorName = LDNetUtil.getMobileOperator(context)
                logStepInfo("运营商:\t$operatorName")
            }
        } else {
            logStepInfo("SIM卡检测: ${result.message}")
        }

        // 检查代理设置并执行 Ping 测试
        val proxyInfo = fetchProxyDetails(context)
        if (proxyInfo.isNotEmpty()) {
            logStepInfo(proxyInfo)
            if (pingExternalServer()) {
                logStepInfo("代理到境外主机")
            }
        }
    }

    private fun fetchProxyDetails(context: Context): String {
        val proxyInfo = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: Network? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    connectivityManager.activeNetwork
                } else null

            activeNetwork?.let {
                val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(it)
                linkProperties?.httpProxy?.let { proxy ->
                    return "已经开启代理: ${proxy.host}:${proxy.port}"
                }
            }
        } else {
            val httpProxyHost = System.getProperty("http.proxyHost")
            val httpProxyPort = System.getProperty("http.proxyPort")?.toIntOrNull() ?: -1
            val httpsProxyHost = System.getProperty("https.proxyHost")
            val httpsProxyPort = System.getProperty("https.proxyPort")?.toIntOrNull() ?: -1

            if (!httpProxyHost.isNullOrEmpty() && httpProxyPort != -1) {
                return "已经开启 HTTP 代理: $httpProxyHost:$httpProxyPort"
            }
            if (!httpsProxyHost.isNullOrEmpty() && httpsProxyPort != -1) {
                return "已经开启 HTTPS 代理: $httpsProxyHost:$httpsProxyPort"
            }
        }
        return proxyInfo
    }

    fun execute() {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            startNetworkDiagnosis()
            mainHandler.post {
                onPostExecute()
            }
        }
    }

    private fun logLocalNetworkInfo() {
        // 网络状态检查
        if (LDNetUtil.isNetworkConnected(context)) {
            isNetConnected = true
            onDomainAccessUpdated("当前是否联网:\t已联网\n")
        } else {
            isNetConnected = false
            onDomainAccessUpdated("当前是否联网:\t未联网\n")
            return // 如果没有联网，直接返回，不继续执行后续代码
        }

        // 获取当前网络类型
        netType = LDNetUtil.getNetWorkType(context)
        onDomainAccessUpdated("当前联网类型:\t$netType\n")

        if (LDNetUtil.NET_WORK_TYPE_WIFI == netType) { // wifi：获取本地ip和网关，其他类型：只获取ip
            localIp = LDNetUtil.getLocalIpByWifi(context)
            gateWay = LDNetUtil.pingGateWayInWifi(context) ?: ""
        } else {
            localIp = LDNetUtil.getLocalIpBy3G() ?: ""
        }
        onDomainAccessUpdated("本地IP:\t$localIp\n")
        onDomainAccessUpdated("本地网关:\t$gateWay\n")

        remoteIpList.clear() // 确保列表干净
        remoteInet.clear()
        isDomainParseOk = resolveDomain() // 域名解析
    }

    private fun resolveDomain(): Boolean {
        // 记录开始时间
        val startTime = System.currentTimeMillis()

        // 从 LDNetUtil 获取域名解析结果的 map
        val map = LDNetUtil.getDomainIp(SERVER_URL)

        // 记录域名解析后的时间
        val domainResolvedTime = System.currentTimeMillis()
        var totalDuration = domainResolvedTime - startTime
        onDomainAccessUpdated("DNS解析总耗时: $totalDuration ms\n") // 解析总耗时

        // 提取并解析用时信息
        val useTime = (map["useTime"] as? String)?.toIntOrNull() ?: 0
        val timeShow = if (useTime > 5000) {
            " (${useTime / 1000}s)"
        } else {
            " ($useTime ms)"
        }

        // 尝试获取 InetAddress 列表，并将其转换为 Array<InetAddress>
        val inetAddressList = (map["remoteInet"] as? Array<*>)?.filterIsInstance<InetAddress>()
        inetAddressList?.let { addresses ->
            // 初始化 remoteInet 和 remoteIpList
            remoteInet.addAll(addresses)
            // 遍历每个 InetAddress，逐个解析成功的 IP 地址并打印日志
            addresses.forEach { inetAddress ->
                val ip = inetAddress.hostAddress ?: "未知IP"
                onDomainAccessUpdated("IP 地址: $ip\n")
                remoteIpList.add(ip)
            }

            // 返回解析是否成功
            return addresses.isNotEmpty()
        }

        // 如果解析失败，记录失败日志
        onDomainAccessUpdated("DNS解析结果:\t解析失败$timeShow\n")

        // 记录解析失败后的总耗时
        val failedTime = System.currentTimeMillis()
        totalDuration = failedTime - startTime
        onDomainAccessUpdated("DNS解析失败总耗时: $totalDuration ms\n") // 解析总耗时

        return false
    }

    private fun pingExternalServer(): Boolean {
        val pingCmd = "ping -c 1 www.google.com"
        val startTime = System.currentTimeMillis()

        return try {
            val process = Runtime.getRuntime().exec(pingCmd)

            val pingTask = Thread {
                try {
                    // 等待进程完成
                    process.waitFor()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            pingTask.start()
            Thread.sleep(460)

            val endTime = System.currentTimeMillis()
            val elapsedTime = endTime - startTime

            if (elapsedTime > 460) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun startNetworkDiagnosis(): String {
        if (SERVER_URL.isEmpty()) return ""
        isRunning = true
        logInfo.setLength(0)

        // 1. 先记录本机信息
        logBasicInfo()

        // 2. 执行诊断域名访问
        if (isNetConnected) {
            // 3. 如果联网，进行 Ping、TCP 测试和 Traceroute，并发执行
            if (isDomainParseOk) {
                performConcurrentDiagnosis() // 并发执行测试
            } else {
                logStepInfo("\n联网但 DNS 解析失败，停止诊断")
            }
        } else {
            logStepInfo("\n\n当前主机未联网,请检查网络！")
        }

        return logInfo.toString()
    }

    private fun performConcurrentDiagnosis() {
        // 创建一个线程池来执行任务
        val executor = Executors.newFixedThreadPool(3)

        // 提交 Ping 分析任务
        executor.submit {
            performPingTests()
        }

        // 提交 TCP 连接测试任务
        executor.submit {
            performTcpConnectionTest()
        }

        // 提交 TraceRouter 任务
        executor.submit {
            performTraceroute()
        }

        // 等待所有任务完成后关闭线程池
        executor.shutdown()
        try {
            // 设置等待时间，防止无限期等待
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }

    private fun logBasicInfo() {
        logAppVersionDetails()
        logLocalNetworkInfo()
    }

    private fun performTcpConnectionTest() {
        onTcpTestUpdated("开始 TCP 连接测试...\n")
        val connectionTester = NetworkConnectionTester.instance
        connectionTester?.apply {
            isSocketConnected = initAndStartTest(
                remoteInet = this@NetworkAnalyzeService.remoteInet,
                remoteIpList = this@NetworkAnalyzeService.remoteIpList,
                listener = this@NetworkAnalyzeService
            )
        }
    }

    private fun performPingTests() {
        onPingAnalysisUpdated("开始 Ping...\n")
        if (netPinger == null) {
            netPinger = NetworkPingTester(this, 4)
        }

        // 创建一个固定大小的线程池
        val executorService: ExecutorService = Executors.newFixedThreadPool(remoteIpList.size + 3)

        // 提交并发任务
        val tasks: MutableList<Future<Unit>> = mutableListOf()

        // 设备自身网络堆栈 Ping 测试
        tasks.add(executorService.submit(Callable {
            logPing("检查设备自身的网络堆栈是否正常\n" ,"127.0.0.1")
        }))

        // 设备本地 IP 网络连接 Ping 测试
        tasks.add(executorService.submit(Callable {
            logPing("检查设备本地 IP 的网络连接是否正常\n", localIp)
        }))

        // 如果是 WIFI 网络，Ping 路由器
        if (LDNetUtil.NET_WORK_TYPE_WIFI == netType) {
            tasks.add(executorService.submit(Callable {
                logPing("检查设备是否能够连接到本地网络的路由器\n", gateWay)
            }))
        }

        // 并发 Ping 所有远端 IP 地址
        for (ip in remoteIpList) {
            tasks.add(executorService.submit(Callable {
                logPing("Ping 远端 IP 地址\n", ip)
            }))
        }

        // 等待所有任务完成
        for (task in tasks) {
            try {
                task.get()  // 等待任务完成并获取结果
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 关闭线程池
        executorService.shutdown()
    }

    private fun logPing(preString: String, ip: String) {
        netPinger?.startTraceroutePing(preString, ip, false)
    }

    private fun performTraceroute() {
        onTraceRouterUpdated("开始对所有解析出的 IP 地址进行 Traceroute...\n")
        traceRouter = NetworkTracer.getInstance()
        traceRouter?.setTraceRouteListener(object : NetworkTracer.NetworkTraceListener {
            override fun onTraceRouteUpdate(log: String) {
                listener.onTraceRouterUpdated(log + "\n")
            }

            override fun onTraceRouteComplete() {
                listener.onTraceRouterUpdated("Traceroute 完成\n")
            }
        })

        // 创建一个固定大小的线程池
        val executorService: ExecutorService = Executors.newFixedThreadPool(remoteIpList.size)

        // 提交每个 IP 地址的 Traceroute 任务
        for (ip in remoteIpList) {
            executorService.submit {
                traceRouter?.beginTraceRoute(ip) // 对每个 IP 进行 Traceroute
            }
        }

        // 关闭线程池（确保所有任务完成后再关闭）
        executorService.shutdown()
    }

    override fun onNetPingFinished(log: String) {
        logInfo.append(log)
        onPingAnalysisUpdated(log)
    }

    override fun onDeviceInfoUpdated(log: String) {
        logInfo.append(log)
        listener.onDeviceInfoUpdated(log)
    }

    override fun onDomainAccessUpdated(log: String) {
        logInfo.append(log)
        listener.onDomainAccessUpdated(log)
    }

    override fun onPingAnalysisUpdated(log: String) {
        logInfo.append(log)
        listener.onPingAnalysisUpdated(log)
    }

    override fun onTcpTestUpdated(log: String) {
        logInfo.append(log)
        listener.onTcpTestUpdated(log)
    }

    override fun onTraceRouterUpdated(log: String) {
        logInfo.append(log)
        listener.onTraceRouterUpdated(log)
    }

    override fun onFailed(e: Exception) {
        TODO("Not yet implemented")
    }
}