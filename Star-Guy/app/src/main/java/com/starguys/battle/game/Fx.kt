package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.cosf
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf
import kotlin.math.roundToInt

/** Particles, shockwaves, damage numbers, beams... (all cosmetic). */
class Fx(val m: Match) {
    private class P {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var g = 0f
        var life = 0f; var max = 1f; var size = 4f; var color = 0; var kind = 0; var rot = 0f; var vr = 0f
        var text = ""; var x2 = 0f; var y2 = 0f; var drag = 0f
    }

    private val ps = ArrayList<P>(512)
    private val pool = ArrayList<P>(512)

    private fun p(): P = if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else P()

    private fun add(kind: Int, x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int, g: Float = 0f): P {
        val q = p()
        q.kind = kind; q.x = x; q.y = y; q.vx = vx; q.vy = vy; q.life = life; q.max = life; q.size = size; q.color = color; q.g = g
        q.rot = rf() * 6.28f; q.vr = rf(-8f, 8f); q.drag = 0f; q.text = ""
        if (ps.size < 1400) ps.add(q)
        return q
    }

    companion object {
        const val DOT = 0; const val CONF = 1; const val SMOKE = 2; const val RING = 3; const val NUM = 4; const val TEXT = 5
        const val LINE = 6; const val STAR = 7; const val ARC = 8; const val PIX = 9; const val DEBRIS = 10; const val FLAME = 11; const val DROP = 12
        val CONFETTI = intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFB46CFF.toInt(), 0xFFFF9A3D.toInt())
    }

    fun confetti(x: Float, y: Float, n: Int) {
        for (i in 0 until n) {
            val a = rf() * 6.28f; val s = rf(150f, 520f)
            add(CONF, x, y, cosf(a) * s, sinf(a) * s - 250f, rf(0.8f, 1.6f), rf(5f, 9f), CONFETTI[i % CONFETTI.size], 700f).drag = 2f
        }
    }

    fun smoke(x: Float, y: Float, n: Int, color: Int) {
        for (i in 0 until n) add(SMOKE, x + rf(-14f, 14f), y + rf(-8f, 8f), rf(-40f, 40f), rf(-70f, -20f), rf(0.5f, 0.9f), rf(9f, 16f), color)
    }

    fun ring(x: Float, y: Float, r: Float, color: Int, life: Float = 0.35f) { add(RING, x, y, 0f, 0f, life, r, color) }

    fun hit(x: Float, y: Float, color: Int) {
        for (i in 0 until 5) { val a = rf() * 6.28f; val s = rf(80f, 260f); add(DOT, x, y, cosf(a) * s, sinf(a) * s, 0.25f, 4f, color).drag = 4f }
        add(STAR, x, y, 0f, 0f, 0.15f, 14f, 0xFFFFFFFF.toInt())
    }

    fun number(x: Float, y: Float, v: Float, color: Int, prefix: String = "") {
        val q = add(NUM, x + rf(-10f, 10f), y, rf(-30f, 30f), -230f, 0.85f, if (v > 999f) 26f else 21f, color, 500f)
        q.text = prefix + v.roundToInt().toString()
    }

    fun text(x: Float, y: Float, s: String, color: Int) {
        val q = add(TEXT, x, y, 0f, -60f, 1.3f, 24f, color)
        q.text = s
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, life: Float = 0.15f, w: Float = 6f) {
        val q = add(LINE, x1, y1, 0f, 0f, life, w, color); q.x2 = x2; q.y2 = y2
    }

    fun zap(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        // jagged lightning
        var px = x1; var py = y1
        for (i in 1..5) {
            val k = i / 5f
            val nx = x1 + (x2 - x1) * k + if (i < 5) rf(-12f, 12f) else 0f
            val ny = y1 + (y2 - y1) * k + if (i < 5) rf(-12f, 12f) else 0f
            line(px, py, nx, ny, color, 0.18f, 4f)
            px = nx; py = ny
        }
    }

    fun swoosh(x: Float, y: Float, r: Float, a: Float, half: Float, color: Int) {
        val q = add(ARC, x, y, 0f, 0f, 0.18f, r, color); q.x2 = a; q.y2 = half
    }

    fun pixels(x: Float, y: Float, color: Int) {
        for (i in 0 until 14) { val a = rf() * 6.28f; val s = rf(60f, 260f); add(PIX, x, y, cosf(a) * s, sinf(a) * s, rf(0.3f, 0.6f), rf(5f, 9f), color).drag = 3f }
    }

    fun debris(x: Float, y: Float, color: Int) {
        for (i in 0 until 10) { val a = rf() * 6.28f; val s = rf(120f, 380f); add(DEBRIS, x, y, cosf(a) * s, sinf(a) * s - 200f, rf(0.5f, 0.9f), rf(6f, 11f), color, 900f) }
    }

    fun splat(x: Float, y: Float, color: Int) {
        for (i in 0 until 12) { val a = rf() * 6.28f; val s = rf(100f, 300f); add(DOT, x, y, cosf(a) * s, sinf(a) * s - 150f, rf(0.4f, 0.7f), rf(5f, 10f), color, 800f) }
    }

    fun flameBit(x: Float, y: Float) { add(FLAME, x + rf(-8f, 8f), y, rf(-20f, 20f), rf(-80f, -40f), 0.4f, rf(5f, 8f), 0xFFFF7A3D.toInt()) }
    fun wind(x: Float, y: Float, dir: Float) { add(LINE, x, y, cosf(dir) * 300f, sinf(dir) * 300f, 0.3f, 2f, 0x66FFFFFF).also { it.x2 = x + cosf(dir) * 26f; it.y2 = y + sinf(dir) * 26f } }
    fun drop(x: Float, y: Float, dir: Float) { add(DROP, x, y, cosf(dir) * rf(200f, 400f), sinf(dir) * rf(200f, 400f) - 80f, 0.6f, 5f, 0xFF6BD3FF.toInt(), 300f) }
    fun stars(x: Float, y: Float, n: Int, color: Int) { for (i in 0 until n) { val a = rf() * 6.28f; val s = rf(60f, 200f); add(STAR, x, y, cosf(a) * s, sinf(a) * s, 0.6f, 9f, color).drag = 3f } }

    fun update(dt: Float) {
        var i = 0
        while (i < ps.size) {
            val q = ps[i]
            q.life -= dt
            if (q.life <= 0f) { ps[i] = ps[ps.size - 1]; ps.removeAt(ps.size - 1); pool.add(q); continue }
            if (q.kind == LINE && q.vx != 0f) { q.x2 += q.vx * dt; q.y2 += q.vy * dt }
            q.vy += q.g * dt
            if (q.drag > 0f) { val k = 1f - minOf(1f, q.drag * dt); q.vx *= k; q.vy *= k }
            q.x += q.vx * dt; q.y += q.vy * dt
            q.rot += q.vr * dt
            i++
        }
    }

    fun draw(c: Canvas) {
        for (q in ps) {
            val k = q.life / q.max
            when (q.kind) {
                DOT -> G.circle(c, q.x, q.y, q.size * (0.5f + k * 0.5f), q.color, false)
                CONF -> {
                    c.save(); c.translate(q.x, q.y); c.rotate(q.rot * 57f)
                    val w = q.size * cosf(q.rot * 3f)
                    G.rect(c, -w, -q.size * 0.4f, w, q.size * 0.4f, q.color, false)
                    c.restore()
                }
                SMOKE -> { G.fade = k; G.ow = 2f; G.circle(c, q.x, q.y, q.size * (1.6f - k * 0.6f), q.color, true); G.fade = 1f }
                RING -> G.ring(c, q.x, q.y, q.size * (1.1f - k * 0.8f).coerceAtLeast(0.05f) * 1.1f, 6f * k + 1f, G.alpha(q.color, k))
                NUM -> {
                    val s = q.size * (if (k > 0.8f) 1f + (k - 0.8f) * 3f else 1f)
                    G.fade = minOf(1f, k * 2.5f)
                    G.text(c, q.text, q.x, q.y, s, q.color)
                    G.fade = 1f
                }
                TEXT -> { G.fade = minOf(1f, k * 2f); G.text(c, q.text, q.x, q.y, q.size, q.color); G.fade = 1f }
                LINE -> G.line(c, q.x, q.y, q.x2, q.y2, q.size * k + 1f, G.alpha(q.color, k))
                STAR -> { G.ow = 1.5f; G.star(c, q.x, q.y, q.size * k + 2f, q.size * k * 0.4f + 1f, 5, q.rot, q.color, false) }
                ARC -> {
                    val a0 = Math.toDegrees((q.x2 - q.y2).toDouble()).toFloat()
                    G.arc(c, q.x, q.y, q.size * (1.05f - k * 0.15f), a0, Math.toDegrees(q.y2 * 2.0).toFloat(), 14f * k + 2f, G.alpha(q.color, 0.8f * k))
                }
                PIX -> G.rect(c, q.x - q.size / 2, q.y - q.size / 2, q.x + q.size / 2, q.y + q.size / 2, G.alpha(q.color, k), false)
                DEBRIS -> { c.save(); c.translate(q.x, q.y); c.rotate(q.rot * 57f); G.ow = 2f; G.rect(c, -q.size / 2, -q.size / 2, q.size / 2, q.size / 2, q.color, true); c.restore() }
                FLAME -> G.circle(c, q.x, q.y, q.size * k + 1f, G.mix(0xFFFFE14D.toInt(), 0xFFFF5E3D.toInt(), 1f - k), false)
                DROP -> G.circle(c, q.x, q.y, q.size, G.alpha(q.color, k), false)
            }
        }
    }
}

class Camera {
    var x = 0f; var y = 0f
    var zoom = 1f
    var targetZoom = 1f
    private var shakeAmt = 0f
    var sx = 0f; var sy = 0f
    var m: Match? = null

    fun shake(a: Float) { shakeAmt = minOf(18f, maxOf(shakeAmt, a)) }
    fun shakeAt(px: Float, py: Float, a: Float) {
        val d = com.starguys.battle.core.dist(px, py, x, y)
        if (d < 900f) shake(a * (1f - d / 900f))
    }

    fun update(tx: Float, ty: Float, dt: Float) {
        x += (tx - x) * minOf(1f, dt * 6f)
        y += (ty - y) * minOf(1f, dt * 6f)
        zoom += (targetZoom - zoom) * minOf(1f, dt * 3f)
        if (shakeAmt > 0f) {
            shakeAmt = maxOf(0f, shakeAmt - dt * 30f)
            sx = rf(-shakeAmt, shakeAmt); sy = rf(-shakeAmt, shakeAmt)
        } else { sx = 0f; sy = 0f }
    }

    fun snap(tx: Float, ty: Float) { x = tx; y = ty; zoom = targetZoom }
}
