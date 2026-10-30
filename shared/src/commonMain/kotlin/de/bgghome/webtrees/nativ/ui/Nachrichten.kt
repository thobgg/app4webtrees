package de.bgghome.webtrees.nativ.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgghome.webtrees.nativ.api.MessageJson
import de.bgghome.webtrees.nativ.api.MessageLink
import de.bgghome.webtrees.nativ.res.*
import org.jetbrains.compose.resources.stringResource

/*
 * Nachrichten am Handy und Tablet: der Posteingang aus webtrees' eigenem Nachrichtensystem als Seite im Nachschlage-Stapel,
 * dazu der Dialog fuer eine neue Nachricht (auch zum Antworten und zum Teilen eines Datensatzes). Links auf Datensaetze
 * dieses Servers kommen vom Modul aufgeloest (links): Personen und Quellen oeffnen nativ, der Rest im eingebetteten Browser.
 */

/** Vorbelegung des Dialogs: Empfaenger, Betreff, Text und die Seite eines geteilten Datensatzes. */
data class NachrichtEntwurf(val to: String = "", val subject: String = "", val body: String = "", val url: String? = null)

/** Antwort auf eine Nachricht: an den Absender, Betreff mit "RE:" wie im webtrees-Block. */
internal fun antwortEntwurf(m: MessageJson): NachrichtEntwurf =
    NachrichtEntwurf(to = m.senderUser.orEmpty(), subject = if (m.subject.startsWith("RE:")) m.subject else "RE: " + m.subject)

