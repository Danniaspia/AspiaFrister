package dk.aspia.frister

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Sender "Ring mig op" til Aspia.
 *
 * Mailen sendes via Web3Forms med kundens mail som Svar-til, så Aspia bare trykker Svar.
 * Kundens adresse kan ikke bruges som afsender (SPF/DMARC ville afvise den).
 */
object HelpSender {

    fun subject(p: Profile) = "Hjælp ønsket – ${p.companyName.ifBlank { "CVR ${p.cvr}" }} (CVR ${p.cvr})"

    fun body(p: Profile, message: String, today: LocalDate = LocalDate.now()): String {
        val next = DeadlineEngine.next(p, today)
        return buildString {
            appendLine("Kunden beder Aspia om at kontakte sig.")
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
            appendLine()
            appendLine("Besked:")
            append(message.ifBlank { "(ingen besked – ring venligst op)" })
        }
    }

    /** true hvis Web3Forms tog imod beskeden. Blokerer – kald fra en baggrundstråd. */
    fun send(p: Profile, message: String): Boolean {
        if (Config.WEB3FORMS_KEY.isBlank()) return false
        return runCatching {
            val payload = JSONObject()
                .put("access_key", Config.WEB3FORMS_KEY)
                .put("subject", subject(p))
                .put("from_name", "Aspia Frister – ${p.companyName.ifBlank { p.contactName }}")
                .put("replyto", p.email)
                .put("name", p.contactName)
                .put("email", p.email)
                .put("phone", p.phone)
                .put("message", body(p, message))
            val c = URL(Config.WEB3FORMS_URL).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            try {
                c.outputStream.use { it.write(payload.toString().toByteArray()) }
                c.responseCode == 200 && JSONObject(c.inputStream.bufferedReader().readText()).optBoolean("success")
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
    }

    /** Reserve: kundens egen mail-app med en færdig mail til Aspia. */
    fun mailIntent(p: Profile, message: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(Config.ASPIA_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject(p))
            putExtra(Intent.EXTRA_TEXT, body(p, message))
        }
}
