package pw.x4.ninety.ui.components

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import pw.x4.ninety.R
import pw.x4.ninety.ui.theme.NinetyState

/**
 * Sidebar mark sourced from the same local HeroMask asset as the desktop visual.
 * A sync frame is decoded once per composition; no network access or duplicate
 * binary is needed. The launcher glyph remains a defensive codec fallback.
 */
@Composable
fun DesktopBrandMark(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame = remember(context) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                context.resources.openRawResourceFd(R.raw.hero_mask).use { asset ->
                    retriever.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    if (frame != null) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_mono),
            contentDescription = null,
            tint = NinetyState.pack.accentBright,
            modifier = modifier,
        )
    }
}
