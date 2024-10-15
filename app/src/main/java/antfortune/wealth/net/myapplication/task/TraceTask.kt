package antfortune.wealth.net.myapplication.task

import android.content.Context
import android.os.Handler
import android.os.Looper
import antfortune.wealth.net.myapplication.MainActivity
import antfortune.wealth.net.myapplication.NetworkAnalyzeListener
import antfortune.wealth.net.myapplication.NetworkAnalyzeService

/**
 * Created by xuzhou on 2016/8/1.
 */
class TraceTask(
    private val context: Context,
    private val callBack: MainActivity
) : NetworkAnalyzeListener {

    private val handler = Handler(Looper.getMainLooper())

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

    override fun onPingCompleted() {
        callBack.let {
            handler.post{
                it.onPingCompleted()
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

    override fun onTcpTestCompleted() {
        callBack.let {
            handler.post{
                it.onTcpTestCompleted()
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

    override fun onTraceRouterCompleted() {
        callBack.let {
            handler.post{
                it.onTraceRouterCompleted()
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

    override fun onPingScoreReceived(score: Int) {
        callBack.let {
            handler.post {

            }
        }
    }

    override fun onTcpTestScoreReceived(score: Int) {
        callBack.let {
            handler.post {
                it.onTcpTestScoreReceived(score)
            }
        }
    }

    override fun onTraceRouterScoreReceived(score: Int) {
        callBack.let {
            handler.post {

            }
        }
    }
}
