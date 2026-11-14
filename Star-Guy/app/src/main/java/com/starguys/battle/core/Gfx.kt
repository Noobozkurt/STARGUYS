package com.starguys.battle.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Cartoon drawing helpers: every shape gets a dark ink outline (cel-shaded look).
 * All drawing happens on the game thread so the shared paints are safe.
 */
object G {
    const val INK = 0xFF1B1530.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    val txtStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    val rf = RectF()

    /** outline width in current canvas units */
    var ow = 3f
    /** 0..1 white flash tint applied to every color (hit flash) */
    var flash = 0f
    /** 0..1 global alpha multiplier (invisibility, fading) */
    var fade = 1f
    /**
     * When true, filled shapes get volumetric toon shading (light from the top-left, darker core shadow,
     * specular highlight) so characters and props look 3D. Enabled while drawing world entities.
     */
    var depth = false
    /** back view: faces (eyes, mouth...) are not drawn */
    var noFace = false

    // ---- shader caches (unit-space gradients positioned with the canvas matrix: no per-shape allocation)
    private val radCache = HashMap<Int, Shader>()
    private val linCache = HashMap<Long, Shader>()
    private val glowCache = HashMap<Int, Shader>()
    private var shadowShader: Shader? = null

    private fun radial(c: Int): Shader = radCache.getOrPut(c) {
        if (radCache.size > 700) radCache.clear()
        RadialGradient(-0.4f, -0.48f, 1.6f, intArrayOf(light(c, 0.58f), c, shade(c, 0.5f)), floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
    }

    private fun linear(c: Int, h: Float): Shader {
        val hb = ((h / 3f).toInt() + 1) * 3
        val key = (c.toLong() shl 16) or hb.toLong()
        return linCache.getOrPut(key) {
            if (linCache.size > 900) linCache.clear()
            LinearGradient(0f, 0f, 0f, hb.toFloat(), intArrayOf(light(c, 0.4f), c, shade(c, 0.6f)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
    }

    private fun glowShader(c: Int): Shader = glowCache.getOrPut(c) {
        RadialGradient(0f, 0f, 1f, intArrayOf(alpha(c, 0.75f), alpha(c, 0.28f), alpha(c, 0f)), floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP)
    }

    private fun opaque(c: Int) = (c ushr 24) == 0xFF

    /** Soft additive-looking glow (projectiles, explosions, lights). */
    fun glow(cv: Canvas, x: Float, y: Float, r: Float, c: Int) {
        if (r <= 0f) return
        cv.save(); cv.translate(x, y); cv.scale(r, r)
        fill.shader = glowShader(col(c)); fill.color = 0xFFFFFFFF.toInt()
        cv.drawCircle(0f, 0f, 1f, fill)
        fill.shader = null
        cv.restore()
    }

    /** Soft blurred contact shadow. */
    fun softShadow(cv: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float = 1f) {
        if (rx <= 0f || ry <= 0f) return
        val sh = shadowShader ?: RadialGradient(0f, 0f, 1f, intArrayOf(0x66000000, 0x33000000, 0x00000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP).also { shadowShader = it }
        cv.save(); cv.translate(x, y); cv.scale(rx, ry)
        fill.shader = sh; fill.color = Color.argb((255 * clamp(a * fade, 0f, 1f)).toInt(), 255, 255, 255)
        cv.drawCircle(0f, 0f, 1f, fill)
        fill.shader = null
        cv.restore()
    }

    fun col(c: Int): Int {
        var r = Color.red(c); var g = Color.green(c); var b = Color.blue(c); var a = Color.alpha(c)
        if (flash > 0f) {
            r = (r + (255 - r) * flash).toInt(); g = (g + (255 - g) * flash).toInt(); b = (b + (255 - b) * flash).toInt()
        }
        if (fade < 1f) a = (a * fade).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun prepFill(c: Int) { fill.shader = null; fill.color = col(c) }
    private fun prepStroke(w: Float, c: Int = INK) { stroke.strokeWidth = w; stroke.color = col(c); stroke.shader = null }

    fun circle(cv: Canvas, x: Float, y: Float, r: Float, c: Int, outline: Boolean = true) {
        val cc = col(c)
        if (depth && r >= 4.5f && opaque(cc)) {
            cv.save(); cv.translate(x, y); cv.scale(r, r)
            fill.shader = radial(cc); fill.color = 0xFFFFFFFF.toInt()
            cv.drawCircle(0f, 0f, 1f, fill)
            fill.shader = null
            cv.restore()
            if (r >= 9f) { fill.color = alpha(0xFFFFFFFF.toInt(), 0.55f * fade); rf.set(x - r * 0.62f, y - r * 0.7f, x - r * 0.2f, y - r * 0.42f); cv.drawOval(rf, fill) }
        } else { fill.shader = null; fill.color = cc; cv.drawCircle(x, y, r, fill) }
        if (outline) { prepStroke(ow); cv.drawCircle(x, y, r, stroke) }
    }

    fun ring(cv: Canvas, x: Float, y: Float, r: Float, w: Float, c: Int) {
        prepStroke(w, c); cv.drawCircle(x, y, r, stroke)
    }

    fun oval(cv: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, c: Int, outline: Boolean = true) {
        val cc = col(c)
        if (depth && rx >= 4.5f && ry >= 4.5f && opaque(cc)) {
            cv.save(); cv.translate(cx, cy); cv.scale(rx, ry)
            fill.shader = radial(cc); fill.color = 0xFFFFFFFF.toInt()
            cv.drawCircle(0f, 0f, 1f, fill)
            fill.shader = null
            cv.restore()
            if (rx >= 9f && ry >= 9f) { fill.color = alpha(0xFFFFFFFF.toInt(), 0.5f * fade); rf.set(cx - rx * 0.6f, cy - ry * 0.7f, cx - rx * 0.18f, cy - ry * 0.4f); cv.drawOval(rf, fill) }
        } else { fill.shader = null; fill.color = cc; rf.set(cx - rx, cy - ry, cx + rx, cy + ry); cv.drawOval(rf, fill) }
        if (outline) { rf.set(cx - rx, cy - ry, cx + rx, cy + ry); prepStroke(ow); cv.drawOval(rf, stroke) }
    }

    fun rect(cv: Canvas, l: Float, t: Float, r: Float, b: Float, c: Int, outline: Boolean = true) {
        val cc = col(c)
        if (depth && b - t >= 6f && opaque(cc)) {
            cv.save(); cv.translate(0f, t)
            fill.shader = linear(cc, b - t); fill.color = 0xFFFFFFFF.toInt()
            cv.drawRect(l, 0f, r, b - t, fill)
            fill.shader = null
            cv.restore()
        } else { fill.shader = null; fill.color = cc; cv.drawRect(l, t, r, b, fill) }
        if (outline) { prepStroke(ow); cv.drawRect(l, t, r, b, stroke) }
    }

    fun rrect(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, c: Int, outline: Boolean = true) {
        val cc = col(c)
        if (depth && b - t >= 6f && opaque(cc)) {
            cv.save(); cv.translate(0f, t)
            rf.set(l, 0f, r, b - t)
            fill.shader = linear(cc, b - t); fill.color = 0xFFFFFFFF.toInt()
            cv.drawRoundRect(rf, rad, rad, fill)
            fill.shader = null
            cv.restore()
        } else { fill.shader = null; fill.color = cc; rf.set(l, t, r, b); cv.drawRoundRect(rf, rad, rad, fill) }
        if (outline) { rf.set(l, t, r, b); prepStroke(ow); cv.drawRoundRect(rf, rad, rad, stroke) }
    }

    /** Vertical gradient fill (UI), top -> bottom. */
    fun vgrad(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, top: Int, bottom: Int) {
        fill.shader = LinearGradient(0f, t, 0f, b, col(top), col(bottom), Shader.TileMode.CLAMP); fill.color = 0xFFFFFFFF.toInt()
        rf.set(l, t, r, b); cv.drawRoundRect(rf, rad, rad, fill)
        fill.shader = null
    }

    /** Glossy Tailwind-style button body (from-light to-dark gradient, inner highlight, ring). */
    fun button(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, c: Int, lip: Float, pressed: Boolean, u: Float) {
        val off = if (pressed) lip * 0.6f else 0f
        rrect(cv, l, t + lip, r, b + lip, rad, shade(c, 0.5f), false)
        vgrad(cv, l, t + off, r, b + off, rad, light(c, 0.28f), shade(c, 0.86f))
        fill.color = col(alpha(WHITE, 0.28f)); rf.set(l + 6 * u, t + off + 4 * u, r - 6 * u, t + off + (b - t) * 0.42f); cv.drawRoundRect(rf, rad * 0.7f, rad * 0.7f, fill)
        prepStroke(2.2f * u, alpha(WHITE, 0.35f)); rf.set(l + 1.5f * u, t + off + 1.5f * u, r - 1.5f * u, b + off - 1.5f * u); cv.drawRoundRect(rf, rad, rad, stroke)
        prepStroke(ow); rf.set(l, t + off, r, b + off); cv.drawRoundRect(rf, rad, rad, stroke)
    }

    /** Frosted-glass panel (bg-slate-900/70 ring-1 ring-white/10). */
    fun glass(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, u: Float, tint: Int = 0xFF0F172A.toInt()) {
        fill.shader = null
        fill.color = col(alpha(tint, 0.72f)); rf.set(l, t, r, b); cv.drawRoundRect(rf, rad, rad, fill)
        vgrad(cv, l, t, r, t + (b - t) * 0.35f, rad, alpha(WHITE, 0.10f), alpha(WHITE, 0f))
        prepStroke(2f * u, alpha(WHITE, 0.16f)); rf.set(l, t, r, b); cv.drawRoundRect(rf, rad, rad, stroke)
    }

    fun line(cv: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Int) {
        prepStroke(w, c); cv.drawLine(x1, y1, x2, y2, stroke)
    }

    /** Polygon from flat x,y pairs. */
    fun poly(cv: Canvas, c: Int, outline: Boolean, vararg p: Float) {
        path.reset()
        path.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size - 1) { path.lineTo(p[i], p[i + 1]); i += 2 }
        path.close()
        drawPath(cv, c, outline)
    }

    fun drawPath(cv: Canvas, c: Int, outline: Boolean = true) {
        val cc = col(c)
        if (depth && opaque(cc)) {
            path.computeBounds(rf, false)
            val top = rf.top; val h = rf.height()
            if (h >= 6f) {
                cv.save(); cv.translate(0f, top); path.offset(0f, -top)
                fill.shader = linear(cc, h); fill.color = 0xFFFFFFFF.toInt()
                cv.drawPath(path, fill)
                fill.shader = null
                path.offset(0f, top); cv.restore()
            } else { fill.shader = null; fill.color = cc; cv.drawPath(path, fill) }
        } else { fill.shader = null; fill.color = cc; cv.drawPath(path, fill) }
        if (outline) { prepStroke(ow); cv.drawPath(path, stroke) }
    }

    fun strokePath(cv: Canvas, w: Float, c: Int) { prepStroke(w, c); cv.drawPath(path, stroke) }

    fun arc(cv: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, w: Float, c: Int) {
        rf.set(cx - r, cy - r, cx + r, cy + r)
        prepStroke(w, c); cv.drawArc(rf, startDeg, sweepDeg, false, stroke)
    }

    fun pie(cv: Canvas, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, c: Int) {
        rf.set(cx - r, cy - r, cx + r, cy + r)
        prepFill(c); cv.drawArc(rf, startDeg, sweepDeg, true, fill)
    }

    fun star(cv: Canvas, x: Float, y: Float, r1: Float, r2: Float, n: Int, rot: Float, c: Int, outline: Boolean = true) {
        path.reset()
        for (i in 0 until n * 2) {
            val a = rot + i * PIF / n - PIF / 2
            val r = if (i % 2 == 0) r1 else r2
            val px = x + cosf(a) * r; val py = y + sinf(a) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(cv, c, outline)
    }

    /** Big cartoon text with an ink outline. */
    fun text(cv: Canvas, s: String, x: Float, y: Float, size: Float, c: Int,
             align: Paint.Align = Paint.Align.CENTER, outline: Boolean = true) {
        txt.textSize = size; txt.textAlign = align; txt.color = col(c)
        if (outline) {
            txtStroke.textSize = size; txtStroke.textAlign = align
            txtStroke.strokeWidth = size * 0.16f; txtStroke.color = col(INK)
            cv.drawText(s, x, y, txtStroke)
        }
        cv.drawText(s, x, y, txt)
    }

    fun textW(s: String, size: Float): Float { txt.textSize = size; return txt.measureText(s) }

    /** Wraps text into lines that fit maxW. */
    fun wrap(s: String, size: Float, maxW: Float): List<String> {
        val out = ArrayList<String>()
        for (para in s.split("\n")) {
            var line = ""
            for (w in para.split(" ")) {
                val t = if (line.isEmpty()) w else "$line $w"
                if (textW(t, size) > maxW && line.isNotEmpty()) { out.add(line); line = w } else line = t
            }
            out.add(line)
        }
        return out
    }

    fun shade(c: Int, f: Float): Int {
        val r = clampi((Color.red(c) * f).toInt(), 0, 255)
        val g = clampi((Color.green(c) * f).toInt(), 0, 255)
        val b = clampi((Color.blue(c) * f).toInt(), 0, 255)
        return Color.argb(Color.alpha(c), r, g, b)
    }

    fun light(c: Int, t: Float): Int = mix(c, WHITE, t)

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = clamp(t, 0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt()
        )
    }

    fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((clamp(a, 0f, 1f) * 255).toInt() shl 24)

    fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v))

    /** Two big cartoon eyes looking toward (lx, ly) normalized. */
    fun eyes(cv: Canvas, x: Float, y: Float, sep: Float, r: Float, lx: Float, ly: Float, blink: Boolean = false,
             angry: Boolean = false) {
        if (noFace) return
        for (s in intArrayOf(-1, 1)) {
            val ex = x + s * sep
            if (blink) {
                line(cv, ex - r, y, ex + r, y, ow, INK)
            } else {
                oval(cv, ex, y, r, r * 1.15f, WHITE)
                circle(cv, ex + lx * r * 0.4f, y + ly * r * 0.35f, r * 0.5f, INK, false)
                circle(cv, ex + lx * r * 0.4f - r * 0.18f, y + ly * r * 0.35f - r * 0.2f, r * 0.16f, WHITE, false)
            }
            if (angry) line(cv, ex - r * 1.1f, y - r * 1.5f - s * r * 0.35f, ex + r * 1.1f, y - r * 1.5f + s * r * 0.35f, ow * 1.2f, INK)
        }
    }

    fun smile(cv: Canvas, x: Float, y: Float, w: Float, sad: Boolean = false) {
        if (noFace) return
        rf.set(x - w, y - w * 0.6f, x + w, y + w * 0.6f)
        prepStroke(ow)
        if (sad) { rf.offset(0f, w * 0.7f); cv.drawArc(rf, 200f, 140f, false, stroke) }
        else cv.drawArc(rf, 20f, 140f, false, stroke)
    }
}
