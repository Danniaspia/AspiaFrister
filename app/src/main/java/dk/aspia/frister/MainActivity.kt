package dk.aspia.frister

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
import android.widget.Toast
import java.time.LocalDate

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_HELP = "help"
    }

    private lateinit var heroLabel: TextView
    private lateinit var heroDays: TextView
    private lateinit var heroCaption: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroOfficial: TextView
    private lateinit var list: LinearLayout
    private lateinit var companyText: TextView
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ticker.ensureChannel(this)
        Ticker.schedule(this)
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
        render()
        DeadlineWidget.updateAll(this)
        if (store.isSetUp && Build.VERSION.SDK_INT >= 33 && !store.askedNotifications) {
            store.askedNotifications = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // ---------- Visning ----------

    private fun render() {
        val p = store.profile
        val today = LocalDate.now()
        val upcoming = if (store.isSetUp) DeadlineEngine.upcoming(p, today) else emptyList()
        val next = upcoming.firstOrNull { it.material >= today }

        if (next == null) {
            heroLabel.text = "NÆSTE FRIST"
            heroDays.text = "Ingen frister"
            heroDays.setTextColor(Palette.ON_HERO)
            heroCaption.text = ""
            heroTitle.text = if (store.isSetUp) "Der er ingen kommende frister" else "Udfyld opsætningen for at se dine frister"
            heroOfficial.text = ""
        } else {
            val days = next.daysToMaterial(today)
            heroLabel.text = "NÆSTE FRIST · ${next.title.uppercase()}"
            heroDays.text = Format.days(days)
            heroDays.setTextColor(Palette.status(days))
            heroCaption.text = if (days > 0) "til Aspia skal have dit materiale" else "Aspia skal have dit materiale i dag"
            heroTitle.text = "Send materiale senest ${Format.long(next.material)}"
            heroOfficial.text = "${authority(next)}-frist: ${Format.long(next.official)} (om ${next.daysToOfficial(today)} dage)"
        }

        list.removeAllViews()
        upcoming.filter { it !== next }.take(8).forEach { list.addView(listRow(it, today)) }
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
        Toast.makeText(this, "Sender…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = HelpSender.send(p, message)
            runOnUiThread {
                sending = false
                if (ok) {
                    AlertDialog.Builder(this)
                        .setTitle("Tak – beskeden er sendt")
                        .setMessage("Aspia kontakter dig hurtigst muligt på ${p.phone}.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    sendByMailApp(p, message)
                }
            }
        }.start()
    }

    /** Reserve når der ikke er forbindelse: kundens egen mail-app med en færdig mail. */
    private fun sendByMailApp(p: Profile, message: String) {
        try {
            startActivity(HelpSender.mailIntent(p, message))
            Toast.makeText(this, "Tryk Send i mail-appen", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Kunne ikke sende")
                .setMessage("Tjek din internetforbindelse og prøv igen – eller skriv til ${Config.ASPIA_EMAIL}.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ---------- Opbygning ----------

    private fun buildUi(): View {
        val col = column()

        val header = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(18)) }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_calendar)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.HERO[1]) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("Aspia Frister", 26f, c.text, bold = true))
        titles.addView(text("Dine frister for moms og regnskab", 14f, c.muted))
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(c.muted)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Indstillinger"
            setOnClickListener { startActivity(Intent(this@MainActivity, SetupActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(header)

        // Næste frist – samme indhold som widgetten – på petrol-gradient med lette ringe.
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, Palette.HERO).apply { cornerRadius = dp(26).toFloat() }
            clipToOutline = true
        }
        listOf(Triple(150, -40, -50), Triple(90, 30, 70)).forEach { (size, right, top) ->
            hero.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(14), 0x14FFFFFF) }
            }, FrameLayout.LayoutParams(dp(size), dp(size), Gravity.END or Gravity.TOP).apply { marginEnd = dp(right); topMargin = dp(top) })
        }
        val heroBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        heroLabel = text("", 11f, Palette.ON_HERO_MUTED, bold = true).apply { letterSpacing = 0.1f }
        heroDays = text("", 46f, Palette.GREEN, bold = true).apply { setPadding(0, dp(6), 0, 0) }
        heroCaption = text("", 14f, Palette.ON_HERO_MUTED)
        heroTitle = text("", 17f, Palette.ON_HERO, bold = true).apply { setPadding(0, dp(14), 0, 0) }
        heroOfficial = text("", 13f, Palette.ON_HERO_MUTED).apply { setPadding(0, dp(4), 0, 0) }
        listOf(heroLabel, heroDays, heroCaption, heroTitle, heroOfficial).forEach(heroBody::addView)
        val help = text("Hjælp – ring mig op", 15f, Palette.HERO[2], bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(Palette.ON_HERO, 22)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { if (store.isSetUp) showHelp() }
        }
        heroBody.addView(help, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(18) })
        hero.addView(heroBody)
        col.addView(hero)

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
                "Din momsfrekvens står i TastSelv Erhverv.",
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
            background = rounded(if (inProgress) Palette.NEUTRAL else Palette.status(days), 3)
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
}
