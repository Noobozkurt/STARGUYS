package com.starguys.battle.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.starguys.battle.core.G
import com.starguys.battle.core.Save
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.TouchEvt
import com.starguys.battle.core.ang
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.len
import com.starguys.battle.core.sinf
import com.starguys.battle.core.atan2

/** On-screen controls (twin sticks + buttons) and all HUD drawing. */
class Hud(val m: Match) {
    private val app = m.app
    private val u get() = app.u
    private val W get() = app.w
    private val H get() = app.h
    private val sizeMul get() = when (Save.stickSize) { 0 -> 0.85f; 2 -> 1.18f; else -> 1f }

    // move stick
    private var mPid = -1
    private var mOx = 0f; private var mOy = 0f; private var mKx = 0f; private var mKy = 0f
    // attack stick
    private var aPid = -1
    private var aDx = 0f; private var aDy = 0f; private var aT = 0f
    // super stick
    private var sPid = -1
    private var sDx = 0f; private var sDy = 0f; private var sT = 0f
    private var btnPid = HashMap<Int, Int>()
    private var pressed = -1

    var zoomMul = 1f
    private class Feed(val s: String, val c: Int, var t: Float)
    private val feed = ArrayList<Feed>()
    private var small = ""; private var smallT = 0f
    private var hitT = 0f
    private var blindT = 0f
    private var minimap: Bitmap? = null
    private var minimapVersion = -1
    private var minimapT = 0f
    private val mmSrc = Rect(); private val mmDst = RectF()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var useLabel: String? = null
    private var labelT = 0f
    private var bigMsg = ""; private var bigT = 0f; private var bigCol = 0

    // button ids
    private companion object { const val B_GADGET = 1; const val B_ITEM = 2; const val B_JUMP = 3; const val B_USE = 4; const val B_PING = 5; const val B_PAUSE = 6
        const val B_RESUME = 7; const val B_QUIT = 8; const val B_PREV = 9; const val B_NEXT = 10 }

    private fun atkX() = W - 175f * u; private fun atkY() = H - 165f * u; private fun atkR() = 80f * u * sizeMul
    private fun supX() = W - 330f * u; private fun supY() = H - 92f * u; private fun supR() = 50f * u * sizeMul
    private fun btnPos(id: Int): FloatArray = when (id) {
        B_GADGET -> floatArrayOf(W - 318f * u, H - 238f * u, 38f * u * sizeMul)
        B_ITEM -> floatArrayOf(W - 196f * u, H - 322f * u, 36f * u * sizeMul)
        B_JUMP -> floatArrayOf(W - 70f * u, H - 300f * u, 48f * u * sizeMul)
        B_USE -> floatArrayOf(W - 470f * u, H - 140f * u, 46f * u * sizeMul)
        B_PING -> floatArrayOf(W - 222f * u, 196f * u, 26f * u)
        B_PAUSE -> floatArrayOf(40f * u, 38f * u, 26f * u)
        B_RESUME -> floatArrayOf(W / 2, H / 2 - 40f * u, 0f)
        B_QUIT -> floatArrayOf(W / 2, H / 2 + 70f * u, 0f)
        B_PREV -> floatArrayOf(W / 2 - 200f * u, H - 70f * u, 36f * u)
        B_NEXT -> floatArrayOf(W / 2 + 200f * u, H - 70f * u, 36f * u)
        else -> floatArrayOf(-999f, -999f, 0f)
    }

    fun feed(s: String, c: Int) { feed.add(0, Feed(s, c, 5f)); if (feed.size > 5) feed.removeAt(feed.size - 1) }
    fun toastSmall(s: String) { small = s; smallT = 1.6f }
    fun hitMarker() { hitT = 0.15f }
    fun blind(t: Float) { blindT = maxOf(blindT, t) }
    fun big(s: String, c: Int = 0xFFFFE14D.toInt(), t: Float = 2f) { bigMsg = s; bigT = t; bigCol = c }

    private val p get() = m.player

    // ------------------------------------------------------------------ input
    /** Local player's input; consumed by the match (single player) or sent over the network (multiplayer). */
    val input = PlayerInput()
    fun takeInput(): PlayerInput = input.snapshot()
    /** Ping mode: tap the "!" button, then tap the spot (world or minimap) to signal. */
    var pingMode = false
    private var pingHintT = 0f
    /** multiplayer pause menu (the match keeps running) */
    var menuOpen = false
    var quitCb: (() -> Unit)? = null
    private var mmX = 0f; private var mmY = 0f; private var mmW = 0f; private var mmH = 0f; private var mmS = 1f

    private fun screenToWorld(sx: Float, sy: Float): FloatArray {
        val z = m.cam.zoom
        return floatArrayOf((sx - W / 2 - m.cam.sx) / z + m.cam.x, (sy - H / 2 - m.cam.sy) / z + m.cam.y)
    }

