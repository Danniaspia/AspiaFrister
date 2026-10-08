package dk.aspia.frister

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class VatFrequency(val label: String) {
    HALF_YEAR("Halvårlig"),
    QUARTER("Kvartalsvis"),
    MONTH("Månedlig"),
    NONE("Ikke momsregistreret"),
}

enum class CompanyForm(val label: String) {
    COMPANY("ApS / A/S"),
    PERSONAL("Enkeltmand / I/S"),
}

enum class Kind(val title: String) {
    VAT("Moms"),
    ANNUAL_REPORT("Årsrapport"),
    TAX_RETURN("Skattemelding"),
}

/** Kundens oplysninger – alt der skal til for at regne fristerne ud og kontakte kunden. */
data class Profile(
    val cvr: String = "",
    val companyName: String = "",
    val vat: VatFrequency = VatFrequency.HALF_YEAR,
    val form: CompanyForm = CompanyForm.COMPANY,
    /** Måneden regnskabsåret slutter i (1–12). Kun for selskaber; enkeltmand følger kalenderåret. */
    val fiscalYearEndMonth: Int = 12,
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
) {
    val isComplete get() = cvr.length == 8 && email.contains('@') && phone.isNotBlank()
}

/**
 * Én frist.
 *
 * [official] er SKATs/Erhvervsstyrelsens frist (rykket til næste bankdag).
 * [material] er Aspias frist for at have kundens materiale (rykket til forrige bankdag).
 */
data class Deadline(
    val kind: Kind,
    val period: String,
    val periodEnd: LocalDate,
    val official: LocalDate,
    val material: LocalDate,
) {
    val id get() = "${kind.name}-$periodEnd"
    val title get() = "${kind.title} · $period"

    fun daysToMaterial(today: LocalDate) = ChronoUnit.DAYS.between(today, material)
    fun daysToOfficial(today: LocalDate) = ChronoUnit.DAYS.between(today, official)

    /** Materialet er (forhåbentlig) afleveret, men SKAT-fristen er ikke nået endnu. */
    fun inProgress(today: LocalDate) = today > material && today <= official
}

/**
 * Bogholderens estimat af kundens momsbetaling.
 * [deadline] er bogholderens frist for bilag; null betyder appens egen materialefrist.
 */
data class Estimate(
    val amount: Double,
    val period: String,
    val deadline: LocalDate?,
    val note: String,
    val updatedAt: String,
    val updatedOn: LocalDate,
)

object DeadlineEngine {

    /**
     * Fristen for bilag til et estimat: bogholderens egen frist, ellers den første momsmaterialefrist
     * efter estimatet blev lavet. Null hvis kunden ikke har moms og bogholderen ikke har sat en frist.
     */
    fun estimateDeadline(e: Estimate, p: Profile): LocalDate? =
        e.deadline ?: upcoming(p, e.updatedOn).firstOrNull { it.kind == Kind.VAT && it.material >= e.updatedOn }?.material

    /** Estimatet vises, til fristen for bilag er passeret. */
    fun activeEstimate(e: Estimate?, p: Profile, today: LocalDate): Estimate? {
        if (e == null || e.amount <= 0) return null
        val d = estimateDeadline(e, p) ?: return null
        return e.takeIf { today <= d }
    }

    /** Materiale til momsen: 1 måned og 10 dage før momsfristen. */
    private const val VAT_MONTHS_BEFORE = 1L
    private const val VAT_DAYS_BEFORE = 10L

    /** Materiale til årsregnskabet: 3 måneder før fristen. */
    private const val ANNUAL_MONTHS_BEFORE = 3L

    /** Materialefristen kan aldrig ligge før perioden er slut + nogle dage (relevant for månedsmoms). */
    private const val MIN_DAYS_AFTER_PERIOD = 5L

    /**
     * Frister, hvor SKAT-fristen ikke er passeret, inden for [horizonMonths].
     * Sorteret efter materialefrist – det kunden skal gøre først, står først.
     */
    fun upcoming(p: Profile, today: LocalDate, horizonMonths: Long = 15): List<Deadline> {
        val until = today.plusMonths(horizonMonths)
        return all(p, today.year - 2..today.year + 2)
            .filter { it.official >= today && it.material <= until }
            .sortedWith(compareBy({ it.material }, { it.official }))
    }

