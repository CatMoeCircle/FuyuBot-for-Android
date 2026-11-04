package com.CatMoeCircle.fuyutd.data

import androidx.core.content.edit
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 应用设置仓库，全部落在 [AppPrefs] 管理的 SharedPreferences 里。
 */
class SettingsRepository {

    private val prefs get() = AppPrefs.prefs()

    var uiMode: String
        get() = prefs.getString(KEY_UI_MODE, UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
        set(value) = prefs.edit { putString(KEY_UI_MODE, value) }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 0)
        set(value) = prefs.edit { putInt(KEY_THEME_MODE, value) }

    var miuixMonet: Boolean
        get() = prefs.getBoolean(KEY_MIUIX_MONET, false)
        set(value) = prefs.edit { putBoolean(KEY_MIUIX_MONET, value) }

    var keyColor: Int
        get() = prefs.getInt(KEY_KEY_COLOR, 0)
        set(value) = prefs.edit { putInt(KEY_KEY_COLOR, value) }

    var colorStyle: String
        get() = prefs.getString(KEY_COLOR_STYLE, PaletteStyle.TonalSpot.name)
            ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString(KEY_COLOR_STYLE, value) }

    var colorSpec: String
        get() = prefs.getString(KEY_COLOR_SPEC, ColorSpec.SpecVersion.SPEC_2025.name)
            ?: ColorSpec.SpecVersion.SPEC_2025.name
        set(value) = prefs.edit { putString(KEY_COLOR_SPEC, value) }

    var enablePredictiveBack: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_PREDICTIVE_BACK, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_PREDICTIVE_BACK, value) }

    var enableSwipeDismiss: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SWIPE_DISMISS, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_SWIPE_DISMISS, value) }

    var pagerInterceptionMode: Int
        get() = prefs.getInt(KEY_PAGER_INTERCEPTION_MODE, 1)
        set(value) = prefs.edit { putInt(KEY_PAGER_INTERCEPTION_MODE, value.coerceIn(0, 2)) }

    var enableBlur: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_BLUR, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_BLUR, value) }

    var enableFloatingBottomBar: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR, value) }

    var enableFloatingBottomBarBlur: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR, value) }

    var navigationRailExpanded: Boolean
        get() = prefs.getBoolean(KEY_NAVIGATION_RAIL_EXPANDED, false)
        set(value) = prefs.edit { putBoolean(KEY_NAVIGATION_RAIL_EXPANDED, value) }

    var pageScale: Float
        get() = prefs.getFloat(KEY_PAGE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_PAGE_SCALE, value.coerceIn(0.8f, 1.1f)) }

    /** 是否允许在进入应用时自动检查更新。 */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_UPDATE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK_UPDATE, value) }

    /** 更新提醒的暂缓截止时间（毫秒时间戳），在此之前不再自动请求与弹窗。 */
    var updateSnoozeUntil: Long
        get() = prefs.getLong(KEY_UPDATE_SNOOZE_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_UPDATE_SNOOZE_UNTIL, value) }

    private companion object {
        const val KEY_UI_MODE = "ui_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MIUIX_MONET = "miuix_monet"
        const val KEY_KEY_COLOR = "key_color"
        const val KEY_COLOR_STYLE = "color_style"
        const val KEY_COLOR_SPEC = "color_spec"
        const val KEY_ENABLE_PREDICTIVE_BACK = "enable_predictive_back"
        const val KEY_ENABLE_SWIPE_DISMISS = "enable_swipe_dismiss"
        const val KEY_PAGER_INTERCEPTION_MODE = "pager_interception_mode"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_ENABLE_FLOATING_BOTTOM_BAR = "enable_floating_bottom_bar"
        const val KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR = "enable_floating_bottom_bar_blur"
        const val KEY_NAVIGATION_RAIL_EXPANDED = "nav_rail_expanded"
        const val KEY_PAGE_SCALE = "page_scale"
        const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
        const val KEY_UPDATE_SNOOZE_UNTIL = "update_snooze_until"
    }
}