    fun touch(e: TouchEvt) {
        val pl = p
        if (m.paused || menuOpen) {
            if (e.action == TouchEvt.UP) {
                if (hitRect(B_RESUME, e.x, e.y)) { m.paused = false; menuOpen = false; Sfx.play(Sfx.CLICK) }
                else if (hitRect(B_QUIT, e.x, e.y)) {
                    Sfx.play(Sfx.CLICK); m.paused = false; menuOpen = false
                    val q = quitCb
                    if (q != null) q() else { m.finish(false, "Abandon"); m.mode.endT = 0.01f }
                }
            }
            return
        }
        if (m.over) return
        when (e.action) {
            TouchEvt.DOWN -> {
                // buttons first
                for (id in intArrayOf(B_PAUSE, B_PING, B_JUMP, B_GADGET, B_ITEM, B_USE, B_PREV, B_NEXT)) {
                    if (!btnVisible(id)) continue
                    val b = btnPos(id)
                    if (dist(e.x, e.y, b[0], b[1]) < b[2] * 1.25f) { btnPid[e.id] = id; pressed = id; onButton(id); return }
                }
                if (pingMode && pl != null && !pl.out) {
                    // second tap: where to signal (minimap or world)
                    val wpt = if (e.x in mmX..(mmX + mmW) && e.y in mmY..(mmY + mmH)) floatArrayOf((e.x - mmX) / mmS, (e.y - mmY) / mmS)
                    else screenToWorld(e.x, e.y)
                    input.add(PlayerInput.PING, clamp(wpt[0], 0f, m.map.pxW), clamp(wpt[1], 0f, m.map.pxH))
                    pingMode = false
                    btnPid[e.id] = -1
                    return
                }
                if (pl != null && !pl.out) {
                    if (dist(e.x, e.y, supX(), supY()) < supR() * 1.3f && sPid < 0) { sPid = e.id; sDx = 0f; sDy = 0f; sT = 0f; return }
                    if (dist(e.x, e.y, atkX(), atkY()) < atkR() * 1.45f && aPid < 0) { aPid = e.id; aDx = 0f; aDy = 0f; aT = 0f; return }
                    if (e.x > W * 0.55f && aPid < 0 && e.y > H * 0.35f) { aPid = e.id; aDx = 0f; aDy = 0f; aT = 0f; return }
                }
                if (e.x < W * 0.5f && mPid < 0) { mPid = e.id; mOx = e.x; mOy = e.y; mKx = e.x; mKy = e.y }
            }
            TouchEvt.MOVE -> {
                when (e.id) {
                    mPid -> { mKx = e.x; mKy = e.y }
                    aPid -> { aDx = e.x - atkX(); aDy = e.y - atkY(); clampStick(true) }
                    sPid -> { sDx = e.x - supX(); sDy = e.y - supY(); clampStick(false) }
                }
            }
            TouchEvt.UP -> {
                if (btnPid.remove(e.id) != null) { pressed = -1; return }
                when (e.id) {
                    mPid -> { mPid = -1; input.mx = 0f; input.my = 0f }
                    aPid -> { aPid = -1; releaseAttack(e.x > -9000f) }
                    sPid -> { sPid = -1; releaseSuper(e.x > -9000f) }
                }
            }
        }
    }

    private fun clampStick(atk: Boolean) {
        val r = if (atk) atkR() else supR() * 1.6f
        if (atk) { val l = len(aDx, aDy); if (l > r) { aDx = aDx / l * r; aDy = aDy / l * r } }
        else { val l = len(sDx, sDy); if (l > r) { sDx = sDx / l * r; sDy = sDy / l * r } }
    }

    private fun hitRect(id: Int, x: Float, y: Float): Boolean {
        val b = btnPos(id)
        return x > b[0] - 160f * u && x < b[0] + 160f * u && y > b[1] - 40f * u && y < b[1] + 40f * u
    }

    private fun btnVisible(id: Int): Boolean {
        val pl = p ?: return id == B_PAUSE
        if (pl.out) return id == B_PAUSE || ((id == B_PREV || id == B_NEXT) && !m.mode.respawns)
        return when (id) {
            B_GADGET -> pl.gadget != null
            B_ITEM -> pl.items.isNotEmpty()
            B_USE -> useLabel != null
            B_PREV, B_NEXT -> false
            else -> true
        }
    }

    private fun onButton(id: Int) {
        val pl = p
        when (id) {
            B_PAUSE -> { if (m.cfg.net) menuOpen = true else m.paused = true; Sfx.play(Sfx.CLICK) }
            B_PING -> { pingMode = !pingMode; pingHintT = 0f; Sfx.play(Sfx.CLICK) }
            B_JUMP -> input.add(PlayerInput.JUMP)
            B_GADGET -> if (pl != null) input.add(PlayerInput.GADGET, if (aPid >= 0 && len(aDx, aDy) > 10f) atan2(aDy, aDx) else pl.face)
            B_ITEM -> input.add(PlayerInput.ITEM)
            B_USE -> input.add(PlayerInput.INTERACT)
            B_PREV, B_NEXT -> cycleSpectate(if (id == B_NEXT) 1 else -1)
        }
    }

