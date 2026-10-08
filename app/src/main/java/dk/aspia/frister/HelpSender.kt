package dk.aspia.frister

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Sender "Ring mig op" direkte til Aspia via Web3Forms – uden kundens mail-app.
 * Kundens mail står som Svar-til, så Aspia bare trykker Svar.
 */
object HelpSender {

    enum class Result { SENT, OFFLINE, FAILED }

    fun subject(p: Profile) = "Hjælp ønsket – ${p.companyName.ifBlank { "CVR ${p.cvr}" }} (CVR ${p.cvr})"

    fun body(p: Profile, message: String, estimate: Estimate? = null, today: LocalDate = LocalDate.now()): String {
        val next = DeadlineEngine.next(p, today)
        return buildString {
            appendLine("Kunden beder Aspia om at ringe op.")
            appendLine()
            appendLine("Firma: ${p.companyName}")
            appendLine("CVR: ${p.cvr}")
            appendLine("Kontaktperson: ${p.contactName}")
            appendLine("Telefon: ${p.phone}")
            appendLine("E-mail: ${p.email}")
            appendLine("Moms: ${p.vat.label} · ${p.form.label}")
            if (next != null) {
                appendLine("Næste frist: ${next.title} – materiale ${Format.short(next.material)}, SKAT ${Format.short(next.official)}")
            }
            if (estimate != null) {
                appendLine("Forventet moms i appen: ${Format.money(estimate.amount)} (${estimate.period})")
            }
            appendLine()
            appendLine("Besked:")
            append(message.ifBlank { "(ingen besked – ring venligst op)" })
        }
    }

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Blokerer – kald fra en baggrundstråd. */
    fun send(ctx: Context, p: Profile, message: String, estimate: Estimate? = null): Result {
        if (!isOnline(ctx)) return Result.OFFLINE
        if (Config.WEB3FORMS_KEY.isBlank()) return Result.FAILED
        val payload = JSONObject()
            .put("access_key", Config.WEB3FORMS_KEY)
            .put("subject", subject(p))
            .put("from_name", "Aspia Frister – ${p.companyName.ifBlank { p.contactName }}")
            .put("replyto", p.email)
            .put("name", p.contactName)
            .put("email", p.email)
            .put("phone", p.phone)
            .put("message", body(p, message, estimate))
        return try {
            val c = URL(Config.WEB3FORMS_URL).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            try {
                c.outputStream.use { it.write(payload.toString().toByteArray()) }
                val ok = c.responseCode == 200 && JSONObject(c.inputStream.bufferedReader().readText()).optBoolean("success")
                if (ok) Result.SENT else Result.FAILED
            } finally {
                c.disconnect()
            }
        } catch (e: IOException) {
            Result.OFFLINE
        } catch (e: Exception) {
            Result.FAILED
        }
    }
}
