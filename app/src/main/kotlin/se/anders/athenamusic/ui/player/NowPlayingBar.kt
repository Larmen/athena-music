package se.anders.athenamusic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import se.anders.athenamusic.R
import se.anders.athenamusic.playback.PlayerState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import se.anders.athenamusic.ui.components.ArtImage
import se.anders.athenamusic.ui.components.PlayPauseButton
import se.anders.athenamusic.ui.components.muted

/** FR-024: the strip under the library screens while something is loaded. */
@Composable
fun NowPlayingBar(state: PlayerState, onOpen: () -> Unit, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(onClickLabel = stringResource(R.string.a11y_open_player), role = Role.Button, onClick = onOpen)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(state.coverArt, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(state.artist, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 1)
            }
            IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.player_previous), Modifier.size(24.dp))
            }
            PlayPauseButton(state.playing, onPlayPause)
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.player_next), Modifier.size(24.dp))
            }
        }
    }
}