@Composable
internal fun NachrichtenSeite(
    state: UiState, viewModel: AppViewModel,
    onOeffnen: (Nachschlag) -> Unit, onZurueck: () -> Unit, onPerson: (String) -> Unit, onWeb: (String) -> Unit,
) {
    // Was beim Oeffnen neu war, bleibt auf dieser Seite hervorgehoben - gesehen ist es trotzdem ab jetzt.
    val gesehenBeimOeffnen = remember { state.messagesSeen }
    LaunchedEffect(state.messages) { viewModel.markMessagesSeen() }
    var entwurf by remember { mutableStateOf<NachrichtEntwurf?>(null) }
    var loeschen by remember { mutableStateOf<MessageJson?>(null) }

    Column(Modifier.fillMaxSize()) {
        Kopf(stringResource(Res.string.messages_title), onZurueck) {
            TextButton(onClick = { entwurf = NachrichtEntwurf() }) { Text(stringResource(Res.string.messages_new)) }
        }
        if (state.messages.isEmpty()) {
            Text(stringResource(Res.string.messages_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            items(state.messages, key = { it.id }) { m ->
                NachrichtKarte(m, neu = m.id > gesehenBeimOeffnen,
                    onReply = m.senderUser?.let { { entwurf = antwortEntwurf(m) } },
                    onDelete = { loeschen = m },
                    onLink = { link -> linkOeffnen(link, onOeffnen, onPerson, onWeb) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.messagesNextPage != null) item {
                TextButton(onClick = viewModel::loadMoreMessages, modifier = Modifier.padding(8.dp)) { Text(stringResource(Res.string.messages_load_more)) }
            }
        }
    }

    entwurf?.let { NachrichtDialog(state, viewModel, it, onDismiss = { entwurf = null }) }
    loeschen?.let { m ->
        ConfirmDialog(
            title = stringResource(Res.string.messages_delete_title), text = stringResource(Res.string.messages_delete_text),
            confirm = stringResource(Res.string.action_delete),
            onDismiss = { loeschen = null }, onConfirm = { loeschen = null; viewModel.deleteMessage(m.id) },
        )
    }
}

/** Links auf Datensaetze: Person und Quelle nativ, alles andere als webtrees-Seite; nicht Sichtbares bleibt zu. */
private fun linkOeffnen(link: MessageLink, onOeffnen: (Nachschlag) -> Unit, onPerson: (String) -> Unit, onWeb: (String) -> Unit) {
    val xref = link.xref ?: return
    when (link.type) {
        "INDI" -> onPerson(xref)
        "SOUR" -> onOeffnen(Nachschlag.Quelle(xref))
        else -> onWeb(link.url)
    }
}

/** Eine Nachricht mit Absender, Zeit, Betreff, Text, Links und den Knoepfen Antworten und Loeschen. */
@Composable
private fun NachrichtKarte(m: MessageJson, neu: Boolean, onReply: (() -> Unit)?, onDelete: () -> Unit, onLink: (MessageLink) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.messages_from, m.absender), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = if (neu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (neu) FontWeight.SemiBold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(nachrichtZeit(m.created), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (m.subject.isNotBlank()) Text(m.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (m.text.isNotBlank()) Text(m.text, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
        if (m.links.isNotEmpty()) LinkChips(m.links, onLink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) }
            if (onReply != null) TextButton(onClick = onReply) { Text(stringResource(Res.string.messages_reply)) }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun LinkChips(links: List<MessageLink>, onLink: (MessageLink) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { link ->
            val art = when (link.type) {
                "INDI" -> stringResource(Res.string.messages_link_person)
                "FAM" -> stringResource(Res.string.messages_link_family)
                "SOUR" -> stringResource(Res.string.messages_link_source)
                "OBJE" -> stringResource(Res.string.messages_link_media)
                "_LOC" -> stringResource(Res.string.messages_link_place)
                else -> link.type
            }
            val text = link.xref?.let { "$art $it" } ?: (art + " · " + stringResource(Res.string.messages_link_hidden))
            AssistChip(onClick = { onLink(link) }, enabled = link.xref != null, label = { Text(text, maxLines = 1, softWrap = false) })
        }
    }
}

/** Kurze Zeile fuer die Startseite: Absender und Betreff bzw. Textanfang, neue hervorgehoben. */
@Composable
internal fun NachrichtZeile(m: MessageJson, neu: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.absender, style = MaterialTheme.typography.labelMedium, fontWeight = if (neu) FontWeight.SemiBold else null,
                color = if (neu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.subject.ifBlank { m.text }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(0.dp))
        Text(nachrichtZeit(m.created), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Dialog fuer eine neue Nachricht, eine Antwort oder das Teilen eines Datensatzes (url und Link im Text sind dann vorbelegt). */
@Composable
internal fun NachrichtDialog(state: UiState, viewModel: AppViewModel, entwurf: NachrichtEntwurf, onDismiss: () -> Unit) {
    val empfaenger = state.recipients
    var to by remember { mutableStateOf(entwurf.to.takeIf { t -> empfaenger.any { it.user == t } } ?: empfaenger.singleOrNull()?.user.orEmpty()) }
    var subject by remember { mutableStateOf(entwurf.subject) }
    var body by remember { mutableStateOf(entwurf.body) }
    val mailHinweis = stringResource(Res.string.messages_contact_mail)

    WtAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entwurf.subject.startsWith("RE:")) Res.string.messages_reply else Res.string.messages_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (empfaenger.isEmpty()) {
                    Text(stringResource(Res.string.messages_no_recipients), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(Res.string.messages_to), style = MaterialTheme.typography.labelMedium)
                    // Nur-Mail-Empfaenger bekommen den Hinweis, damit niemand im Posteingang auf eine Antwort wartet
                    ChipRow(empfaenger.map { r -> r.user to (r.name.ifBlank { r.user } + if (r.contact == "messaging3" || r.contact == "mailto") " · $mailHinweis" else "") }, to) { to = it }
                }
                Field(subject, { subject = it }, Res.string.messages_subject)
                Field(body, { body = it }, Res.string.messages_text, minLines = 4)
                entwurf.url?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = to.isNotEmpty() && body.isNotBlank() && !state.busy,
                onClick = { viewModel.sendMessage(listOf(to), subject, body, entwurf.url) { ok -> if (ok) { onDismiss(); viewModel.loadMessages() } } },
            ) { Text(stringResource(Res.string.messages_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Zeitpunkt der Nachricht in der Form des Geraets; der Server liefert ISO 8601 mit Zeitzone. */
internal fun nachrichtZeit(created: String): String = runCatching {
    java.time.OffsetDateTime.parse(created).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(java.util.Locale.getDefault()))
}.getOrDefault(created.take(16).replace('T', ' '))
