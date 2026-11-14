package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.cosf
import com.starguys.battle.core.sinf
import kotlin.math.abs

/**
 * Procedural cartoon drawings of the heroes. Local space: feet at (0,0), body goes up to about y = -60.
 * lx, ly: normalized look direction (lx >= 0, the caller mirrors the canvas for left-facing).
 */
object HeroArt {
    const val SKIN = 0xFFFFD2B0.toInt()

    private fun feet(c: Canvas, t: Float, mv: Boolean, col: Int, spread: Float = 9f) {
        val ph = if (mv) sinf(t * 14f) else 0f
        G.oval(c, -spread, -3f - maxOf(0f, ph) * 4f, 7.5f, 5f, col)
        G.oval(c, spread, -3f - maxOf(0f, -ph) * 4f, 7.5f, 5f, col)
    }

    fun bob(t: Float, mv: Boolean) = if (mv) abs(sinf(t * 14f)) * 3.5f else sinf(t * 3f) * 1.2f

    fun draw(c: Canvas, id: Int, col: Int, t: Float, mv: Boolean, lx: Float, ly: Float, superOn: Boolean = false, blink: Boolean = false) {
        val b = bob(t, mv)
        val dark = G.shade(col, 0.72f)
        val bl = blink || ((t * 0.7f) % 4f) < 0.08f
        when (id) {
            0 -> { // Gumbo
                if (superOn) {
                    G.circle(c, 0f, -34f, 36f, G.alpha(G.light(col, 0.2f), 0.85f))
                    if (!G.noFace) { G.circle(c, -14f, -50f, 9f, 0xAAFFFFFF.toInt(), false) }
                    G.eyes(c, lx * 4f, -36f, 9f, 6f, lx, ly, bl)
                    G.smile(c, lx * 4f, -22f, 8f)
                    return
                }
                feet(c, t, mv, dark, 10f)
                G.oval(c, 0f, -26f - b, 24f, 23f, col)
                G.circle(c, 9f, -49f - b, 9f, G.light(col, 0.25f))
                G.circle(c, 6f, -52f - b, 3f, 0xCCFFFFFF.toInt(), false)
                G.oval(c, -14f + lx * 3, -20f - b, 5f, 3f, G.shade(col, 0.85f), false)
                G.oval(c, 14f + lx * 3, -20f - b, 5f, 3f, G.shade(col, 0.85f), false)
                G.eyes(c, lx * 4f, -31f - b, 8f, 5.5f, lx, ly, bl)
                G.smile(c, lx * 4f, -20f - b, 6f)
            }
            1 -> { // Volt
                val ph = if (mv) sinf(t * 14f) * 4f else 0f
                G.line(c, -7f, -14f, -8f + ph, -2f, 4f, G.INK); G.line(c, 7f, -14f, 8f - ph, -2f, 4f, G.INK)
                G.oval(c, -9f + ph, -2f, 6f, 3.5f, 0xFF3A3A44.toInt()); G.oval(c, 9f - ph, -2f, 6f, 3.5f, 0xFF3A3A44.toInt())
                G.rrect(c, -15f, -54f - b, 15f, -12f - b, 7f, 0xFF2E2B3A.toInt())
                G.rrect(c, -15f, -54f - b, 15f, -32f - b, 7f, col)
                G.rect(c, -5f, -60f - b, 5f, -54f - b, 0xFFB0B8C8.toInt())
                G.poly(c, 0xFFFFE14D.toInt(), true, 2f, -30f - b, -5f, -21f - b, 0f, -21f - b, -3f, -14f - b, 5f, -23f - b, 0f, -23f - b)
                G.eyes(c, lx * 3f, -43f - b, 6.5f, 4.6f, lx, ly, bl)
                if (superOn || ((t * 3f) % 1f < 0.15f)) {
                    G.line(c, -20f, -50f - b, -26f, -58f - b, 2.5f, 0xFFFFF27A.toInt()); G.line(c, 20f, -40f - b, 27f, -46f - b, 2.5f, 0xFFFFF27A.toInt())
                }
            }
            2 -> { // Mama Tortue
                if (superOn) {
                    c.save(); c.rotate(t * 900f % 360f, 0f, -22f)
                    G.circle(c, 0f, -22f, 22f, 0xFF3E8A3C.toInt())
                    for (k in 0 until 6) { val a = k * 1.047f; G.circle(c, cosf(a) * 11f, -22f + sinf(a) * 11f, 5f, 0xFF6CC56A.toInt(), false) }
                    G.circle(c, 0f, -22f, 6f, 0xFF6CC56A.toInt(), false)
                    c.restore(); return
                }
                // roller skates
                val ph = if (mv) sinf(t * 10f) * 3f else 0f
                for (s in intArrayOf(-1, 1)) {
                    val fx = s * 9f + s * ph
                    G.rrect(c, fx - 8f, -9f, fx + 8f, -2f, 3f, 0xFFFF5E7E.toInt())
                    G.circle(c, fx - 5f, -1f, 3f, 0xFFFFE14D.toInt()); G.circle(c, fx + 5f, -1f, 3f, 0xFFFFE14D.toInt())
                }
                G.oval(c, 0f, -26f - b, 21f, 17f, 0xFF3E8A3C.toInt())
                G.oval(c, 0f, -24f - b, 13f, 11f, 0xFFF2E3B3.toInt())
                G.line(c, -8f, -24f - b, 8f, -24f - b, 2f, 0x55000000)
                G.circle(c, lx * 4f, -46f - b, 13f, col)
                G.circle(c, lx * 4f, -61f - b, 7f, 0xFFD8D8E0.toInt())
                G.ow = 2f
                if (!G.noFace) { G.ring(c, lx * 4f - 5f, -47f - b, 5.5f, 2f, G.INK); G.ring(c, lx * 4f + 5f, -47f - b, 5.5f, 2f, G.INK) }
                G.eyes(c, lx * 4f, -47f - b, 5f, 3.6f, lx, ly, bl)
                G.ow = 3f
                G.smile(c, lx * 4f, -39f - b, 4f)
            }
            3 -> { // Pixel
                val ph = if (mv) sinf(t * 14f) else 0f
                G.rect(c, -14f, -10f - maxOf(0f, ph) * 4f, -4f, -1f - maxOf(0f, ph) * 4f, dark)
                G.rect(c, 4f, -10f - maxOf(0f, -ph) * 4f, 14f, -1f - maxOf(0f, -ph) * 4f, dark)
                G.rect(c, -18f, -48f - b, 18f, -10f - b, col)
                for (k in 1..3) { G.line(c, -18f + k * 9f, -48f - b, -18f + k * 9f, -10f - b, 1.5f, 0x33FFFFFF); G.line(c, -18f, -48f - b + k * 9.5f, 18f, -48f - b + k * 9.5f, 1.5f, 0x33FFFFFF) }
                G.rect(c, -18f, -48f - b, -9f, -39f - b, G.light(col, 0.3f), false)
                for (s in intArrayOf(-1, 1)) {
                    val ex = s * 7f + lx * 3f
                    if (!G.noFace) { if (bl) G.rect(c, ex - 4f, -31f - b, ex + 4f, -29f - b, G.INK, false) else { G.rect(c, ex - 4.5f, -36f - b, ex + 4.5f, -26f - b, G.WHITE); G.rect(c, ex - 1.5f + lx * 2f, -33f - b + ly * 2f, ex + 2.5f + lx * 2f, -28f - b + ly * 2f, G.INK, false) } }
                }
                if (!G.noFace) { G.rect(c, -4f, -20f - b, 4f, -17f - b, G.INK, false) }
                G.rect(c, -2f, -56f - b, 2f, -48f - b, G.INK, false); G.rect(c, -4f, -60f - b, 4f, -55f - b, 0xFFFFE14D.toInt())
            }
            4 -> { // Chef Boulette
                feet(c, t, mv, 0xFFE8E2D8.toInt(), 8f)
                G.poly(c, col, true, 0f, -52f - b, 21f, -14f - b, 16f, -8f - b, -16f, -8f - b, -21f, -14f - b)
                G.rect(c, -13f, -22f - b, 13f, -9f - b, 0xFF2D4A2D.toInt())
                G.rrect(c, -11f, -70f - b, 11f, -54f - b, 5f, G.WHITE)
                G.circle(c, -7f, -70f - b, 6f, G.WHITE); G.circle(c, 7f, -70f - b, 6f, G.WHITE); G.circle(c, 0f, -74f - b, 7f, G.WHITE)
                G.eyes(c, lx * 3f, -34f - b, 6f, 4.4f, lx, ly, bl)
                if (!G.noFace) { G.oval(c, -11f, -27f - b, 3.5f, 2f, 0xFFFFB0B0.toInt(), false); G.oval(c, 11f, -27f - b, 3.5f, 2f, 0xFFFFB0B0.toInt(), false) }
                G.smile(c, lx * 3f, -27f - b, 4f)
            }
            5 -> { // Capitaine Bouchon
                feet(c, t, mv, 0xFF6B4A2E.toInt(), 8f)
                G.rrect(c, -14f, -44f - b, 14f, -8f - b, 6f, col)
                G.line(c, -6f, -40f - b, -6f, -12f - b, 2f, 0x33000000); G.line(c, 6f, -40f - b, 6f, -12f - b, 2f, 0x33000000)
                G.oval(c, 0f, -46f - b, 17f, 8f, G.shade(col, 0.85f))
                G.line(c, -12f, -38f - b, 12f, -30f - b, 2f, 0xFFB0B8C8.toInt())
                G.poly(c, 0xFF2A2433.toInt(), true, -22f, -50f - b, 22f, -50f - b, 12f, -66f - b, 0f, -60f - b, -12f, -66f - b)
                G.circle(c, 0f, -56f - b, 3f, G.WHITE, false)
                // eye + patch
                if (!G.noFace) { G.oval(c, -6f + lx * 3, -30f - b, 4.5f, 5f, G.WHITE); G.circle(c, -6f + lx * 4.5f, -30f - b + ly * 1.5f, 2.3f, G.INK, false) }
                if (!G.noFace) { G.circle(c, 6f + lx * 3, -30f - b, 5f, G.INK, false) }
                if (!G.noFace) { G.line(c, -14f, -36f - b, 14f, -34f - b, 2f, G.INK) }
                G.smile(c, lx * 3f, -21f - b, 4f)
            }
            6 -> { // Nuage
                val fl = sinf(t * 2.5f) * 3f
                G.circle(c, -12f, -30f + fl, 13f, col); G.circle(c, 12f, -30f + fl, 13f, col)
                G.circle(c, 0f, -40f + fl, 16f, col); G.circle(c, 0f, -24f + fl, 15f, col)
                G.circle(c, -5f, -44f + fl, 6f, 0x88FFFFFF.toInt(), false)
                G.eyes(c, lx * 3f, -33f + fl, 6.5f, 4.2f, lx, ly, bl, angry = true)
                G.smile(c, lx * 3f, -22f + fl, 4f, sad = true)
                if (mv || superOn) for (k in 0..2) {
                    val dy = ((t * 2f + k * 0.33f) % 1f) * 14f
                    G.oval(c, -10f + k * 10f, -8f + dy + fl, 2f, 3.5f, 0xFF6BD3FF.toInt(), false)
                }
            }
            7 -> { // Rex-Tracteur
                val wr = if (mv) t * 600f else 0f
                G.rrect(c, -20f, -38f - b, 16f, -14f - b, 6f, col)
                G.rect(c, -8f, -58f - b, 12f, -36f - b, G.shade(col, 0.85f))
                G.rect(c, -5f, -55f - b, 9f, -42f - b, 0xFFBFE8FF.toInt())
                for (k in 0 until 4) G.poly(c, 0xFFFF9A3D.toInt(), true, -8f + k * 5f, -58f - b, -5.5f + k * 5f, -66f - b, -3f + k * 5f, -58f - b)
                G.rect(c, 10f, -50f - b, 14f, -38f - b, 0xFF3A3A44.toInt())
                G.eyes(c, 2f + lx * 2f, -48f - b, 4f, 3.2f, lx, ly, bl)
                c.save(); c.rotate(wr % 360f, -11f, -14f)
                G.circle(c, -11f, -14f, 13f, 0xFF2E2B3A.toInt()); G.circle(c, -11f, -14f, 5f, 0xFFFFC23D.toInt())
                G.line(c, -11f, -25f, -11f, -3f, 2f, 0xFF555066.toInt())
                c.restore()
                G.circle(c, 12f, -9f, 8f, 0xFF2E2B3A.toInt()); G.circle(c, 12f, -9f, 3f, 0xFFFFC23D.toInt())
            }
            8 -> { // Lady Mousse
                feet(c, t, mv, 0xFFFF5E7E.toInt(), 8f)
                G.rrect(c, -17f, -50f - b, 17f, -8f - b, 5f, col)
                G.circle(c, -9f, -18f - b, 2.5f, G.shade(col, 0.8f), false); G.circle(c, 10f, -14f - b, 3f, G.shade(col, 0.8f), false)
                G.circle(c, 11f, -24f - b, 2f, G.shade(col, 0.8f), false); G.circle(c, -12f, -40f - b, 2f, G.shade(col, 0.8f), false)
                G.rect(c, -17f, -56f - b, 17f, -48f - b, 0xFF4FB86B.toInt())
                G.oval(c, 0f, -58f - b, 20f, 4f, 0xFF2A2433.toInt()); G.rrect(c, -8f, -68f - b, 8f, -57f - b, 3f, 0xFF2A2433.toInt())
                G.line(c, 6f, -66f - b, 16f, -76f - b, 3f, 0xFFFF5E7E.toInt())
                G.eyes(c, lx * 3f, -36f - b, 6.5f, 4.4f, lx, ly, bl)
                if (!G.noFace) { G.line(c, -11f + lx * 3, -42f - b, -14f + lx * 3, -45f - b, 1.8f, G.INK); G.line(c, 11f + lx * 3, -42f - b, 14f + lx * 3, -45f - b, 1.8f, G.INK) }
                for (k in -2..2) G.circle(c, k * 5f, -22f - b + abs(k) * -1.2f, 2.2f, G.WHITE, false)
                if (!G.noFace) { G.oval(c, lx * 3f, -28f - b, 3.5f, 2f, 0xFFE84545.toInt(), false) }
            }
            9 -> { // Glitch
                val gl = ((t * 7f).toInt() % 5 == 0)
                val off = if (gl) 4f else 2f
                feet(c, t, mv, G.shade(col, 0.6f), 8f)
                G.oval(c, -off, -30f - b, 16f, 24f, 0x8835F4F4.toInt(), false)
                G.oval(c, off, -30f - b, 16f, 24f, 0x88FF3D7A.toInt(), false)
                G.oval(c, 0f, -30f - b, 16f, 24f, col)
                if (gl) { G.rect(c, -18f, -38f - b, 14f, -33f - b, G.light(col, 0.4f), false); G.rect(c, -12f, -22f - b, 20f, -19f - b, G.shade(col, 0.6f), false) }
                if (!G.noFace) { G.rect(c, -9f + lx * 3, -40f - b, -2f + lx * 3, -32f - b, G.WHITE); G.rect(c, -6.5f + lx * 4.5f, -38f - b, -3.5f + lx * 4.5f, -34f - b, G.INK, false) }
                if (!G.noFace) { G.oval(c, 6f + lx * 3, -36f - b, 4.5f, 5f, G.WHITE); G.circle(c, 6f + lx * 4.5f, -36f - b + ly, 2.3f, G.INK, false) }
                if (!G.noFace) { G.line(c, -6f, -22f - b, -2f, -24f - b, 2f, G.INK); G.line(c, -2f, -24f - b, 2f, -21f - b, 2f, G.INK); G.line(c, 2f, -21f - b, 6f, -23f - b, 2f, G.INK) }
            }
            10 -> { // Mamie Météo
                feet(c, t, mv, 0xFF5A3A5E.toInt(), 7f)
                G.poly(c, col, true, -17f, -8f - b, 17f, -8f - b, 10f, -36f - b, -10f, -36f - b)
                G.line(c, -12f, -16f - b, 12f, -16f - b, 2f, G.light(col, 0.4f))
                G.circle(c, lx * 3f, -46f - b, 12f, SKIN)
                G.circle(c, lx * 3f, -59f - b, 7f, 0xFFE0E0EA.toInt()); G.circle(c, -9f + lx * 3, -50f - b, 5f, 0xFFE0E0EA.toInt()); G.circle(c, 9f + lx * 3, -50f - b, 5f, 0xFFE0E0EA.toInt())
                G.ow = 1.8f
                if (!G.noFace) { G.ring(c, lx * 3f - 4.5f, -46f - b, 4.5f, 1.8f, G.INK); G.ring(c, lx * 3f + 4.5f, -46f - b, 4.5f, 1.8f, G.INK) }
                G.eyes(c, lx * 3f, -46f - b, 4.5f, 3f, lx, ly, bl)
                G.ow = 3f
                G.smile(c, lx * 3f, -40f - b, 3f)
                // umbrella
                val open = superOn
                G.line(c, 16f, -26f - b, 16f, if (open) -78f - b else -60f - b, 3f, 0xFF5A3A44.toInt())
                if (open) {
                    G.pie(c, 16f, -76f - b, 30f, 180f, 180f, 0xFFFF5E7E.toInt())
                    G.pie(c, 16f, -76f - b, 30f, 225f, 45f, 0xFFFFE14D.toInt()); G.pie(c, 16f, -76f - b, 30f, 315f, 45f, 0xFF6BD3FF.toInt())
                    G.arc(c, 16f, -76f - b, 30f, 180f, 180f, 3f, G.INK); G.line(c, -14f, -76f - b, 46f, -76f - b, 3f, G.INK)
                } else {
                    G.poly(c, 0xFFFF5E7E.toInt(), true, 16f, -78f - b, 22f, -58f - b, 10f, -58f - b)
                }
            }
            11 -> { // Bolt le Toaster
                val ph = if (mv) sinf(t * 14f) * 3f else 0f
                G.rect(c, -12f, -12f, -7f, -2f + ph, 0xFF555066.toInt()); G.rect(c, 7f, -12f, 12f, -2f - ph, 0xFF555066.toInt())
                G.rrect(c, -12f, -60f - b, -2f, -42f - b, 4f, 0xFFE0A050.toInt()); G.rrect(c, 2f, -60f - b, 12f, -42f - b, 4f, 0xFFE0A050.toInt())
                G.rrect(c, -19f, -48f - b, 19f, -10f - b, 8f, col)
                G.rrect(c, 12f, -46f - b, 19f, -12f - b, 6f, G.shade(col, 0.8f), false)
                G.rect(c, 19f, -36f - b, 25f, -30f - b, 0xFF3A3A44.toInt())
                if (!G.noFace) { G.rrect(c, -13f, -38f - b, 11f, -21f - b, 5f, 0xFF1E2A3A.toInt()) }
                if (!G.noFace) { if (bl) { G.line(c, -8f, -29f - b, -3f, -29f - b, 2f, 0xFF7CF0FF.toInt()); G.line(c, 2f, -29f - b, 7f, -29f - b, 2f, 0xFF7CF0FF.toInt()) } else { G.circle(c, -5f + lx * 2.5f, -30f - b + ly, 3.5f, 0xFF7CF0FF.toInt(), false); G.circle(c, 5f + lx * 2.5f, -30f - b + ly, 3.5f, 0xFF7CF0FF.toInt(), false) } }
                G.circle(c, -15f, -42f - b, 2.5f, 0xAAFFFFFF.toInt(), false)
            }
            12 -> { // bean
                feet(c, t, mv, G.shade(col, 0.7f), 8f)
                G.oval(c, 0f, -28f - b, 15f, 24f, col)
                G.oval(c, -6f, -40f - b, 4f, 6f, 0x66FFFFFF, false)
                G.eyes(c, lx * 3f, -34f - b, 5.5f, 4.2f, lx, ly, bl)
                G.smile(c, lx * 3f, -24f - b, 4f)
            }
            13 -> { // infected slime
                val w = sinf(t * 6f) * 2f
                G.oval(c, 0f, -20f - b, 21f + w, 20f - w, col)
                G.circle(c, -14f, -6f, 5f, col, false); G.circle(c, 13f, -5f, 4f, col, false)
                G.circle(c, -7f, -32f - b, 4f, 0x88FFFFFF.toInt(), false)
                G.eyes(c, lx * 3f, -24f - b, 7f, 4.8f, lx, ly, false, angry = true)
                G.line(c, -6f + lx * 3, -13f - b, 6f + lx * 3, -13f - b, 3f, G.INK)
            }
            else -> G.circle(c, 0f, -26f, 20f, col)
        }
    }

    /** Small portrait for menus: hero centered in a circle of radius r at (x, y). */
    fun portrait(c: Canvas, id: Int, col: Int, x: Float, y: Float, r: Float, t: Float) {
        c.save()
        c.translate(x, y + r * 0.62f)
        val s = r / 40f
        c.scale(s, s)
        val ow = G.ow
        G.ow = 3f
        draw(c, id, col, t, false, 0.4f, 0.1f)
        G.ow = ow
        c.restore()
    }
}
