package dk.aspia.frister

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Holder widgetten opdateret og sender påmindelser.
 * Vågner lige efter midnat (ny dato i widgetten) og kl. 9 (påmindelser), og efter genstart.
 */
class Ticker : BroadcastReceiver() {

    companion object {
        private const val ACTION_TICK = "dk.aspia.frister.TICK"
        private const val CHANNEL = "frister"
        private const val REMIND_HOUR = 9

        /** Påmindelse når der er så mange dage til materialefristen. */
        private val REMIND_DAYS = listOf(14L, 7L, 1L, 0L)

        fun schedule(ctx: Context) {
            val now = LocalDateTime.now()
            val midnight = now.toLocalDate().plusDays(1).atTime(0, 1)
            val morning = now.toLocalDate().atTime(REMIND_HOUR, 0).let { if (it > now) it else it.plusDays(1) }
            val at = minOf(midnight, morning).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, Ticker::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }

        fun tick(ctx: Context) {
            DeadlineWidget.updateAll(ctx)
            if (LocalTime.now().hour >= REMIND_HOUR) remind(ctx)
            schedule(ctx)
        }

        fun ensureChannel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Frister", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Påmindelser om at sende materiale til Aspia"
                },
            )
        }

        private fun remind(ctx: Context) {
            val store = Store(ctx)
            if (!store.isSetUp) return
            val today = LocalDate.now()
            val upcoming = DeadlineEngine.upcoming(store.profile, today).filter { it.material >= today }
            val sent = store.notified.filter { key -> upcoming.any { key.startsWith(it.id + ":") } }.toMutableSet()

            ensureChannel(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            upcoming.forEach { d ->
                val days = d.daysToMaterial(today)
                // Kun den mest akutte tærskel – var telefonen slukket på 7-dagen, kommer 7-dages-beskeden dagen efter.
                val threshold = REMIND_DAYS.lastOrNull { days <= it } ?: return@forEach
                if ("${d.id}:$threshold" in sent) return@forEach
                REMIND_DAYS.filter { it >= threshold }.forEach { sent.add("${d.id}:$it") }
                nm.notify(d.id.hashCode(), notification(ctx, d, days))
            }
            store.notified = sent
        }

        private fun notification(ctx: Context, d: Deadline, days: Long): Notification {
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val whenText = when (days) {
                0L -> "i dag"
                1L -> "i morgen"
                else -> "om $days dage"
            }
            return Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(Palette.HERO[1])
                .setContentTitle("Send materiale til Aspia $whenText")
                .setContentText("${d.title} · senest ${Format.weekday(d.material)}")
                .setStyle(
                    Notification.BigTextStyle().bigText(
                        "${d.title}\nSend materiale senest ${Format.long(d.material)}, så Aspia kan nå SKAT-fristen ${Format.short(d.official)}.",
                    ),
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        tick(context)
    }
}
