package antfortune.wealth.net.myapplication.service

interface NetworkAnalyzeListener {

    /**
     * 本机信息更新时的回调
     * @param log 日志字符串
     */
    fun onDeviceInfoUpdated(log: String)

    /**
     * 诊断域名访问更新时的回调
     * @param log 日志字符串
     */
    fun onDomainAccessUpdated(log: String)

    /**
     * Ping分析更新时的回调
     * @param log 日志字符串
     */
    fun onPingAnalysisUpdated(log: String)

    /**
     * TCP连接测试更新时的回调
     * @param log 日志字符串
     */
    fun onTcpTestUpdated(log: String)

    /**
     * Tracerouter更新时的回调
     * @param log 日志字符串
     */
    fun onTraceRouterUpdated(log: String)

    fun onFailed(e: Exception)
}