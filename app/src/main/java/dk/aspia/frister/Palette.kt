package dk.aspia.frister

import android.content.Context
import android.content.res.Configuration

/** Aspia-farver: dyb petrol, lys teal-accent. Lyst og mørkt tema. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF0A1B26.toInt() else 0xFFF3F6F8.toInt()
    val card = if (dark) 0xFF12293A.toInt() else 0xFFFFFFFF.toInt()
    val tile = if (dark) 0xFF1A3549.toInt() else 0xFFE6EDF1.toInt()
    val text = if (dark) 0xFFEAF2F6.toInt() else 0xFF0E2A3B.toInt()
    val muted = if (dark) 0xFF8FA8B8.toInt() else 0xFF5B7183.toInt()
    val primary = if (dark) 0xFF2BB3AC.toInt() else 0xFF0B4F6C.toInt()
    val onPrimary = if (dark) 0xFF062A3C.toInt() else 0xFFFFFFFF.toInt()
    val accent = 0xFF2BB3AC.toInt()
    val line = if (dark) 0xFF21405A.toInt() else 0xFFDCE5EA.toInt()

    companion object {
        /** Det store kort og widgetten er altid petrol, uanset tema. */
        val HERO = intArrayOf(0xFF0E5F80.toInt(), 0xFF0B4F6C.toInt(), 0xFF073247.toInt())
        val ON_HERO = 0xFFFFFFFF.toInt()
        val ON_HERO_MUTED = 0xC8FFFFFF.toInt()

        val GREEN = 0xFF3CCB8B.toInt()
        val AMBER = 0xFFF2B33D.toInt()
        val RED = 0xFFFF6B6B.toInt()
        val NEUTRAL = 0xFF9FB6C4.toInt()

        /** Grøn > 14 dage, gul ≤ 14 dage, rød ≤ 3 dage. */
        fun status(daysLeft: Long): Int = when {
            daysLeft <= 3 -> RED
            daysLeft <= 14 -> AMBER
            else -> GREEN
        }

        fun of(ctx: Context) = Palette(
            ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
        )
    }
}
