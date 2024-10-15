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
     * Ping分析完成时的回调
     */
    fun onPingCompleted()

    /**
     * TCP连接测试更新时的回调
     * @param log 日志字符串
     */
    fun onTcpTestUpdated(log: String)

    /**
     * TCP连接测试完成时的回调
     */
    fun onTcpTestCompleted()

    /**
     * Tracerouter更新时的回调
     * @param log 日志字符串
     */
    fun onTraceRouterUpdated(log: String)

    /**
     * Tracerouter完成时的回调
     */
    fun onTraceRouterCompleted()

    /**
     * 处理失败时的回调
     * @param e 异常
     */
    fun onFailed(e: Exception)
}
