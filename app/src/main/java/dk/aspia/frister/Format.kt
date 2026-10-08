package dk.aspia.frister

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val DA = Locale("da", "DK")
    private val MONTHS = listOf(
        "januar", "februar", "marts", "april", "maj", "juni",
        "juli", "august", "september", "oktober", "november", "december",
    )
    private val SHORT = DateTimeFormatter.ofPattern("d. MMM", DA)
    private val LONG = DateTimeFormatter.ofPattern("EEEE 'd.' d. MMMM yyyy", DA)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE 'd.' d. MMM", DA)

    fun monthName(m: Int) = MONTHS[m - 1].replaceFirstChar { it.uppercase() }

    /** "22. jul." */
    fun short(d: LocalDate): String = d.format(SHORT)

    /** "ons. d. 22. jul." */
    fun weekday(d: LocalDate): String = d.format(WEEKDAY)

    /** "onsdag d. 22. juli 2026" */
    fun long(d: LocalDate): String = d.format(LONG)

    /** "200.000 kr." */
    fun money(amount: Double): String =
        java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols(DA)).format(Math.round(amount)) + " kr."

    /** "I dag", "1 dag", "14 dage". */
    fun days(n: Long): String = when {
        n < 0 -> "Overskredet"
        n == 0L -> "I dag"
        n == 1L -> "1 dag"
        else -> "$n dage"
    }
}
