package dk.aspia.frister

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeadlineEngineTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun easter() {
        assertEquals(d(2024, 3, 31), Holidays.easter(2024))
        assertEquals(d(2025, 4, 20), Holidays.easter(2025))
        assertEquals(d(2026, 4, 5), Holidays.easter(2026))
        assertEquals(d(2027, 3, 28), Holidays.easter(2027))
    }

    @Test
    fun bankDays() {
        assertFalse(Holidays.isBankDay(d(2026, 5, 25))) // 2. pinsedag
        assertFalse(Holidays.isBankDay(d(2026, 5, 15))) // dagen efter Kristi himmelfart
        assertFalse(Holidays.isBankDay(d(2026, 6, 5))) // grundlovsdag
        assertTrue(Holidays.isBankDay(d(2026, 5, 1))) // store bededag findes ikke længere
        assertEquals(d(2026, 12, 28), Holidays.onOrAfter(d(2026, 12, 24)))
        assertEquals(d(2026, 12, 23), Holidays.onOrBefore(d(2026, 12, 27)))
    }

    /** Skat.dk's tabel for månedsmoms 2026. */
    @Test
    fun monthlyVatMatchesSkat2026() {
        val due = DeadlineEngine.vat(VatFrequency.MONTH, 2026).map { it.official }
        assertEquals(
            listOf(
                d(2026, 2, 25), d(2026, 3, 25), d(2026, 4, 27), d(2026, 5, 26), d(2026, 6, 25), d(2026, 8, 17),
                d(2026, 8, 25), d(2026, 9, 25), d(2026, 10, 26), d(2026, 11, 25), d(2026, 12, 28), d(2027, 1, 25),
            ),
            due,
        )
        // December 2025 → 26. januar 2026 (25. er en søndag).
        assertEquals(d(2026, 1, 26), DeadlineEngine.vat(VatFrequency.MONTH, 2025).last().official)
    }

    @Test
    fun quarterlyVatMatchesSkat2026() {
        assertEquals(d(2026, 3, 2), DeadlineEngine.vat(VatFrequency.QUARTER, 2025).last().official)
        assertEquals(
            listOf(d(2026, 6, 1), d(2026, 9, 1), d(2026, 12, 1), d(2027, 3, 1)),
            DeadlineEngine.vat(VatFrequency.QUARTER, 2026).map { it.official },
        )
    }

    @Test
    fun halfYearVatAndMaterial() {
        val h = DeadlineEngine.vat(VatFrequency.HALF_YEAR, 2026)
        assertEquals(d(2026, 9, 1), h[0].official)
        assertEquals(d(2026, 7, 22), h[0].material) // 1/9 − 1 md. − 10 dage
        assertEquals(d(2027, 3, 1), h[1].official)
        assertEquals(d(2027, 1, 22), h[1].material) // 1/3 − 1 md. − 10 dage = 22/1 (fredag)
    }

    @Test
    fun materialMovesBackFromWeekend() {
        // Q1 2026: 1/6 − 1 md. − 10 dage = 21/4 (tirsdag). Q3 2026: 1/12 → 22/10 (torsdag).
        val q = DeadlineEngine.vat(VatFrequency.QUARTER, 2026)
        assertEquals(d(2026, 4, 21), q[0].material)
        assertEquals(d(2026, 10, 22), q[2].material)
        // H1 2028: 1/9 → 22/7/2028 er en lørdag → fredag 21/7.
        assertEquals(d(2028, 7, 21), DeadlineEngine.vat(VatFrequency.HALF_YEAR, 2028)[0].material)
    }

    @Test
    fun monthlyMaterialNeverBeforePeriodEnd() {
        DeadlineEngine.vat(VatFrequency.MONTH, 2026).forEach {
            assertTrue(it.period, it.material > it.periodEnd)
            assertTrue(it.period, it.material < it.official)
        }
    }

    @Test
    fun annualReportCalendarYear() {
        val a = DeadlineEngine.annual(Profile(form = CompanyForm.COMPANY), 2026).single()
        assertEquals(Kind.ANNUAL_REPORT, a.kind)
        assertEquals(d(2027, 5, 31), a.official)
        assertEquals(d(2027, 2, 26), a.material) // 28/2/2027 er en søndag → fredag 26/2
        assertEquals(d(2026, 6, 1), DeadlineEngine.annual(Profile(), 2025).single().official) // 31/5/2026 er en søndag
        assertEquals(d(2025, 2, 28), DeadlineEngine.annual(Profile(), 2024).single().material)
    }

    @Test
    fun annualReportShiftedFiscalYear() {
        val a = DeadlineEngine.annual(Profile(fiscalYearEndMonth = 6), 2026).single()
        assertEquals("regnskabsår 2025/26", a.period)
        assertEquals(d(2026, 11, 30), a.official)
        assertEquals(d(2026, 8, 31), a.material)
        val sep = DeadlineEngine.annual(Profile(fiscalYearEndMonth = 9), 2027).single()
        assertEquals(d(2028, 2, 29), sep.official) // skudår
    }

    @Test
    fun personalTaxReturn() {
        val t = DeadlineEngine.annual(Profile(form = CompanyForm.PERSONAL), 2026).single()
        assertEquals(Kind.TAX_RETURN, t.kind)
        assertEquals(d(2027, 7, 1), t.official)
        assertEquals(d(2027, 4, 1), t.material)
    }

    @Test
    fun nextSkipsDeadlinesWhereMaterialIsDue() {
        val p = Profile(vat = VatFrequency.HALF_YEAR)
        val today = d(2026, 10, 7)
        val next = DeadlineEngine.next(p, today)!!
        assertEquals("2. halvår 2026", next.period)
        assertEquals(d(2027, 1, 22), next.material)

        // 1/8: materialet til 1. halvår er afleveret, men SKAT-fristen 1/9 er ikke nået → stadig på listen.
        val aug = d(2026, 8, 1)
        val up = DeadlineEngine.upcoming(p, aug)
        assertTrue(up.first().inProgress(aug))
        assertEquals("1. halvår 2026", up.first().period)
        assertEquals("2. halvår 2026", DeadlineEngine.next(p, aug)!!.period)
    }

    @Test
    fun noVat() {
        val p = Profile(vat = VatFrequency.NONE)
        assertTrue(DeadlineEngine.upcoming(p, d(2026, 10, 7)).all { it.kind == Kind.ANNUAL_REPORT })
    }

    private fun estimate(deadline: LocalDate?, updatedOn: LocalDate, amount: Double = 200_000.0) =
        Estimate(amount, "2. halvår 2026", deadline, "", "x", updatedOn)

    @Test
    fun estimateUsesBookkeepersDeadline() {
        val p = Profile(vat = VatFrequency.HALF_YEAR)
        val e = estimate(d(2026, 11, 15), d(2026, 10, 8))
        assertEquals(d(2026, 11, 15), DeadlineEngine.estimateDeadline(e, p))
        assertEquals(e, DeadlineEngine.activeEstimate(e, p, d(2026, 11, 15)))
        assertEquals(null, DeadlineEngine.activeEstimate(e, p, d(2026, 11, 16)))
    }

    @Test
    fun estimateFallsBackToNextVatMaterialDeadline() {
        val p = Profile(vat = VatFrequency.HALF_YEAR)
        val e = estimate(null, d(2026, 10, 8))
        assertEquals(d(2027, 1, 22), DeadlineEngine.estimateDeadline(e, p))
        // Fristen ligger fast ud fra hvornår estimatet blev lavet – det forsvinder bagefter.
        assertEquals(null, DeadlineEngine.activeEstimate(e, p, d(2027, 1, 23)))
    }

    @Test
    fun estimateWithoutAmountIsHidden() {
        val p = Profile()
        assertEquals(null, DeadlineEngine.activeEstimate(estimate(d(2026, 12, 1), d(2026, 10, 8), 0.0), p, d(2026, 10, 8)))
        assertEquals(null, DeadlineEngine.activeEstimate(null, p, d(2026, 10, 8)))
    }

    @Test
    fun money() {
        assertEquals("200.000 kr.", Format.money(200_000.0))
        assertEquals("1.234.568 kr.", Format.money(1_234_567.6))
    }
}
