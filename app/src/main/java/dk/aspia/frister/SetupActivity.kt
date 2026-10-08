package dk.aspia.frister

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Opsætning og indstillinger: CVR, momsfrekvens, virksomhedsform, regnskabsår og kontaktoplysninger. */
class SetupActivity : BaseActivity() {

    companion object {
        const val EXTRA_FIRST_RUN = "first_run"
    }

    private var vat = VatFrequency.HALF_YEAR
    private var form = CompanyForm.COMPANY
    private var fyEnd = 12
    private var lookedUp = ""

    private lateinit var cvrInput: EditText
    private lateinit var cvrStatus: TextView
    private lateinit var nameInput: EditText
    private lateinit var vatChips: List<Pair<VatFrequency, TextView>>
    private lateinit var formChips: List<Pair<CompanyForm, TextView>>
    private lateinit var fyBlock: LinearLayout
    private lateinit var fyText: TextView
    private lateinit var contactInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var preview: TextView

    private val firstRun get() = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = store.profile
        vat = p.vat
        form = p.form
        fyEnd = p.fiscalYearEndMonth
        lookedUp = p.cvr
        setContentView(buildUi(p))
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Uden opsætning er der intet at vise – så lukker vi i stedet for at hoppe tilbage i en tom app.
        if (firstRun && !store.isSetUp) finishAffinity() else super.onBackPressed()
    }

    private fun current() = Profile(
        cvr = cvrInput.text.toString().filter(Char::isDigit),
        companyName = nameInput.text.toString().trim(),
        vat = vat,
        form = form,
        fiscalYearEndMonth = fyEnd,
        contactName = contactInput.text.toString().trim(),
        email = emailInput.text.toString().trim(),
        phone = phoneInput.text.toString().trim(),
    )

    private fun render() {
        vatChips.forEach { (v, chip) -> styleChip(chip, v == vat) }
        formChips.forEach { (f, chip) -> styleChip(chip, f == form) }
        fyBlock.visibility = if (form == CompanyForm.COMPANY) View.VISIBLE else View.GONE
        fyText.text = "${lastDay(fyEnd)}. ${Format.monthName(fyEnd).lowercase()}"
        val next = DeadlineEngine.next(current(), java.time.LocalDate.now())
        preview.text = if (next == null) "" else
            "Næste frist bliver: ${next.title}\nMateriale til Aspia senest ${Format.long(next.material)}"
    }

    private fun lastDay(month: Int) = java.time.YearMonth.of(2025, month).lengthOfMonth()

    private fun save() {
        val p = current()
        val error = when {
            p.cvr.length != 8 -> "CVR-nummeret skal have 8 cifre"
            p.contactName.isEmpty() -> "Skriv dit navn"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(p.email).matches() -> "Skriv en gyldig e-mail"
            p.phone.filter(Char::isDigit).length < 8 -> "Skriv et telefonnummer, så Aspia kan ringe dig op"
            else -> null
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }
        if (p.cvr != store.profile.cvr) {
            store.estimate = null
            store.notifiedEstimate = ""
        }
        store.profile = p
        store.notified = emptySet()
        DeadlineWidget.updateAll(this)
        Ticker.schedule(this)
        // Meld kunden (CVR, navn, momsfrekvens) ind hos Aspia, så bogholderen kan se den.
        store.registered = false
        SyncJob.runNow(this)
        setResult(RESULT_OK)
        finish()
    }

    private fun lookupCvr(cvr: String) {
        if (cvr == lookedUp) return
        lookedUp = cvr
        cvrStatus.text = "Slår op i CVR…"
        Thread {
            val name = CvrLookup.companyName(cvr)
            runOnUiThread {
                if (name != null) {
                    nameInput.setText(name)
                    cvrStatus.text = "Fundet i CVR-registret"
                } else {
                    cvrStatus.text = "Ikke fundet – skriv firmanavnet selv"
                }
            }
        }.start()
    }

    // ---------- Opbygning ----------

    private fun buildUi(p: Profile): View {
        val col = column()

        col.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_aspia_logo)
            adjustViewBounds = true
            contentDescription = "Aspia"
        }, LinearLayout.LayoutParams(WRAP, dp(32)).apply { bottomMargin = dp(20) })

        col.addView(text(if (firstRun) "Velkommen" else "Indstillinger", 28f, c.text, bold = true))
        col.addView(text(
            if (firstRun) "Fortæl os lidt om virksomheden, så viser appen dine næste frister – og hvornår Aspia skal have dit materiale."
            else "Ret oplysningerne om virksomheden og dig.",
            14f, c.muted,
        ).apply { setPadding(0, dp(4), 0, 0) })

        col.addView(section("Virksomhed"))
        val company = card()
        company.addView(label("CVR-/momsnummer"))
        cvrInput = input(p.cvr, "12345678", InputType.TYPE_CLASS_NUMBER).apply {
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val cvr = s.toString()
                    if (cvr.length == 8) {
                        lookupCvr(cvr)
                    } else {
                        cvrStatus.text = ""
                    }
                }
            })
        }
        company.addView(cvrInput)
        cvrStatus = text("", 12f, c.muted).apply { setPadding(dp(4), dp(4), 0, 0) }
        company.addView(cvrStatus)
        company.addView(label("Firmanavn"))
        nameInput = input(p.companyName, "Hentes automatisk", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        company.addView(nameInput)
        col.addView(company)

        col.addView(section("Momsfrekvens"))
        val vatGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val vatList = mutableListOf<Pair<VatFrequency, TextView>>()
        VatFrequency.entries.chunked(2).forEach { pair ->
            val r = row().apply { setPadding(0, 0, 0, dp(8)) }
            pair.forEach { v ->
                val chip = chip(v.label) { vat = v; render() }
                vatList += v to chip
                r.addView(chip, weighted())
            }
            vatGrid.addView(r)
        }
        vatChips = vatList
        col.addView(vatGrid)
        col.addView(text(
            "Halvårlig: omsætning under 5 mio. kr. · Kvartalsvis: 5–50 mio. kr. eller nystartet · Månedlig: over 50 mio. kr. " +
                "Er du i tvivl, så se på skat.dk under Moms → Frister – eller tryk Hjælp.",
            12f, c.muted,
        ).apply { setPadding(dp(4), 0, dp(4), 0) })

        col.addView(section("Virksomhedsform"))
        val formRow = row()
        formChips = CompanyForm.entries.map { f ->
            val chip = chip(f.label) { form = f; render() }
            formRow.addView(chip, weighted())
            f to chip
        }
        col.addView(formRow)

        fyBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fyBlock.addView(section("Regnskabsåret slutter"))
        val fyRow = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fyRow.addView(stepper("‹") { fyEnd = if (fyEnd == 1) 12 else fyEnd - 1; render() })
        fyText = text("", 17f, c.text, bold = true).apply { gravity = Gravity.CENTER }
        fyRow.addView(fyText, LinearLayout.LayoutParams(0, WRAP, 1f))
        fyRow.addView(stepper("›") { fyEnd = if (fyEnd == 12) 1 else fyEnd + 1; render() })
        fyBlock.addView(fyRow)
        col.addView(fyBlock)

        col.addView(section("Kontaktperson"))
        val contact = card()
        contact.addView(label("Navn"))
        contactInput = input(p.contactName, "Dit navn", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        contact.addView(contactInput)
        contact.addView(label("E-mail"))
        emailInput = input(p.email, "navn@firma.dk", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        contact.addView(emailInput)
        contact.addView(label("Telefon"))
        phoneInput = input(p.phone, "12 34 56 78", InputType.TYPE_CLASS_PHONE)
        contact.addView(phoneInput)
        contact.addView(text("Bruges kun, når du trykker Hjælp, så Aspia kan ringe dig op. Oplysningerne gemmes kun på din telefon.", 12f, c.muted).apply {
            setPadding(dp(4), dp(10), 0, 0)
        })
        col.addView(contact)

        preview = text("", 13f, c.primary).apply { setPadding(dp(4), dp(18), dp(4), 0) }
        col.addView(preview)

        col.addView(button(if (firstRun) "Kom i gang" else "Gem") { save() }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })

        return ScrollView(this).apply {
            setBackgroundColor(c.bg)
            addView(col)
        }
    }

    private fun label(s: String) = text(s, 12f, c.muted, bold = true).apply { setPadding(dp(4), dp(10), 0, dp(4)) }

    private fun input(value: String, hint: String, type: Int) = EditText(this).apply {
        setText(value)
        this.hint = hint
        inputType = type
        isSingleLine = true
        textSize = 16f
        setTextColor(c.text)
        setHintTextColor(c.muted)
        background = rounded(c.tile, 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun stepper(label: String, onClick: () -> Unit) = text(label, 24f, c.primary, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(c.tile, 12)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { onClick() }
    }
}
