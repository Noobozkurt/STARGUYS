package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.cosf
import com.starguys.battle.core.frf
import com.starguys.battle.core.sinf
import kotlin.math.roundToInt
import kotlin.math.abs

/** Particles, shockwaves, damage numbers, beams... (all cosmetic, in 3D world space). */
class Fx(val m: Match) {
    class P {
        var x = 0f; var y = 0f; var z = 0f; var vx = 0f; var vy = 0f; var vz = 0f; var g = 0f
        var life = 0f; var max = 1f; var size = 4f; var color = 0; var kind = 0; var rot = 0f; var vr = 0f
        var text = ""; var x2 = 0f; var y2 = 0f; var z2 = 0f; var drag = 0f
    }

    val ps = ArrayList<P>(512)
    private val pool = ArrayList<P>(512)

    private fun p(): P = if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else P()

    /** resolves a height: values below GND/2 are relative to the ground at (x, y) */
    private fun hz(x: Float, y: Float, h: Float): Float = if (h < GND / 2) maxOf(0f, m.map.groundAt(x, y)) + (h - GND) else h

    private fun add(kind: Int, x: Float, y: Float, h: Float, vx: Float, vy: Float, vz: Float, life: Float, size: Float, color: Int, g: Float = 0f): P {
        val q = p()
        q.kind = kind; q.x = x; q.y = y; q.z = hz(x, y, h); q.vx = vx; q.vy = vy; q.vz = vz; q.life = life; q.max = life; q.size = size; q.color = color; q.g = g
        q.rot = frf() * 6.28f; q.vr = frf(-8f, 8f); q.drag = 0f; q.text = ""
        if (ps.size < 1400) ps.add(q) else pool.add(q)
        return q
    }

