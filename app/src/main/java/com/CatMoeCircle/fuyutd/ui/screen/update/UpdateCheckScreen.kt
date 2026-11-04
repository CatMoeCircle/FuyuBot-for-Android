package com.CatMoeCircle.fuyutd.ui.screen.update

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.CatMoeCircle.fuyutd.data.update.UpdateChannel
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.viewmodel.UpdateViewModel

@Composable
fun UpdateCheckScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<UpdateViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.bind(context)
    }

    val actions = UpdateScreenActions(
        onBack = { navigator.pop() },
        onCheckApp = { viewModel.check(UpdateChannel.App) },
        onSetAutoCheck = viewModel::setAutoCheck,
        onOpenUrl = { url ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> UpdateCheckScreenMiuix(uiState, actions)
        UiMode.Material -> UpdateCheckScreenMaterial(uiState, actions)
    }
}
