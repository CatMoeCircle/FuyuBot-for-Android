package com.CatMoeCircle.fuyutd.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.SPEC_2025.name,
    val enablePredictiveBack: Boolean = false,
    val enableSwipeDismiss: Boolean = true,
    val pagerInterceptionMode: Int = 1,
    val enableBlur: Boolean = false,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val pageScale: Float = 1.0f,
)

@Immutable
data class SettingsScreenActions(
    val onOpenTheme: () -> Unit,
    val onOpenUpdate: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
)
