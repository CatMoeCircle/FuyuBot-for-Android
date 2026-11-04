package com.CatMoeCircle.fuyutd

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import com.CatMoeCircle.fuyutd.data.AppPrefs
import com.CatMoeCircle.fuyutd.data.SettingsRepository
import org.lsposed.hiddenapibypass.HiddenApiBypass

class FuyuTDApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // setEnableOnBackInvokedCallback 是隐藏 API，不豁免会被系统直接拒绝，
            // 表现为开关看似保存成功但预测性返回动画从不出现。
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
            )
            setEnableOnBackInvokedCallback(
                applicationInfo,
                SettingsRepository().enablePredictiveBack,
            )
        }
    }

    companion object {
        /**
         * 运行时切换系统的预测性返回动画。
         *
         * 该标志由系统在解析 `ApplicationInfo` 时读取，因此调用后必须重建 Activity 才会生效。
         */
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val method = ApplicationInfo::class.java.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType,
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }
}