    /** Den næste frist, kunden selv skal handle på (materialefristen er ikke passeret). */
    fun next(p: Profile, today: LocalDate): Deadline? =
        upcoming(p, today).firstOrNull { it.material >= today }

    fun all(p: Profile, years: IntRange): List<Deadline> =
        years.flatMap { vat(p.vat, it) + annual(p, it) }

    // ---------- Moms ----------

    fun vat(freq: VatFrequency, year: Int): List<Deadline> = when (freq) {
        VatFrequency.NONE -> emptyList()
        VatFrequency.HALF_YEAR -> listOf(
            vatDeadline("1. halvår $year", LocalDate.of(year, 6, 30), LocalDate.of(year, 9, 1)),
            vatDeadline("2. halvår $year", LocalDate.of(year, 12, 31), LocalDate.of(year + 1, 3, 1)),
        )
        VatFrequency.QUARTER -> listOf(
            vatDeadline("1. kvartal $year", LocalDate.of(year, 3, 31), LocalDate.of(year, 6, 1)),
            vatDeadline("2. kvartal $year", LocalDate.of(year, 6, 30), LocalDate.of(year, 9, 1)),
            vatDeadline("3. kvartal $year", LocalDate.of(year, 9, 30), LocalDate.of(year, 12, 1)),
            vatDeadline("4. kvartal $year", LocalDate.of(year, 12, 31), LocalDate.of(year + 1, 3, 1)),
        )
        VatFrequency.MONTH -> (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            // Den 25. i måneden efter – undtagen juni, der har sommerfrist 17. august.
            val due = if (m == 6) LocalDate.of(year, 8, 17) else ym.plusMonths(1).atDay(25)
            vatDeadline("${Format.monthName(m)} $year", ym.atEndOfMonth(), due)
        }
    }

    private fun vatDeadline(period: String, periodEnd: LocalDate, nominal: LocalDate): Deadline {
        val raw = nominal.minusMonths(VAT_MONTHS_BEFORE).minusDays(VAT_DAYS_BEFORE)
        return Deadline(Kind.VAT, period, periodEnd, Holidays.onOrAfter(nominal), materialDate(raw, periodEnd))
    }

    // ---------- Årsregnskab ----------

    fun annual(p: Profile, year: Int): List<Deadline> = when (p.form) {
        CompanyForm.COMPANY -> {
            // Regnskabsåret, der slutter i [year]. Årsrapporten skal være indsendt 5 måneder efter.
            val end = YearMonth.of(year, p.fiscalYearEndMonth.coerceIn(1, 12))
            val nominal = end.plusMonths(5).atEndOfMonth()
            val raw = end.plusMonths(5 - ANNUAL_MONTHS_BEFORE).atEndOfMonth()
            val period = if (end.monthValue == 12) "regnskabsår $year" else "regnskabsår ${year - 1}/${year % 100}"
            listOf(Deadline(Kind.ANNUAL_REPORT, period, end.atEndOfMonth(), Holidays.onOrAfter(nominal), materialDate(raw, end.atEndOfMonth())))
        }
        CompanyForm.PERSONAL -> {
            // Selvstændige: skattemeldingen for [year] skal være indsendt 1. juli året efter.
            val end = LocalDate.of(year, 12, 31)
            val nominal = LocalDate.of(year + 1, 7, 1)
            val raw = nominal.minusMonths(ANNUAL_MONTHS_BEFORE)
            listOf(Deadline(Kind.TAX_RETURN, "$year", end, Holidays.onOrAfter(nominal), materialDate(raw, end)))
        }
    }

    private fun materialDate(raw: LocalDate, periodEnd: LocalDate): LocalDate {
        val earliest = periodEnd.plusDays(MIN_DAYS_AFTER_PERIOD)
        return if (raw < earliest) Holidays.onOrAfter(earliest) else Holidays.onOrBefore(raw)
    }
}
