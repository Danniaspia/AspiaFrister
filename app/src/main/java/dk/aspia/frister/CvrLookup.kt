package dk.aspia.frister

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Slår firmanavnet op ud fra CVR-nummeret (cvrapi.dk, gratis og uden nøgle). */
object CvrLookup {

    /** Firmanavnet, eller null hvis det ikke kunne findes. Blokerer – kald fra en baggrundstråd. */
    fun companyName(cvr: String): String? = runCatching {
        val c = URL("https://cvrapi.dk/api?search=$cvr&country=dk").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        // cvrapi.dk kræver en beskrivende User-Agent.
        c.setRequestProperty("User-Agent", "Aspia Frister - Android app")
        try {
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            json.optString("name").takeIf { it.isNotBlank() && !json.has("error") }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
