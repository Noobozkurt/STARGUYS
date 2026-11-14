package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.pickOrNull
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.chance
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.len
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.core.sinf
import kotlin.math.abs

/** Follows an A* path toward a goal; recomputes when needed. Shared by bots and mobs. */
class PathFollower(val m: Match) {
    var path: IntArray? = null
    var idx = 0
    var goalTx = -999; var goalTy = -999
    var recalc = 0f
    var dirX = 0f; var dirY = 0f
    var needJump = false

    fun steer(b: Body, gx: Float, gy: Float, dt: Float): Boolean {
        recalc -= dt
        needJump = false
        val gtx = (gx / T).floorInt(); val gty = (gy / T).floorInt()
        val direct = m.losWalk(b.x, b.y, gx, gy, b.z)
        if (direct) {
            val d = dist(b.x, b.y, gx, gy)
            if (d < 6f) { dirX = 0f; dirY = 0f; return true }
            dirX = (gx - b.x) / d; dirY = (gy - b.y) / d
            path = null
            checkJump(b)
            return d < 20f
        }
        if (path == null || recalc <= 0f || gtx != goalTx || gty != goalTy) {
            path = m.nav.find(b.tileX(), b.tileY(), gtx, gty, 1800)
            idx = 0; goalTx = gtx; goalTy = gty; recalc = 1.2f + rf() * 0.6f
        }
        val p = path
        if (p == null || idx * 2 >= p.size) {
            val d = dist(b.x, b.y, gx, gy).coerceAtLeast(1f)
            dirX = (gx - b.x) / d; dirY = (gy - b.y) / d
            checkJump(b)
            return false
        }
        val wx = p[idx * 2] * T + T / 2; val wy = p[idx * 2 + 1] * T + T / 2
        val d = dist(b.x, b.y, wx, wy)
        if (d < T * 0.45f) { idx++; return false }
        // skip ahead if next waypoint is directly reachable
        if (idx * 2 + 3 < p.size) {
            val nx = p[idx * 2 + 2] * T + T / 2; val ny = p[idx * 2 + 3] * T + T / 2
            if (m.losWalk(b.x, b.y, nx, ny, b.z)) idx++
        }
        dirX = (wx - b.x) / d.coerceAtLeast(1f); dirY = (wy - b.y) / d.coerceAtLeast(1f)
        checkJump(b)
        return false
    }

    private fun checkJump(b: Body) {
        val ax = b.x + dirX * (b.radius + 14f); val ay = b.y + dirY * (b.radius + 14f)
        val tz = m.map.groundAt(ax, ay)
        val tp = m.map.tAt(ax, ay)
        if (tz > b.z + STEP && tz <= b.z + LH + 6f) needJump = true
        if (tz <= VOID_Z + 1 || tp == Tl.LAVA) needJump = true
    }
}

open class Mob(m: Match, val kind: Int, var size: Int = 2) : Actor(m) {
    companion object {
        const val GLUANT = 0; const val DRONE = 1; const val TRASHBOT = 2; const val GNOME = 3; const val CRAB = 4
        const val BOSS_JELLY = 10; const val BOSS_HAMMER = 11; const val BOSS_CLAW = 12; const val BOSS_CHEF = 13
        val NAMES = mapOf(GLUANT to "Gluant", DRONE to "Drone-mouche", TRASHBOT to "Robot-poubelle", GNOME to "Nain kamikaze", CRAB to "Crabe géant",
            BOSS_JELLY to "Roi Gélatine", BOSS_HAMMER to "Méga-Marteau", BOSS_CLAW to "La Pieuvre-Grue", BOSS_CHEF to "Le Chef Cuistot Géant")
    }

    var speed = 150f
    var dmg = 300f
    var cd = 1f
    var target: Body? = null
    var retarget = 0f
    var animT = rf() * 5f
    var face = 0f
    var hopT = 0f
    val pf = PathFollower(m)
    open val isBoss = false
    var worth = 1
    var hpScale = 1f
    var wanderA = rf() * 6.28f
    private var numAcc = 0f
    private var numT = 0f

