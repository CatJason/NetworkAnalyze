package antfortune.wealth.net.myapplication;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Created by Darkal on 2016/9/21.
 */

public class DeviceUtils {
    /**
     * 获取版本号
     *
     * @return 当前应用的版本号
     */
    public static String getVersion(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取版本号
     *
     * @return 当前应用的版本号
     */
    public static int getVersionCode(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @SuppressLint("HardwareIds")
    public static String getAndroidID(Context context) {
        String androidId;
        try {
            androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            // 处理异常，可能是由于权限或设备问题
            androidId = "";
        }

        // 过滤掉已知的不合法的 Android ID
        if (TextUtils.isEmpty(androidId) || "9774d56d682e549c".equals(androidId)) {
            return "";
        }

        return androidId;
    }
}