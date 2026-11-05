package se.anders.athenamusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import se.anders.athenamusic.R
import se.anders.athenamusic.data.art.ArtworkStore
import se.anders.athenamusic.domain.ArtStyle
import se.anders.athenamusic.domain.GRAYSCALE_MATRIX

/** Provided once at the top of the app, so rows do not need the store or the style passed down. */
val LocalArtworkStore = compositionLocalOf<ArtworkStore?> { null }
val LocalArtStyle = compositionLocalOf { ArtStyle.Grayscale }

private sealed interface ArtState {
    data object Loading : ArtState
    data object Missing : ArtState
    class Ready(val bitmap: ImageBitmap) : ArtState
}

/**
 * A square cover picture for [coverArtId] (FR-040). While it loads the square is left blank; when there is no
 * picture, or it cannot be fetched, the Athena Music icon is drawn at the same size (FR-041). Decorative for
 * screen readers: the title next to it says it all.
 */
@Composable
fun ArtImage(coverArtId: String?, size: Dp, modifier: Modifier = Modifier) {
    val store = LocalArtworkStore.current
    val style = LocalArtStyle.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val state by produceState<ArtState>(if (coverArtId == null || store == null) ArtState.Missing else ArtState.Loading, coverArtId, px, store) {
        value = if (coverArtId == null || store == null) ArtState.Missing
        else if (store.ensure(coverArtId)) store.bitmap(coverArtId, px)?.let { ArtState.Ready(it) } ?: ArtState.Missing
        else ArtState.Missing
    }
    when (val s = state) {
        ArtState.Loading -> Box(modifier.size(size))
        ArtState.Missing -> Box(modifier.size(size).background(Color.Black)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(size))
        }
        is ArtState.Ready -> Image(
            bitmap = s.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (style == ArtStyle.Grayscale) ColorFilter.colorMatrix(ColorMatrix(GRAYSCALE_MATRIX)) else null,
            modifier = modifier.size(size),
        )
    }
}