    init {
        team = if (kind == CRAB) TEAM_NEUTRAL else TEAM_MOBS
        when (kind) {
            GLUANT -> { hp = floatArrayOf(600f, 1200f, 2400f)[size]; radius = floatArrayOf(14f, 20f, 28f)[size]; speed = 170f; dmg = 300f; bodyH = radius * 1.6f }
            DRONE -> { hp = 500f; radius = 16f; speed = 290f; dmg = 200f; flying = true; z = 80f; bodyH = 30f }
            TRASHBOT -> { hp = 3500f; radius = 26f; speed = 90f; dmg = 700f; bodyH = 60f; mass = 2.5f }
            GNOME -> { hp = 400f; radius = 16f; speed = 330f; dmg = 1100f; bodyH = 40f }
            CRAB -> { hp = 2200f; radius = 30f; speed = 120f; dmg = 400f; bodyH = 40f; mass = 2f }
        }
        maxHp = hp
    }

    fun scaleHp(k: Float) { hp *= k; maxHp *= k; hpScale = k }

    override fun update(dt: Float) {
        animT += dt
        tickStatus(dt)
        if (!alive) return
        cd -= dt
        retarget -= dt
        if (retarget <= 0f || target?.let { !it.alive || (it is Fighter && it.out) } == true) {
            target = m.mobTarget(this); retarget = 0.8f + rf() * 0.5f
        }
        val canMove = stunT <= 0 && bubbleT <= 0 && ragdollT <= 0
        if (canMove) think(dt) else { vx *= 0.9f; vy *= 0.9f }
        if (kind == DRONE) {
            x += vx * dt; y += vy * dt
            x = x.coerceIn(10f, m.map.pxW - 10f); y = y.coerceIn(10f, m.map.pxH - 10f)
            z = 80f + sinf(animT * 3f) * 10f; gz = maxOf(0f, m.map.groundAt(x, y).coerceAtMost(200f))
        } else {
            moveCollide(dt)
            physicsZ(dt)
        }
    }

    protected fun walkTo(tx: Float, ty: Float, dt: Float, sp: Float = speed) {
        pf.steer(this, tx, ty, dt)
        val s = sp * statusSpeedMul()
        val k = minOf(1f, 8f * dt)
        vx += (pf.dirX * s - vx) * k; vy += (pf.dirY * s - vy) * k
        if (pf.needJump && onGround) vz = 400f
        if (len(vx, vy) > 10f) face = com.starguys.battle.core.atan2(vy, vx)
    }

    open fun think(dt: Float) {
        val tg = target
        when (kind) {
            GLUANT -> {
                hopT -= dt
                if (tg != null && hopT <= 0f && onGround) {
                    pf.steer(this, tg.x, tg.y, dt)
                    vx = pf.dirX * speed * 1.6f; vy = pf.dirY * speed * 1.6f; vz = 260f
                    hopT = 0.7f + rf() * 0.3f
                } else if (onGround) { vx *= 0.85f; vy *= 0.85f }
                contact(tg, 0.8f)
            }
            DRONE -> {
                if (tg != null) {
                    val d = dist(tg)
                    val a = ang(x, y, tg.x, tg.y) + sinf(animT * 2f) * 0.9f
                    val want = if (d > 4f * T) 1f else -0.3f
                    vx += (cosf(a) * speed * want - vx) * minOf(1f, 3f * dt); vy += (sinf(a) * speed * want - vy) * minOf(1f, 3f * dt)
                    if (cd <= 0f && d < 6.5f * T) {
                        val p = Fire.bullet(this, ang(x, y, tg.x, tg.y), 600f, 6.5f, dmg, 6f, PV.DART, 0xFF6BD3FF.toInt())
                        p.z = z; cd = 1.4f + rf() * 0.4f
                    }
                } else { vx *= 0.95f; vy *= 0.95f }
            }
            TRASHBOT -> {
                if (tg != null) {
                    val d = dist(tg)
                    if (d > radius + tg.radius + 20f) walkTo(tg.x, tg.y, dt) else { vx *= 0.8f; vy *= 0.8f }
                    if (cd <= 0f && d < 1.4f * T) {
                        m.melee(this, x, y, ang(x, y, tg.x, tg.y), 1.5f * T, 1.2f, dmg, 800f, false, 0.4f, 0xFF6E8C5A.toInt()); cd = 1.6f
                    } else if (cd <= 0f && d < 6f * T && chance(0.02f)) {
                        Fire.lob(this, ang(x, y, tg.x, tg.y), d, 6f, 500f, 0.9f, PV.TRASH, 0xFF6E8C5A.toInt(), 0.8f); cd = 2f
                    }
                }
            }
            GNOME -> {
                if (tg != null) {
                    walkTo(tg.x, tg.y, dt)
                    if (dist(tg) < radius + tg.radius + 10f) { hp = 0f; die(null) }
                }
            }
            CRAB -> {
                val near = m.nearestFighter(x, y, 2.5f * T)
                if (near != null) {
                    walkTo(near.x, near.y, dt)
                    if (cd <= 0f && dist(near) < radius + near.radius + 14f) {
                        near.hurt(dmg, this, cosf(face) * 400f, sinf(face) * 400f); cd = 1f; m.sound(Sfx.HIT, x, y)
                    }
                } else {
                    if (chance(dt * 0.5f)) wanderA += rf(-1.5f, 1.5f)
                    vx += (cosf(wanderA) * speed * 0.5f - vx) * minOf(1f, 3f * dt); vy += (sinf(wanderA) * speed * 0.5f - vy) * minOf(1f, 3f * dt)
                    if (hitWall) wanderA += 2f
                }
            }
        }
    }

