package antfortune.wealth.net.myapplication.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.util.*

@SuppressLint("DefaultLocale")
object LDNetUtil {
    private const val PERMISSION_REQUEST_CODE = 1
    private const val NET_WORK_TYPE_INVALID = "UNKNOWN" // 没有网络
    const val NET_WORK_TYPE_WIFI = "WIFI" // wifi网络

    fun getNetWorkType(context: Context): String {
        val mNetWorkType: String?

        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            ?: return "ConnectivityManager not found"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return NET_WORK_TYPE_INVALID // 没有连接到任何网络
            val networkCapabilities = manager.getNetworkCapabilities(network) ?: return NET_WORK_TYPE_INVALID // 没有网络能力信息

            mNetWorkType = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NET_WORK_TYPE_WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> mobileNetworkType(context)
                else -> NET_WORK_TYPE_INVALID // 其他类型，如蓝牙或以太网
            }
        } else {
            val networkInfo = manager.activeNetworkInfo
            if (networkInfo != null && networkInfo.isConnected) {
                val type = networkInfo.typeName
                mNetWorkType = if (type.equals("WIFI", ignoreCase = true)) {
                    NET_WORK_TYPE_WIFI
                } else if (type.equals("MOBILE", ignoreCase = true)) {
                    mobileNetworkType(context)
                } else {
                    NET_WORK_TYPE_INVALID
                }
            } else {
                mNetWorkType = NET_WORK_TYPE_INVALID
            }
        }

        return mNetWorkType ?: NET_WORK_TYPE_INVALID
    }

    fun isNetworkConnected(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        val networkinfo = manager?.activeNetworkInfo
        return networkinfo?.isAvailable == true
    }

    @SuppressLint("PermissionApiAndConstantDetector")
    fun getMobileOperator(context: Context): String {
        val telManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        val operator = telManager?.simOperator ?: return "未知运营商"

        return when (operator) {
            "46000", "46002", "46007" -> "中国移动"
            "46001" -> "中国联通"
            "46003" -> "中国电信"
            else -> "未知运营商: $operator"
        }
    }

    fun checkSimState(context: Context): Result {
        val telManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val (isReady, message) = when (telManager.simState) {
            TelephonyManager.SIM_STATE_READY -> true to "SIM 卡已准备好"
            TelephonyManager.SIM_STATE_ABSENT -> false to "无 SIM 卡"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> false to "需要 SIM 卡 PIN"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> false to "需要 SIM 卡 PUK 码"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> false to "SIM 卡被网络锁定"
            TelephonyManager.SIM_STATE_NOT_READY -> false to "SIM 卡未准备好"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> false to "SIM 卡已永久禁用"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> false to "SIM 卡 IO 错误"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> false to "SIM 卡受限"
            else -> false to "未知的 SIM 卡状态"
        }

        return Result(isReady, message)
    }

    data class Result(val isReady: Boolean, val message: String)

    @SuppressLint("PermissionApiAndConstantDetector")
    fun getLocalIpByWifi(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            ?: return "wifiManager not found"

        val wifiInfo = wifiManager.connectionInfo ?: return "wifiInfo not found"
        val ipAddress = wifiInfo.ipAddress

        return String.format(
            "%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff
        )
    }

    fun getLocalIpBy3G(): String? {
        return try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun pingGateWayInWifi(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            ?: return "wifiManager not found"

        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val tmp = dhcpInfo.gateway
        return String.format("%d.%d.%d.%d", tmp and 0xff, tmp shr 8 and 0xff, tmp shr 16 and 0xff, tmp shr 24 and 0xff)
    }

    fun getLocalDns(context: Context): Array<String> {
        var dnsServers = getDnsFromCommand()
        if (dnsServers.isEmpty()) {
            dnsServers = getDnsFromConnectionManager(context)
        }
        return dnsServers
    }
    private fun getDnsFromCommand(): Array<String> {
        val dnsServers = mutableListOf<String>()
        val regex = """\[(.+?)\]: \[(.+?)\]""".toRegex()

        try {
            Runtime.getRuntime().exec("getprop").inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { regex.find(it)?.groupValues?.get(2) }
                    .forEach { value ->
                        runCatching {
                            InetAddress.getByName(value).hostAddress
                        }.onSuccess { address ->
                            if (address.isNotEmpty()) {
                                dnsServers.add(address)
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return dnsServers.toTypedArray()
    }

    private fun getDnsFromConnectionManager(context: Context): Array<String> {
        val dnsServers = LinkedList<String>()
        if (Build.VERSION.SDK_INT >= 21) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectivityManager != null) {
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                if (activeNetworkInfo != null) {
                    for (network in connectivityManager.allNetworks) {
                        val networkInfo = connectivityManager.getNetworkInfo(network)
                        if (networkInfo?.type == activeNetworkInfo.type) {
                            val lp = connectivityManager.getLinkProperties(network)
                            lp?.dnsServers?.forEach { addr ->
                                dnsServers.add(addr.hostAddress ?: "")
                            }
                        }
                    }
                }
            }
        }
        return if (dnsServers.isEmpty()) arrayOf() else dnsServers.toTypedArray()
    }

    fun getDomainIp(domain: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        var time: String? = null
        val remoteInet: Array<InetAddress>?
        val start = System.currentTimeMillis()

        try {
            remoteInet = InetAddress.getAllByName(domain)
            time = (System.currentTimeMillis() - start).toString()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            return map
        }

        map["remoteInet"] = remoteInet
        map["useTime"] = time
        return map
    }

    @SuppressLint("PermissionApiAndConstantDetector")
    private fun mobileNetworkType(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
            ?: return "TM==null"

        // 检查是否有 READ_PHONE_STATE 权限
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，则请求权限
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.READ_PHONE_STATE),
                PERMISSION_REQUEST_CODE
            )
            return "权限未授予"
        }

        // 有权限时执行网络类型判断逻辑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (telephonyManager.networkType == TelephonyManager.NETWORK_TYPE_NR) {
                return "5G"
            }
        }

        return when (telephonyManager.networkType) {
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
            TelephonyManager.NETWORK_TYPE_CDMA -> "2G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "未知网络环境"
            else -> "" + telephonyManager.networkType
        }
    }
}
