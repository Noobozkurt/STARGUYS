package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.angleDiff
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.len
import com.starguys.battle.core.sinf
import kotlin.math.abs
import com.starguys.battle.core.atan2

class Vehicle(m: Match, val kind: Int, px: Float, py: Float) : Body(m) {
    companion object {
        const val BUMPER = 0; const val DUCK = 1; const val UNICYCLE = 2; const val CRANE = 3; const val BALLOON = 4; const val TANK = 5; const val COASTER = 6
        val NAMES = arrayOf("Auto-tamponneuse", "Canard gonflable", "Monocycle à fusée", "Grue de chantier", "Montgolfière", "Char d'assaut jouet", "Wagon de montagnes russes")
    }

    var driver: Fighter? = null
    var hp = 0f; var maxHp = 0f
    var heading = 0f
    var held: Fighter? = null
    var turret = 0f
    var path: FloatArray? = null
    var pathPos = 0f
    var flash = 0f
    var t = 0f
    private val bumpT = HashMap<Body, Float>()
    var idleT = 0f

    init {
        x = px; y = py; z = maxOf(0f, m.map.groundAt(px, py)); team = TEAM_NEUTRAL
        radius = 30f; mass = 3f; bodyH = 50f
        hp = when (kind) { TANK -> 6500f; BALLOON -> 2600f; CRANE -> 4500f; COASTER -> 9999f; else -> 3200f }
        maxHp = hp
        if (kind == BALLOON) flying = true
        if (kind == COASTER) { flying = true; radius = 32f }
    }

    val allowsShooting get() = kind == BUMPER || kind == DUCK || kind == UNICYCLE || kind == BALLOON
    val protectsDriver get() = kind == TANK || kind == CRANE || kind == COASTER || kind == BALLOON
    val showsDriver get() = false
    val seatZ get() = when (kind) { BALLOON -> 60f; TANK -> 30f; CRANE -> 40f; else -> 14f }
    override val hittable: Boolean get() = alive && kind != COASTER
    override val solid: Boolean get() = true

    fun fireCd() = if (kind == TANK) 1.4f else 0.5f
    fun clawX() = x + cosf(heading) * 70f
    fun clawY() = y + sinf(heading) * 70f

    fun fire(f: Fighter, a: Float, d: Float) {
        when (kind) {
            TANK -> {
                turret = a
                Fire.lob(this, a, d, 7.5f, 1400f, 1.3f, PV.SHELL, 0xFF5D6B3A.toInt(), 0.55f) { owner = f; team = f.team; knock = 600f; tileDmg = 2f }
                m.sound(Sfx.EXPLODE, x, y, 0.5f, 1.4f); m.cam.shakeAt(x, y, 2f)
                vx -= cosf(a) * 60f; vy -= sinf(a) * 60f
            }
            CRANE -> {
                val h = held
                if (h != null) {
                    held = null; h.heldBy = null
                    h.vx = cosf(heading) * 900f; h.vy = sinf(heading) * 900f; h.vz = 420f; h.ragdollT = 0.8f
                    h.hurt(500f, f, 0f, 0f)
                    m.sound(Sfx.SWING, x, y)
                } else {
                    var best: Fighter? = null; var bd = 2.2f * T
                    for (o in m.fighters) {
                        if (o.out || o === f || !m.isEnemy(f.team, o) || o.vehicle != null) continue
                        val dd = dist(clawX(), clawY(), o.x, o.y)
                        if (dd < bd) { bd = dd; best = o }
                    }
                    val b = best
                    if (b != null) { held = b; b.heldBy = this; m.sound(Sfx.THUD, x, y); m.fx.text(b.x, b.y, Fx.GND + 80f, "ATTRAPÉ !", 0xFFFFE14D.toInt()) }
                }
            }
        }
    }

