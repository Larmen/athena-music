package se.anders.athenamusic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.anders.athenamusic.R
import se.anders.athenamusic.playback.PlayerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import se.anders.athenamusic.ui.components.ArtImage
import se.anders.athenamusic.ui.components.PlayPauseButton
import se.anders.athenamusic.ui.components.ScreenHeader
import se.anders.athenamusic.ui.components.muted
import se.anders.athenamusic.ui.formatClock

/** FR-024: title, artist, album, position, seek bar and Previous / Play-Pause / Next. */
@Composable
fun PlayerScreen(
    state: PlayerState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.player_title), onBack)
        if (state.trackId == null) {
            Text(stringResource(R.string.player_nothing), color = muted(), modifier = Modifier.padding(24.dp))
            return@Column
        }
        // FR-040: the song's own cover (some albums have a different picture per song).
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { ArtImage(state.coverArt, 280.dp) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.title, style = MaterialTheme.typography.headlineSmall)
            Text(state.artist, style = MaterialTheme.typography.titleMedium)
            Text(state.album, style = MaterialTheme.typography.bodyMedium, color = muted())
        }
        // The slider follows playback until the finger is on it; the seek happens when it is released.
        var dragging by remember { mutableStateOf<Float?>(null) }
        val duration = state.durationMs.coerceAtLeast(1L)
        val shown = dragging ?: (state.positionMs.toFloat() / duration)
        val progressText = stringResource(R.string.a11y_progress, formatClock(state.positionMs), formatClock(state.durationMs))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { onSeek((it * duration).toLong()) }; dragging = null },
                enabled = state.durationMs > 0,
                modifier = Modifier.semantics { contentDescription = progressText },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(dragging?.let { (it * duration).toLong() } ?: state.positionMs), style = MaterialTheme.typography.bodyMedium, color = muted())
                Text(formatClock(state.durationMs), style = MaterialTheme.typography.bodyMedium, color = muted())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = state.hasPrevious, modifier = Modifier.size(64.dp)) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.player_previous), Modifier.size(32.dp))
            }
            PlayPauseButton(state.playing, onPlayPause, size = 72.dp, iconSize = 40.dp)
            IconButton(onClick = onNext, enabled = state.hasNext, modifier = Modifier.size(64.dp)) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.player_next), Modifier.size(32.dp))
            }
        }
    }
}
