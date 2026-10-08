package dk.aspia.frister

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Supabase (REST): kunden melder sig ind med CVR og henter bogholderens momsestimat. */
object Backend {

    val enabled get() = Config.SUPABASE_URL.isNotBlank() && Config.SUPABASE_KEY.isNotBlank()

    /** Opretter eller opdaterer kundens række. Estimatfelterne røres ikke. Blokerer. */
    fun register(p: Profile) {
        val body = JSONArray().put(
            JSONObject()
                .put("cvr", p.cvr)
                .put("company_name", p.companyName)
                .put("vat_frequency", p.vat.name),
        )
        request("POST", "customers?on_conflict=cvr", body.toString(), "resolution=merge-duplicates,return=minimal")
    }

    /** Kundens estimat, eller null hvis bogholderen ikke har sat et (eller kunden ikke findes). Blokerer. */
    fun fetchEstimate(cvr: String): Row? {
        val select = "cvr,estimate_amount,estimate_period,estimate_deadline,estimate_note,updated_at"
        val arr = JSONArray(request("GET", "customers?cvr=eq.${enc(cvr)}&select=$select", null, null))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        val updatedAt = o.optString("updated_at")
        val estimate = if (o.isNull("estimate_amount")) null else Estimate(
            amount = o.getDouble("estimate_amount"),
            period = o.optString("estimate_period").takeUnless { o.isNull("estimate_period") } ?: "",
            deadline = if (o.isNull("estimate_deadline")) null else LocalDate.parse(o.getString("estimate_deadline")),
            note = if (o.isNull("estimate_note")) "" else o.getString("estimate_note"),
            updatedAt = updatedAt,
            updatedOn = runCatching { OffsetDateTime.parse(updatedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }
                .getOrDefault(LocalDate.now()),
        )
        return Row(estimate)
    }

    /** Kundens række findes; [estimate] er null, hvis der ikke er noget estimat. */
    data class Row(val estimate: Estimate?)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(method: String, path: String, body: String?, prefer: String?): String {
        val c = URL("${Config.SUPABASE_URL.trimEnd('/')}/rest/v1/$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("apikey", Config.SUPABASE_KEY)
        c.setRequestProperty("Accept", "application/json")
        if (prefer != null) c.setRequestProperty("Prefer", prefer)
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("Supabase $code: ${c.errorStream?.bufferedReader()?.readText()}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }
}
