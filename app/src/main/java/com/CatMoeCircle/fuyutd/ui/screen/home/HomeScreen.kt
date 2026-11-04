package com.CatMoeCircle.fuyutd.ui.screen.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 卡片圆角：与底部栏同一圆角量级，取 28dp。 */
val HomeCardCornerRadius: Dp = 28.dp

/** 卡片内容的内边距。 */
val HomeCardPadding: Dp = 16.dp

/** 卡片之间的间距。 */
val HomeCardSpacing: Dp = 12.dp

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    bottomInnerPadding: Dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> HomeMiuix(
            state = state,
            onStart = onStart,
            onStop = onStop,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomeMaterial(
            state = state,
            onStart = onStart,
            onStop = onStop,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}

/**
 * 圆形头像位，两套风格共用。
 *
 * 有本地图片就画图片，否则交给 [fallback] 画占位图标——两套主题的
 * `Icon` 不是同一个组件，所以占位内容由调用方给。
 */
@Composable
fun HomeAvatar(
    avatarPath: String?,
    background: Color,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val image = rememberAvatarImage(avatarPath)
    Box(
        modifier = modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.home_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            fallback()
        }
    }
}

/** 头像直径，与主操作按钮的视觉比例保持一致。 */
private val AvatarSize: Dp = 44.dp

/**
 * 从本地文件解码头像；路径为空或解码失败返回 null。
 *
 * 图片由内核（TDLib）下到应用私有目录，路径不会变，因此只按路径做缓存。
 * 解码放 IO 线程，避免 640×640 的图拖慢首帧。
 */
@Composable
private fun rememberAvatarImage(path: String?): ImageBitmap? {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        image = if (path.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return image
}
