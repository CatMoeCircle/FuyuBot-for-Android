package com.CatMoeCircle.fuyutd.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.navigation3.Route
import com.CatMoeCircle.fuyutd.ui.screen.update.hasAnyUpdate
import com.CatMoeCircle.fuyutd.ui.screen.update.updateEntrySummary
import com.CatMoeCircle.fuyutd.ui.viewmodel.LocalSettingsViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.UpdateViewModel

@Composable
fun SettingPager(
    bottomInnerPadding: Dp,
) {
    val viewModel = LocalSettingsViewModel.current
    val updateViewModel = viewModel<UpdateViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        updateViewModel.bind(context)
    }

    val actions = SettingsScreenActions(
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onOpenUpdate = { navigator.push(Route.UpdateCheck) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(
                if (index == 0) UiMode.Miuix.value else UiMode.Material.value
            )
        },
    )
    val updateSummary = updateEntrySummary(updateState)
    val hasUpdate = updateState.hasAnyUpdate

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, bottomInnerPadding, updateSummary, hasUpdate)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, bottomInnerPadding, updateSummary, hasUpdate)
    }
}