    private fun cycleSpectate(d: Int) {
        val alive = m.fighters.filter { !it.out }
        if (alive.isEmpty()) return
        val i = alive.indexOf(m.spectate)
        m.spectate = alive[((i + d) % alive.size + alive.size) % alive.size]
    }

    private fun aimMag(): Float = len(aDx, aDy) / atkR()
    private fun superMag(): Float = len(sDx, sDy) / (supR() * 1.6f)

    private fun releaseAttack(valid: Boolean) {
        val pl = p ?: return
        if (!valid || pl.out) return
        val w = pl.currentWeapon()
        val mag = aimMag()
        if (mag < 0.22f) {
            if (aT < 0.4f) autoFire(pl, w.range, w.aim, false)
        } else if (!w.auto) {
            val a = atan2(aDy, aDx)
            input.add(PlayerInput.ATTACK, a, mag * w.range * T * pl.rangeMul())
        }
    }

    private fun releaseSuper(valid: Boolean) {
        val pl = p ?: return
        if (!valid || pl.out || !pl.ready) return
        val h = pl.hero
        val mag = superMag()
        if (mag < 0.22f) autoFire(pl, h.superRange, h.superAim, true)
        else input.add(PlayerInput.SUPER, atan2(sDy, sDx), mag * h.superRange * T)
    }

    private fun autoFire(pl: Fighter, rangeTiles: Float, aim: Aim, sup: Boolean) {
        val tg = autoTarget(pl, rangeTiles * T * pl.rangeMul() * 1.05f)
        var a = pl.face; var d = rangeTiles * T * 0.6f
        if (tg != null) {
            d = dist(pl.x, pl.y, tg.x, tg.y)
            val lead = if (aim == Aim.LOB) 0.6f else d / 1000f
            val tx = tg.x + tg.vx * lead * 0.7f; val ty = tg.y + tg.vy * lead * 0.7f
            a = ang(pl.x, pl.y, tx, ty); d = dist(pl.x, pl.y, tx, ty)
        }
        if (sup) { if (aim == Aim.SELF) input.add(PlayerInput.SUPER, pl.face, 0f) else input.add(PlayerInput.SUPER, a, d) }
        else input.add(PlayerInput.ATTACK, a, d)
    }

