package se.anders.athenamusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.anders.athenamusic.R
import se.anders.athenamusic.data.server.ServerErrorKind
import se.anders.athenamusic.ui.Load

/** Back button (when [onBack] is given), title and an optional action on the right. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } else Box {}
            action?.invoke()
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
    }
}

@Composable
fun errorText(kind: ServerErrorKind): String = stringResource(
    when (kind) {
        ServerErrorKind.InvalidUrl -> R.string.error_invalid_url
        ServerErrorKind.Network -> R.string.error_network
        ServerErrorKind.Auth -> R.string.error_auth
        ServerErrorKind.NotSubsonic -> R.string.error_not_subsonic
        ServerErrorKind.Server -> R.string.error_server
    },
)

/** Shows loading / error with Retry / the content (FR-013). */
@Composable
fun <T> LoadView(load: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (load) {
        Load.Loading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading), color = muted())
        }
        is Load.Failed -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(errorText(load.kind), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        is Load.Ready -> content(load.value)
    }
}