    override fun update(dt: Float) {
        t += dt
        if (flash > 0) flash -= dt
        val d = driver
        if (d != null && d.out) { m.exitVehicle(d) }
        val h = held
        if (h != null && (h.out || driver == null)) { h.heldBy = null; held = null }
        if (h != null) h.stunT = maxOf(h.stunT, 0.1f)
        var mx = d?.moveX ?: 0f; var my = d?.moveY ?: 0f
        if (d != null && m.mode.mirrorActive(d)) { mx = -mx; my = -my }
        val input = len(mx, my) > 0.1f
        idleT = if (d == null) idleT + dt else 0f
        when (kind) {
            COASTER -> { followPath(dt); ram(dt, 650f, 1300f); return }
            BALLOON -> {
                val targetZ = if (d != null) 170f else 0f
                z += (targetZ - z) * minOf(1f, dt * 0.8f)
                gz = 0f
                drive(dt, mx, my, 150f, 1.5f)
                x = clamp(x, 40f, m.map.pxW - 40f); y = clamp(y, 40f, m.map.pxH - 40f)
                x += vx * dt; y += vy * dt
                return
            }
            UNICYCLE -> {
                if (input) {
                    val want = atan2(my, mx)
                    heading += clamp(angleDiff(heading, want), -2.6f * dt, 2.6f * dt)
                }
                heading += sinf(t * 7f) * 0.45f * dt
                val target = if (input) 580f else 0f
                val sp = len(vx, vy)
                val nsp = sp + clamp(target - sp, -200f * dt, 420f * dt)
                vx = cosf(heading) * nsp; vy = sinf(heading) * nsp
                if (input && com.starguys.battle.core.chance(0.5f)) m.fx.smoke(x - cosf(heading) * 30f, y - sinf(heading) * 30f, Fx.GND, 1, 0xFFFF9A3D.toInt())
            }
            DUCK -> {
                val water = m.map.t(tileX(), tileY()) == Tl.WATER
                drive(dt, mx, my, if (water) 340f else 75f, if (water) 4f else 8f)
            }
            BUMPER -> drive(dt, mx, my, 360f, 3.2f)
            CRANE -> drive(dt, mx, my, 150f, 5f)
            TANK -> { drive(dt, mx, my, 140f, 5f); if (d != null) turret += clamp(angleDiff(turret, d.face), -3f * dt, 3f * dt) }
        }
        if (input && kind != UNICYCLE) heading = atan2(vy, vx)
        val fast = len(vx, vy)
        moveCollide(dt)
        if (hitWall && kind == UNICYCLE && fast > 380f && d != null) {
            m.exitVehicle(d); d.ragdollT = 1f; d.vz = 400f; d.vx = -cosf(heading) * 300f; d.vy = -sinf(heading) * 300f
            m.fx.text(x, y, Fx.GND + 80f, "OUPS !", 0xFFFF9A3D.toInt()); m.sound(Sfx.BOING, x, y)
            vx = 0f; vy = 0f
        }
        if (hitWall && kind == BUMPER) { val dot = vx * wallNx + vy * wallNy; vx -= 1.6f * dot * wallNx; vy -= 1.6f * dot * wallNy }
        physicsZ(dt)
        if (kind == BUMPER || kind == UNICYCLE) ram(dt, if (kind == BUMPER) 180f else 350f, if (kind == BUMPER) 950f else 700f)
        if (kind == TANK) ram(dt, 150f, 500f)
    }

    private fun drive(dt: Float, mx: Float, my: Float, maxSp: Float, accel: Float) {
        val k = minOf(1f, accel * dt)
        vx += (mx * maxSp - vx) * k; vy += (my * maxSp - vy) * k
    }

    private fun ram(dt: Float, dmg: Float, kn: Float) {
        val sp = len(vx, vy)
        if (sp < 120f && kind != COASTER) return
        val dTeam = driver?.team ?: TEAM_NEUTRAL
        for (b in m.movers) {
            if (b === this || !b.alive || b === driver) continue
            if (b is Fighter && (b.out || b.vehicle != null)) continue
            if (dist(x, y, b.x, b.y) > radius + b.radius + 4f) continue
            if (abs(b.z - z) > 60f && kind != COASTER) continue
            val last = bumpT[b] ?: -9f
            if (m.time - last < 0.5f) continue
            bumpT[b] = m.time
            val a = ang(x, y, b.x, b.y)
            val dir = if (sp > 1f) atan2(vy, vx) else a
            b.knock(cosf((a + dir) / 2f) * kn, sinf((a + dir) / 2f) * kn, 200f)
            if (b is Actor && (driver == null || m.isEnemy(dTeam, b))) { b.hurt(dmg, driver ?: m.envBody, 0f, 0f); b.ragdollT = maxOf(b.ragdollT, 0.4f) }
            if (b is Vehicle) { b.vx += cosf(a) * kn * 0.5f; b.vy += sinf(a) * kn * 0.5f }
            m.sound(Sfx.BOING, x, y, 0.7f)
        }
    }