    fun autoTarget(pl: Fighter, range: Float): Body? {
        var best: Body? = null; var bd = range
        for (b in m.hittables) {
            if (b === pl || !m.isEnemy(pl.team, b) || b is Breakable || b is Ball) continue
            if (b is Fighter && !m.canSee(pl, b)) continue
            if (b is Vehicle && b.driver == null) continue
            val d = dist(pl.x, pl.y, b.x, b.y)
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    fun update(dt: Float) {
        for (f in feed) f.t -= dt
        feed.removeAll { it.t <= 0f }
        if (smallT > 0) smallT -= dt
        if (hitT > 0) hitT -= dt
        if (blindT > 0) blindT -= dt
        if (bigT > 0) bigT -= dt
        pingHintT += dt
        aT += dt; sT += dt
        val pl = p
        if (pl == null || pl.out) { input.mx = 0f; input.my = 0f; input.aiming = false; input.hold = false; pingMode = false; return }
        if (mPid >= 0) {
            var dx = mKx - mOx; var dy = mKy - mOy
            val r = 70f * u * sizeMul
            val l = len(dx, dy)
            if (l > r) { mOx = mKx - dx / l * r; mOy = mKy - dy / l * r; dx = mKx - mOx; dy = mKy - mOy }
            val mag = clamp(len(dx, dy) / r, 0f, 1f)
            if (mag < 0.12f) { input.mx = 0f; input.my = 0f } else {
                val a = atan2(dy, dx)
                input.mx = cosf(a) * mag.coerceAtLeast(0.55f); input.my = sinf(a) * mag.coerceAtLeast(0.55f)
            }
        } else { input.mx = 0f; input.my = 0f }
        // aiming turns the hero; automatic weapons fire while held
        input.aiming = false; input.hold = false
        val w = pl.currentWeapon()
        if (aPid >= 0 && aimMag() > 0.22f) {
            input.aiming = true; input.aim = atan2(aDy, aDx)
            if (w.auto) { input.hold = true; input.holdDist = aimMag() * w.range * T }
        } else if (aPid >= 0 && aT > 0.4f && w.auto) {
            val tg = autoTarget(pl, w.range * T)
            if (tg != null) { input.aiming = true; input.hold = true; input.aim = ang(pl.x, pl.y, tg.x, tg.y); input.holdDist = dist(pl.x, pl.y, tg.x, tg.y) }
        }
        if (sPid >= 0 && superMag() > 0.22f) { input.aiming = true; input.aim = atan2(sDy, sDx) }
        labelT -= dt
        if (labelT <= 0f) { useLabel = m.interactLabel(pl); labelT = 0.15f }
    }

    // ------------------------------------------------------------------ world-space overlays (aim indicators)
    fun drawWorld(c: Canvas) {
        val pl = p ?: return
        if (pl.out) return
        val px = pl.vehicle?.x ?: pl.x; val py = (pl.vehicle?.y ?: pl.y) - pl.z * ZS * 0.2f
        if (aPid >= 0 && aimMag() > 0.22f) {
            val w = pl.currentWeapon()
            drawAim(c, px, py, atan2(aDy, aDx), aimMag(), w.aim, w.range * T * pl.rangeMul(), w.width, w.spread, 0xFFFFFFFF.toInt(), if (w.aim == Aim.LOB) 1.1f * T else 0f)
        }
        if (sPid >= 0 && superMag() > 0.22f && pl.ready) {
            val h = pl.hero
            drawAim(c, px, py, atan2(sDy, sDx), superMag(), h.superAim, h.superRange * T, 34f, 40f, 0xFFFFE14D.toInt(), if (h.superAim == Aim.LOB || h.superAim == Aim.POINT) 2f * T else 0f)
        } else if (sPid >= 0 && pl.ready && pl.hero.superAim == Aim.SELF) {
            G.ring(c, px, py, pl.hero.superRange * T, 4f, 0x88FFE14D.toInt())
        }
    }

    private fun drawAim(c: Canvas, x: Float, y: Float, a: Float, mag: Float, aim: Aim, range: Float, width: Float, spread: Float, col: Int, areaR: Float) {
        val fill = G.alpha(col, 0.28f)
        c.save(); c.translate(x, y)
        when (aim) {
            Aim.LINE -> {
                c.rotate(Math.toDegrees(a.toDouble()).toFloat())
                G.rrect(c, 20f, -width, range, width, width, fill, false)
            }
            Aim.CONE, Aim.ARC -> {
                val sp = if (spread > 0f) spread else 30f
                G.pie(c, 0f, 0f, range, Math.toDegrees(a.toDouble()).toFloat() - sp / 2, sp, fill)
            }
            Aim.LOB, Aim.POINT -> {
                val d = mag * range
                val tx = cosf(a) * d; val ty = sinf(a) * d
                for (k in 1..8) { val t = k / 9f; G.circle(c, tx * t, ty * t - sinf(t * 3.14f) * d * 0.25f, 4f, G.alpha(col, 0.6f), false) }
                G.circle(c, tx, ty, maxOf(areaR, 30f), fill, false)
                G.ring(c, tx, ty, maxOf(areaR, 30f), 3f, G.alpha(col, 0.7f))
            }
            Aim.SELF -> G.ring(c, 0f, 0f, range, 4f, G.alpha(col, 0.6f))
        }
        c.restore()
    }

    // ------------------------------------------------------------------ screen-space HUD
    fun draw(c: Canvas) {
        val pl = p
        G.ow = 3f * u
        // blind (confetti in your face)
        if (blindT > 0f) {
            val a = clamp(blindT / 1.4f, 0f, 1f)
            for (i in 0 until 40) {
                val x = (i * 173.3f) % W; val y = (i * 91.7f + m.time * 50f) % H
                G.rect(c, x, y, x + 60f * u, y + 30f * u, G.alpha(Fx.CONFETTI[i % 6], 0.85f * a), false)
            }
        }
        if (pl != null && m.mode.mirrorActive(pl)) {
            G.stroke.strokeWidth = 18f * u; G.stroke.color = 0x99B46CFF.toInt(); G.stroke.shader = null
            c.drawRect(0f, 0f, W, H, G.stroke)
            G.text(c, "MIROIR !", W / 2, H * 0.3f, 44f * u, 0xFFE0B0FF.toInt())
        }
        drawMinimap(c)
        // pause & ping
        drawRoundBtn(c, B_PAUSE, 0xFF5B4B8A.toInt()) { x, y, s -> G.rect(c, x - s * 0.45f, y - s * 0.5f, x - s * 0.12f, y + s * 0.5f, G.WHITE, false); G.rect(c, x + s * 0.12f, y - s * 0.5f, x + s * 0.45f, y + s * 0.5f, G.WHITE, false) }
        if (pl != null && !pl.out) {
            if (pingMode) {
                val b = btnPos(B_PING)
                G.ring(c, b[0], b[1], b[2] * (1.35f + 0.15f * sinf(m.time * 10f)), 4f * u, 0xFFFBBF24.toInt())
                G.ring(c, mmX + mmW / 2, mmY + mmH / 2, maxOf(mmW, mmH) * 0.6f, 3f * u, G.alpha(0xFFFBBF24.toInt(), 0.5f + 0.5f * sinf(m.time * 8f)))
                val hint = "Touche l'endroit à signaler (ou la mini-carte)"
                val tw = G.textW(hint, 24f * u) + 40f * u
                G.rrect(c, W / 2 - tw / 2, H * 0.25f - 30f * u, W / 2 + tw / 2, H * 0.25f + 14f * u, 20f * u, 0xDD78350F.toInt())
                G.text(c, hint, W / 2, H * 0.25f, 24f * u, 0xFFFDE68A.toInt())
            }
            drawRoundBtn(c, B_PING, if (pingMode) 0xFFF59E0B.toInt() else 0xFFFBBF24.toInt()) { x, y, s -> G.text(c, "!", x, y + s * 0.4f, s * 1.2f, G.WHITE) }
        }
        // kill feed
        for ((i, f) in feed.withIndex()) {
            G.fade = clamp(f.t, 0f, 1f)
            G.text(c, f.s, 84f * u, 50f * u + i * 26f * u, 18f * u, f.c, android.graphics.Paint.Align.LEFT)
            G.fade = 1f
        }
        // mode info
        m.mode.drawHud(c, this)
        // boss bar
        val b = m.boss
        if (b != null && b.alive) {
            val bw = 460f * u; val bx = W / 2 - bw / 2; val by = 96f * u
            G.rrect(c, bx, by, bx + bw, by + 22f * u, 10f * u, 0xCC1B1530.toInt())
            G.rrect(c, bx + 3f * u, by + 3f * u, bx + 3f * u + (bw - 6f * u) * (b.hp / b.maxHp).coerceIn(0f, 1f), by + 19f * u, 8f * u, if (b.phase2) 0xFFFF3D3D.toInt() else 0xFFFF7A3D.toInt(), false)
            G.text(c, b.name, W / 2, by - 6f * u, 22f * u, G.WHITE)
        }
        if (pl == null) return
        if (pl.out) { drawDead(c, pl); return }
        drawSticks(c, pl)
        // HP top-left under feed
        if (smallT > 0f) { G.fade = clamp(smallT * 2f, 0f, 1f); G.text(c, small, W / 2, H * 0.72f, 26f * u, 0xFFFFF3B0.toInt()); G.fade = 1f }
        if (bigT > 0f) { G.fade = clamp(bigT * 2f, 0f, 1f); G.text(c, bigMsg, W / 2, H * 0.36f, 56f * u, bigCol); G.fade = 1f }
        if (hitT > 0f) {
            val cx = W / 2; val cy = H / 2 - 30f * u; val s = 12f * u
            G.line(c, cx - s, cy - s, cx - s / 3, cy - s / 3, 3f * u, G.WHITE); G.line(c, cx + s, cy - s, cx + s / 3, cy - s / 3, 3f * u, G.WHITE)
            G.line(c, cx - s, cy + s, cx - s / 3, cy + s / 3, 3f * u, G.WHITE); G.line(c, cx + s, cy + s, cx + s / 3, cy + s / 3, 3f * u, G.WHITE)
        }
        // edge markers for revealed enemies (radar) & pings
        drawEdgeMarkers(c, pl)
        if (m.paused || menuOpen) drawPause(c)
    }

    private fun drawSticks(c: Canvas, pl: Fighter) {
        G.ow = 3f * u
        // move stick
        if (mPid >= 0) {
            G.circle(c, mOx, mOy, 70f * u * sizeMul, 0x33FFFFFF, false)
            G.ring(c, mOx, mOy, 70f * u * sizeMul, 3f * u, 0x66FFFFFF)
            G.circle(c, mKx, mKy, 34f * u * sizeMul, 0xAAFFFFFF.toInt())
        } else {
            G.text(c, "◀ ▶", 150f * u, H - 90f * u, 30f * u, 0x55FFFFFF, outline = false)
        }
        // attack stick
        val w = pl.currentWeapon()
        val ax = atkX(); val ay = atkY(); val ar = atkR()
        G.circle(c, ax, ay, ar, 0x33FF5E7E, false)
        G.ring(c, ax, ay, ar, 3f * u, 0x88FF9AB0.toInt())
        val kx = ax + aDx; val ky = ay + aDy
        G.depth = true
        G.circle(c, if (aPid >= 0) kx else ax, if (aPid >= 0) ky else ay, ar * 0.5f, if (pl.canAttack() || pl.modeWeapon != null) 0xFFF43F5E.toInt() else 0xFF8A6A78.toInt())
        G.depth = false
        if (pl.weapon != null || pl.modeWeapon != null) Icons.weapon(c, w.id, if (aPid >= 0) kx else ax, if (aPid >= 0) ky else ay, ar * 0.32f)
        else G.star(c, if (aPid >= 0) kx else ax, if (aPid >= 0) ky else ay, ar * 0.22f, ar * 0.1f, 4, 0.78f, G.WHITE, false)
        // ammo text for pickup weapons
        if (pl.weapon != null && pl.modeWeapon == null) G.text(c, "${pl.wAmmo}", ax + ar * 0.8f, ay + ar * 0.95f, 22f * u, 0xFFFFE14D.toInt())
        if (pl.unarmed && pl.weapon == null) G.text(c, "Sans arme", ax, ay + ar + 26f * u, 18f * u, 0xFFFFC94D.toInt())
        // super
        val sx = supX(); val sy = supY(); val sr = supR()
        val ch = pl.superCharge
        G.circle(c, sx, sy, sr, 0x66000000, false)
        G.pie(c, sx, sy, sr, -90f, 360f * ch, if (pl.ready) 0xFFFFD23F.toInt() else 0xAA9C7A20.toInt())
        G.ring(c, sx, sy, sr, 3f * u, if (pl.ready) 0xFFFFF3B0.toInt() else 0x88FFFFFF.toInt())
        if (pl.ready) G.ring(c, sx, sy, sr * (1.15f + 0.08f * sinf(m.time * 8f)), 3f * u, 0x88FFE14D.toInt())
        val skx = if (sPid >= 0) sx + sDx * 0.6f else sx; val sky = if (sPid >= 0) sy + sDy * 0.6f else sy
        G.star(c, skx, sky, sr * 0.5f, sr * 0.22f, 5, 0f, if (pl.ready) G.WHITE else 0x88FFFFFF.toInt())
        // other buttons
        if (btnVisible(B_GADGET)) drawRoundBtn(c, B_GADGET, 0xFF35C6F4.toInt()) { x, y, s -> Icons.gadget(c, pl.gadget!!.id, x, y, s * 0.7f); G.text(c, "${pl.gadgetCharges}", x + s * 0.9f, y + s * 1.0f, 18f * u, G.WHITE) }
        if (btnVisible(B_ITEM)) drawRoundBtn(c, B_ITEM, 0xFFFF8CC6.toInt()) { x, y, s -> Icons.item(c, pl.items[0], x, y, s * 0.7f); if (pl.items.size > 1) G.text(c, "×${pl.items.size}", x + s * 0.9f, y + s, 18f * u, G.WHITE) }
        drawRoundBtn(c, B_JUMP, 0xFF7CC243.toInt()) { x, y, s -> G.poly(c, G.WHITE, true, x, y - s * 0.55f, x + s * 0.5f, y + s * 0.1f, x + s * 0.18f, y + s * 0.1f, x + s * 0.18f, y + s * 0.5f, x - s * 0.18f, y + s * 0.5f, x - s * 0.18f, y + s * 0.1f, x - s * 0.5f, y + s * 0.1f) }
        val lab = useLabel
        if (lab != null) drawRoundBtn(c, B_USE, 0xFFFF9A3D.toInt()) { x, y, s -> G.text(c, lab, x, y + s * 0.18f, minOf(s * 0.48f, 2.2f * s / maxOf(1, lab.length) * 1.6f), G.WHITE) }
        // parachute & item hints
        if (pl.parachutes > 0) { Icons.gadget(c, Gadgets.PARACHUTE, W - 318f * u, H - 330f * u, 16f * u); G.text(c, "×${pl.parachutes}", W - 290f * u, H - 322f * u, 16f * u, G.WHITE) }
        // own HP big bar (bottom-left)
        val hw = 260f * u; val hx = 24f * u; val hy = H - 40f * u
        G.rrect(c, hx, hy, hx + hw, hy + 22f * u, 10f * u, 0xCC1B1530.toInt())
        if (m.mode.percentMode) {
            G.text(c, "${pl.dmgPct.toInt()}%   ${"♥".repeat(pl.stocks.coerceAtLeast(0))}", hx + hw / 2, hy + 18f * u, 20f * u, G.mix(G.WHITE, 0xFFFF3D3D.toInt(), clamp(pl.dmgPct / 150f, 0f, 1f)))
        } else {
            G.vgrad(c, hx + 3f * u, hy + 3f * u, hx + 3f * u + (hw - 6f * u) * clamp(pl.hp / pl.maxHp, 0.02f, 1f), hy + 19f * u, 8f * u, 0xFF86EFAC.toInt(), 0xFF16A34A.toInt())
            G.text(c, "${pl.hp.toInt()}", hx + hw / 2, hy + 18f * u, 17f * u, G.WHITE)
        }
        G.text(c, "${pl.hero.name}", hx, hy - 8f * u, 20f * u, G.WHITE, android.graphics.Paint.Align.LEFT)
    }

    private fun drawRoundBtn(c: Canvas, id: Int, col: Int, icon: (Float, Float, Float) -> Unit) {
        val b = btnPos(id)
        val pr = pressed == id
        val r = b[2]
        G.ow = 3f * u
        G.glow(c, b[0], b[1], r * 1.6f, G.alpha(col, 0.35f))
        G.circle(c, b[0], b[1] + 4f * u, r, G.shade(col, 0.5f))
        G.depth = true
        G.circle(c, b[0], b[1] + (if (pr) 3f * u else 0f), r, col)
        G.depth = false
        icon(b[0], b[1] + (if (pr) 3f * u else 0f), r * 0.75f)
    }

    private fun drawDead(c: Canvas, pl: Fighter) {
        val k = m.killer
        if (m.killCamT > 0f) {
            G.rect(c, 0f, 0f, W, 70f * u, 0x88000000.toInt(), false); G.rect(c, 0f, H - 70f * u, W, H, 0x88000000.toInt(), false)
            G.text(c, if (k != null && k !== pl) "Éliminé par ${k.name} (${k.hero.name})" else "Éliminé !", W / 2, H - 26f * u, 30f * u, 0xFFFF5E7E.toInt())
            G.text(c, "KILL CAM", 90f * u, 46f * u, 22f * u, 0xFFFF3D3D.toInt())
            return
        }
        if (m.mode.respawns && !m.over) {
            G.text(c, "Réapparition dans ${maxOf(0f, pl.respawnT).toInt() + 1}…", W / 2, H * 0.62f, 40f * u, G.WHITE)
        } else if (!m.over) {
            val s = m.spectate
            G.rrect(c, W / 2 - 170f * u, H - 104f * u, W / 2 + 170f * u, H - 36f * u, 20f * u, 0xAA1B1530.toInt())
            G.text(c, "Spectateur", W / 2, H - 78f * u, 18f * u, 0xFFFFF3B0.toInt())
            G.text(c, s?.name ?: "-", W / 2, H - 48f * u, 26f * u, G.WHITE)
            drawRoundBtn(c, B_PREV, 0xFF5B4B8A.toInt()) { x, y, sz -> G.text(c, "◀", x, y + sz * 0.35f, sz, G.WHITE) }
            drawRoundBtn(c, B_NEXT, 0xFF5B4B8A.toInt()) { x, y, sz -> G.text(c, "▶", x, y + sz * 0.35f, sz, G.WHITE) }
            G.text(c, "Pause ▸ Quitter pour abandonner", W / 2, H - 12f * u, 15f * u, 0xAAFFFFFF.toInt())
        }
        if (m.paused || menuOpen) drawPause(c)
    }

    private fun drawPause(c: Canvas) {
        G.rect(c, 0f, 0f, W, H, 0xAA000000.toInt(), false)
        G.text(c, if (m.cfg.net) "MENU" else "PAUSE", W / 2, H / 2 - 130f * u, 60f * u, G.WHITE)
        if (m.cfg.net) G.text(c, "La partie continue en multijoueur !", W / 2, H / 2 + 160f * u, 22f * u, 0xFFFDE68A.toInt())
        G.text(c, m.mode.let { com.starguys.battle.modes.Modes.def(m.cfg.mode).name }, W / 2, H / 2 - 90f * u, 24f * u, 0xFFFFF3B0.toInt())
        for (id in intArrayOf(B_RESUME, B_QUIT)) {
            val b = btnPos(id)
            G.ow = 3f * u
            val col = if (id == B_RESUME) 0xFF7CC243.toInt() else 0xFFE84545.toInt()
            G.rrect(c, b[0] - 160f * u, b[1] - 36f * u + 6f * u, b[0] + 160f * u, b[1] + 36f * u + 6f * u, 18f * u, G.shade(col, 0.6f))
            G.rrect(c, b[0] - 160f * u, b[1] - 36f * u, b[0] + 160f * u, b[1] + 36f * u, 18f * u, col)
            G.text(c, if (id == B_RESUME) "Reprendre" else "Quitter la partie", b[0], b[1] + 11f * u, 30f * u, G.WHITE)
        }
    }

    private fun drawEdgeMarkers(c: Canvas, pl: Fighter) {
        val z = m.cam.zoom
        for (f in m.fighters) {
            if (f.out || !m.isEnemy(pl.team, f) || f.revealT <= 0f) continue
            val sx = (f.x - m.cam.x) * z + W / 2; val sy = (f.y - m.cam.y) * z + H / 2
            if (sx in 0f..W && sy in 0f..H) continue
            val ex = clamp(sx, 30f * u, W - 30f * u); val ey = clamp(sy, 30f * u, H - 30f * u)
            G.circle(c, ex, ey, 12f * u, 0xFFFF5E7E.toInt())
        }
        for (pg in m.pings) {
            val sx = (pg.x - m.cam.x) * z + W / 2; val sy = (pg.y - m.cam.y) * z + H / 2
            val ex = clamp(sx, 30f * u, W - 30f * u); val ey = clamp(sy, 30f * u, H - 30f * u)
            G.circle(c, ex, ey, 14f * u, 0xFFFFC23D.toInt()); G.text(c, "!", ex, ey + 8f * u, 22f * u, G.WHITE)
        }
    }

    private fun buildMinimap() {
        val mp = m.map
        val bmp = try { Bitmap.createBitmap(mp.w, mp.h, Bitmap.Config.ARGB_8888) } catch (e: Throwable) { null } ?: return
        for (y in 0 until mp.h) for (x in 0 until mp.w) {
            val tp = mp.t(x, y)
            val col = when (tp) {
                Tl.VOID -> 0x00000000
                Tl.WALL, Tl.DOOR -> mp.biome.wallSide
                Tl.BOX, Tl.LOW -> 0xFFC28A4E.toInt()
                Tl.BRICK -> 0xFFE07B4A.toInt()
                Tl.GLASS -> 0xFFCDEBFF.toInt()
                Tl.BUSH -> mp.biome.bush
                Tl.WATER -> 0xFF4FB5F0.toInt()
                Tl.LAVA -> 0xFFFF5E7E.toInt()
                Tl.ICE -> 0xFFCFEFFF.toInt()
                Tl.GOAL -> 0xFFFFFFFF.toInt()
                else -> G.shade(mp.biome.floorA, 1f - mp.e(x, y) * 0.06f)
            }
            try { bmp.setPixel(x, y, col) } catch (_: Throwable) { }
        }
        minimap = bmp
        minimapVersion = mp.version
    }

    private fun drawMinimap(c: Canvas) {
        val mp = m.map
        minimapT -= 1f / 60f
        if (minimap == null || (mp.version != minimapVersion && minimapT <= 0f)) { buildMinimap(); minimapT = 1.5f }
        val size = 170f * u
        val aspect = mp.w.toFloat() / mp.h
        val mw = if (aspect >= 1f) size else size * aspect
        val mh = if (aspect >= 1f) size / aspect else size
        val mx = W - mw - 16f * u; val my = 14f * u
        mmX = mx; mmY = my; mmW = mw; mmH = mh; mmS = mw / mp.pxW
        G.ow = 3f * u
        G.rrect(c, mx - 4f * u, my - 4f * u, mx + mw + 4f * u, my + mh + 4f * u, 8f * u, 0xAA1B1530.toInt())
        val bmp = minimap
        if (bmp != null) {
            mmSrc.set(0, 0, mp.w, mp.h); mmDst.set(mx, my, mx + mw, my + mh)
            c.drawBitmap(bmp, mmSrc, mmDst, bmpPaint)
        }
        val s = mw / mp.pxW
        if (m.stormR > 0f) { G.ring(c, mx + m.stormX * s, my + m.stormY * s, m.stormR * s, 2f * u, 0xFFE0B0FF.toInt()) }
        m.mode.minimapExtra(c, mx, my, s)
        val pl = p
        for (f in m.fighters) {
            if (f.out) continue
            val ally = pl != null && !m.isEnemy(pl.team, f)
            if (!ally && !(f.revealT > 0f || (pl != null && !pl.out && dist(pl.x, pl.y, f.x, f.y) < 9f * T && m.canSee(pl, f)))) continue
            G.circle(c, mx + f.x * s, my + f.y * s, (if (f === pl) 4.5f else 3.2f) * u, if (f === pl) 0xFF7CFF8A.toInt() else if (ally) 0xFF6BD3FF.toInt() else 0xFFFF5E7E.toInt(), false)
        }
        val b = m.boss
        if (b != null) G.circle(c, mx + b.x * s, my + b.y * s, 5f * u, 0xFFFF7A3D.toInt(), false)
        for (pg in m.pings) G.circle(c, mx + pg.x * s, my + pg.y * s, 4f * u, 0xFFFFC23D.toInt(), false)
    }

    // helpers for modes
    fun topText(c: Canvas, s: String, line: Int = 0, col: Int = G.WHITE, size: Float = 30f) {
        G.text(c, s, W / 2, (40f + line * 34f) * u, size * u, col)
    }

    fun topPill(c: Canvas, s: String, col: Int) {
        val w = G.textW(s, 26f * u) + 40f * u
        G.ow = 3f * u
        G.rrect(c, W / 2 - w / 2, 10f * u, W / 2 + w / 2, 54f * u, 20f * u, col)
        G.text(c, s, W / 2, 42f * u, 26f * u, G.WHITE)
    }

    fun scoreBar(c: Canvas, left: String, right: String, lc: Int, rc: Int, center: String) {
        val y = 12f * u
        G.ow = 3f * u
        G.rrect(c, W / 2 - 230f * u, y, W / 2 - 60f * u, y + 46f * u, 18f * u, lc)
        G.rrect(c, W / 2 + 60f * u, y, W / 2 + 230f * u, y + 46f * u, 18f * u, rc)
        G.text(c, left, W / 2 - 145f * u, y + 33f * u, 26f * u, G.WHITE)
        G.text(c, right, W / 2 + 145f * u, y + 33f * u, 26f * u, G.WHITE)
        G.text(c, center, W / 2, y + 32f * u, 24f * u, G.WHITE)
    }
    val scaleU get() = u
}