    protected fun contact(tg: Body?, rate: Float) {
        if (tg == null || cd > 0f) return
        if (dist(tg) < radius + tg.radius + 6f && abs(tg.z - z) < 50f) {
            val a = ang(x, y, tg.x, tg.y)
            tg.hurt(dmg, this, cosf(a) * 350f, sinf(a) * 350f); cd = rate
        }
    }

    override val ownerF: Fighter? get() = null

    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        if (!alive) return false
        val atk = (src as? Fighter) ?: src?.ownerF
        if (src is Mob && src.team == team) return false
        hp -= d; hitFlash = 0.1f
        if (atk != null) { atk.onDealt(d, this); lastAttacker = atk; lastAttackerT = 0f }
        numAcc += d
        if (numAcc >= 120f || d >= 60f || m.time - numT > 0.6f) { m.fx.number(x, y - z * ZS - bodyH - 6f, numAcc, 0xFFFFFFFF.toInt()); numAcc = 0f; numT = m.time }
        if (!isBoss) knock(kx * 0.6f, ky * 0.6f)
        if (hp <= 0f) die(atk)
        return true
    }

    open fun die(killer: Fighter?) {
        if (!alive) return
        alive = false
        m.fx.confetti(x, y - z * ZS - 20f, if (isBoss) 80 else 12)
        m.sound(Sfx.POP, x, y, 0.7f)
        when (kind) {
            GLUANT -> if (size > 0) for (k in 0 until 2) {
                val s = Mob(m, GLUANT, size - 1)
                s.x = x + rf(-10f, 10f); s.y = y + rf(-10f, 10f); s.z = z
                val a = rf() * 6.28f
                s.vx = cosf(a) * 300f; s.vy = sinf(a) * 300f; s.vz = 300f
                s.scaleHp(hpScale)
                m.addMob(s)
            }
            GNOME -> m.explode(x, y, 1.3f * T, dmg, this, TEAM_MOBS, 800f, color = 0xFFFF7A3D.toInt())
        }
        m.onMobDeath(this, killer)
    }

    override fun onFallOut() { hp = 0f; die(lastAttacker) }

    override fun draw(c: Canvas) {
        shadow(c)
        val sy = y - z * ZS
        c.save(); c.translate(x, sy)
        if (cosf(face) < 0) c.scale(-1f, 1f)
        if (ragdollT > 0) c.rotate((spin * 57f) % 360f, 0f, -radius)
        G.flash = if (hitFlash > 0) 0.6f else 0f
        G.ow = 3f
        drawBody(c)
        G.flash = 0f
        c.restore()
        drawStatusFx(c, sy - bodyH - 6f)
        if (!isBoss && hp < maxHp) {
            val w = radius * 2f
            G.rect(c, x - w / 2, sy - bodyH - 12f, x + w / 2, sy - bodyH - 7f, 0xAA1B1530.toInt(), false)
            G.rect(c, x - w / 2, sy - bodyH - 12f, x - w / 2 + w * (hp / maxHp).coerceIn(0f, 1f), sy - bodyH - 7f, 0xFFFF5E7E.toInt(), false)
        }
    }

    open fun drawBody(c: Canvas) {
        val t = animT
        when (kind) {
            GLUANT -> {
                val r = radius
                val sq = if (!onGround) 1.15f else 1f + sinf(t * 8f) * 0.06f
                G.oval(c, 0f, -r * 0.9f, r * 1.1f / sq, r * sq, 0xCC7BE84A.toInt())
                G.circle(c, -r * 0.4f, -r * 1.3f, r * 0.25f, 0xAAFFFFFF.toInt(), false)
                G.eyes(c, r * 0.15f, -r, r * 0.32f, r * 0.22f, 1f, 0f, false, angry = true)
            }
            DRONE -> {
                val w = sinf(t * 60f) * 6f
                G.oval(c, -12f, -18f, 10f, 4f + w * 0.3f, 0x88FFFFFF.toInt()); G.oval(c, 12f, -18f, 10f, 4f - w * 0.3f, 0x88FFFFFF.toInt())
                G.circle(c, 0f, -8f, 13f, 0xFF3A3A44.toInt())
                G.circle(c, 5f, -10f, 6f, 0xFFFF3D3D.toInt()); G.circle(c, 7f, -12f, 2f, 0xFFFFFFFF.toInt(), false)
                G.line(c, -10f, 2f, -14f, 10f, 2f, G.INK); G.line(c, 10f, 2f, 14f, 10f, 2f, G.INK)
            }
            TRASHBOT -> {
                val b = HeroArt.bob(t, len(vx, vy) > 20f)
                G.rect(c, -18f, -8f, -8f, 0f, 0xFF3A3A44.toInt()); G.rect(c, 8f, -8f, 18f, 0f, 0xFF3A3A44.toInt())
                G.rrect(c, -24f, -56f - b, 24f, -8f - b, 6f, 0xFF6E8C5A.toInt())
                G.rrect(c, -27f, -64f - b, 27f, -54f - b, 4f, 0xFF5A7448.toInt())
                for (k in 0..2) G.line(c, -12f + k * 12f, -50f - b, -12f + k * 12f, -14f - b, 3f, 0x44000000)
                G.rrect(c, -14f, -46f - b, 14f, -32f - b, 4f, 0xFF1E2A3A.toInt())
                G.circle(c, -6f, -39f - b, 3.5f, 0xFFFF3D3D.toInt(), false); G.circle(c, 6f, -39f - b, 3.5f, 0xFFFF3D3D.toInt(), false)
                G.line(c, -24f, -36f - b, -34f, -20f - b, 5f, 0xFF5A7448.toInt()); G.line(c, 24f, -36f - b, 34f, -20f - b, 5f, 0xFF5A7448.toInt())
            }
            GNOME -> {
                val b = HeroArt.bob(t, true)
                G.oval(c, -6f, -3f, 6f, 4f, 0xFF6B4028.toInt()); G.oval(c, 6f, -3f, 6f, 4f, 0xFF6B4028.toInt())
                G.oval(c, 0f, -16f - b, 12f, 12f, 0xFF3D7AE8.toInt())
                G.circle(c, 0f, -27f - b, 9f, HeroArt.SKIN)
                G.poly(c, 0xFFFFFFFF.toInt(), true, -9f, -26f - b, 9f, -26f - b, 0f, -12f - b)
                G.poly(c, 0xFFE84545.toInt(), true, -10f, -31f - b, 10f, -31f - b, 2f, -54f - b)
                G.circle(c, 3f, -29f - b, 2f, G.INK, false)
                if ((t * 8).toInt() % 2 == 0) G.circle(c, 14f, -40f - b, 4f, 0xFFFFE14D.toInt(), false)
                G.line(c, 10f, -20f - b, 14f, -38f - b, 2f, G.INK)
            }
            CRAB -> {
                val wk = sinf(t * 10f) * 4f
                for (k in -1..1) { G.line(c, -20f, -10f + k * 6f, -32f, -2f + k * 6f + wk, 3f, 0xFFE84545.toInt()); G.line(c, 20f, -10f + k * 6f, 32f, -2f + k * 6f - wk, 3f, 0xFFE84545.toInt()) }
                G.oval(c, 0f, -18f, 26f, 16f, 0xFFFF6B4A.toInt())
                G.circle(c, -30f, -34f, 10f, 0xFFFF6B4A.toInt()); G.circle(c, 30f, -34f, 10f, 0xFFFF6B4A.toInt())
                G.line(c, -36f, -40f, -24f, -34f, 3f, G.INK); G.line(c, 36f, -40f, 24f, -34f, 3f, G.INK)
                G.line(c, -6f, -30f, -8f, -42f, 2.5f, G.INK); G.line(c, 6f, -30f, 8f, -42f, 2.5f, G.INK)
                G.circle(c, -8f, -44f, 4f, 0xFFFFFFFF.toInt()); G.circle(c, 8f, -44f, 4f, 0xFFFFFFFF.toInt())
            }
        }
    }
}

