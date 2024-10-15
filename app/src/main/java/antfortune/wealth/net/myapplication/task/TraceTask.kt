package antfortune.wealth.net.myapplication.task

import android.content.Context
import android.os.Handler
import android.os.Looper
import antfortune.wealth.net.myapplication.MainActivity
import antfortune.wealth.net.myapplication.service.NetworkAnalyzeListener
import antfortune.wealth.net.myapplication.service.NetworkAnalyzeService

/**
 * Created by xuzhou on 2016/8/1.
 */
class TraceTask(
    private val context: Context,
    private val callBack: MainActivity
) : NetworkAnalyzeListener {

    private var deviceId: String = ""
    private val handler = Handler(Looper.getMainLooper())

    fun setDeviceId(deviceId: String) {
        this.deviceId = deviceId
    }

    private fun getExecRunnable(): Runnable {
        return execRunnable
    }

    private val execRunnable = Runnable {
        try {
            val netAnalyzeService =
                NetworkAnalyzeService(
                    context = context,
                    carrierName = "",
                    listener = this
                )
            // 设置是否使用JNIC完成traceroute
            netAnalyzeService.execute()
        } catch (e: Exception) {
            callBack.let {
                handler.post {
                    it.onFailed(e)
                }
            }
        }
    }

    fun doTask() {
        getExecRunnable().run()
    }

    override fun onDeviceInfoUpdated(log: String) {
        callBack.let {
            handler.post {
                it.onDeviceInfoUpdated(log)
            }
        }
    }

    override fun onDomainAccessUpdated(log: String) {
        callBack.let {
            handler.post {
                it.onDomainAccessUpdated(log)
            }
        }
    }

    override fun onPingAnalysisUpdated(log: String) {
        callBack.let {
            handler.post {
                it.onPingAnalysisUpdated(log)
            }
        }
    }

    override fun onTcpTestUpdated(log: String) {
        callBack.let {
            handler.post {
                it.onTcpTestUpdated(log)
            }
        }
    }

    override fun onTraceRouterUpdated(log: String) {
        callBack.let {
            handler.post {
                it.onTraceRouterUpdated(log)
            }
        }
    }

    override fun onFailed(e: Exception) {
        callBack.let {
            handler.post {
                it.onFailed(e)
            }
        }
    }
}
