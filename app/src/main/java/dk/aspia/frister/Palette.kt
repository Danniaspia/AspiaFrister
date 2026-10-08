package dk.aspia.frister

import android.content.Context
import android.content.res.Configuration

/** Aspias farver fra aspia.dk: navy, petrol og lime – plus logoets blå-pink-orange gradient. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF111C2B.toInt() else 0xFFF5F7FA.toInt()
    val card = if (dark) 0xFF1D2D44.toInt() else 0xFFFFFFFF.toInt()
    val tile = if (dark) 0xFF26395A.toInt() else 0xFFE8F0F2.toInt()
    val text = if (dark) 0xFFFFFFFF.toInt() else NAVY
    val muted = if (dark) 0xFFA9B6C8.toInt() else 0xFF5A6778.toInt()
    /** Overskrifter, valgte chips og primære knapper. */
    val primary = if (dark) TURQUOISE else PETROL
    val onPrimary = if (dark) NAVY else 0xFFFFFFFF.toInt()
    val line = if (dark) 0xFF2C4161.toInt() else 0xFFDDE4EA.toInt()

    companion object {
        val NAVY = 0xFF1D2D44.toInt()
        val PETROL = 0xFF004650.toInt()
        val LIME = 0xFFF5FF6B.toInt()
        val MINT = 0xFFCCF6F3.toInt()
        val TURQUOISE = 0xFF38E2D6.toInt()

        /** Logoets gradient: blå → pink → orange. */
        val BRAND = intArrayOf(0xFF0072FF.toInt(), 0xFFFF4B8F.toInt(), 0xFFFF7B24.toInt())

        /** Det store kort og widgetten er altid petrol, uanset tema. */
        val HERO = intArrayOf(0xFF005A66.toInt(), PETROL, 0xFF00343C.toInt())
        val ON_HERO = 0xFFFFFFFF.toInt()
        val ON_HERO_MUTED = 0xCCFFFFFF.toInt()

        // Lyse nok til at kunne læses på petrol.
        val GREEN = 0xFF5BE3A0.toInt()
        val AMBER = 0xFFFFC94D.toInt()
        val RED = 0xFFFF7A7A.toInt()
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