/** The four bosses share one class with a small state machine each. */
class Boss(m: Match, kind: Int) : Mob(m, kind) {
    override val isBoss = true
    var state = 0
    var stateT = 2f
    var phase2 = false
    var tx = 0f; var ty = 0f
    var clawX = 0f; var clawY = 0f; var clawZ = 220f
    var grabbed: Fighter? = null
    var spinA = 0f
    val name = NAMES[kind] ?: "Boss"

    init {
        team = TEAM_MOBS
        radius = 62f; mass = 20f; bodyH = 150f; speed = 110f
        hp = when (kind) { BOSS_JELLY -> 60000f; BOSS_HAMMER -> 42000f; BOSS_CLAW -> 46000f; else -> 48000f }
        maxHp = hp; worth = 20
        if (kind == BOSS_CLAW) { radius = 58f }
    }

    override fun think(dt: Float) {
        val tg = target
        stateT -= dt
        if (!phase2 && hp < maxHp * 0.5f) { phase2 = true; m.fx.text(x, y - 200f, "PHASE 2 !", 0xFFFF3D3D.toInt()); m.sound(Sfx.ALARM, x, y); m.cam.shake(8f) }
        val sp = if (phase2) 1.35f else 1f
        when (kind) {
            BOSS_JELLY -> jelly(tg, dt, sp)
            BOSS_HAMMER -> hammer(tg, dt, sp)
            BOSS_CLAW -> claw(tg, dt, sp)
            BOSS_CHEF -> chef(tg, dt, sp)
        }
    }

