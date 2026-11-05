package se.anders.athenamusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.anders.athenamusic.R

/** Play / pause as one icon button; the icon shows what pressing it will do. */
@Composable
fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, size: Dp = 48.dp, iconSize: Dp = 28.dp) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(
            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
            tint = LocalContentColor.current,
            modifier = Modifier.size(iconSize),
        )
    }
}
