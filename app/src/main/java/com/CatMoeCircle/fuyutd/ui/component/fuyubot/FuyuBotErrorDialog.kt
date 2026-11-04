package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.viewmodel.HomeError

/** 启动流程中的错误提示。 */
@Composable
fun FuyuBotErrorDialog(
    error: HomeError?,
    onDismiss: () -> Unit,
) {
    val current = error ?: return
    val title = stringResource(R.string.fuyu_error_title)
    val message = when (current) {
        is HomeError.Runtime -> current.message
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> FuyuBotErrorDialogMiuix(
            title = title,
            message = message,
            onDismiss = onDismiss,
        )

        UiMode.Material -> FuyuBotErrorDialogMaterial(
            title = title,
            message = message,
            onDismiss = onDismiss,
        )
    }
}
