package com.starguys.battle.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
        prepFill(c); cv.drawCircle(x, y, r, fill)
        if (outline) { prepStroke(ow); cv.drawCircle(x, y, r, stroke) }
    }

    fun ring(cv: Canvas, x: Float, y: Float, r: Float, w: Float, c: Int) {
        prepStroke(w, c); cv.drawCircle(x, y, r, stroke)
    }

    fun oval(cv: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, c: Int, outline: Boolean = true) {
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry)
        prepFill(c); cv.drawOval(rf, fill)
        if (outline) { prepStroke(ow); cv.drawOval(rf, stroke) }
    }

    fun rect(cv: Canvas, l: Float, t: Float, r: Float, b: Float, c: Int, outline: Boolean = true) {
        prepFill(c); cv.drawRect(l, t, r, b, fill)
        if (outline) { prepStroke(ow); cv.drawRect(l, t, r, b, stroke) }
    }

    fun rrect(cv: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, c: Int, outline: Boolean = true) {
        rf.set(l, t, r, b)
        prepFill(c); cv.drawRoundRect(rf, rad, rad, fill)
        if (outline) { prepStroke(ow); cv.drawRoundRect(rf, rad, rad, stroke) }
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
        prepFill(c); cv.drawPath(path, fill)
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
        rf.set(x - w, y - w * 0.6f, x + w, y + w * 0.6f)
        prepStroke(ow)
        if (sad) { rf.offset(0f, w * 0.7f); cv.drawArc(rf, 200f, 140f, false, stroke) }
        else cv.drawArc(rf, 20f, 140f, false, stroke)
    }
}
