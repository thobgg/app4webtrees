package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Fehlermeldung als Dialog statt Snackbar: Der Text bleibt stehen, bis man ihn schliesst, laesst sich mitsamt den
 * technischen Einzelheiten kopieren, und "Details" klappt Adresse, HTTP-Code und die Fehlerseite des Servers auf.
 * So kann ein Nutzer eine Fehlermeldung schicken, ohne etwas abzutippen.
 */
@Composable
fun MeldungDialog(message: String, details: String?, onClose: () -> Unit) {
    var offen by remember { mutableStateOf(false) }
    var kopiert by remember { mutableStateOf(false) }
    val ablage = LocalClipboardManager.current

    WtAlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.err_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer { Text(message, style = MaterialTheme.typography.bodyMedium) }
                if (offen && details != null) {
                    SelectionContainer {
                        Text(
                            details, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(8.dp),
                        )
                    }
                }
                if (kopiert) Text(stringResource(Res.string.msg_copied), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    ablage.setText(AnnotatedString(if (details != null) message + "\n\n" + details else message))
                    kopiert = true
                }) { Text(stringResource(Res.string.action_copy)) }
                if (details != null) TextButton(onClick = { offen = !offen }) {
                    Text(stringResource(if (offen) Res.string.action_details_hide else Res.string.action_details))
                }
            }
        },
    )
}