    private fun next(s: Int, t: Float) { state = s; stateT = t }

    private fun idleWalk(tg: Body?, dt: Float, keep: Float) {
        if (tg == null) { vx *= 0.9f; vy *= 0.9f; return }
        if (dist(tg) > keep) walkTo(tg.x, tg.y, dt) else { vx *= 0.9f; vy *= 0.9f; face = ang(x, y, tg.x, tg.y) }
    }

    private fun jelly(tg: Body?, dt: Float, sp: Float) {
        when (state) {
            0 -> { idleWalk(tg, dt, 3f * T); if (stateT <= 0f) { val r = ri(3); if (r == 0 && tg != null) { tx = tg.x; ty = tg.y; next(1, 0.6f) } else if (r == 1) next(3, 0.4f) else next(4, 0.5f) } }
            1 -> { vx *= 0.8f; vy *= 0.8f; if (stateT <= 0f) { // jump
                val flight = 0.9f
                vx = (tx - x) / flight; vy = (ty - y) / flight; vz = 0.5f * GRAV * m.gravity * flight; next(2, 3f) } }
            2 -> if (onGround && stateT < 2.8f) { // land: shockwave
                vx = 0f; vy = 0f
                m.explode(x, y, 2.6f * T, 1300f, this, TEAM_MOBS, 1100f, tiles = false, color = 0xFF7BE84A.toInt())
                for (k in 0 until 14) { val a = k * 6.2832f / 14; Fire.bullet(this, a, 360f, 9f, 500f, 12f, PV.GEL, 0xFF7BE84A.toInt()) }
                m.cam.shake(9f); next(0, 1.6f / sp)
            }
            3 -> { if (stateT <= 0f) { for (f in m.liveTargets()) if (dist(f) < 9f * T) Fire.lob(this, ang(x, y, f.x, f.y), dist(f), 9f, 900f, 1.1f, PV.GEL, 0xFF7BE84A.toInt(), 0.9f) { r = 16f }
                next(0, 1.8f / sp) } }
            4 -> { if (stateT <= 0f) { for (k in 0 until (if (phase2) 3 else 2)) { val s = Mob(m, GLUANT, 1); s.x = x + rf(-60f, 60f); s.y = y + 70f; s.vz = 300f; m.addMob(s) }; next(0, 2f / sp) } }
        }
    }

