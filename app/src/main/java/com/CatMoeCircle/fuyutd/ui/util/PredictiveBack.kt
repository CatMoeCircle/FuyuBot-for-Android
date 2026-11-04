package com.CatMoeCircle.fuyutd.ui.util

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.CatMoeCircle.fuyutd.FuyuTDApplication

/**
 * 运行时切换系统预测性返回动画。
 *
 * 该标志由系统在解析 `ApplicationInfo` 时读取，所以写入后必须重建 Activity 才会生效。
 * 实际的反射与 hidden API 豁免在 [FuyuTDApplication] 里统一处理。
 */
@Composable
fun rememberPredictiveBackToggler(): (Boolean) -> Unit {
    val activity = LocalContext.current as? Activity
    return { enabled ->
        if (activity != null) {
            runCatching {
                FuyuTDApplication.setEnableOnBackInvokedCallback(
                    activity.applicationInfo,
                    enabled,
                )
            }.onSuccess {
                activity.recreate()
            }
        }
    }
}
