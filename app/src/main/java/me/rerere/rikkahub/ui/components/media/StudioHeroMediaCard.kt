package me.rerere.rikkahub.ui.components.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.placeholder
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.Maximize01
import me.rerere.hugeicons.stroke.Share01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.components.ui.LocalExportContext
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.koin.compose.koinInject
import java.io.File

@Composable
fun StudioHeroMediaCard(
    images: List<String>,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    aspectRatio: String? = null,
) {
    if (images.isEmpty()) return

    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    var showPreviewDialog by remember { mutableStateOf(false) }
    var previewStartIndex by remember { mutableStateOf(initialIndex) }

    fun openPreview(index: Int) {
        previewStartIndex = index.coerceIn(0, images.size - 1)
        showPreviewDialog = true
    }

    fun saveImage(url: String) {
        scope.launch {
            runCatching {
                toaster.show(context.getString(R.string.chat_message_image_saving))
                filesManager.saveMessageImage(context, url)
                toaster.show(
                    context.getString(R.string.chat_message_image_saved),
                    type = ToastType.Success,
                )
            }.onFailure {
                toaster.show(
                    it.message ?: context.getString(R.string.failed),
                    type = ToastType.Error,
                )
            }
        }
    }

    fun shareImage(url: String) {
        runCatching {
            val uri: Uri = if (url.startsWith("file://")) {
                val file = File(url.removePrefix("file://"))
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else if (url.startsWith("/")) {
                val file = File(url)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                url.toUri()
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, null)
            context.startActivity(chooser)
        }.onFailure {
            toaster.show(it.message ?: context.getString(R.string.failed), type = ToastType.Error)
        }
    }

    val parsedRatio = remember(aspectRatio) {
        when (aspectRatio?.lowercase()?.trim()) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "2:3" -> 2f / 3f
            "3:2" -> 3f / 2f
            else -> null
        }
    }

    val cardShape = RoundedCornerShape(12.dp)
    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(max = 320.dp)
        .then(if (parsedRatio != null) Modifier.aspectRatio(parsedRatio) else Modifier)
        .clip(cardShape)
        .background(MaterialTheme.colorScheme.surfaceContainer)

    if (images.size == 1) {
        val imageUrl = images.first()
        Box(
            modifier = cardModifier,
            contentAlignment = Alignment.Center
        ) {
            HeroImageItem(
                url = imageUrl,
                onClick = { openPreview(0) },
                modifier = Modifier.fillMaxSize()
            )

            QuickActionOverlay(
                onFullscreen = { openPreview(0) },
                onDownload = { saveImage(imageUrl) },
                onShare = { shareImage(imageUrl) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    } else {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, images.size - 1)
        ) { images.size }

        Box(
            modifier = cardModifier,
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                HeroImageItem(
                    url = images[page],
                    onClick = { openPreview(page) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            QuickActionOverlay(
                onFullscreen = { openPreview(pagerState.currentPage) },
                onDownload = { saveImage(images[pagerState.currentPage]) },
                onShare = { shareImage(images[pagerState.currentPage]) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(images.size) { pageIndex ->
                    val isSelected = pagerState.currentPage == pageIndex
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isSelected) 16.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.6f)
                            )
                    )
                }
            }
        }
    }

    if (showPreviewDialog) {
        ImagePreviewDialog(
            images = images,
            initialPage = previewStartIndex,
            onDismissRequest = { showPreviewDialog = false }
        )
    }
}

@Composable
private fun HeroImageItem(
    url: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val placeholder = if (LocalDarkMode.current) R.drawable.placeholder_dark else R.drawable.placeholder
    val export = LocalExportContext.current
    var isLoading by remember { mutableStateOf(false) }

    val coilModel = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .placeholder(placeholder)
            .crossfade(true)
            .allowHardware(!export)
            .build()
    }

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .shimmer(isLoading = isLoading),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = coilModel,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { isLoading = false },
        )
    }
}

@Composable
private fun QuickActionOverlay(
    onFullscreen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = HugeIcons.Maximize01,
                    contentDescription = "Fullscreen",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = HugeIcons.Download01,
                    contentDescription = "Download",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = HugeIcons.Share01,
                    contentDescription = "Share",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}