    private fun hammer(tg: Body?, dt: Float, sp: Float) {
        when (state) {
            0 -> { idleWalk(tg, dt, 1.8f * T); if (stateT <= 0f) { val r = ri(3); next(if (r == 0) 1 else if (r == 1) 3 else 5, if (r == 2) 2.5f else 0.8f / sp) } }
            1 -> { vx *= 0.7f; vy *= 0.7f; if (tg != null) face = ang(x, y, tg.x, tg.y); if (stateT <= 0f) {
                m.melee(this, x, y, face, 3.2f * T, 1.75f, 1600f, 1300f, false, 0.6f, 0xFFFFE14D.toInt()); m.cam.shake(6f); next(0, 1.2f / sp) } }
            3 -> { if (stateT <= 0f) { vz = 650f; next(4, 2f) } }
            4 -> if (onGround && stateT < 1.8f) {
                m.explode(x, y, 2f * T, 1200f, this, TEAM_MOBS, 1000f, tiles = false)
                val n = if (phase2) 8 else 4
                for (k in 0 until n) { val a = k * 6.2832f / n + spinA; Fire.bullet(this, a, 480f, 8f, 700f, 18f, PV.ROCK, 0xFF8A8494.toInt()) { pierce = true } }
                spinA += 0.4f; m.cam.shake(8f); next(0, 1.4f / sp)
            }
            5 -> { // spin toward target
                spinA += dt * 18f
                if (tg != null) { val a = ang(x, y, tg.x, tg.y); vx += (cosf(a) * 260f * sp - vx) * minOf(1f, 2f * dt); vy += (sinf(a) * 260f * sp - vy) * minOf(1f, 2f * dt) }
                for (b in m.liveTargets()) if (dist(b) < radius + b.radius + 30f && (b as? Actor)?.ragdollT ?: 0f <= 0f) {
                    val a = ang(x, y, b.x, b.y); b.hurt(700f, this, cosf(a) * 1200f, sinf(a) * 1200f); (b as? Actor)?.ragdollT = 0.6f
                }
                if (stateT <= 0f) next(0, 1.5f / sp)
            }
        }
    }

    private fun claw(tg: Body?, dt: Float, sp: Float) {
        val g = grabbed
        if (g != null) { g.x = clawX; g.y = clawY; g.z = clawZ - 60f; g.stunT = 0.2f; g.vx = 0f; g.vy = 0f }
        when (state) {
            0 -> { idleWalk(tg, dt, 4f * T); clawX += (x - clawX) * dt * 2f; clawY += (y + 40f - clawY) * dt * 2f; clawZ = 220f
                if (stateT <= 0f) { val r = ri(3); if (r == 0 && tg != null) { tx = tg.x; ty = tg.y; next(1, 1.2f / sp) } else if (r == 1) next(4, 3f) else next(5, 0.5f) } }
            1 -> { // claw travels over target
                vx *= 0.8f; vy *= 0.8f
                if (tg != null) { tx += (tg.x - tx) * dt * 1.5f; ty += (tg.y - ty) * dt * 1.5f }
                clawX += (tx - clawX) * dt * 4f; clawY += (ty - clawY) * dt * 4f
                if (stateT <= 0f) next(2, 0.35f)
            }
            2 -> { clawZ = 220f * (stateT / 0.35f); if (stateT <= 0f) {
                clawZ = 40f
                var best: Fighter? = null
                for (f in m.fighters) if (!f.out && dist(clawX, clawY, f.x, f.y) < 50f) best = f
                if (best != null) { grabbed = best; best.hurt(600f, this, 0f, 0f); m.fx.text(best.x, best.y - 90f, "ATTRAPÉ !", 0xFFFF5E7E.toInt()) }
                m.sound(Sfx.THUD, clawX, clawY); next(3, 1.5f) } }
            3 -> { clawZ += (260f - clawZ) * dt * 3f
                if (stateT <= 0f) { val gg = grabbed; if (gg != null) { gg.vx = rf(-500f, 500f); gg.vy = rf(-500f, 500f); gg.vz = 200f; gg.ragdollT = 0.8f; gg.hurt(700f, this, 0f, 0f) }; grabbed = null; next(0, 1.2f / sp) } }
            4 -> { spinA += dt * 2.6f * sp
                for (k in 0 until 4) { val a = spinA + k * 1.5708f; for (b in m.liveTargets()) {
                    val d = dist(b); if (d < 3.4f * T && d > radius && abs(com.starguys.battle.core.angleDiff(a, ang(x, y, b.x, b.y))) * d < 34f && (b as? Actor)?.ragdollT ?: 0f <= 0f) {
                        b.hurt(650f, this, cosf(a + 1.57f) * 1000f, sinf(a + 1.57f) * 1000f); (b as? Actor)?.ragdollT = 0.5f } } }
                if (stateT <= 0f) next(0, 1f) }
            5 -> { if (stateT <= 0f) { val n = if (phase2) 9 else 6
                for (k in 0 until n) { val tgt = m.liveTargets().pickOrNull(); val px = (tgt?.x ?: x) + rf(-2f * T, 2f * T); val py = (tgt?.y ?: y) + rf(-2f * T, 2f * T)
                    m.addZone(Zone(m, Zone.METEOR, px, py, 0.9f * T, 1.4f + rf() * 0.6f, this, TEAM_MOBS)) }
                next(0, 2.5f / sp) } }
        }
    }

