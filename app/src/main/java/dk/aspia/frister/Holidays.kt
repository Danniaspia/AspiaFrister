package dk.aspia.frister

import java.time.DayOfWeek
import java.time.LocalDate

/** Danske bankdage. SKAT og Erhvervsstyrelsen rykker frister, der falder på en lukkedag, til næste bankdag. */
object Holidays {

    private val cache = HashMap<Int, Set<LocalDate>>()

    /** Påskedag (gregoriansk, Meeus/Jones/Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    /** Helligdage og andre dage, hvor bankerne holder lukket (ud over weekender). */
    fun closedDays(year: Int): Set<LocalDate> = synchronized(cache) {
        cache.getOrPut(year) {
            val e = easter(year)
            buildSet {
                add(LocalDate.of(year, 1, 1))
                add(e.minusDays(3)) // skærtorsdag
                add(e.minusDays(2)) // langfredag
                add(e)
                add(e.plusDays(1)) // 2. påskedag
                if (year < 2024) add(e.plusDays(26)) // store bededag, afskaffet fra 2024
                add(e.plusDays(39)) // Kristi himmelfart
                add(e.plusDays(40)) // bankerne lukker dagen efter
                add(e.plusDays(49)) // pinsedag
                add(e.plusDays(50)) // 2. pinsedag
                add(LocalDate.of(year, 6, 5)) // grundlovsdag
                add(LocalDate.of(year, 12, 24))
                add(LocalDate.of(year, 12, 25))
                add(LocalDate.of(year, 12, 26))
                add(LocalDate.of(year, 12, 31))
            }
        }
    }

    fun isBankDay(d: LocalDate): Boolean =
        d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && d !in closedDays(d.year)

    /** [d] selv, hvis det er en bankdag, ellers den næste. */
    fun onOrAfter(d: LocalDate): LocalDate {
        var x = d
        while (!isBankDay(x)) x = x.plusDays(1)
        return x
    }

    /** [d] selv, hvis det er en bankdag, ellers den forrige. */
    fun onOrBefore(d: LocalDate): LocalDate {
        var x = d
        while (!isBankDay(x)) x = x.minusDays(1)
        return x
    }
}
