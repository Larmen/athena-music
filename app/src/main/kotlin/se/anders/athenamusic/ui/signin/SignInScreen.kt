package se.anders.athenamusic.ui.signin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import se.anders.athenamusic.R
import se.anders.athenamusic.ui.SignInProblem
import se.anders.athenamusic.ui.SignInUiState
import se.anders.athenamusic.ui.components.muted

/** The address the home server is expected at (spec §7): prefilled, editable. */
const val DEFAULT_SERVER = "http://192.168.1.180:4533"

/** FR-001: server address, user name and password, checked with `ping` before anything is saved. */
@Composable
fun SignInScreen(
    initialServer: String,
    initialUsername: String,
    state: SignInUiState,
    onSignIn: (server: String, username: String, password: String) -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(initialServer.ifEmpty { DEFAULT_SERVER }) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.signin_hint), style = MaterialTheme.typography.bodyMedium, color = muted())
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = server, onValueChange = { server = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.signin_server)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.signin_username)) },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.signin_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(16.dp))
        state.problem?.let {
            Text(stringResource(it.textRes()), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { onSignIn(server, username, password) },
            enabled = !state.working && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
        ) { Text(stringResource(if (state.working) R.string.signin_working else R.string.signin_button)) }
    }
}

private fun SignInProblem.textRes(): Int = when (this) {
    SignInProblem.InvalidUrl -> R.string.error_invalid_url
    SignInProblem.Network -> R.string.error_network
    SignInProblem.Auth -> R.string.error_auth
    SignInProblem.NotSubsonic -> R.string.error_not_subsonic
    SignInProblem.Server -> R.string.error_server
    SignInProblem.LocalNetwork -> R.string.error_local_network
    SignInProblem.Rejected -> R.string.signin_rejected
}