    private fun chef(tg: Body?, dt: Float, sp: Float) {
        when (state) {
            0 -> { idleWalk(tg, dt, 4.5f * T); if (stateT <= 0f) next(1 + ri(3), 0.6f) }
            1 -> if (stateT <= 0f) { // pans (boomerangs)
                for (f in m.liveTargets().take(3)) Fire.bullet(this, ang(x, y, f.x, f.y), 650f, 7f, 800f, 20f, PV.PAN, 0xFF4A4A55.toInt()) { boomerang = true; life = 5f; maxTravel = 7f * T; z = 60f }
                next(0, 1.6f / sp)
            }
            2 -> if (stateT <= 0f) { // pots: lob + fire
                for (f in m.liveTargets()) if (dist(f) < 9f * T) Fire.lob(this, ang(x, y, f.x, f.y), dist(f), 9f, 900f, 1.2f, PV.POT, 0xFFB0B8C8.toInt(), 1f) {
                    r = 16f; onLand = { p -> m.addZone(Zone(m, Zone.FIRE, p.x, p.y, 1.1f * T, 4f, this@Boss, TEAM_MOBS)) } }
                next(0, 1.8f / sp)
            }
            3 -> { // plate barrage (2 waves)
                if (stateT <= 0f) {
                    val n = if (phase2) 20 else 14
                    for (k in 0 until n) Fire.bullet(this, k * 6.2832f / n + spinA, 420f, 9f, 450f, 14f, PV.BULLET, 0xFFF4F7FF.toInt()) { z = 50f }
                    spinA += 0.15f
                    if (spinA % 0.3f < 0.16f) next(3, 0.35f) else next(0, 1.6f / sp)
                }
            }
        }
    }

