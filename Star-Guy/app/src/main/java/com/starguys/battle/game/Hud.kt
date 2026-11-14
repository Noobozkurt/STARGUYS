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
import kotlin.math.abs

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

    private fun fireX() = W - 150f * u; private fun fireY() = H - 190f * u; private fun fireR() = 74f * u * sizeMul
    private fun supX() = W - 330f * u; private fun supY() = H - 108f * u; private fun supR() = 52f * u * sizeMul
    private fun btnPos(id: Int): FloatArray = when (id) {
        B_GADGET -> floatArrayOf(W - 318f * u, H - 262f * u, 36f * u * sizeMul)
        B_ITEM -> floatArrayOf(W - 210f * u, H - 352f * u, 34f * u * sizeMul)
        B_JUMP -> floatArrayOf(W - 74f * u, H - 340f * u, 48f * u * sizeMul)
        B_USE -> floatArrayOf(W - 480f * u, H - 150f * u, 44f * u * sizeMul)
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

    // ------------------------------------------------------------------ TPS controls
    class AimPrev(val aim: Aim, val a: Float, val d: Float, val range: Float, val width: Float, val spread: Float, val area: Float, val superAim: Boolean)
    /** ground preview of the current aim (drawn by the 3D scene) */
    var aimPreview: AimPrev? = null
    private val view get() = m.scene.view
    private var firePid = -1; private var fireT = 0f
    private var lookPid = -1
    private var lastX = HashMap<Int, Float>(); private var lastY = HashMap<Int, Float>()
    /** current aim (absolute sim angle + distance) after aim assist */
    var aimA = 0f; var aimD = 0f
    var locked: Body? = null
    private val sens get() = 0.0052f / u

    private fun rotateCam(id: Int, x: Float, y: Float) {
        val lx = lastX[id] ?: x; val ly = lastY[id] ?: y
        lastX[id] = x; lastY[id] = y
        view.yaw += (x - lx) * sens
        view.pitch = clamp(view.pitch + (y - ly) * sens * 0.6f, 0.05f, 0.95f)
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
                lastX[e.id] = e.x; lastY[e.id] = e.y
                for (id in intArrayOf(B_PAUSE, B_PING, B_JUMP, B_GADGET, B_ITEM, B_USE, B_PREV, B_NEXT)) {
                    if (!btnVisible(id)) continue
                    val b = btnPos(id)
                    if (dist(e.x, e.y, b[0], b[1]) < b[2] * 1.25f) { btnPid[e.id] = id; pressed = id; onButton(id); return }
                }
                if (pingMode && pl != null && !pl.out) {
                    val wpt = if (e.x in mmX..(mmX + mmW) && e.y in mmY..(mmY + mmH)) floatArrayOf((e.x - mmX) / mmS, (e.y - mmY) / mmS)
                    else view.pick(e.x, e.y) ?: floatArrayOf(pl.x + view.fwdX() * 400f, pl.y + view.fwdY() * 400f)
                    input.add(PlayerInput.PING, clamp(wpt[0], 0f, m.map.pxW), clamp(wpt[1], 0f, m.map.pxH))
                    pingMode = false
                    btnPid[e.id] = -1
                    return
                }
                if (pl != null && !pl.out) {
                    if (dist(e.x, e.y, supX(), supY()) < supR() * 1.3f && sPid < 0) { sPid = e.id; sT = 0f; return }
                    if (dist(e.x, e.y, fireX(), fireY()) < fireR() * 1.3f && firePid < 0) {
                        firePid = e.id; fireT = 0f
                        computeAim(pl, false)
                        input.add(PlayerInput.ATTACK, aimA, aimD)
                        return
                    }
                }
                if (e.x < W * 0.42f && mPid < 0) { mPid = e.id; mOx = e.x; mOy = e.y; mKx = e.x; mKy = e.y; return }
                if (lookPid < 0) lookPid = e.id
            }
            TouchEvt.MOVE -> {
                when (e.id) {
                    mPid -> { mKx = e.x; mKy = e.y }
                    lookPid, firePid, sPid -> rotateCam(e.id, e.x, e.y)
                }
            }
            TouchEvt.UP -> {
                lastX.remove(e.id); lastY.remove(e.id)
                if (btnPid.remove(e.id) != null) { pressed = -1; return }
                when (e.id) {
                    mPid -> { mPid = -1; input.mx = 0f; input.my = 0f }
                    lookPid -> lookPid = -1
                    firePid -> firePid = -1
                    sPid -> { sPid = -1; releaseSuper(e.x > -9000f) }
                }
            }
        }
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
            B_GADGET -> if (pl != null) { computeAim(pl, false); input.add(PlayerInput.GADGET, aimA) }
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

    /** Crosshair aim with aim assist: returns angle + distance in [aimA]/[aimD]. */
    private fun computeAim(pl: Fighter, sup: Boolean) {
        val w = pl.currentWeapon()
        val range = (if (sup) pl.hero.superRange else w.range) * T * (if (sup) 1f else pl.rangeMul())
        val aim = if (sup) pl.hero.superAim else w.aim
        val yaw = view.yaw
        val gp = view.pick(view.crossX(), view.crossY(), range * 1.6f + 600f)
        var d = if (gp != null) dist(pl.x, pl.y, gp[0], gp[1]) else range
        d = clamp(d, 40f, range)
        var best: Body? = null; var bs = 9f
        for (b in m.hittables) {
            if (b === pl || !m.isEnemy(pl.team, b) || b is Breakable || b is Ball) continue
            if (b is Fighter && !m.canSee(pl, b)) continue
            if (b is Vehicle && b.driver == null) continue
            val bd = dist(pl.x, pl.y, b.x, b.y)
            if (bd > range * 1.15f || bd < 1f) continue
            val da = abs(com.starguys.battle.core.angleDiff(yaw, ang(pl.x, pl.y, b.x, b.y)))
            val lim = 0.2f + 26f / bd
            if (da > lim) continue
            val score = da / lim + bd / range * 0.3f
            if (score < bs) { bs = score; best = b }
        }
        locked = best
        if (best != null) {
            val bd = dist(pl.x, pl.y, best.x, best.y)
            val lead = if (aim == Aim.LOB) 0.6f else bd / 1100f
            val tx = best.x + best.vx * lead * 0.7f; val ty = best.y + best.vy * lead * 0.7f
            aimA = ang(pl.x, pl.y, tx, ty); aimD = dist(pl.x, pl.y, tx, ty)
        } else {
            aimA = yaw; aimD = if (aim == Aim.LOB || aim == Aim.POINT) d else range
        }
    }

    private fun releaseSuper(valid: Boolean) {
        val pl = p ?: return
        if (!valid || pl.out || !pl.ready) return
        computeAim(pl, true)
        if (pl.hero.superAim == Aim.SELF) input.add(PlayerInput.SUPER, pl.face, 0f)
        else input.add(PlayerInput.SUPER, aimA, aimD)
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
        fireT += dt; sT += dt
        aimPreview = null
        val pl = p
        if (pl == null || pl.out) { input.mx = 0f; input.my = 0f; input.aiming = false; input.hold = false; pingMode = false; return }
        if (mPid >= 0) {
            var dx = mKx - mOx; var dy = mKy - mOy
            val r = 70f * u * sizeMul
            val l = len(dx, dy)
            if (l > r) { mOx = mKx - dx / l * r; mOy = mKy - dy / l * r; dx = mKx - mOx; dy = mKy - mOy }
            val mag = clamp(len(dx, dy) / r, 0f, 1f)
            if (mag < 0.12f) { input.mx = 0f; input.my = 0f } else {
                val k = mag.coerceAtLeast(0.55f) / maxOf(len(dx, dy), 1f)
                val fwd = -dy * k; val rt = dx * k
                val y = view.yaw
                input.mx = cosf(y) * fwd - sinf(y) * rt; input.my = sinf(y) * fwd + cosf(y) * rt
            }
        } else { input.mx = 0f; input.my = 0f }
        input.aiming = false; input.hold = false
        val w = pl.currentWeapon()
        if (firePid >= 0) {
            computeAim(pl, false)
            input.aiming = true; input.aim = aimA
            input.hold = true; input.holdDist = aimD
            if (w.aim != Aim.LINE) aimPreview = AimPrev(w.aim, aimA, aimD, w.range * T * pl.rangeMul(), w.width, w.spread, if (w.aim == Aim.LOB) 1.1f * T else 0f, false)
        } else locked = null
        if (sPid >= 0 && pl.ready) {
            computeAim(pl, true)
            val h = pl.hero
            input.aiming = h.superAim != Aim.SELF; input.aim = aimA
            aimPreview = AimPrev(h.superAim, aimA, aimD, h.superRange * T, 34f, 40f, if (h.superAim == Aim.LOB || h.superAim == Aim.POINT) 2f * T else 0f, true)
        }
        labelT -= dt
        if (labelT <= 0f) { useLabel = m.interactLabel(pl); labelT = 0.15f }
    }

    fun drawWorld(c: Canvas) {}

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
        drawOverlays(c)
        drawMinimap(c)
        if (p?.out == false) drawCompass(c)
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

    private val pt = FloatArray(2)

    /** Names, health bars, damage numbers and pings projected from the 3D world. */
    private fun drawOverlays(c: Canvas) {
        val v = view
        val pl = p
        for (f in m.fighters) {
            if (f.out || f === pl && !pl.out || f.vehicle != null) continue
            if (!m.visibleToPlayer(f)) continue
            if (f.invisT > 0 && f.hero.id == 3 && pl != null && m.isEnemy(pl.team, f) && f.revealT <= 0) continue
            if (m.mode.hideBars(f, pl) || (f.disguise >= 0 && pl != null && m.isEnemy(pl.team, f))) continue
            val d = v.depth(f.x, f.y, f.z)
            if (d > 2200f) continue
            if (!v.project(f.x, f.y, f.z + f.bodyH + 22f, pt)) continue
            val s = clamp(380f / d, 0.45f, 1.25f) * u
            val ally = pl != null && !m.isEnemy(pl.team, f)
            val col = if (ally) 0xFF38BDF8.toInt() else 0xFFF43F5E.toInt()
            val bw = 80f * s; val bh = 9f * s
            val l = pt[0] - bw / 2; val t = pt[1]
            G.rrect(c, l - 2f * s, t - 2f * s, l + bw + 2f * s, t + bh + 2f * s, 5f * s, 0xAA0F172A.toInt(), false)
            if (m.mode.percentMode) G.text(c, "${f.dmgPct.toInt()}%", pt[0], t + bh, 20f * s, G.mix(G.WHITE, 0xFFFF3D3D.toInt(), clamp(f.dmgPct / 150f, 0f, 1f)))
            else {
                G.rrect(c, l, t, l + bw * clamp(f.hp / f.maxHp, 0f, 1f), t + bh, 4f * s, col, false)
                if (f.shieldHp > 0f) G.rect(c, l, t, l + bw * clamp(f.shieldHp / 2000f, 0f, 1f), t + bh * 0.4f, 0xFF93C5FD.toInt(), false)
            }
            G.text(c, f.name, pt[0], t - 5f * s, 16f * s, if (ally) 0xFFBAE6FD.toInt() else G.WHITE)
            if (f.ready && ally) G.circle(c, l - 9f * s, t + bh / 2, 5f * s, 0xFFFFE14D.toInt())
        }
        for (mb in m.mobs) {
            if (!mb.alive || mb.isBoss || mb.hp >= mb.maxHp || !m.visibleToPlayer(mb)) continue
            val d = v.depth(mb.x, mb.y, mb.z); if (d > 1800f) continue
            if (!v.project(mb.x, mb.y, mb.z + mb.bodyH + 14f, pt)) continue
            val s = clamp(380f / d, 0.45f, 1.2f) * u
            val bw = mb.radius * 2.4f * s
            G.rect(c, pt[0] - bw / 2, pt[1], pt[0] + bw / 2, pt[1] + 6f * s, 0xAA0F172A.toInt(), false)
            G.rect(c, pt[0] - bw / 2, pt[1], pt[0] - bw / 2 + bw * (mb.hp / mb.maxHp).coerceIn(0f, 1f), pt[1] + 6f * s, 0xFFF43F5E.toInt(), false)
        }
        for (vh in m.vehicles) {
            if (!vh.alive || vh.kind == Vehicle.COASTER) continue
            val d = v.depth(vh.x, vh.y, vh.z); if (d > 1600f) continue
            if (!v.project(vh.x, vh.y, vh.z + (if (vh.kind == Vehicle.BALLOON) 240f else 100f), pt)) continue
            val s = clamp(380f / d, 0.45f, 1.2f) * u
            val dr = vh.driver
            if (dr == null && d < 700f) G.text(c, Vehicle.NAMES[vh.kind], pt[0], pt[1], 15f * s, 0xDDFFFFFF.toInt())
            else if (dr != null && dr !== pl) {
                G.rect(c, pt[0] - 40f * s, pt[1], pt[0] + 40f * s, pt[1] + 7f * s, 0xAA0F172A.toInt(), false)
                G.rect(c, pt[0] - 40f * s, pt[1], pt[0] - 40f * s + 80f * s * (vh.hp / vh.maxHp).coerceIn(0f, 1f), pt[1] + 7f * s, 0xFFFFD23F.toInt(), false)
                G.text(c, dr.name, pt[0], pt[1] - 4f * s, 15f * s, G.WHITE)
            }
        }
        for (q in m.fx.ps) {
            if (q.kind != Fx.NUM && q.kind != Fx.TEXT) continue
            if (!v.project(q.x, q.y, q.z, pt)) continue
            val k = q.life / q.max
            val d = v.depth(q.x, q.y, q.z)
            val s = clamp(420f / d, 0.5f, 1.5f) * u * (if (q.kind == Fx.NUM && k > 0.8f) 1f + (k - 0.8f) * 3f else 1f)
            G.fade = minOf(1f, k * 2.5f)
            G.text(c, q.text, pt[0], pt[1], q.size * 1.2f * s, q.color)
            G.fade = 1f
        }
        val myTeam = pl?.team
        for (pg in m.pings) {
            if (myTeam != null && pg.team != myTeam) continue
            val h = maxOf(0f, m.map.groundAt(pg.x, pg.y))
            val on = v.project(pg.x, pg.y, h + 120f, pt)
            var sx = pt[0]; var sy = pt[1]
            if (!on) { sx = if (com.starguys.battle.core.angleDiff(view.yaw, ang(view.camX, view.camY, pg.x, pg.y)) > 0) W - 40f * u else 40f * u; sy = H / 2 }
            sx = clamp(sx, 40f * u, W - 40f * u); sy = clamp(sy, 60f * u, H - 60f * u)
            G.fade = clamp(pg.life, 0f, 1f)
            G.circle(c, sx, sy, 20f * u, 0xFFF59E0B.toInt()); G.text(c, "!", sx, sy + 10f * u, 28f * u, G.WHITE)
            if (pl != null) G.text(c, "${(dist(pl.x, pl.y, pg.x, pg.y) / 32f).toInt()} m", sx, sy + 40f * u, 17f * u, 0xFFFDE68A.toInt())
            G.fade = 1f
        }
    }

    private fun drawCompass(c: Canvas) {
        val cw = 420f * u; val cx = W / 2; val y = 74f * u
        G.rrect(c, cx - cw / 2, y - 16f * u, cx + cw / 2, y + 16f * u, 14f * u, 0x660F172A, false)
        val labels = arrayOf("E", "SE", "S", "SO", "O", "NO", "N", "NE")
        for (k in 0 until 8) {
            val a = k * 0.7854f
            val da = com.starguys.battle.core.angleDiff(view.yaw, a)
            if (abs(da) > 1.2f) continue
            val x = cx + da / 1.2f * cw / 2
            G.fade = 1f - abs(da) / 1.2f * 0.6f
            G.text(c, labels[k], x, y + 7f * u, (if (labels[k].length == 1) 21f else 15f) * u, if (labels[k] == "N") 0xFFFBBF24.toInt() else G.WHITE)
        }
        G.fade = 1f
        G.poly(c, 0xFFFBBF24.toInt(), true, cx, y + 14f * u, cx - 6f * u, y + 22f * u, cx + 6f * u, y + 22f * u)
    }

    private fun drawSticks(c: Canvas, pl: Fighter) {
        G.ow = 3f * u
        // crosshair
        val cx = view.crossX(); val cy = view.crossY()
        val lk = locked != null
        val cc = if (lk) 0xFFF43F5E.toInt() else 0xEEFFFFFF.toInt()
        val g = (if (firePid >= 0) 13f else 9f) * u
        for (k in 0 until 4) {
            val a = k * 1.5708f
            G.line(c, cx + cosf(a) * g, cy + sinf(a) * g, cx + cosf(a) * (g + 11f * u), cy + sinf(a) * (g + 11f * u), 3f * u, cc)
        }
        G.circle(c, cx, cy, 2.5f * u, cc, false)
        if (lk) G.ring(c, cx, cy, 26f * u, 2f * u, 0x88F43F5E.toInt())
        // move stick
        if (mPid >= 0) {
            G.circle(c, mOx, mOy, 70f * u * sizeMul, 0x33FFFFFF, false)
            G.ring(c, mOx, mOy, 70f * u * sizeMul, 3f * u, 0x66FFFFFF)
            G.circle(c, mKx, mKy, 34f * u * sizeMul, 0xAAFFFFFF.toInt())
        } else {
            G.circle(c, 170f * u, H - 150f * u, 70f * u * sizeMul, 0x1AFFFFFF, false)
            G.ring(c, 170f * u, H - 150f * u, 70f * u * sizeMul, 2f * u, 0x44FFFFFF)
        }
        // fire button
        val w = pl.currentWeapon()
        val fx = fireX(); val fy = fireY(); val fr = fireR()
        val ok = pl.canAttack() || pl.modeWeapon != null
        G.glow(c, fx, fy, fr * 1.5f, 0x55F43F5E)
        G.circle(c, fx, fy + 4f * u, fr, 0xFF7F1D1D.toInt())
        G.depth = true
        G.circle(c, fx, fy + (if (firePid >= 0) 3f * u else 0f), fr, if (ok) 0xFFF43F5E.toInt() else 0xFF8A6A78.toInt())
        G.depth = false
        val iy = fy + (if (firePid >= 0) 3f * u else 0f)
        if (pl.weapon != null || pl.modeWeapon != null) Icons.weapon(c, w.id, fx, iy, fr * 0.42f)
        else {
            G.ring(c, fx, iy, fr * 0.34f, 4f * u, G.WHITE)
            G.line(c, fx - fr * 0.5f, iy, fx + fr * 0.5f, iy, 4f * u, G.WHITE); G.line(c, fx, iy - fr * 0.5f, fx, iy + fr * 0.5f, 4f * u, G.WHITE)
        }
        // ammo
        if (pl.weapon != null && pl.modeWeapon == null) {
            G.rrect(c, fx - 60f * u, fy + fr + 6f * u, fx + 60f * u, fy + fr + 34f * u, 12f * u, 0xAA0F172A.toInt(), false)
            G.text(c, "${pl.wAmmo}", fx, fy + fr + 29f * u, 22f * u, 0xFFFFE14D.toInt())
        } else if (pl.modeWeapon == null) {
            val aw = 30f * u
            for (i in 0 until 3) {
                val fill = clamp(pl.ammo - i, 0f, 1f)
                val ax = fx - 50f * u + i * (aw + 5f * u)
                G.rrect(c, ax, fy + fr + 10f * u, ax + aw, fy + fr + 20f * u, 4f * u, 0xAA0F172A.toInt(), false)
                G.rrect(c, ax, fy + fr + 10f * u, ax + aw * fill, fy + fr + 20f * u, 4f * u, if (fill >= 1f) 0xFFFB923C.toInt() else 0xFF9A5A2A.toInt(), false)
            }
        }
        if (pl.unarmed && pl.weapon == null) G.text(c, "Sans arme", fx, fy - fr - 12f * u, 18f * u, 0xFFFFC94D.toInt())
        // super
        val sx = supX(); val sy = supY(); val sr = supR()
        val ch = pl.superCharge
        G.circle(c, sx, sy, sr, 0x880F172A.toInt(), false)
        G.pie(c, sx, sy, sr, -90f, 360f * ch, if (pl.ready) 0xFFFFD23F.toInt() else 0xAA9C7A20.toInt())
        G.ring(c, sx, sy, sr, 3f * u, if (pl.ready) 0xFFFFF3B0.toInt() else 0x88FFFFFF.toInt())
        if (pl.ready) G.ring(c, sx, sy, sr * (1.15f + 0.08f * sinf(m.time * 8f)), 3f * u, 0x88FFE14D.toInt())
        G.star(c, sx, sy, sr * 0.5f, sr * 0.22f, 5, 0f, if (pl.ready) G.WHITE else 0x88FFFFFF.toInt())
        if (btnVisible(B_GADGET)) drawRoundBtn(c, B_GADGET, 0xFF35C6F4.toInt()) { x, y, s -> Icons.gadget(c, pl.gadget!!.id, x, y, s * 0.7f); G.text(c, "${pl.gadgetCharges}", x + s * 0.9f, y + s * 1.0f, 18f * u, G.WHITE) }
        if (btnVisible(B_ITEM)) drawRoundBtn(c, B_ITEM, 0xFFFF8CC6.toInt()) { x, y, s -> Icons.item(c, pl.items[0], x, y, s * 0.7f); if (pl.items.size > 1) G.text(c, "×${pl.items.size}", x + s * 0.9f, y + s, 18f * u, G.WHITE) }
        drawRoundBtn(c, B_JUMP, 0xFF22C55E.toInt()) { x, y, s -> G.poly(c, G.WHITE, true, x, y - s * 0.55f, x + s * 0.5f, y + s * 0.1f, x + s * 0.18f, y + s * 0.1f, x + s * 0.18f, y + s * 0.5f, x - s * 0.18f, y + s * 0.5f, x - s * 0.18f, y + s * 0.1f, x - s * 0.5f, y + s * 0.1f) }
        val lab = useLabel
        if (lab != null) drawRoundBtn(c, B_USE, 0xFFFF9A3D.toInt()) { x, y, s -> G.text(c, lab, x, y + s * 0.18f, minOf(s * 0.48f, 2.2f * s / maxOf(1, lab.length) * 1.6f), G.WHITE) }
        if (pl.parachutes > 0) { Icons.gadget(c, Gadgets.PARACHUTE, W - 318f * u, H - 340f * u, 16f * u); G.text(c, "×${pl.parachutes}", W - 290f * u, H - 332f * u, 16f * u, G.WHITE) }
        // Fortnite-style health & shield (bottom centre)
        val hw = 380f * u; val hx = W / 2 - hw / 2; val hy = H - 46f * u
        if (m.mode.percentMode) {
            G.rrect(c, hx, hy, hx + hw, hy + 26f * u, 10f * u, 0xCC0F172A.toInt())
            G.text(c, "${pl.dmgPct.toInt()}%   ${"♥".repeat(pl.stocks.coerceAtLeast(0))}", W / 2, hy + 21f * u, 22f * u, G.mix(G.WHITE, 0xFFFF3D3D.toInt(), clamp(pl.dmgPct / 150f, 0f, 1f)))
        } else {
            if (pl.shieldHp > 0f) {
                G.rrect(c, hx, hy - 16f * u, hx + hw, hy - 4f * u, 6f * u, 0xCC0F172A.toInt(), false)
                G.vgrad(c, hx + 2f * u, hy - 14f * u, hx + 2f * u + (hw - 4f * u) * clamp(pl.shieldHp / 2000f, 0f, 1f), hy - 6f * u, 4f * u, 0xFF93C5FD.toInt(), 0xFF2563EB.toInt())
            }
            G.rrect(c, hx, hy, hx + hw, hy + 26f * u, 10f * u, 0xCC0F172A.toInt())
            G.vgrad(c, hx + 3f * u, hy + 3f * u, hx + 3f * u + (hw - 6f * u) * clamp(pl.hp / pl.maxHp, 0.02f, 1f), hy + 23f * u, 8f * u, 0xFF86EFAC.toInt(), 0xFF16A34A.toInt())
            G.text(c, "${pl.hp.toInt()} / ${pl.maxHp.toInt()}", W / 2, hy + 20f * u, 18f * u, G.WHITE)
        }
        G.text(c, pl.hero.name, hx, hy - 22f * u, 20f * u, G.WHITE, android.graphics.Paint.Align.LEFT)
        // low health vignette
        if (!m.mode.percentMode && pl.hp < pl.maxHp * 0.3f) {
            G.stroke.shader = null; G.stroke.strokeWidth = 40f * u; G.stroke.color = G.alpha(0xFFDC2626.toInt(), 0.25f + 0.15f * sinf(m.time * 6f))
            c.drawRect(0f, 0f, W, H, G.stroke)
        }
        if (m.stormR > 0f && dist(pl.x, pl.y, m.stormX, m.stormY) > m.stormR) G.rect(c, 0f, 0f, W, H, 0x33A855F7, false)
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

    private fun drawEdgeMarkers(c: Canvas, pl: Fighter) {}

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
