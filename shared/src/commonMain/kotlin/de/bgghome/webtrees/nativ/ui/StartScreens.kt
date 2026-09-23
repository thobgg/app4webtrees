package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import de.bgghome.webtrees.nativ.res.*

@Composable
private fun StartFrame(title: String, subtitle: String, error: String?, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                content()
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
fun SetupScreen(state: UiState, onSubmit: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf(state.baseUrl) }

    StartFrame(
        title = stringResource(Res.string.app_name),
        subtitle = stringResource(Res.string.setup_subtitle),
        error = state.error,
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(Res.string.setup_address)) },
            placeholder = { Text("https://example.org/webtrees") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(url) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSubmit(url) }, enabled = !state.busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) stringResource(Res.string.setup_connecting) else stringResource(Res.string.setup_connect))
        }
        Text(stringResource(Res.string.setup_address_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.setup_pair_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun LoginScreen(state: UiState, onLogin: (String, String) -> Unit, onGuest: () -> Unit, onChangeServer: () -> Unit) {
    var user by rememberSaveable { mutableStateOf(state.userName) }
    var password by rememberSaveable { mutableStateOf("") }
    val info = state.info

    StartFrame(
        title = stringResource(Res.string.action_sign_in),
        subtitle = state.baseUrl + (info?.let { "\n" + stringResource(Res.string.login_server_info, it.webtrees, it.module) } ?: ""),
        error = state.error,
    ) {
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text(stringResource(Res.string.login_user)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text(stringResource(Res.string.login_password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onLogin(user, password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onLogin(user, password) },
            enabled = !state.busy && user.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) stringResource(Res.string.login_busy) else stringResource(Res.string.action_sign_in))
        }
        if (info != null && info.trees.isNotEmpty()) {
            TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.login_guest)) }
        }
        TextButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.login_other_address)) }
    }
}

@Composable
fun TreesScreen(state: UiState, onChoose: (de.bgghome.webtrees.nativ.api.TreeInfo) -> Unit, onLogout: () -> Unit, onLogin: () -> Unit, onCancel: (() -> Unit)? = null) {
    val trees = state.info?.trees.orEmpty()

    val user = state.info?.user
    val subtitle = when {
        user?.loggedIn == true -> stringResource(Res.string.trees_signed_in_as, user.realName)
        else -> stringResource(Res.string.trees_not_signed_in)
    }

    StartFrame(
        title = stringResource(Res.string.trees_title),
        subtitle = subtitle,
        error = if (trees.isEmpty()) stringResource(Res.string.trees_none) else state.error,
    ) {
        trees.forEach { tree ->
            Card(Modifier.fillMaxWidth().clickable { onChoose(tree) }) {
                ListItem(
                    headlineContent = { Text(tree.title) },
                    supportingContent = { Text(roleLabel(tree.role)) },
                )
            }
        }
        // Aus dem Hauptbildschirm gekommen: ein Weg zurueck, ohne den Baum zu wechseln
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.action_cancel)) }
        }
        if (state.info?.user?.loggedIn == true) {
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.action_sign_out)) }
        } else {
            TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.action_sign_in)) }
        }
    }
}

@Composable
fun roleLabel(role: String): String = stringResource(
    when (role) {
        "manager" -> Res.string.role_manager
        "moderator" -> Res.string.role_moderator
        "editor" -> Res.string.role_editor
        "member" -> Res.string.role_member
        else -> Res.string.role_visitor
    }
)
