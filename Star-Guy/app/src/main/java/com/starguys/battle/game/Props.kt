package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.core.sinf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

abstract class Prop(m: Match, px: Float, py: Float) : Body(m) {
    open val solidR: Float get() = 0f
    open val blocksShots: Boolean get() = solidR > 0f
    open fun interactLabel(f: Fighter): String? = null
    open fun interact(f: Fighter) {}
    open val interactRange: Float get() = T * 1.3f
    var t = rf() * 10f

    init { x = px; y = py; z = maxOf(0f, m.map.groundAt(px, py)) }

    override fun update(dt: Float) { t += dt }

    fun bodiesNear(r: Float, f: (Body) -> Unit) {
        for (b in m.movers) if (b.alive && abs(b.x - x) < r + b.radius && abs(b.y - y) < r + b.radius && dist(x, y, b.x, b.y) < r + b.radius) f(b)
    }
}

// ============================================================================ interactive props
class JumpPad(m: Match, px: Float, py: Float, val power: Float = 900f, var temp: Float = 0f) : Prop(m, px, py) {
    override fun update(dt: Float) {
        super.update(dt)
        if (temp > 0f) { temp -= dt; if (temp <= 0f) alive = false }
        bodiesNear(26f) { b ->
            if (b.z <= z + 6f && b.vz <= 10f && b !is Vehicle) {
                b.vz = power * (if (m.gravity < 1f) 0.75f else 1f); b.onGround = false
                if (b is Fighter) { b.hasDived = false; b.vx *= 1.2f; b.vy *= 1.2f }
                m.sound(Sfx.BOING, x, y); t = 0f
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        val sq = if (t < 0.25f) sinf(t * 25f) * 5f else 0f
        G.ow = 3f
        G.oval(c, x, sy + 4f, 30f, 13f, 0xFF3A3A44.toInt())
        G.oval(c, x, sy - sq, 26f, 11f, if (temp > 0f) 0xFF5BD3FF.toInt() else 0xFFFF5E7E.toInt())
        G.oval(c, x, sy - sq, 13f, 5f, 0xFFFFFFFF.toInt(), false)
    }
}

class Fan(m: Match, px: Float, py: Float, var dir: Float, val length: Float = 4.5f * T, val force: Float = 1700f, val cycle: Boolean = false) : Prop(m, px, py) {
    var on = true
    override val solidR: Float get() = 24f
    override fun update(dt: Float) {
        super.update(dt)
        if (cycle) on = (t % 6f) < 3.5f
        if (!on) return
        val cx = cosf(dir); val cy = sinf(dir)
        for (b in m.movers) {
            if (!b.alive) continue
            val dx = b.x - x; val dy = b.y - y
            val along = dx * cx + dy * cy
            if (along < 0f || along > length) continue
            val side = abs(-dx * cy + dy * cx)
            if (side > T * 0.9f) continue
            val k = 1f - along / length
            b.vx += cx * force * k * dt / b.mass; b.vy += cy * force * k * dt / b.mass
        }
        if (com.starguys.battle.core.chance(0.4f)) m.fx.wind(x + cx * rf(20f, length), y + cy * rf(20f, length) + rf(-30f, 30f) * abs(cx), dir)
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 3f
        G.rect(c, x - 8f, sy - 20f, x + 8f, sy, 0xFF6A6A78.toInt())
        c.save(); c.translate(x, sy - 34f)
        G.circle(c, 0f, 0f, 26f, 0xFFD8DEE8.toInt())
        c.rotate(if (on) (t * 900f) % 360f else 0f)
        for (k in 0 until 3) { c.rotate(120f); G.oval(c, 0f, -12f, 6f, 12f, 0xFF6BD3FF.toInt()) }
        c.restore()
        G.circle(c, x, sy - 34f, 5f, G.INK, false)
        G.line(c, x + cosf(dir) * 30f, sy - 34f + sinf(dir) * 14f, x + cosf(dir) * 44f, sy - 34f + sinf(dir) * 20f, 4f, 0x88FFFFFF.toInt())
    }
}

class SwingDoor(m: Match, px: Float, py: Float, val baseAng: Float, val len: Float = T * 1.6f) : Prop(m, px, py) {
    var a = baseAng; var av = 0f
    override fun update(dt: Float) {
        super.update(dt)
        a = baseAng + sinf(t * 1.4f) * 1.3f
        av = cosf(t * 1.4f) * 1.3f * 1.4f
        val ex = x + cosf(a) * len; val ey = y + sinf(a) * len
        for (b in m.movers) {
            if (!b.alive || b.z > z + 60f) continue
            val px = b.x - x; val py = b.y - y
            val lx = ex - x; val ly = ey - y
            val tt = clamp((px * lx + py * ly) / (lx * lx + ly * ly), 0f, 1f)
            val cx = x + lx * tt; val cy = y + ly * tt
            val d = dist(b.x, b.y, cx, cy)
            if (d < b.radius + 8f && d > 0.01f) {
                val nx = (b.x - cx) / d; val ny = (b.y - cy) / d
                b.x = cx + nx * (b.radius + 8f); b.y = cy + ny * (b.radius + 8f)
                val push = abs(av) * len * tt * 1.6f + 120f
                b.knock(nx * push * 0.15f, ny * push * 0.15f)
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        val ex = x + cosf(a) * len; val ey = y + sinf(a) * len
        G.ow = 3f
        G.poly(c, 0xFFC28A4E.toInt(), true, x, sy, ex, ey - z * ZS, ex, ey - z * ZS - 50f, x, sy - 50f)
        G.line(c, x, sy - 25f, ex, ey - z * ZS - 25f, 2f, 0x55000000)
        G.circle(c, x, sy - 52f, 6f, 0xFF6A6A78.toInt())
    }
}

class Cannon(m: Match, px: Float, py: Float, var tx: Float, var ty: Float) : Prop(m, px, py) {
    var cd = 0f
    override val solidR: Float get() = 0f
    override fun interactLabel(f: Fighter): String? = if (cd <= 0f && f.vehicle == null) "Canon" else null
    override fun update(dt: Float) {
        super.update(dt)
        if (cd > 0) cd -= dt
        if (cd <= 0f) for (f in m.fighters) {
            if (f.out || f.human || f.vehicle != null || f.cannonT > 0) continue
            if (dist(x, y, f.x, f.y) < 30f && f.brain?.wantsCannon == true) { launch(f); break }
        }
    }
    override fun interact(f: Fighter) { if (cd <= 0f) launch(f) }
    fun launch(f: Fighter) {
        val flight = 1.6f
        f.x = x; f.y = y; f.z = z + 20f
        val g = GRAV * 0.6f * m.gravity
        f.vx = (tx - x) / flight; f.vy = (ty - y) / flight
        val tz = maxOf(0f, m.map.groundAt(tx, ty))
        f.vz = ((tz - f.z) + 0.5f * g * flight * flight) / flight
        f.cannonT = flight + 0.5f
        f.onGround = false
        cd = 1.5f
        m.fx.smoke(x, y - 40f, 8, 0xFFFFFFFF.toInt()); m.sound(Sfx.EXPLODE, x, y, 0.6f); m.cam.shake(3f)
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        val a = ang(x, y, tx, ty)
        G.ow = 3f
        G.oval(c, x, sy, 30f, 14f, 0xFF4A4A55.toInt())
        c.save(); c.translate(x, sy - 22f)
        val dx = cosf(a); val flip = dx < 0
        if (flip) c.scale(-1f, 1f)
        c.rotate(-35f)
        G.rrect(c, -14f, -14f, 40f, 14f, 10f, 0xFFE84545.toInt())
        G.rect(c, 30f, -16f, 40f, 16f, 0xFFFFD23F.toInt())
        G.star(c, 6f, 0f, 8f, 3.5f, 5, 0f, 0xFFFFE14D.toInt())
        c.restore()
        G.circle(c, x - 18f, sy - 2f, 9f, 0xFF8B5A3C.toInt()); G.circle(c, x + 18f, sy - 2f, 9f, 0xFF8B5A3C.toInt())
    }
}

/** Explosive barrel (0), slime canister (1), fire hydrant (2), loot box (3). */
class Breakable(m: Match, px: Float, py: Float, val kind: Int) : Prop(m, px, py) {
    var hp = when (kind) { 0 -> 500f; 1 -> 450f; 2 -> 700f; else -> 900f }
    var flash = 0f
    var broken = false
    override val solidR: Float get() = if (broken) 0f else 22f
    override val hittable: Boolean get() = alive && !broken
    init { team = TEAM_NEUTRAL; radius = 22f; bodyH = 46f }

    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        if (broken) return false
        hp -= d; flash = 0.1f
        if (hp <= 0f) breakIt(src)
        return true
    }

    fun breakIt(src: Body?) {
        broken = true
        when (kind) {
            0 -> { alive = false; m.explode(x, y, 1.7f * T, 1500f, src ?: m.envBody, TEAM_NEUTRAL, 1000f, color = 0xFFFF7A3D.toInt()) }
            1 -> { alive = false; m.addZone(Zone(m, Zone.SLIME, x, y, 1.7f * T, 10f, null, TEAM_NEUTRAL)); m.fx.splat(x, y - 20f, 0xFF7BE84A.toInt()); m.sound(Sfx.SPLASH, x, y) }
            2 -> { m.addZone(Zone(m, Zone.JET, x, y, 3.5f * T, 9f, null, TEAM_NEUTRAL).also { it.dir = rf() * 6.28f }); m.sound(Sfx.SPLASH, x, y) }
            3 -> {
                alive = false
                m.fx.debris(x, y - 20f, 0xFFD9A066.toInt())
                m.dropLoot(x, y, 2)
                m.sound(Sfx.THUD, x, y)
            }
        }
    }

    override fun update(dt: Float) { super.update(dt); if (flash > 0) flash -= dt }

    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.oval(c, x, sy + 2f, 22f, 9f, 0x33000000, false)
        G.flash = if (flash > 0) 0.6f else 0f
        G.ow = 3f
        when (kind) {
            0 -> { G.rrect(c, x - 18f, sy - 46f, x + 18f, sy, 6f, 0xFFE84545.toInt()); G.rect(c, x - 18f, sy - 32f, x + 18f, sy - 26f, 0xFFFFD23F.toInt(), false); G.rect(c, x - 18f, sy - 16f, x + 18f, sy - 10f, 0xFFFFD23F.toInt(), false); G.oval(c, x, sy - 46f, 18f, 6f, 0xFFB33952.toInt()) }
            1 -> { G.rrect(c, x - 16f, sy - 48f, x + 16f, sy, 10f, 0xCC7BE84A.toInt()); G.rect(c, x - 10f, sy - 54f, x + 10f, sy - 46f, 0xFF6A6A78.toInt()); G.circle(c, x - 5f, sy - 30f, 4f, 0xFFD8FFB0.toInt(), false) }
            2 -> if (!broken) { G.rrect(c, x - 13f, sy - 40f, x + 13f, sy, 6f, 0xFFE84545.toInt()); G.circle(c, x, sy - 42f, 11f, 0xFFE84545.toInt()); G.rect(c, x - 20f, sy - 30f, x + 20f, sy - 22f, 0xFFB33952.toInt()) }
                else { G.rrect(c, x - 13f, sy - 14f, x + 13f, sy, 4f, 0xFFB33952.toInt()) }
            3 -> {
                G.rect(c, x - 20f, sy - 36f, x + 20f, sy, 0xFFD9A066.toInt())
                G.rect(c, x - 20f, sy - 42f, x + 20f, sy - 34f, 0xFFC08A55.toInt())
                G.rect(c, x - 4f, sy - 42f, x + 4f, sy, 0xFFFF5E7E.toInt(), false)
                G.star(c, x, sy - 20f, 8f, 3.5f, 5, 0f, 0xFFFFE14D.toInt())
            }
        }
        G.flash = 0f
    }
}

class Zipline(m: Match, ax: Float, ay: Float, val bx: Float, val by: Float) : Prop(m, ax, ay) {
    val h = 70f
    override fun interactLabel(f: Fighter): String? =
        if (f.vehicle == null && (dist(f.x, f.y, x, y) < T * 1.1f || dist(f.x, f.y, bx, by) < T * 1.1f)) "Tyrolienne" else null
    override val interactRange: Float get() = maxOf(dist(x, y, bx, by), T)
    override fun interact(f: Fighter) {
        val fromA = dist(f.x, f.y, x, y) < dist(f.x, f.y, bx, by)
        f.zipline = this; f.zipT = if (fromA) 0f else 1f
        f.vx = if (fromA) 1f else -1f // direction marker
        m.sound(Sfx.SWING, f.x, f.y)
    }
    fun ride(f: Fighter, dt: Float) {
        val len = dist(x, y, bx, by)
        val dir = if (f.vx >= 0f) 1f else -1f
        f.zipT += dir * 560f * dt / len
        val tt = clamp(f.zipT, 0f, 1f)
        f.x = x + (bx - x) * tt; f.y = y + (by - y) * tt
        f.z = h - 40f + sinf(tt * 3.14f) * -20f
        f.gz = 0f
        f.face = atan2(by - y, bx - x) + if (dir < 0) 3.14159f else 0f
        if (f.zipT <= 0f || f.zipT >= 1f) { f.zipline = null; f.vx = 0f; f.vy = 0f; f.vz = 0f }
    }
    override fun draw(c: Canvas) {
        G.ow = 3f
        val sa = y - z * ZS; val sb = by - m.map.groundAt(bx, by).coerceAtLeast(0f) * ZS
        G.rect(c, x - 5f, sa - h, x + 5f, sa, 0xFF8B5A3C.toInt()); G.rect(c, bx - 5f, sb - h, bx + 5f, sb, 0xFF8B5A3C.toInt())
        G.line(c, x, sa - h, bx, sb - h, 3f, 0xFF3A3A44.toInt())
        G.circle(c, x, sa - h, 6f, 0xFFFFD23F.toInt()); G.circle(c, bx, sb - h, 6f, 0xFFFFD23F.toInt())
    }
}

class LootMachine(m: Match, px: Float, py: Float) : Prop(m, px, py) {
    var cd = 0f
    var anim = 0f
    var user: Fighter? = null
    override val solidR: Float get() = 26f
    override fun interactLabel(f: Fighter): String? = if (cd <= 0f && anim <= 0f) "Pince !" else null
    override fun interact(f: Fighter) { if (cd <= 0f && anim <= 0f) { anim = 1.3f; user = f; m.sound(Sfx.ENGINE, x, y) } }
    override fun update(dt: Float) {
        super.update(dt)
        if (cd > 0) cd -= dt
        if (anim > 0) {
            anim -= dt
            if (anim <= 0f) {
                val ang = ang(x, y, user?.x ?: x, user?.y ?: (y + 10f))
                val w = Weapons.pickupable[ri(Weapons.pickupable.size)]
                m.addPickup(Pickup(m, Pickup.WEAPON, w, x + cosf(ang) * 50f, y + sinf(ang) * 50f))
                m.sound(Sfx.PICKUP, x, y); cd = 22f
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 3f
        G.rect(c, x - 26f, sy - 30f, x + 26f, sy, 0xFFFF5E7E.toInt())
        G.rect(c, x - 24f, sy - 84f, x + 24f, sy - 30f, 0x88BFE8FF.toInt())
        G.rect(c, x - 26f, sy - 92f, x + 26f, sy - 82f, 0xFFFF5E7E.toInt())
        val cx = x + sinf(t * 2f) * 12f
        val drop = if (anim > 0) (1.3f - anim) * 30f else 0f
        G.line(c, cx, sy - 82f, cx, sy - 66f + drop, 2f, G.INK)
        G.line(c, cx - 6f, sy - 60f + drop, cx, sy - 66f + drop, 2.5f, G.INK); G.line(c, cx + 6f, sy - 60f + drop, cx, sy - 66f + drop, 2.5f, G.INK)
        G.circle(c, x - 10f, sy - 38f, 6f, 0xFFFFE14D.toInt()); G.circle(c, x + 8f, sy - 40f, 6f, 0xFF6BD3FF.toInt()); G.circle(c, x, sy - 36f, 6f, 0xFF7CFF8A.toInt())
        if (cd > 0f) G.text(c, "${cd.toInt()}", x, sy - 12f, 16f, 0xFFFFFFFF.toInt())
    }
}

class Teleporter(m: Match, px: Float, py: Float, val color: Int) : Prop(m, px, py) {
    var partner: Teleporter? = null
    override fun update(dt: Float) {
        super.update(dt)
        val p = partner ?: return
        for (f in m.fighters) {
            if (f.out || f.teleCd > 0f || f.vehicle != null || f.cannonT > 0f) continue
            if (dist(x, y, f.x, f.y) < 24f && f.z <= z + 10f) {
                f.x = p.x; f.y = p.y + 4f; f.z = p.z; f.teleCd = 2f
                m.fx.pixels(x, y - 20f, color); m.fx.pixels(p.x, p.y - 20f, color)
                m.sound(Sfx.TELEPORT, p.x, p.y)
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 3f
        G.oval(c, x, sy, 28f, 13f, 0xFF3A3A44.toInt())
        G.oval(c, x, sy - 2f, 22f, 10f, color)
        for (k in 0 until 3) { val h = ((t * 50f + k * 20f) % 60f); G.oval(c, x, sy - h, 18f - h * 0.15f, 6f, G.alpha(color, 1f - h / 60f), false) }
    }
}

/** Platform moving between two points (also used as an elevator with z range). */
class MovingPlatform(m: Match, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val hw: Float, val hh: Float,
                     val period: Float = 6f, val z0: Float = 0f, val z1: Float = 0f) : Prop(m, x0, y0) {
    var dx = 0f; var dy = 0f
    var topZ = z0
    init { z = z0 }
    override fun update(dt: Float) {
        super.update(dt)
        val k = (1f - cosf(t * 6.2832f / period)) / 2f
        val nx = x0 + (x1 - x0) * k; val ny = y0 + (y1 - y0) * k
        dx = nx - x; dy = ny - y
        x = nx; y = ny
        topZ = z0 + (z1 - z0) * k; z = topZ
    }
    fun contains(px: Float, py: Float) = abs(px - x) <= hw && abs(py - y) <= hh
    override val sortY: Float get() = y - hh
    override fun draw(c: Canvas) {
        val sy = y - topZ * ZS
        G.ow = 3f
        G.rect(c, x - hw, sy + hh, x + hw, sy + hh + 16f, 0xFF7383B3.toInt())
        G.rect(c, x - hw, sy - hh, x + hw, sy + hh, 0xFFFFC23D.toInt())
        G.rect(c, x - hw + 8f, sy - hh + 8f, x + hw - 8f, sy + hh - 8f, 0xFFFFD86B.toInt(), false)
        for (k in -1..1) G.circle(c, x + k * hw * 0.6f, sy, 4f, 0xFFC48A12.toInt(), false)
    }
}

/** Swinging pendulum blade (hachoir pendulaire). */
class Pendulum(m: Match, px: Float, py: Float, val axisAng: Float, val amp: Float = 2.2f * T, val speed: Float = 1.8f) : Prop(m, px, py) {
    var bx = px; var by = py
    override fun update(dt: Float) {
        super.update(dt)
        val s = sinf(t * speed)
        bx = x + cosf(axisAng) * s * amp; by = y + sinf(axisAng) * s * amp
        val vel = cosf(t * speed) * speed * amp
        for (b in m.movers) {
            if (!b.alive || b.z > 70f) continue
            if (dist(bx, by, b.x, b.y) < 30f + b.radius) {
                val dir = if (vel >= 0f) 1f else -1f
                b.knock(cosf(axisAng) * 1200f * dir, sinf(axisAng) * 1200f * dir, 300f)
                if (b is Actor && b.ragdollT <= 0f) { b.ragdollT = 0.7f; b.hurt(450f, m.envBody, 0f, 0f); m.sound(Sfx.HIT, bx, by) }
            }
        }
    }
    override val sortY: Float get() = by
    override fun draw(c: Canvas) {
        G.ow = 3f
        val top = y - 190f
        G.line(c, x, top, bx, by - 50f, 4f, 0xFF6A6A78.toInt())
        G.circle(c, x, top, 7f, 0xFF3A3A44.toInt())
        G.oval(c, bx, by + 2f, 26f, 9f, 0x33000000, false)
        c.save(); c.translate(bx, by - 40f)
        c.rotate(sinf(t * speed) * 25f)
        G.poly(c, 0xFFD8DEE8.toInt(), true, -30f, -14f, 30f, -14f, 26f, 18f, 0f, 26f, -26f, 18f)
        G.line(c, -22f, 10f, 22f, 10f, 3f, 0xFFFFFFFF.toInt())
        G.rect(c, -6f, -26f, 6f, -12f, 0xFF8B5A3C.toInt())
        c.restore()
    }
}

/** Giant mouse trap. */
class MouseTrap(m: Match, px: Float, py: Float) : Prop(m, px, py) {
    var armed = true; var re = 0f
    override fun update(dt: Float) {
        super.update(dt)
        if (!armed) { re -= dt; if (re <= 0f) armed = true; return }
        bodiesNear(24f) { b ->
            if (armed && b is Actor && b.z < z + 10f) {
                armed = false; re = 4f
                b.hurt(800f, m.envBody, 0f, 0f); b.applyStatus(St.STUN, 1.2f, m.envBody); b.vz = 300f
                m.sound(Sfx.THUD, x, y); m.cam.shake(2f)
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 3f
        G.rect(c, x - 28f, sy - 16f, x + 28f, sy + 16f, 0xFFC28A4E.toInt())
        G.rect(c, x - 24f, sy - 12f, x + 24f, sy + 12f, 0xFFD9A066.toInt(), false)
        if (armed) { G.ring(c, x, sy, 18f, 3f, 0xFFB0B8C8.toInt()); G.poly(c, 0xFFFFD23F.toInt(), true, x - 6f, sy + 4f, x + 6f, sy + 4f, x, sy - 6f) }
        else G.line(c, x - 24f, sy - 2f, x + 24f, sy - 2f, 5f, 0xFFB0B8C8.toInt())
    }
}

/** Spinning sweeper bar (roue qui tourne). */
class SpinBar(m: Match, px: Float, py: Float, val len: Float = 2.6f * T, val speed: Float = 1.6f, val arms: Int = 2) : Prop(m, px, py) {
    var a = 0f
    override val solidR: Float get() = 20f
    override fun update(dt: Float) {
        super.update(dt)
        a += speed * dt
        for (b in m.movers) {
            if (!b.alive || b.z > z + 40f) continue
            val d = dist(x, y, b.x, b.y)
            if (d > len + b.radius || d < 20f) continue
            val ba = ang(x, y, b.x, b.y)
            for (k in 0 until arms) {
                val arm = a + k * 6.2832f / arms
                if (abs(com.starguys.battle.core.angleDiff(arm, ba)) * d < b.radius + 10f) {
                    val tang = arm + 1.5708f * (if (speed > 0) 1f else -1f)
                    b.knock(cosf(tang) * 900f, sinf(tang) * 900f, 260f)
                    if (b is Actor && b.ragdollT <= 0f) { b.ragdollT = 0.5f; b.hurt(250f, m.envBody, 0f, 0f) }
                }
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 3f
        for (k in 0 until arms) {
            val arm = a + k * 6.2832f / arms
            val ex = x + cosf(arm) * len; val ey = sy - 18f + sinf(arm) * len
            G.line(c, x, sy - 18f, ex, ey, 18f, G.INK); G.line(c, x, sy - 18f, ex, ey, 12f, 0xFFFF5E7E.toInt())
            G.circle(c, ex, ey, 10f, 0xFFFFE14D.toInt())
        }
        G.circle(c, x, sy - 18f, 20f, 0xFF6A6A78.toInt()); G.circle(c, x, sy - 18f, 8f, 0xFFFFE14D.toInt(), false)
    }
}

/** Factory press: slams down periodically on its tile. */
class Press(m: Match, px: Float, py: Float, val period: Float = 3f, offset: Float = 0f) : Prop(m, px, py) {
    init { t = offset }
    val phase get() = (t % period) / period
    override fun update(dt: Float) {
        super.update(dt)
        val p = phase
        if (p > 0.78f && p < 0.86f) {
            for (b in m.movers) {
                if (!b.alive || b.z > 60f) continue
                if (abs(b.x - x) < T * 0.5f + b.radius * 0.5f && abs(b.y - y) < T * 0.5f + b.radius * 0.5f) {
                    if (b is Actor && b.stunT <= 0f) { b.hurt(1800f, m.envBody, 0f, 0f); b.applyStatus(St.STUN, 1f, m.envBody) }
                }
            }
            if (p < 0.8f) { m.cam.shakeAt(x, y, 3f); m.sound(Sfx.THUD, x, y, 0.6f) }
        }
    }
    override val sortY: Float get() = y + T * 0.45f
    override fun draw(c: Canvas) {
        val p = phase
        val h = when { p < 0.6f -> 150f; p < 0.78f -> 150f - (p - 0.6f) / 0.18f * 20f; p < 0.86f -> 4f; else -> 4f + (p - 0.86f) / 0.14f * 146f }
        val sy = y - z * ZS
        G.ow = 3f
        G.rect(c, x - T / 2 + 2f, sy - T / 2 + 2f, x + T / 2 - 2f, sy + T / 2 - 2f, G.alpha(0xFFFF3D3D.toInt(), if (p > 0.55f && p < 0.86f) 0.45f else 0.15f), false)
        G.rect(c, x - 6f, sy - 260f, x + 6f, sy - h - 30f, 0xFF8A93A8.toInt())
        G.rect(c, x - T / 2, sy - h - 34f, x + T / 2, sy - h + T / 2 - 4f, 0xFF4A5480.toInt())
        G.rect(c, x - T / 2, sy - h - 34f, x + T / 2, sy - h - 22f, 0xFFFFC23D.toInt())
        for (k in 0..3) G.line(c, x - T / 2 + 6f + k * 16f, sy - h - 20f, x - T / 2 + k * 16f, sy - h - 8f, 4f, 0xFFFFD23F.toInt())
    }
}

class BananaPeel(m: Match, px: Float, py: Float, val ownerTeam: Int, val owner: Fighter?) : Prop(m, px, py) {
    var life = 25f
    override fun update(dt: Float) {
        super.update(dt)
        life -= dt; if (life <= 0f) { alive = false; return }
        bodiesNear(20f) { b ->
            if (alive && b is Actor && b.team != ownerTeam && b.z < z + 8f && b.ragdollT <= 0f) {
                b.applyStatus(St.SLIP, 1.1f, owner); b.vx *= 1.6f; b.vy *= 1.6f; b.vz = 200f
                m.sound(Sfx.SWING, x, y); alive = false
            }
        }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 2.5f
        G.poly(c, 0xFFFFE14D.toInt(), true, x - 16f, sy, x - 4f, sy - 8f, x, sy - 2f, x + 4f, sy - 8f, x + 16f, sy, x + 6f, sy + 6f, x - 6f, sy + 6f)
        G.circle(c, x, sy - 2f, 4f, 0xFFFFF3B0.toInt(), false)
    }
}

/** CTF flag. */
class FlagProp(m: Match, val homeX: Float, val homeY: Float, val ownerTeam: Int, val color: Int) : Prop(m, homeX, homeY) {
    var carrier: Fighter? = null
    var atHome = true
    var dropT = 0f
    fun drop(px: Float, py: Float) { carrier = null; x = px; y = py; z = maxOf(0f, m.map.groundAt(px, py)); atHome = false; dropT = 12f; if (z < -10f) reset() }
    fun reset() { carrier = null; x = homeX; y = homeY; z = maxOf(0f, m.map.groundAt(x, y)); atHome = true }
    override fun update(dt: Float) {
        super.update(dt)
        val c = carrier
        if (c != null) { x = c.x; y = c.y; z = c.z; if (c.out) drop(c.x, c.y); return }
        if (!atHome) { dropT -= dt; if (dropT <= 0f) reset() }
    }
    override fun draw(c: Canvas) {
        if (carrier != null) return
        val sy = y - z * ZS
        G.ow = 3f
        if (atHome) G.oval(c, x, sy, 30f, 13f, G.alpha(color, 0.4f))
        G.line(c, x, sy, x, sy - 80f, 4f, G.INK)
        val w = sinf(t * 5f) * 4f
        G.poly(c, color, true, x, sy - 80f, x + 36f, sy - 70f + w, x, sy - 58f)
        G.star(c, x + 12f, sy - 69f + w / 2, 6f, 2.5f, 5, 0f, 0xFFFFFFFF.toInt(), false)
    }
}

/** Giant explosive football. */
class Ball(m: Match, px: Float, py: Float) : Prop(m, px, py) {
    var rot = 0f
    init { radius = 44f; mass = 3f; team = TEAM_NEUTRAL; bodyH = 80f }
    override val hittable: Boolean get() = true
    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        val s = src ?: return false
        val a = if (kx != 0f || ky != 0f) atan2(ky, kx) else ang(s.x, s.y, x, y)
        val imp = 220f + d * 0.45f
        vx += cosf(a) * imp / mass; vy += sinf(a) * imp / mass; vz = maxOf(vz, 60f + d * 0.05f)
        lastTouch = s.ownerF ?: (s as? Fighter)
        return true
    }
    var lastTouch: Fighter? = null
    override fun knock(kx: Float, ky: Float, kz: Float) { vx += kx / mass; vy += ky / mass }
    override fun update(dt: Float) {
        super.update(dt)
        // players push the ball by walking into it
        for (f in m.fighters) {
            if (f.out) continue
            val d = dist(x, y, f.x, f.y)
            if (d < radius + f.radius && d > 0.1f) {
                val nx = (x - f.x) / d; val ny = (y - f.y) / d
                x = f.x + nx * (radius + f.radius); y = f.y + ny * (radius + f.radius)
                vx += nx * 90f; vy += ny * 90f
                lastTouch = f
            }
        }
        vx *= (1f - 0.9f * dt); vy *= (1f - 0.9f * dt)
        moveCollide(dt)
        if (hitWall) { val dot = vx * wallNx + vy * wallNy; vx -= 1.7f * dot * wallNx; vy -= 1.7f * dot * wallNy; m.sound(Sfx.BOING, x, y, 0.5f) }
        physicsZ(dt)
        rot += com.starguys.battle.core.len(vx, vy) * dt * 0.03f
    }
    override fun onFallOut() { }
    override fun draw(c: Canvas) {
        shadow(c, radius)
        val sy = y - z * ZS - radius
        G.ow = 3.5f
        G.circle(c, x, sy, radius, 0xFFFFFFFF.toInt())
        c.save(); c.rotate(rot * 57f, x, sy)
        for (k in 0 until 5) { val a = k * 1.2566f; G.poly(c, 0xFF2A2433.toInt(), false, x + cosf(a) * 26f, sy + sinf(a) * 26f, x + cosf(a + 0.3f) * 36f, sy + sinf(a + 0.3f) * 36f, x + cosf(a - 0.3f) * 36f, sy + sinf(a - 0.3f) * 36f) }
        G.star(c, x, sy, 14f, 9f, 5, 0f, 0xFF2A2433.toInt(), false)
        c.restore()
        G.circle(c, x - 14f, sy - 16f, 9f, 0x88FFFFFF.toInt(), false)
        G.star(c, x + 18f, sy - 30f + sinf(t * 8f) * 3, 7f, 3f, 4, t * 4, 0xFFFF7A3D.toInt())
    }
}

/** Avalanche snowball rolling across the map. */
class Snowball(m: Match, px: Float, py: Float, val dvx: Float, val dvy: Float) : Prop(m, px, py) {
    var r = 46f
    init { flying = true; radius = 46f }
    override fun update(dt: Float) {
        super.update(dt)
        x += dvx * dt; y += dvy * dt
        if (x < -200f || y < -200f || x > m.map.pxW + 200f || y > m.map.pxH + 200f) alive = false
        bodiesNear(r) { b ->
            if (b is Actor && b !== this) {
                val a = atan2(dvy, dvx)
                b.knock(cosf(a) * 1300f, sinf(a) * 1300f, 300f)
                if (b.ragdollT <= 0f) { b.ragdollT = 0.8f; b.hurt(900f, m.envBody, 0f, 0f) }
            }
        }
        if (com.starguys.battle.core.chance(0.5f)) m.fx.smoke(x - dvx * 0.05f, y, 1, 0xFFFFFFFF.toInt())
    }
    override fun draw(c: Canvas) {
        G.oval(c, x, y + 4f, r, r * 0.4f, 0x33000000, false)
        G.ow = 3f
        G.circle(c, x, y - r, r, 0xFFF3F8FF.toInt())
        G.circle(c, x - r * 0.3f, y - r * 1.3f, r * 0.3f, 0xFFFFFFFF.toInt(), false)
        G.circle(c, x + r * 0.35f, y - r * 0.7f, r * 0.15f, 0xFFD8E4F2.toInt(), false)
    }
}

/** Mob spawner portal (castle defense / raid). */
class Portal(m: Match, px: Float, py: Float) : Prop(m, px, py) {
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 3f
        G.oval(c, x, sy - 40f, 34f, 46f, 0xFF4A1F7A.toInt())
        for (k in 0 until 3) { val r = ((t * 30f + k * 12f) % 36f); G.oval(c, x, sy - 40f, r, r * 1.35f, G.alpha(0xFFB46CFF.toInt(), 1f - r / 36f), false) }
        G.ring(c, x, sy - 40f, 36f, 4f, 0xFFB46CFF.toInt())
    }
}

/** Defended core in castle defense. */
class CastleCore(m: Match, px: Float, py: Float) : Prop(m, px, py) {
    var hp = 20000f
    val maxHp = 20000f
    var flash = 0f
    override val solidR: Float get() = 44f
    override val hittable: Boolean get() = alive
    init { team = 0; radius = 44f; bodyH = 120f }
    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        if (src?.team == team) return false
        hp -= d; flash = 0.1f
        if (hp <= 0f) { hp = 0f; alive = false; m.explode(x, y, 3f * T, 0f, m.envBody, TEAM_NEUTRAL, 0f) }
        return true
    }
    override fun update(dt: Float) { super.update(dt); if (flash > 0) flash -= dt }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.flash = if (flash > 0) 0.5f else 0f
        G.ow = 3.5f
        G.oval(c, x, sy, 52f, 20f, 0x33000000, false)
        G.rrect(c, x - 40f, sy - 70f, x + 40f, sy, 8f, 0xFFB0B8C8.toInt())
        for (k in 0..3) G.rect(c, x - 40f + k * 22f, sy - 82f, x - 28f + k * 22f, sy - 68f, 0xFFB0B8C8.toInt())
        G.rect(c, x - 12f, sy - 30f, x + 12f, sy, 0xFF6B4028.toInt())
        G.star(c, x, sy - 110f + sinf(t * 2f) * 6f, 26f, 11f, 5, t, 0xFFFFE14D.toInt())
        G.line(c, x, sy - 82f, x, sy - 96f, 3f, G.INK)
        G.flash = 0f
        val w = 120f
        G.rect(c, x - w / 2, sy - 150f, x + w / 2, sy - 140f, 0xAA1B1530.toInt(), false)
        G.rect(c, x - w / 2, sy - 150f, x - w / 2 + w * (hp / maxHp), sy - 140f, 0xFF7CFF8A.toInt(), false)
    }
}

/** Static decorative props (some are solid and block shots). */
class Decor(m: Match, px: Float, py: Float, val kind: Int, val scale: Float = 1f) : Prop(m, px, py) {
    override val solidR: Float get() = SOLID[kind] * scale
    override fun update(dt: Float) { t += dt }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        if (SOLID[kind] > 0f) G.oval(c, x, sy + 2f, SOLID[kind] * scale * 1.1f, SOLID[kind] * scale * 0.45f, 0x33000000, false)
        c.save(); c.translate(x, sy); c.scale(scale, scale)
        G.ow = 3f
        drawKind(c, kind, t, m.map.biome)
        c.restore()
    }

    companion object {
        const val NEON = 0; const val BALLOONS = 1; const val PENNANT = 2; const val CONE = 3; const val VENDING = 4; const val BENCH = 5
        const val LAMP = 6; const val MUSHROOM = 7; const val STATUE = 8; const val WRECK = 9; const val CONTAINER = 10; const val TENT = 11
        const val TOWER = 12; const val PALM = 13; const val PINE = 14; const val ROCK = 15; const val BOAT = 16; const val PLATE = 17
        const val FORK = 18; const val POT = 19; const val SNOWMAN = 20; const val CRATE = 21; const val BARREL = 22; const val TOYBLOCK = 23
        const val GRAVE = 24; const val DEADTREE = 25; const val LANTERN = 26; const val CRYSTAL = 27; const val STALL = 28; const val FERRIS = 29
        const val DISH = 30; const val FLOWER = 31; const val CUP = 32; const val GIFT = 33; const val COUNT = 34

        val SOLID = floatArrayOf(0f, 0f, 0f, 14f, 26f, 0f, 10f, 24f, 28f, 40f, 46f, 34f, 22f, 16f, 18f, 26f, 44f, 40f, 12f, 34f, 22f, 24f, 22f, 26f, 14f, 18f, 0f, 22f, 40f, 70f, 30f, 0f, 26f, 0f)
        val NAMES = arrayOf("Néon", "Ballons", "Fanions", "Cône", "Distributeur", "Banc", "Réverbère", "Champignon géant", "Statue", "Épave",
            "Conteneur", "Tente", "Tour radio", "Cocotier", "Sapin", "Rocher", "Bateau", "Assiette", "Fourchette", "Casserole", "Bonhomme de neige",
            "Caisse", "Baril", "Cube jouet", "Tombe", "Arbre mort", "Lanterne", "Cristal jelly", "Stand de foire", "Grande roue", "Parabole", "Fleurs", "Tasse", "Cadeau")

        /** props usable as prop-hunt disguises */
        val DISGUISES = intArrayOf(CRATE, ROCK, CONE, BARREL, MUSHROOM, TOYBLOCK, GIFT)

        fun drawDisguise(c: Canvas, kind: Int) { drawKind(c, kind, 0f, Biome.MEADOW) }

        fun drawKind(c: Canvas, kind: Int, t: Float, b: Biome) {
            when (kind) {
                NEON -> { G.line(c, -2f, 0f, -2f, -60f, 4f, 0xFF3A3A44.toInt()); G.rrect(c, -34f, -96f, 30f, -60f, 8f, 0xFF2A2433.toInt()); val on = (t % 2f) < 1.6f; G.text(c, "STAR", -2f, -70f, 22f, if (on) 0xFFFF5EC4.toInt() else 0xFF6A3A5A.toInt(), outline = false) }
                BALLOONS -> {
                    for (k in 0 until 3) { val bx = -12f + k * 12f; val by = -80f - (k % 2) * 14f + sinf(t * 2f + k) * 4f; G.line(c, 0f, 0f, bx, by + 14f, 1.5f, G.INK); G.oval(c, bx, by, 11f, 14f, intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt())[k]) }
                }
                PENNANT -> { G.line(c, -40f, -70f, 40f, -70f, 2f, G.INK); for (k in 0 until 5) G.poly(c, intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFB46CFF.toInt())[k], true, -40f + k * 16f, -70f, -26f + k * 16f, -70f, -33f + k * 16f, -56f + sinf(t * 3 + k) * 2) }
                CONE -> { G.poly(c, 0xFFFF7A3D.toInt(), true, -14f, 0f, 14f, 0f, 4f, -36f, -4f, -36f); G.rect(c, -10f, -18f, 10f, -12f, 0xFFFFFFFF.toInt(), false); G.rect(c, -17f, -4f, 17f, 2f, 0xFFFF7A3D.toInt()) }
                VENDING -> { G.rect(c, -24f, -76f, 24f, 0f, 0xFFE84545.toInt()); G.rect(c, -18f, -68f, 8f, -24f, 0xFFBFE8FF.toInt()); for (k in 0..2) G.circle(c, -5f, -58f + k * 13f, 4f, intArrayOf(0xFFFFE14D.toInt(), 0xFF7CFF8A.toInt(), 0xFF6BD3FF.toInt())[k], false); G.rect(c, 12f, -60f, 20f, -40f, 0xFF3A3A44.toInt()) }
                BENCH -> { G.rect(c, -30f, -24f, 30f, -16f, 0xFFC28A4E.toInt()); G.rect(c, -30f, -40f, 30f, -32f, 0xFFC28A4E.toInt()); G.line(c, -24f, -16f, -24f, 0f, 4f, G.INK); G.line(c, 24f, -16f, 24f, 0f, 4f, G.INK) }
                LAMP -> { G.line(c, 0f, 0f, 0f, -110f, 5f, 0xFF3A3A44.toInt()); G.circle(c, 0f, -114f, 12f, 0xFFFFF3B0.toInt()); G.circle(c, 0f, -114f, 22f, 0x33FFF3B0, false) }
                MUSHROOM -> { G.rect(c, -9f, -40f, 9f, 0f, 0xFFFFF3E0.toInt()); G.pie(c, 0f, -36f, 32f, 180f, 180f, 0xFFE84545.toInt()); G.line(c, -32f, -36f, 32f, -36f, 3f, G.INK); G.arc(c, 0f, -36f, 32f, 180f, 180f, 3f, G.INK); G.circle(c, -14f, -50f, 6f, 0xFFFFFFFF.toInt(), false); G.circle(c, 10f, -58f, 5f, 0xFFFFFFFF.toInt(), false) }
                STATUE -> { G.rect(c, -26f, -20f, 26f, 0f, 0xFFB0B8C8.toInt()); c.save(); c.translate(0f, -20f); c.scale(1.4f, 1.4f); HeroArt.draw(c, 0, 0xFFD8DEE8.toInt(), 0f, false, 0.3f, 0f); c.restore(); G.star(c, 0f, -110f, 12f, 5f, 5, 0f, 0xFFFFE14D.toInt()) }
                WRECK -> { G.rrect(c, -40f, -34f, 40f, -6f, 10f, 0xFF8A6A5A.toInt()); G.rrect(c, -22f, -52f, 16f, -30f, 8f, 0xFF7A5A4A.toInt()); G.circle(c, -24f, -6f, 9f, 0xFF3A3A44.toInt()); G.circle(c, 22f, -4f, 7f, 0xFF3A3A44.toInt()); G.line(c, -10f, -26f, 4f, -16f, 3f, 0x66000000) }
                CONTAINER -> { G.rect(c, -46f, -64f, 46f, 0f, 0xFF35A0D8.toInt()); for (k in 0..6) G.line(c, -40f + k * 13f, -60f, -40f + k * 13f, -4f, 3f, 0x44000000); G.rect(c, -46f, -70f, 46f, -62f, 0xFF2A80B0.toInt()) }
                TENT -> { G.poly(c, 0xFFFF7A3D.toInt(), true, -36f, 0f, 36f, 0f, 0f, -60f); G.poly(c, 0xFF8B3D1A.toInt(), true, -10f, 0f, 10f, 0f, 0f, -26f) }
                TOWER -> { G.line(c, -18f, 0f, 0f, -150f, 4f, 0xFFE84545.toInt()); G.line(c, 18f, 0f, 0f, -150f, 4f, 0xFFE84545.toInt()); for (k in 1..4) G.line(c, -18f + k * 3.6f, -k * 30f, 18f - k * 3.6f, -k * 30f, 3f, 0xFFFFFFFF.toInt()); G.circle(c, 0f, -156f, 6f, if ((t % 1f) < 0.5f) 0xFFFF3D3D.toInt() else 0xFF6A2A2A.toInt()) }
                PALM -> { G.line(c, 0f, 0f, 8f, -90f, 10f, 0xFF8B5A3C.toInt()); for (k in 0 until 5) { val a = -2.6f + k * 0.55f + sinf(t + k) * 0.05f; G.oval(c, 8f + cosf(a) * 26f, -94f + sinf(a) * 14f, 26f, 8f, 0xFF3DAE5A.toInt()) }; G.circle(c, 4f, -86f, 6f, 0xFF8B5A3C.toInt()); G.circle(c, 13f, -84f, 6f, 0xFF8B5A3C.toInt()) }
                PINE -> { G.rect(c, -5f, -16f, 5f, 0f, 0xFF8B5A3C.toInt()); G.poly(c, 0xFF2F8C5A.toInt(), true, -30f, -14f, 30f, -14f, 0f, -60f); G.poly(c, 0xFF38A06A.toInt(), true, -22f, -44f, 22f, -44f, 0f, -86f); if (b == Biome.SNOW) G.poly(c, 0xFFFFFFFF.toInt(), true, -9f, -72f, 9f, -72f, 0f, -86f) }
                ROCK -> { G.poly(c, 0xFF9A94A8.toInt(), true, -26f, 0f, -20f, -26f, 0f, -36f, 22f, -24f, 28f, 0f); G.line(c, -6f, -26f, 4f, -12f, 2.5f, 0x44000000) }
                BOAT -> { G.poly(c, 0xFFE84545.toInt(), true, -50f, -30f, 50f, -30f, 36f, 0f, -36f, 0f); G.rect(c, -50f, -34f, 50f, -28f, 0xFFFFFFFF.toInt()); G.line(c, 0f, -30f, 0f, -96f, 4f, 0xFF8B5A3C.toInt()); G.poly(c, 0xFFFFF3E0.toInt(), true, 2f, -94f, 36f, -40f, 2f, -40f) }
                PLATE -> { G.oval(c, 0f, -8f, 46f, 22f, 0xFFF4F7FF.toInt()); G.oval(c, 0f, -8f, 30f, 14f, 0xFFE0E6F0.toInt(), false); G.circle(c, -8f, -12f, 7f, 0xFFFF7A3D.toInt()) }
                FORK -> { G.rect(c, -4f, -90f, 4f, 0f, 0xFFDCE3EA.toInt()); for (k in 0..3) G.rect(c, -12f + k * 7f, -120f, -8f + k * 7f, -88f, 0xFFDCE3EA.toInt()) }
                POT -> { G.rrect(c, -34f, -50f, 34f, 0f, 10f, 0xFFB0B8C8.toInt()); G.rect(c, -40f, -54f, 40f, -46f, 0xFF808898.toInt()); G.line(c, 34f, -40f, 58f, -46f, 6f, 0xFF3A3A44.toInt()); for (k in 0..2) G.circle(c, -12f + k * 12f, -62f - ((t * 30 + k * 10) % 20), 5f, 0x88FFFFFF.toInt(), false) }
                SNOWMAN -> { G.circle(c, 0f, -18f, 20f, 0xFFFFFFFF.toInt()); G.circle(c, 0f, -50f, 14f, 0xFFFFFFFF.toInt()); G.poly(c, 0xFFFF7A3D.toInt(), true, 0f, -52f, 14f, -49f, 0f, -46f); G.circle(c, -5f, -55f, 2f, G.INK, false); G.circle(c, 5f, -55f, 2f, G.INK, false); G.rect(c, -10f, -74f, 10f, -62f, 0xFF2A2433.toInt()) }
                CRATE -> { G.rect(c, -22f, -44f, 22f, 0f, 0xFFC28A4E.toInt()); G.line(c, -22f, -44f, 22f, 0f, 4f, 0xFF8E5C2E.toInt()); G.line(c, 22f, -44f, -22f, 0f, 4f, 0xFF8E5C2E.toInt()); G.rect(c, -22f, -44f, 22f, 0f, 0x00000000) }
                BARREL -> { G.rrect(c, -18f, -46f, 18f, 0f, 8f, 0xFF8B5A3C.toInt()); G.rect(c, -18f, -36f, 18f, -32f, 0xFF6A6A78.toInt(), false); G.rect(c, -18f, -14f, 18f, -10f, 0xFF6A6A78.toInt(), false) }
                TOYBLOCK -> { G.rect(c, -24f, -48f, 24f, 0f, 0xFF6BD3FF.toInt()); G.text(c, "A", 0f, -14f, 30f, 0xFFFFE14D.toInt()) }
                GRAVE -> { G.rrect(c, -16f, -44f, 16f, 0f, 14f, 0xFF8A8494.toInt()); G.line(c, -6f, -28f, 6f, -28f, 3f, 0x66000000); G.line(c, 0f, -34f, 0f, -18f, 3f, 0x66000000) }
                DEADTREE -> { G.line(c, 0f, 0f, 0f, -80f, 9f, 0xFF4A3A2E.toInt()); G.line(c, 0f, -50f, -26f, -76f, 6f, 0xFF4A3A2E.toInt()); G.line(c, 0f, -64f, 22f, -92f, 5f, 0xFF4A3A2E.toInt()) }
                LANTERN -> { G.line(c, 0f, 0f, 0f, -70f, 4f, 0xFF3A3A44.toInt()); G.circle(c, 0f, -78f, 34f, 0x33FFC94D, false); G.rrect(c, -10f, -92f, 10f, -66f, 4f, 0xFFFFC94D.toInt()); G.rect(c, -12f, -96f, 12f, -90f, 0xFF3A3A44.toInt()) }
                CRYSTAL -> { G.poly(c, 0xFFFF5E7E.toInt(), true, -16f, 0f, -10f, -40f, 0f, -56f, 10f, -40f, 16f, 0f); G.poly(c, 0xFFFF9AB0.toInt(), false, -6f, -10f, -2f, -40f, 4f, -10f) }
                STALL -> { G.rect(c, -40f, -40f, 40f, 0f, 0xFFFFF3E0.toInt()); for (k in 0 until 5) G.rect(c, -40f + k * 16f, -72f, -24f + k * 16f, -52f, if (k % 2 == 0) 0xFFFF5E7E.toInt() else 0xFFFFFFFF.toInt()); G.line(c, -40f, -52f, -40f, 0f, 3f, G.INK); G.line(c, 40f, -52f, 40f, 0f, 3f, G.INK); G.circle(c, -10f, -50f, 6f, 0xFFFFE14D.toInt()); G.circle(c, 10f, -50f, 6f, 0xFF6BD3FF.toInt()) }
                FERRIS -> {
                    G.line(c, -40f, 0f, 0f, -150f, 8f, 0xFF8A93A8.toInt()); G.line(c, 40f, 0f, 0f, -150f, 8f, 0xFF8A93A8.toInt())
                    G.ring(c, 0f, -150f, 110f, 6f, 0xFFFF5EC4.toInt())
                    for (k in 0 until 8) { val a = t * 0.4f + k * 0.785f; val cx = cosf(a) * 110f; val cy = -150f + sinf(a) * 110f; G.line(c, 0f, -150f, cx, cy, 2f, 0xFFB0B8C8.toInt()); G.rrect(c, cx - 12f, cy, cx + 12f, cy + 18f, 4f, intArrayOf(0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFFF7A3D.toInt())[k % 4]) }
                    G.circle(c, 0f, -150f, 12f, 0xFFFFE14D.toInt())
                }
                DISH -> { G.line(c, 0f, 0f, 0f, -40f, 6f, 0xFF8A93A8.toInt()); G.oval(c, 0f, -56f, 34f, 22f, 0xFFDCE3EA.toInt()); G.line(c, 0f, -56f, 14f, -80f, 3f, G.INK); G.circle(c, 14f, -80f, 4f, 0xFFFF3D3D.toInt()) }
                FLOWER -> { for (k in 0 until 3) { val fx = -14f + k * 14f; G.line(c, fx, 0f, fx, -18f, 2.5f, 0xFF2F7D43.toInt()); G.circle(c, fx, -20f, 6f, intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFFB46CFF.toInt())[k]); G.circle(c, fx, -20f, 2.5f, 0xFFFFFFFF.toInt(), false) } }
                CUP -> { G.rrect(c, -24f, -54f, 24f, 0f, 10f, 0xFFFFFFFF.toInt()); G.ring(c, 32f, -30f, 12f, 6f, 0xFFFFFFFF.toInt()); G.rect(c, -24f, -40f, 24f, -32f, 0xFFFF5E7E.toInt(), false) }
                GIFT -> { G.rect(c, -20f, -38f, 20f, 0f, 0xFFB46CFF.toInt()); G.rect(c, -4f, -38f, 4f, 0f, 0xFFFFE14D.toInt(), false); G.oval(c, -8f, -42f, 8f, 5f, 0xFFFFE14D.toInt()); G.oval(c, 8f, -42f, 8f, 5f, 0xFFFFE14D.toInt()) }
            }
        }
    }
}