    private fun followPath(dt: Float) {
        val p = path ?: return
        val n = p.size / 2
        if (n < 2) return
        val running = driver != null
        val speed = if (running) 430f else 0f
        var remaining = speed * dt
        while (remaining > 0f) {
            val i = pathPos.toInt() % n; val j = (i + 1) % n
            val ax = p[i * 2]; val ay = p[i * 2 + 1]; val bx = p[j * 2]; val by = p[j * 2 + 1]
            val seg = dist(ax, ay, bx, by).coerceAtLeast(1f)
            val frac = pathPos - pathPos.toInt()
            val left = seg * (1f - frac)
            if (remaining < left) { pathPos += remaining / seg; remaining = 0f } else { pathPos = (pathPos.toInt() + 1).toFloat() % n; remaining -= left }
        }
        val i = pathPos.toInt() % n; val j = (i + 1) % n
        val frac = pathPos - pathPos.toInt()
        val nx = p[i * 2] + (p[j * 2] - p[i * 2]) * frac; val ny = p[i * 2 + 1] + (p[j * 2 + 1] - p[i * 2 + 1]) * frac
        vx = (nx - x) / dt; vy = (ny - y) / dt
        if (len(nx - x, ny - y) > 0.5f) heading = atan2(ny - y, nx - x)
        x = nx; y = ny; z = 0f; gz = 0f
    }

    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        if (!alive || kind == COASTER) return false
        val atk = (src as? Fighter) ?: src?.ownerF
        val dr = driver
        if (dr != null && atk != null && !m.mode.canHurt(atk, dr)) return false
        if (dr == null && atk == null) return false
        hp -= d * (if (kind == TANK) 0.6f else 1f); flash = 0.1f
        if (atk != null && dr != null) atk.onDealt(d, dr)
        m.fx.number(x, y, z + 70f, d, 0xFFFFD23F.toInt())
        vx += kx * 0.2f / mass; vy += ky * 0.2f / mass
        if (hp <= 0f) destroy()
        return true
    }

    fun destroy() {
        alive = false
        val d = driver
        if (d != null) { m.exitVehicle(d); d.vz = 400f; d.ragdollT = 0.9f; d.hurt(400f, m.envBody, 0f, 0f) }
        held?.let { it.heldBy = null }; held = null
        m.explode(x, y, 1.4f * T, 600f, m.envBody, TEAM_NEUTRAL, 600f, tiles = false)
        m.fx.debris(x, y, Fx.GND + 30f, 0xFF8A93A8.toInt())
    }

    override fun onFallOut() {
        val d = driver
        if (d != null) { m.exitVehicle(d) }
        alive = false
    }

    override val sortY: Float get() = y

    override fun draw(c: Canvas) {
        if (kind != BALLOON) shadow(c, radius * 1.1f) else G.oval(c, x, y + 4f, 34f, 13f, 0x33000000, false)
        val sy = y - z * ZS
        val d = driver
        val lx = cosf(heading)
        val flip = lx < 0f
        G.flash = if (flash > 0) 0.5f else 0f
        G.ow = 3f
        c.save(); c.translate(x, sy)
        if (flip) c.scale(-1f, 1f)
        val col = d?.color ?: 0xFFB0B8C8.toInt()
        when (kind) {
            BUMPER -> {
                G.rrect(c, -34f, -26f, 34f, 0f, 14f, 0xFF3A3A44.toInt())
                G.rrect(c, -30f, -40f, 30f, -12f, 14f, 0xFFFF5E7E.toInt())
                G.line(c, -6f, -40f, -6f, -90f, 2f, G.INK); G.star(c, -6f, -92f, 6f, 3f, 5, t * 3, 0xFFFFE14D.toInt())
                drawDriver(c, d, 0f, -22f, 0.75f)
                G.rrect(c, -30f, -20f, 30f, -8f, 6f, 0xFFFF8CA8.toInt())
            }
            DUCK -> {
                val bob = sinf(t * 3f) * 2f
                G.oval(c, 0f, -16f + bob, 36f, 18f, 0xFFFFE14D.toInt())
                drawDriver(c, d, -4f, -18f + bob, 0.75f)
                G.circle(c, 28f, -40f + bob, 14f, 0xFFFFE14D.toInt())
                G.poly(c, 0xFFFF9A3D.toInt(), true, 38f, -42f + bob, 52f, -38f + bob, 38f, -34f + bob)
                G.circle(c, 31f, -44f + bob, 3f, G.INK, false)
                G.oval(c, 0f, -8f + bob, 30f, 6f, 0xFFF5C83A.toInt(), false)
            }
            UNICYCLE -> {
                c.rotate(sinf(t * 7f) * 8f, 0f, 0f)
                G.circle(c, 0f, -18f, 18f, 0xFF3A3A44.toInt()); G.circle(c, 0f, -18f, 6f, 0xFFB0B8C8.toInt())
                G.line(c, 0f, -18f, 0f, -44f, 5f, 0xFFB0B8C8.toInt())
                G.rrect(c, -26f, -40f, -8f, -30f, 4f, 0xFFE84545.toInt())
                G.poly(c, 0xFFFF9A3D.toInt(), false, -26f, -40f, -40f, -35f, -26f, -30f)
                drawDriver(c, d, 0f, -44f, 0.8f)
            }
            CRANE -> {
                G.rrect(c, -36f, -30f, 30f, 0f, 6f, 0xFFFFC23D.toInt())
                G.circle(c, -22f, -2f, 10f, 0xFF3A3A44.toInt()); G.circle(c, 18f, -2f, 10f, 0xFF3A3A44.toInt())
                G.rect(c, -30f, -60f, -6f, -28f, 0xFFFFC23D.toInt()); G.rect(c, -26f, -56f, -10f, -40f, 0xFFBFE8FF.toInt())
                G.line(c, -8f, -56f, 64f, -96f, 7f, 0xFFC48A12.toInt())
                G.line(c, 64f, -96f, 70f, -60f, 2f, G.INK)
                val h = held
                G.line(c, 60f, -54f, 70f, -60f, 3f, G.INK); G.line(c, 80f, -54f, 70f, -60f, 3f, G.INK)
                if (h != null) { c.save(); c.translate(70f, -40f); c.scale(0.8f, 0.8f); HeroArt.draw(c, h.hero.id, h.color, t, false, 0.5f, 0f, false, true); c.restore() }
                drawDriver(c, d, -18f, -36f, 0.5f)
            }
            BALLOON -> {
                val bob = sinf(t * 1.5f) * 4f
                G.line(c, -16f, -40f + bob, -24f, -110f + bob, 2f, G.INK); G.line(c, 16f, -40f + bob, 24f, -110f + bob, 2f, G.INK)
                G.oval(c, 0f, -150f + bob, 48f, 56f, 0xFFFF5E7E.toInt())
                G.oval(c, 0f, -150f + bob, 18f, 56f, 0xFFFFE14D.toInt(), false)
                G.ring(c, 0f, -150f + bob, 0f, 0f, 0)
                drawDriver(c, d, 0f, -46f + bob, 0.7f)
                G.rrect(c, -22f, -40f + bob, 22f, -14f + bob, 4f, 0xFFC28A4E.toInt())
            }
            TANK -> {
                G.rrect(c, -40f, -24f, 40f, 0f, 10f, 0xFF3A3A44.toInt())
                for (k in 0..4) G.circle(c, -30f + k * 15f, -10f, 6f, 0xFF6A6A78.toInt(), false)
                G.rrect(c, -34f, -42f, 34f, -20f, 8f, 0xFF7C8C4A.toInt())
                c.save(); c.translate(0f, -46f)
                val ta = if (flip) 3.14159f - turret else turret
                c.rotate(Math.toDegrees(ta.toDouble()).toFloat() * 0.35f)
                G.rect(c, 0f, -5f, 44f, 5f, 0xFF5D6B3A.toInt())
                c.restore()
                G.circle(c, 0f, -46f, 18f, 0xFF8C9C5A.toInt())
                G.star(c, -20f, -30f, 6f, 2.5f, 5, 0f, 0xFFFFE14D.toInt(), false)
                drawDriver(c, d, 0f, -58f, 0.45f)
            }
            COASTER -> {
                G.rrect(c, -34f, -36f, 34f, -4f, 10f, 0xFFE84545.toInt())
                G.rect(c, -34f, -24f, 34f, -18f, 0xFFFFE14D.toInt(), false)
                G.circle(c, -20f, -2f, 7f, 0xFF3A3A44.toInt()); G.circle(c, 20f, -2f, 7f, 0xFF3A3A44.toInt())
                drawDriver(c, d, 0f, -30f, 0.7f)
                G.rrect(c, -34f, -30f, 34f, -16f, 6f, 0xFFFF6B7E.toInt())
            }
        }
        c.restore()
        G.flash = 0f
        if (kind != COASTER && (d != null || hp < maxHp)) {
            val w = 70f; val top = sy - (if (kind == BALLOON) 220f else 100f)
            G.rect(c, x - w / 2, top, x + w / 2, top + 8f, 0xAA1B1530.toInt(), false)
            G.rect(c, x - w / 2, top, x - w / 2 + w * (hp / maxHp).coerceIn(0f, 1f), top + 8f, 0xFFFFD23F.toInt(), false)
            if (d != null) {
                val pl = m.player
                val dc = if (d === pl) 0xFF7CFF8A.toInt() else if (pl != null && !m.isEnemy(pl.team, d)) 0xFF6BD3FF.toInt() else 0xFFFF5E7E.toInt()
                G.text(c, d.name, x, top - 5f, 13f, dc)
                val fw = (d.hp / d.maxHp).coerceIn(0f, 1f)
                G.rect(c, x - w / 2, top + 10f, x - w / 2 + w * fw, top + 14f, dc, false)
            }
        }
        if (d == null) G.text(c, NAMES[kind], x, sy + 22f, 12f, 0xCCFFFFFF.toInt())
    }

    private fun drawDriver(c: Canvas, d: Fighter?, ox: Float, oy: Float, s: Float) {
        if (d == null) return
        c.save(); c.translate(ox, oy); c.scale(s, s)
        G.flash = if (d.hitFlash > 0) 0.6f else 0f
        HeroArt.draw(c, d.hero.id, d.color, d.animT, false, 0.6f, 0.1f)
        G.flash = 0f
        c.restore()
    }
}
