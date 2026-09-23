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
 * Taegliche Erinnerung: fragt einmal am Tag (gegen 8 Uhr) die heutigen Jahrestage des gewaehlten Baums ab und
 * zeigt sie als Benachrichtigung. Abschaltbar; laeuft nur, wenn der Benutzer es eingeschaltet hat.
 * Ist die webtrees-Sitzung abgelaufen, passiert einfach nichts - es wird nie ein Passwort gespeichert.
 */
class AnniversaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WtApp
        val tree = app.settings.tree

        if (!app.settings.reminders || tree.isEmpty() || app.settings.baseUrl.isEmpty()) return Result.success()

        val today = runCatching { app.plattform.client.anniversaries(tree, 1).data }.getOrNull() ?: return Result.success()
        if (today.isEmpty()) return Result.success()

        val lines = today.map { Texte.t(Res.string.anniv_notification_line, it.name, it.label, it.years) }
        notify(applicationContext, lines)

        return Result.success()
    }

    companion object {
        private const val WORK = "anniversaries"
        private const val CHANNEL = "anniversaries"

        fun schedule(context: Context, on: Boolean) {
            val manager = WorkManager.getInstance(context)

            if (!on) {
                manager.cancelUniqueWork(WORK)
                return
            }

            val now = LocalDateTime.now()
            var next = now.withHour(8).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val request = PeriodicWorkRequestBuilder<AnniversaryWorker>(24, TimeUnit.HOURS)
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
    }
}