    override fun die(killer: Fighter?) {
        grabbed?.let { it.stunT = 0f }
        grabbed = null
        m.explode(x, y, 3f * T, 0f, m.envBody, TEAM_NEUTRAL, 0f)
        super.die(killer)
    }

    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        return super.hurt(d, src, kx * 0.05f, ky * 0.05f)
    }

    override fun applyStatus(s: Int, t: Float, src: Body?) {
        if (s == St.STUN || s == St.BUBBLE || s == St.SLIP) return
        super.applyStatus(s, t * 0.5f, src)
    }

    override fun drawBody(c: Canvas) {
        val t = animT
        when (kind) {
            BOSS_JELLY -> {
                val sq = 1f + sinf(t * 4f) * 0.05f
                // mechanical base
                G.rrect(c, -70f, -30f, 70f, 0f, 12f, 0xFF6A6A78.toInt())
                for (k in 0..3) G.circle(c, -52f + k * 35f, -4f, 10f, 0xFF3A3A44.toInt())
                G.oval(c, 0f, -90f, 74f / sq, 70f * sq, 0xCC7BE84A.toInt())
                if (!phase2) {
                    G.rrect(c, -60f, -130f, 60f, -110f, 8f, 0xFFB0B8C8.toInt())
                    G.rect(c, -6f, -150f, 6f, -128f, 0xFF8A93A8.toInt()); G.circle(c, 0f, -156f, 10f, 0xFFFF3D3D.toInt())
                }
                G.poly(c, 0xFFFFD23F.toInt(), true, -40f, -150f, -30f, -180f, -14f, -158f, 0f, -186f, 14f, -158f, 30f, -180f, 40f, -150f)
                G.circle(c, -30f, -110f, 12f, 0x88FFFFFF.toInt(), false)
                G.eyes(c, 6f, -100f, 22f, 13f, 1f, 0.2f, false, angry = true)
                G.rrect(c, -20f, -70f, 30f, -58f, 6f, 0xFF2A4A1A.toInt())
            }
            BOSS_HAMMER -> {
                c.save(); if (state == 5) c.rotate((spinA * 57f) % 360f, 0f, -70f)
                G.rect(c, -10f, -110f, 10f, 0f, 0xFF8B5A3C.toInt())
                G.rrect(c, -70f, -170f, 70f, -100f, 14f, 0xFF8A93A8.toInt())
                G.rrect(c, -70f, -170f, -50f, -100f, 6f, 0xFFB0B8C8.toInt()); G.rrect(c, 50f, -170f, 70f, -100f, 6f, 0xFFB0B8C8.toInt())
                G.eyes(c, 6f, -140f, 18f, 11f, 1f, 0.1f, false, angry = true)
                G.rect(c, -20f, -118f, 30f, -110f, G.INK, false)
                c.restore()
                G.line(c, -10f, -40f, -36f, -10f, 6f, 0xFF8B5A3C.toInt()); G.line(c, 10f, -40f, 36f, -10f, 6f, 0xFF8B5A3C.toInt())
                if (state == 1) G.arc(c, 0f, -40f, 3.2f * T, -100f, 200f, 8f, 0x88FFE14D.toInt())
            }
            BOSS_CLAW -> {
                for (k in 0 until 6) { val a = k * 1.047f + sinf(t * 2 + k) * 0.2f + (if (state == 4) spinA else 0f)
                    G.line(c, 0f, -40f, cosf(a) * 80f, -30f + sinf(a) * 36f, 14f, 0xFFB46CFF.toInt()); G.circle(c, cosf(a) * 80f, -30f + sinf(a) * 36f, 9f, 0xFFD8A8FF.toInt()) }
                G.rrect(c, -56f, -160f, 56f, -40f, 12f, 0xFFFF5EC4.toInt())
                G.rect(c, -46f, -150f, 46f, -70f, 0x88BFE8FF.toInt())
                G.circle(c, -20f, -84f, 10f, 0xFFFFE14D.toInt()); G.circle(c, 14f, -90f, 10f, 0xFF6BD3FF.toInt()); G.circle(c, 0f, -80f, 9f, 0xFF7CFF8A.toInt())
                G.eyes(c, 0f, -125f, 20f, 12f, 1f, 0.2f, false, angry = true)
                G.rect(c, -60f, -172f, 60f, -158f, 0xFFFFD23F.toInt())
            }
            BOSS_CHEF -> {
                val b = HeroArt.bob(t, len(vx, vy) > 20f) * 2f
                G.oval(c, -24f, -6f, 18f, 10f, 0xFF3A3A44.toInt()); G.oval(c, 24f, -6f, 18f, 10f, 0xFF3A3A44.toInt())
                G.rrect(c, -50f, -120f - b, 50f, -10f - b, 24f, 0xFFFFFFFF.toInt())
                G.rect(c, -4f, -110f - b, 4f, -20f - b, 0xFFE0E6F0.toInt(), false)
                for (k in 0..2) G.circle(c, -16f, -96f - b + k * 26f, 4f, G.INK, false)
                G.circle(c, 0f, -150f - b, 36f, HeroArt.SKIN)
                G.oval(c, 0f, -132f - b, 26f, 8f, 0xFF6B4028.toInt())
                G.rrect(c, -34f, -224f - b, 34f, -180f - b, 10f, 0xFFFFFFFF.toInt())
                G.circle(c, -22f, -222f - b, 18f, 0xFFFFFFFF.toInt()); G.circle(c, 22f, -222f - b, 18f, 0xFFFFFFFF.toInt()); G.circle(c, 0f, -232f - b, 20f, 0xFFFFFFFF.toInt())
                G.eyes(c, 6f, -156f - b, 12f, 8f, 1f, 0.1f, false, angry = true)
                G.line(c, 50f, -90f - b, 80f, -120f - b, 10f, 0xFFFFFFFF.toInt())
                G.circle(c, 92f, -128f - b, 18f, 0xFF4A4A55.toInt())
            }
        }
    }

    override fun draw(c: Canvas) {
        super.draw(c)
        if (kind == BOSS_CLAW) {
            val sy = clawY - clawZ * ZS
            G.ow = 3f
            G.oval(c, clawX, clawY, 30f, 12f, if (state == 1) 0x55FF3D3D else 0x33000000, false)
            G.line(c, clawX, sy - 300f, clawX, sy - 20f, 3f, G.INK)
            G.circle(c, clawX, sy - 20f, 12f, 0xFFB0B8C8.toInt())
            G.line(c, clawX - 4f, sy - 16f, clawX - 22f, sy + 18f, 5f, 0xFFB0B8C8.toInt()); G.line(c, clawX + 4f, sy - 16f, clawX + 22f, sy + 18f, 5f, 0xFFB0B8C8.toInt())
        }
    }
}
