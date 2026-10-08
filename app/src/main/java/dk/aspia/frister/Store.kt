package dk.aspia.frister

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

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

    /** Bogholderens seneste estimat (fra Supabase). */
    var estimate: Estimate?
        get() = runCatching {
            val o = JSONObject(sp.getString("estimate", null) ?: return null)
            Estimate(
                amount = o.getDouble("amount"),
                period = o.optString("period"),
                deadline = o.optString("deadline").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                note = o.optString("note"),
                updatedAt = o.optString("updatedAt"),
                updatedOn = LocalDate.parse(o.getString("updatedOn")),
            )
        }.getOrNull()
        set(e) = sp.edit().putString(
            "estimate",
            e?.let {
                JSONObject()
                    .put("amount", it.amount)
                    .put("period", it.period)
                    .put("deadline", it.deadline?.toString() ?: "")
                    .put("note", it.note)
                    .put("updatedAt", it.updatedAt)
                    .put("updatedOn", it.updatedOn.toString())
                    .toString()
            },
        ).apply()

    /** updated_at på det estimat, kunden sidst fik notifikation om. */
    var notifiedEstimate: String
        get() = sp.getString("notified_estimate", "") ?: ""
        set(v) = sp.edit().putString("notified_estimate", v).apply()

    /** true når CVR, navn og momsfrekvens er meldt ind i Supabase. */
    var registered: Boolean
        get() = sp.getBoolean("registered", false)
        set(v) = sp.edit().putBoolean("registered", v).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    /** Hjælp-besked, der venter på internet. */
    var pendingHelp: String?
        get() = sp.getString("pending_help", null)
        set(v) = sp.edit().putString("pending_help", v).commit().let { }
}
