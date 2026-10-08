package dk.aspia.frister

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Fælles notifikationer: påmindelser, nye momsestimater og Hjælp-beskeder i kø. */
object Notify {
    private const val CHANNEL = "frister"

    fun ensureChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Frister og moms", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Påmindelser om materiale, forventet moms og beskeder til Aspia"
            },
        )
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun dialAspia(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 2, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Config.ASPIA_PHONE}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(ctx: Context, id: Int, title: String, text: String, long: String = text, tap: PendingIntent = openApp(ctx)) {
        ensureChannel(ctx)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(Palette.PETROL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(long))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
