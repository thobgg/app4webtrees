package de.bgghome.webtrees.nativ.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.bgghome.webtrees.nativ.Texte
import de.bgghome.webtrees.nativ.res.*
import de.bgghome.webtrees.nativ.shared.R
import de.bgghome.webtrees.nativ.WtApp
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Erinnerungen: laeuft alle sechs Stunden (ab 8 Uhr) und fragt den gewaehlten Baum ab - die heutigen Jahrestage einmal
 * am Tag, neue Nachrichten aus webtrees' Nachrichtensystem und (fuer Moderatoren) ausstehende Aenderungen jedes Mal.
 * Abschaltbar; laeuft nur, wenn der Benutzer es eingeschaltet hat. Jede Meldung kommt nur einmal: gemerkt werden der Tag
 * der Jahrestage, die hoechste gemeldete Nachrichtennummer und die Zahl der gemeldeten Aenderungen.
 * Ist die webtrees-Sitzung abgelaufen, passiert einfach nichts - es wird nie ein Passwort gespeichert.
 */
class AnniversaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WtApp
        val settings = app.settings
        val tree = settings.tree

        if (!settings.reminders || tree.isEmpty() || settings.baseUrl.isEmpty()) return Result.success()
        val client = app.plattform.client

        // Jahrestage: einmal am Tag
        val heute = java.time.LocalDate.now().toString()
        if (settings.anniversaryNotified != heute) {
            val today = runCatching { client.anniversaries(tree, 1).data }.getOrNull()
                ?.let { liste -> if (settings.anniversaryLivingOnly) liste.filter { it.vonLebenden() } else liste }
            if (today != null) {
                settings.anniversaryNotified = heute
                if (today.isNotEmpty()) notify(applicationContext, today.map { Texte.t(Res.string.anniv_notification_line, it.name, it.label, it.years) })
            }
        }

        // Nachrichten (ab Stufe 42; aeltere Module lehnen die Aktion ab) und ausstehende Aenderungen (nur Moderatoren)
        val zeilen = mutableListOf<String>()
        runCatching { client.messages(tree, 1).messages }.getOrNull()?.let { nachrichten ->
            val neue = nachrichten.count { it.id > settings.messagesSeen }
            val hoechste = nachrichten.maxOfOrNull { it.id } ?: 0
            if (neue > 0 && hoechste > settings.messagesNotified) {
                settings.messagesNotified = hoechste
                zeilen += if (neue == 1) Texte.t(Res.string.notify_message_one) else Texte.t(Res.string.notify_messages, neue)
            }
        }
        runCatching { client.pending(tree).data.size }.getOrNull()?.let { offen ->
            if (offen > 0 && offen != settings.pendingNotified) {
                zeilen += if (offen == 1) Texte.t(Res.string.notify_pending_one) else Texte.t(Res.string.notify_pending, offen)
            }
            settings.pendingNotified = offen
        }
        if (zeilen.isNotEmpty()) notifyNews(applicationContext, zeilen)

        return Result.success()
    }

    companion object {
        private const val WORK = "anniversaries"
        private const val CHANNEL = "anniversaries"
        private const val CHANNEL_NEWS = "news"

        fun schedule(context: Context, on: Boolean) {
            val manager = WorkManager.getInstance(context)

            if (!on) {
                manager.cancelUniqueWork(WORK)
                return
            }

            val now = LocalDateTime.now()
            var next = now.withHour(8).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val request = PeriodicWorkRequestBuilder<AnniversaryWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun notify(context: Context, lines: List<String>) {
            // Ab Android 13 ohne erteilte Erlaubnis: still bleiben (die Oberflaeche fragt sie beim Einschalten ab).
            val allowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return

            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL, Texte.t(Res.string.home_anniversaries), NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(context, 0, context.packageManager.getLaunchIntentForPackage(context.packageName), flags)
            val style = NotificationCompat.InboxStyle().also { style -> lines.take(6).forEach(style::addLine) }

            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(Texte.t(Res.string.anniv_notification_title))
                .setContentText(lines.first())
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(1, notification)
        }

        /** Neue Nachrichten und ausstehende Aenderungen - eigener Kanal, eigene Nummer, damit die Jahrestage stehen bleiben. */
        fun notifyNews(context: Context, lines: List<String>) {
            val allowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_NEWS, Texte.t(Res.string.notify_news_title), NotificationManager.IMPORTANCE_DEFAULT))

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(context, 1, context.packageManager.getLaunchIntentForPackage(context.packageName), flags)
            val style = NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) }

            val notification = NotificationCompat.Builder(context, CHANNEL_NEWS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(Texte.t(Res.string.notify_news_title))
                .setContentText(lines.joinToString(" · "))
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(2, notification)
        }
    }
}
