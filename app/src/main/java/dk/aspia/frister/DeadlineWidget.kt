package dk.aspia.frister

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

/** Hjemmeskærms-widgetten: næste materialefrist med nedtælling og en Hjælp-knap. */
class DeadlineWidget : AppWidgetProvider() {

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DeadlineWidget::class.java))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, render(ctx))
        }

        private fun render(ctx: Context): RemoteViews {
            val store = Store(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_deadline)
            val today = LocalDate.now()
            val next = if (store.isSetUp) DeadlineEngine.next(store.profile, today) else null

            when {
                !store.isSetUp -> {
                    v.setTextViewText(R.id.label, "ASPIA · FRISTER")
                    v.setTextViewText(R.id.days, "Kom i gang")
                    v.setTextColor(R.id.days, Palette.ON_HERO)
                    v.setTextViewText(R.id.caption, "")
                    v.setTextViewText(R.id.title, "Tryk for at indtaste CVR og momsfrekvens")
                    v.setTextViewText(R.id.footer, "")
                }
                next == null -> {
                    v.setTextViewText(R.id.label, "ASPIA · FRISTER")
                    v.setTextViewText(R.id.days, "Ingen frister")
                    v.setTextColor(R.id.days, Palette.ON_HERO)
                    v.setTextViewText(R.id.caption, "")
                    v.setTextViewText(R.id.title, store.profile.companyName)
                    v.setTextViewText(R.id.footer, "")
                }
                else -> {
                    val days = next.daysToMaterial(today)
                    v.setTextViewText(R.id.label, "${next.kind.title.uppercase()} · ${next.period.uppercase()}")
                    v.setTextViewText(R.id.days, Format.days(days))
                    v.setTextColor(R.id.days, Palette.status(days))
                    v.setTextViewText(R.id.caption, if (days > 0) "til materialefristen" else "")
                    v.setTextViewText(R.id.title, "Send materiale senest ${Format.weekday(next.material)}")
                    v.setTextViewText(R.id.footer, "Frist hos ${if (next.kind == Kind.ANNUAL_REPORT) "Erhvervsstyrelsen" else "SKAT"}: ${Format.short(next.official)}")
                }
            }

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val help = PendingIntent.getActivity(
                ctx, 1,
                Intent(ctx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_HELP, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.root, open)
            v.setOnClickPendingIntent(R.id.help, help)
            return v
        }
    }

    override fun onEnabled(context: Context) {
        Ticker.schedule(context)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Ticker.schedule(context)
        mgr.updateAppWidget(ids, render(context))
    }
}