    companion object {
        const val DOT = 0; const val CONF = 1; const val SMOKE = 2; const val RING = 3; const val NUM = 4; const val TEXT = 5
        const val LINE = 6; const val STAR = 7; const val ARC = 8; const val PIX = 9; const val DEBRIS = 10; const val FLAME = 11; const val DROP = 12
        const val GLOW = 13
        /** ground-relative height marker: Fx.GND + 20f means "20 above the ground there" */
        const val GND = -100000f
        val CONFETTI = intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFB46CFF.toInt(), 0xFFFF9A3D.toInt())
    }

    private inline fun burst(n: Int, s0: Float, s1: Float, up: Float, f: (Float, Float, Float) -> Unit) {
        for (i in 0 until n) {
            val a = frf() * 6.28f; val s = frf(s0, s1); val e = frf(-0.3f, 1f)
            f(cosf(a) * s * (1f - abs(e) * 0.5f), sinf(a) * s * (1f - abs(e) * 0.5f), e * s * up)
        }
    }

    fun confetti(x: Float, y: Float, h: Float, n: Int) {
        for (i in 0 until n) {
            val a = frf() * 6.28f; val s = frf(60f, 260f)
            add(CONF, x, y, h, cosf(a) * s, sinf(a) * s, frf(250f, 560f), frf(1f, 1.8f), frf(5f, 9f), CONFETTI[i % CONFETTI.size], 700f).drag = 1.6f
        }
    }

    fun smoke(x: Float, y: Float, h: Float, n: Int, color: Int) {
        for (i in 0 until n) add(SMOKE, x + frf(-14f, 14f), y + frf(-14f, 14f), h, frf(-30f, 30f), frf(-30f, 30f), frf(20f, 70f), frf(0.5f, 0.9f), frf(9f, 16f), color)
    }

    fun ring(x: Float, y: Float, h: Float, r: Float, color: Int, life: Float = 0.35f) { add(RING, x, y, h, 0f, 0f, 0f, life, r, color) }
    fun flash(x: Float, y: Float, h: Float, r: Float, color: Int, life: Float = 0.3f) { add(GLOW, x, y, h, 0f, 0f, 0f, life, r, color) }

    fun hit(x: Float, y: Float, h: Float, color: Int) {
        burst(5, 80f, 260f, 1f) { vx, vy, vz -> add(DOT, x, y, h, vx, vy, vz, 0.25f, 4f, color).drag = 4f }
        add(STAR, x, y, h, 0f, 0f, 0f, 0.15f, 14f, 0xFFFFFFFF.toInt())
    }

    fun number(x: Float, y: Float, h: Float, v: Float, color: Int, prefix: String = "") {
        val q = add(NUM, x + frf(-10f, 10f), y + frf(-10f, 10f), h, frf(-20f, 20f), frf(-20f, 20f), 230f, 0.85f, if (v > 999f) 26f else 21f, color, 500f)
        q.text = prefix + v.roundToInt().toString()
    }

    fun text(x: Float, y: Float, h: Float, s: String, color: Int) {
        val q = add(TEXT, x, y, h, 0f, 0f, 60f, 1.3f, 24f, color)
        q.text = s
    }

    fun line(x1: Float, y1: Float, h1: Float, x2: Float, y2: Float, h2: Float, color: Int, life: Float = 0.15f, w: Float = 6f) {
        val q = add(LINE, x1, y1, h1, 0f, 0f, 0f, life, w, color); q.x2 = x2; q.y2 = y2; q.z2 = hz(x2, y2, h2)
    }

    fun zap(x1: Float, y1: Float, h1: Float, x2: Float, y2: Float, h2: Float, color: Int) {
        var px = x1; var py = y1; var pz = hz(x1, y1, h1)
        val ez = hz(x2, y2, h2)
        for (i in 1..5) {
            val k = i / 5f
            val nx = x1 + (x2 - x1) * k + if (i < 5) frf(-12f, 12f) else 0f
            val ny = y1 + (y2 - y1) * k + if (i < 5) frf(-12f, 12f) else 0f
            val nz = pz + (ez - pz) / (6 - i) + if (i < 5) frf(-10f, 10f) else 0f
            line(px, py, pz, nx, ny, nz, color, 0.18f, 4f)
            px = nx; py = ny; pz = nz
        }
    }

    fun swoosh(x: Float, y: Float, h: Float, r: Float, a: Float, half: Float, color: Int) {
        val q = add(ARC, x, y, h, 0f, 0f, 0f, 0.18f, r, color); q.x2 = a; q.y2 = half
    }

    fun pixels(x: Float, y: Float, h: Float, color: Int) {
        burst(14, 60f, 260f, 1f) { vx, vy, vz -> add(PIX, x, y, h, vx, vy, vz, frf(0.3f, 0.6f), frf(5f, 9f), color).drag = 3f }
    }

    fun debris(x: Float, y: Float, h: Float, color: Int) {
        for (i in 0 until 10) { val a = frf() * 6.28f; val s = frf(80f, 300f); add(DEBRIS, x, y, h, cosf(a) * s, sinf(a) * s, frf(200f, 420f), frf(0.6f, 1f), frf(6f, 11f), color, 900f) }
    }

    fun splat(x: Float, y: Float, h: Float, color: Int) {
        for (i in 0 until 12) { val a = frf() * 6.28f; val s = frf(80f, 260f); add(DOT, x, y, h, cosf(a) * s, sinf(a) * s, frf(150f, 350f), frf(0.4f, 0.7f), frf(5f, 10f), color, 800f) }
    }

    fun flameBit(x: Float, y: Float, h: Float) { add(FLAME, x + frf(-8f, 8f), y + frf(-8f, 8f), h, frf(-20f, 20f), frf(-20f, 20f), frf(40f, 80f), 0.4f, frf(5f, 8f), 0xFFFF7A3D.toInt()) }
    fun wind(x: Float, y: Float, h: Float, dir: Float) {
        val q = add(LINE, x, y, h + frf(0f, 50f), cosf(dir) * 300f, sinf(dir) * 300f, 0f, 0.3f, 2f, 0x66FFFFFF)
        q.x2 = q.x + cosf(dir) * 26f; q.y2 = q.y + sinf(dir) * 26f; q.z2 = q.z
    }
    fun drop(x: Float, y: Float, h: Float, dir: Float) { add(DROP, x, y, h, cosf(dir) * frf(200f, 400f), sinf(dir) * frf(200f, 400f), frf(40f, 160f), 0.6f, 5f, 0xFF6BD3FF.toInt(), 300f) }
    fun stars(x: Float, y: Float, h: Float, n: Int, color: Int) {
        burst(n, 60f, 200f, 1f) { vx, vy, vz -> add(STAR, x, y, h, vx, vy, vz, 0.6f, 9f, color).drag = 3f }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < ps.size) {
            val q = ps[i]
            q.life -= dt
            if (q.life <= 0f) { ps[i] = ps[ps.size - 1]; ps.removeAt(ps.size - 1); pool.add(q); continue }
            if (q.kind == LINE && (q.vx != 0f || q.vy != 0f)) { q.x2 += q.vx * dt; q.y2 += q.vy * dt }
            q.vz -= q.g * dt
            if (q.drag > 0f) { val k = 1f - minOf(1f, q.drag * dt); q.vx *= k; q.vy *= k; q.vz *= k }
            q.x += q.vx * dt; q.y += q.vy * dt; q.z += q.vz * dt
            if (q.kind == DEBRIS || q.kind == CONF) {
                val g = m.map.groundAt(q.x, q.y)
                if (q.z < g && g > VOID_Z + 1) { q.z = g; q.vz = -q.vz * 0.35f; q.vx *= 0.6f; q.vy *= 0.6f; q.vr *= 0.5f }
            }
            q.rot += q.vr * dt
            i++
        }
    }

    fun draw(c: Canvas) {}
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
            sx = frf(-shakeAmt, shakeAmt); sy = frf(-shakeAmt, shakeAmt)
        } else { sx = 0f; sy = 0f }
    }

    fun snap(tx: Float, ty: Float) { x = tx; y = ty; zoom = targetZoom }
}
