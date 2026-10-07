package dk.aspia.frister

import android.content.Context

/** Kundens profil og små tilstande, delt mellem app, widget og påmindelser. Gemmes kun på telefonen. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("frister", Context.MODE_PRIVATE)

    var profile: Profile
        get() = Profile(
            cvr = sp.getString("cvr", "") ?: "",
            companyName = sp.getString("company", "") ?: "",
            vat = runCatching { VatFrequency.valueOf(sp.getString("vat", null)!!) }.getOrDefault(VatFrequency.HALF_YEAR),
            form = runCatching { CompanyForm.valueOf(sp.getString("form", null)!!) }.getOrDefault(CompanyForm.COMPANY),
            fiscalYearEndMonth = sp.getInt("fy_end", 12),
            contactName = sp.getString("contact", "") ?: "",
            email = sp.getString("email", "") ?: "",
            phone = sp.getString("phone", "") ?: "",
        )
        set(p) = sp.edit()
            .putString("cvr", p.cvr)
            .putString("company", p.companyName)
            .putString("vat", p.vat.name)
            .putString("form", p.form.name)
            .putInt("fy_end", p.fiscalYearEndMonth)
            .putString("contact", p.contactName)
            .putString("email", p.email)
            .putString("phone", p.phone)
            .apply()

    val isSetUp get() = profile.isComplete

    /** Påmindelser, der allerede er sendt ("<frist-id>:<dage>"), så de ikke kommer to gange. */
    var notified: Set<String>
        get() = sp.getStringSet("notified", null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("notified", v).apply()

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()
}
