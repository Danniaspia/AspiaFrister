package dk.aspia.frister

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.time.LocalDate

/**
 * Baggrundsarbejde, der kræver internet:
 * - henter bogholderens momsestimat hvert 15. minut (og når appen åbnes),
 * - sender en Hjælp-besked, der ligger i kø, så snart telefonen er online.
 */
class SyncJob : JobService() {

    companion object {
        private const val PERIODIC_ID = 10
        private const val NOW_ID = 11
        private const val PERIOD_MS = 15 * 60 * 1000L // det hyppigste Android tillader
        private const val ID_ESTIMATE = 100
        private const val ID_HELP = 101

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            // Planlæg igen, hvis en ældre version kørte med et andet interval.
            if (js.getPendingJob(PERIODIC_ID)?.intervalMillis == PERIOD_MS) return
            js.schedule(
                JobInfo.Builder(PERIODIC_ID, ComponentName(ctx, SyncJob::class.java))
                    .setPeriodic(PERIOD_MS, 5 * 60 * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }

        /** Kører så snart der er internet – med det samme, hvis telefonen er online. */
        fun runNow(ctx: Context) {
            ctx.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(NOW_ID, ComponentName(ctx, SyncJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }

        /** Blokerer – kald fra en baggrundstråd. Returnerer true, hvis en Hjælp-besked stadig venter på internet. */
        fun sync(ctx: Context): Boolean {
            val store = Store(ctx)
            if (!store.isSetUp) return false
            val p = store.profile
            val waiting = sendPendingHelp(ctx, store, p)
            if (Backend.enabled) {
                try {
                    if (!store.registered) {
                        Backend.register(p)
                        store.registered = true
                    }
                    val row = Backend.fetchEstimate(p.cvr)
                    if (row == null) store.registered = false // rækken er slettet – meld kunden ind igen næste gang
                    val e = row?.estimate
                    store.estimate = e
                    store.lastSync = System.currentTimeMillis()
                    val active = DeadlineEngine.activeEstimate(e, p, LocalDate.now())
                    if (active != null && active.updatedAt != store.notifiedEstimate) {
                        store.notifiedEstimate = active.updatedAt
                        notifyEstimate(ctx, active, DeadlineEngine.estimateDeadline(active, p)!!)
                    }
                } catch (e: Exception) {
                    // Ingen forbindelse eller Supabase nede – vi prøver igen ved næste kørsel.
                }
            }
            DeadlineWidget.updateAll(ctx)
            MainActivity.notifyChanged()
            return waiting
        }

        private fun sendPendingHelp(ctx: Context, store: Store, p: Profile): Boolean {
            val msg = store.pendingHelp ?: return false
            when (HelpSender.send(ctx, p, msg, store.estimate)) {
                HelpSender.Result.SENT -> {
                    store.pendingHelp = null
                    Notify.post(ctx, ID_HELP, "Din besked er sendt til Aspia", "Aspia ringer dig op på ${p.phone} hurtigst muligt.")
                }
                HelpSender.Result.FAILED -> {
                    store.pendingHelp = null
                    Notify.post(
                        ctx, ID_HELP, "Beskeden kunne ikke sendes",
                        "Tryk for at ringe til Aspia på ${Config.ASPIA_PHONE_DISPLAY}.",
                        tap = Notify.dialAspia(ctx),
                    )
                }
                HelpSender.Result.OFFLINE -> return true
            }
            return false
        }

        private fun notifyEstimate(ctx: Context, e: Estimate, deadline: LocalDate) {
            Notify.post(
                ctx, ID_ESTIMATE,
                "Forventet moms: ${Format.money(e.amount)}",
                "Send bilag senest ${Format.weekday(deadline)} for at nedbringe beløbet",
                long = buildString {
                    append("${e.period.ifBlank { "Næste momsperiode" }}: Aspia forventer, at du skal betale ${Format.money(e.amount)} i moms.\n")
                    append("Send dine bilag senest ${Format.long(deadline)}, så kan beløbet blive mindre.")
                    if (e.note.isNotBlank()) append("\n\nFra Aspia: ${e.note}")
                },
            )
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val retry = sync(applicationContext)
            jobFinished(params, retry) // JobScheduler prøver igen, når der er netværk
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
