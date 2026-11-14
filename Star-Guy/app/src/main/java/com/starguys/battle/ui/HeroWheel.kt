package com.starguys.battle.ui

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Save
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.TAU
import com.starguys.battle.core.TW
import com.starguys.battle.core.TouchEvt
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.sinf
import com.starguys.battle.game.Heroes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A 3D carousel wheel of the 12 heroes: drag or fling to spin it, it snaps on a hero,
 * tap a hero to bring it to the front.
 */
class HeroWheel(start: Int, val onChange: (Int) -> Unit) {
    private val n = Heroes.list.size
    private var pos = start.toFloat()
    private var vel = 0f
    private var target: Float? = null
    private var cx = 0f; private var cy = 0f; private var rx = 1f; private var ry = 1f; private var itemR = 40f
    private var pid = -1
    private var lastX = 0f; private var downX = 0f; private var downY = 0f
    private var lastT = 0L
    private var dragged = false
    private var lastSel = start
    private val order = IntArray(n)
    private val depth = FloatArray(n)

    val selected: Int get() = (((pos.roundToInt() % n) + n) % n)
    val moving: Boolean get() = pid >= 0 || abs(vel) > 0.05f || target != null

    fun layout(cx: Float, cy: Float, rx: Float, ry: Float, itemR: Float) { this.cx = cx; this.cy = cy; this.rx = rx; this.ry = ry; this.itemR = itemR }

    fun step(d: Int) { target = (target ?: pos.roundToInt().toFloat()) + d; vel = 0f; Sfx.play(Sfx.CLICK) }

    private fun itemPos(i: Int): FloatArray {
        var d = i - pos
        d -= (d / n).roundToInt() * n.toFloat()
        val a = d * TAU / n
        val dep = cosf(a)
        return floatArrayOf(cx + sinf(a) * rx, cy + dep * ry, dep, a)
    }

    fun touch(e: TouchEvt): Boolean {
        when (e.action) {
            TouchEvt.DOWN -> {
                if (abs(e.y - cy) > ry + itemR * 2.2f || abs(e.x - cx) > rx + itemR * 2f) return false
                pid = e.id; lastX = e.x; downX = e.x; downY = e.y; lastT = System.nanoTime(); dragged = false; vel = 0f; target = null
                return true
            }
            TouchEvt.MOVE -> if (e.id == pid) {
                val dx = e.x - lastX
                if (abs(e.x - downX) > 12f) dragged = true
                val perItem = rx * TAU / n
                pos -= dx / perItem
                val now = System.nanoTime()
                val dt = ((now - lastT) / 1e9f).coerceAtLeast(0.001f)
                vel = (-dx / perItem / dt) * 0.6f + vel * 0.4f
                lastX = e.x; lastT = now
                return true
            }
            TouchEvt.UP -> if (e.id == pid) {
                pid = -1
                if (!dragged) {
                    // tap: bring the touched hero to the front
                    var best = -1; var bd = Float.MAX_VALUE
                    for (i in 0 until n) {
                        val p = itemPos(i)
                        val s = 0.5f + 0.5f * (p[2] + 1f) / 2f
                        val d = abs(e.x - p[0]) + abs(e.y - (p[1] - itemR * s))
                        if (d < itemR * 1.6f * s && d < bd) { bd = d; best = i }
                    }
                    if (best >= 0) {
                        var d = best - pos
                        d -= (d / n).roundToInt() * n.toFloat()
                        target = pos + d; vel = 0f
                    }
                }
                return true
            }
        }
        return false
    }

    fun update(dt: Float) {
        if (pid < 0) {
            val tg = target
            if (tg != null) {
                pos += (tg - pos) * clamp(dt * 9f, 0f, 1f)
                if (abs(tg - pos) < 0.002f) { pos = tg; target = null }
            } else if (abs(vel) > 0.05f) {
                pos += vel * dt
                vel *= (1f - clamp(dt * 3.2f, 0f, 1f))
                if (abs(vel) < 1.2f) { target = pos.roundToInt().toFloat(); vel = 0f }
            } else if (abs(pos - pos.roundToInt()) > 0.001f) target = pos.roundToInt().toFloat()
        }
        val s = selected
        if (s != lastSel) { lastSel = s; Sfx.play(Sfx.CLICK, 0.5f, 1.3f); onChange(s) }
    }

    fun draw(c: Canvas, t: Float, u: Float) {
        // the wheel's rim
        G.ow = 3f * u
        G.glow(c, cx, cy + ry * 0.2f, rx * 1.15f, G.alpha(TW.FUCHSIA_400, 0.35f))
        G.oval(c, cx, cy + itemR * 0.4f, rx + itemR * 1.4f, ry + itemR * 0.7f, G.alpha(TW.INDIGO_950, 0.55f), false)
        G.stroke.shader = null
        G.rf.set(cx - rx - itemR, cy - ry - itemR * 0.3f, cx + rx + itemR, cy + ry + itemR * 0.3f)
        G.stroke.strokeWidth = 5f * u; G.stroke.color = G.alpha(TW.AMBER_300, 0.6f); c.drawOval(G.rf, G.stroke)
        // depth sort (back first)
        for (i in 0 until n) { order[i] = i; depth[i] = itemPos(i)[2] }
        val sorted = order.sortedBy { depth[it] }
        for (i in sorted) {
            val p = itemPos(i)
            val k = (p[2] + 1f) / 2f
            val s = 0.42f + 0.58f * k
            val x = p[0]; val y = p[1]
            val hero = Heroes.list[i]
            val col = hero.colors[Save.skin(i).coerceIn(0, hero.colors.size - 1)]
            val front = i == selected
            G.fade = 0.35f + 0.65f * k
            pedestal(c, x, y, itemR * 1.15f * s, if (front) TW.AMBER_400 else TW.INDIGO_400, t + i)
            hero3d(c, i, col, x, y, s * itemR / 22f, t + i * 0.7f, moving = front && (t % 3f) > 2f, lx = sinf(p[3]) * -0.8f)
            G.fade = 1f
            if (front) {
                G.text(c, hero.name, x, y + itemR * 0.9f, 30f * u, TW.WHITE)
                G.text(c, hero.cls, x, y + itemR * 0.9f + 26f * u, 18f * u, TW.AMBER_200)
            }
        }
    }
}
