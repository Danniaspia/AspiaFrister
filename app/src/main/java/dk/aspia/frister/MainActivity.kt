package dk.aspia.frister

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_HELP = "help"
        private const val SYNC_STALE_MS = 15 * 60 * 1000L

        private var current: WeakReference<MainActivity>? = null

        /** Kaldes fra baggrundsjobbet, når der er nye data. */
        fun notifyChanged() {
            current?.get()?.let { a -> a.runOnUiThread { a.render() } }
        }
    }

    private lateinit var heroLabel: TextView
    private lateinit var heroBig: TextView
    private lateinit var heroCaption: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroNote: TextView
    private lateinit var pendingCard: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var companyText: TextView
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notify.ensureChannel(this)
        Ticker.schedule(this)
        SyncJob.schedule(this)
        setContentView(buildUi())
        if (!store.isSetUp) {
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_FIRST_RUN, true))
        } else if (intent.getBooleanExtra(EXTRA_HELP, false)) {
            showHelp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_HELP, false) && store.isSetUp) showHelp()
    }

    override fun onResume() {
        super.onResume()
        current = WeakReference(this)
        render()
        DeadlineWidget.updateAll(this)
        if (store.isSetUp && System.currentTimeMillis() - store.lastSync > SYNC_STALE_MS) SyncJob.runNow(this)
        if (store.isSetUp && Build.VERSION.SDK_INT >= 33 && !store.askedNotifications) {
            store.askedNotifications = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onPause() {
        super.onPause()
        if (current?.get() === this) current = null
    }

    // ---------- Visning ----------

    fun render() {
        if (!::heroBig.isInitialized) return
        val p = store.profile
        val today = LocalDate.now()
        val upcoming = if (store.isSetUp) DeadlineEngine.upcoming(p, today) else emptyList()
        val next = upcoming.firstOrNull { it.material >= today }
        val estimate = if (store.isSetUp) DeadlineEngine.activeEstimate(store.estimate, p, today) else null

        heroNote.visibility = View.GONE
        when {
            estimate != null -> {
                val deadline = DeadlineEngine.estimateDeadline(estimate, p)!!
                val days = ChronoUnit.DAYS.between(today, deadline)
                heroLabel.text = "MOMS · ${estimate.period.ifBlank { "FORVENTET" }.uppercase()}"
                heroBig.text = Format.money(estimate.amount)
                heroBig.setTextColor(Palette.LIME)
                heroCaption.text = "forventet moms at betale"
                heroTitle.text = "Send bilag senest ${Format.long(deadline)}"
                heroSub.text = "– så kan beløbet blive mindre. ${Format.days(days)} tilbage."
                heroSub.setTextColor(Palette.status(days))
                if (estimate.note.isNotBlank()) {
                    heroNote.text = "Fra Aspia: ${estimate.note}"
                    heroNote.visibility = View.VISIBLE
                }
            }
            next != null -> {
                val days = next.daysToMaterial(today)
                heroLabel.text = "NÆSTE FRIST · ${next.title.uppercase()}"
                heroBig.text = Format.days(days)
                heroBig.setTextColor(Palette.status(days))
                heroCaption.text = if (days > 0) "til Aspia skal have dit materiale" else "Aspia skal have dit materiale i dag"
                heroTitle.text = "Send materiale senest ${Format.long(next.material)}"
                heroSub.text = "${authority(next)}-frist: ${Format.long(next.official)}"
                heroSub.setTextColor(Palette.ON_HERO_MUTED)
            }
            else -> {
                heroLabel.text = "NÆSTE FRIST"
                heroBig.text = "Ingen frister"
                heroBig.setTextColor(Palette.ON_HERO)
                heroCaption.text = ""
                heroTitle.text = if (store.isSetUp) "Der er ingen kommende frister" else "Udfyld opsætningen for at se dine frister"
                heroSub.text = ""
            }
        }

        pendingCard.visibility = if (store.pendingHelp != null) View.VISIBLE else View.GONE

        list.removeAllViews()
        upcoming.filter { estimate != null || it !== next }.take(8).forEach { list.addView(listRow(it, today)) }
        if (list.childCount == 0) list.addView(text("Ingen andre frister de næste måneder.", 13f, c.muted).apply { setPadding(dp(4), 0, 0, 0) })

        companyText.text = listOfNotNull(
            p.companyName.ifBlank { null },
            "CVR ${p.cvr}",
            "Moms: ${p.vat.label.lowercase()}",
            if (p.form == CompanyForm.COMPANY) "Regnskabsår slutter ${Format.monthName(p.fiscalYearEndMonth).lowercase()}" else p.form.label,
        ).joinToString("\n")
    }

    private fun authority(d: Deadline) = if (d.kind == Kind.ANNUAL_REPORT) "Erhvervsstyrelsen" else "SKAT"

    // ---------- Hjælp ----------

    private fun showHelp() {
        val p = store.profile
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        box.addView(TextView(this).apply {
            text = "Aspia ringer dig op på ${p.phone} hurtigst muligt. Skriv gerne kort, hvad det drejer sig om."
            textSize = 14f
        })
        val input = EditText(this).apply {
            hint = "Hvad drejer det sig om? (valgfrit)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP
        }
        box.addView(input, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })

        AlertDialog.Builder(this)
            .setTitle("Brug for hjælp?")
            .setView(box)
            .setPositiveButton("Ring mig op") { _, _ -> sendHelp(input.text.toString().trim()) }
            .setNegativeButton("Annuller", null)
            .show()
    }

    private fun sendHelp(message: String) {
        if (sending) return
        sending = true
        val p = store.profile
        val progress = AlertDialog.Builder(this).setMessage("Sender din besked til Aspia…").setCancelable(false).show()
        Thread {
            val result = HelpSender.send(applicationContext, p, message, store.estimate)
            runOnUiThread {
                sending = false
                progress.dismiss()
                when (result) {
                    HelpSender.Result.SENT -> AlertDialog.Builder(this)
                        .setTitle("Tak – beskeden er sendt")
                        .setMessage("Aspia ringer dig op på ${p.phone} hurtigst muligt.")
                        .setPositiveButton("OK", null)
                        .show()
                    HelpSender.Result.OFFLINE -> offerAlternatives(
                        message,
                        "Du er offline",
                        "Din telefon har ikke forbindelse til internettet lige nu.\n\n" +
                            "Du kan ringe til Aspia på ${Config.ASPIA_PHONE_DISPLAY} – eller lægge beskeden i kø. " +
                            "Så sendes den automatisk, så snart du er online igen.",
                    )
                    HelpSender.Result.FAILED -> offerAlternatives(
                        message,
                        "Beskeden kunne ikke sendes",
                        "Der skete en fejl, så beskeden ikke kom frem.\n\n" +
                            "Du kan ringe til Aspia på ${Config.ASPIA_PHONE_DISPLAY} – eller lægge beskeden i kø, så prøver appen igen.",
                    )
                }
            }
        }.start()
    }

    private fun offerAlternatives(message: String, title: String, text: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("Læg i kø") { _, _ -> queueHelp(message) }
            .setNegativeButton("Ring til Aspia") { _, _ -> dialAspia() }
            .setNeutralButton("Annuller", null)
            .show()
    }

    private fun queueHelp(message: String) {
        store.pendingHelp = message
        SyncJob.runNow(this)
        render()
    }

    private fun dialAspia() {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Config.ASPIA_PHONE}")))
    }

    // ---------- Opbygning ----------

    private fun buildUi(): View {
        val col = column()

        // Aspia-logo og indstillinger.
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(4)) }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_aspia_logo)
            adjustViewBounds = true
            contentDescription = "Aspia"
        }, LinearLayout.LayoutParams(WRAP, dp(36)))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(c.muted)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Indstillinger"
            setOnClickListener { startActivity(Intent(this@MainActivity, SetupActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(header)
        col.addView(text("Dine frister for moms og regnskab", 15f, c.muted).apply { setPadding(dp(2), dp(6), 0, dp(18)) })

        // Det store kort – samme indhold som widgetten – i Aspia-petrol med logoets gradient-stribe.
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, Palette.HERO).apply { cornerRadius = dp(24).toFloat() }
            clipToOutline = true
        }
        listOf(Triple(170, -50, -40), Triple(100, 40, 90)).forEach { (size, right, top) ->
            hero.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(16), 0x10FFFFFF) }
            }, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.END or Gravity.TOP).apply { marginEnd = dp(right); topMargin = dp(top) })
        }
        hero.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, Palette.BRAND)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(5), Gravity.TOP))

        val heroBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
        }
        heroLabel = text("", 11f, Palette.ON_HERO_MUTED, bold = true).apply { letterSpacing = 0.1f }
        heroBig = text("", 42f, Palette.GREEN, bold = true).apply { setPadding(0, dp(6), 0, 0) }
        heroCaption = text("", 14f, Palette.ON_HERO_MUTED)
        heroTitle = text("", 17f, Palette.ON_HERO, bold = true).apply { setPadding(0, dp(14), 0, 0) }
        heroSub = text("", 13f, Palette.ON_HERO_MUTED).apply { setPadding(0, dp(4), 0, 0) }
        heroNote = text("", 14f, Palette.ON_HERO).apply {
            background = rounded(0x1FFFFFFF, 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        listOf(heroLabel, heroBig, heroCaption, heroTitle, heroSub).forEach(heroBody::addView)
        heroBody.addView(heroNote, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        // Lime knap med mørk tekst – som knapperne på aspia.dk.
        val help = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Palette.LIME, 24)
            setPadding(dp(16), dp(11), dp(20), dp(11))
            setOnClickListener { if (store.isSetUp) showHelp() }
        }
        help.addView(ImageView(this).apply { setImageResource(R.drawable.ic_help) }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
        help.addView(text("Hjælp – ring mig op", 15f, Palette.NAVY, bold = true))
        heroBody.addView(help, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(18) })
        hero.addView(heroBody)
        col.addView(hero)

        // Besked i kø.
        pendingCard = card(topMargin = 12).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pendingCard.addView(text("Din besked til Aspia venter på internet og sendes automatisk.", 14f, c.text), LinearLayout.LayoutParams(0, WRAP, 1f))
        pendingCard.addView(text("Annuller", 14f, c.primary, bold = true).apply {
            setPadding(dp(12), dp(8), 0, dp(8))
            setOnClickListener { store.pendingHelp = null; render() }
        })
        col.addView(pendingCard)

        col.addView(section("Kommende frister"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)

        col.addView(section("Virksomhed"))
        val company = card()
        companyText = text("", 14f, c.text).apply { setLineSpacing(dp(3).toFloat(), 1f) }
        company.addView(companyText)
        company.addView(button("Ret oplysninger", primary = false) {
            startActivity(Intent(this, SetupActivity::class.java))
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        col.addView(company)

        col.addView(text(
            "Materialefristen er Aspias anbefaling: 1 måned og 10 dage før momsfristen og 3 måneder før årsregnskabsfristen. " +
                "Frister, der falder på en weekend eller helligdag, er rykket til næste bankdag som hos SKAT. " +
                "Forventet moms er et estimat fra din bogholder ud fra de bilag, Aspia har modtaget.",
            12f, c.muted,
        ).apply { setPadding(dp(4), dp(18), dp(4), 0) })

        return ScrollView(this).apply {
            setBackgroundColor(c.bg)
            addView(col)
        }
    }

    private fun listRow(d: Deadline, today: LocalDate): View {
        val card = card(topMargin = 8).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(16), dp(14))
        }
        val inProgress = d.inProgress(today)
        val days = d.daysToMaterial(today)
        // Farvet streg i venstre side – samme farvekode som det store kort.
        card.addView(View(this).apply {
            background = rounded(if (inProgress) Palette.NEUTRAL else statusOnCard(days), 3)
        }, LinearLayout.LayoutParams(dp(5), dp(44)).apply { marginEnd = dp(14) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = row().apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(d.title, 15f, c.text, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(text(if (inProgress) "Behandles" else Format.days(days), 14f, if (inProgress) c.muted else c.primary, bold = true))
        body.addView(top)
        body.addView(text(
            if (inProgress) "Materialefristen er passeret – send straks, hvis noget mangler"
            else "Materiale senest ${Format.weekday(d.material)}",
            13f, c.text,
        ).apply { setPadding(0, dp(2), 0, 0) })
        body.addView(text("${authority(d)}-frist ${Format.weekday(d.official)}", 12f, c.muted))
        card.addView(body, LinearLayout.LayoutParams(0, WRAP, 1f))
        return card
    }

    /** Statusfarverne er lyse (til petrol); på hvide kort bruges mørkere udgaver. */
    private fun statusOnCard(days: Long): Int = if (c.dark) Palette.status(days) else when {
        days <= 3 -> 0xFFD93F3F.toInt()
        days <= 14 -> 0xFFE09B00.toInt()
        else -> 0xFF1F9D63.toInt()
    }
}
