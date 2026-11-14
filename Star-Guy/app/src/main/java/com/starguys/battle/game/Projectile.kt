package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.cosf
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.len
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf
import kotlin.math.abs
import kotlin.math.atan2

/** Projectile visual styles */
object PV {
    const val BULLET = 0; const val GUM = 1; const val BOLT = 2; const val PIXEL = 3; const val RICE = 4; const val CORK = 5
    const val HAIL = 6; const val DIRT = 7; const val SOAP = 8; const val UMBRELLA = 9; const val TOAST = 10; const val MAGNET = 11
    const val FOAM = 12; const val ARROW = 13; const val FLAME = 14; const val BOOMERANG = 15; const val CONFETTI = 16
    const val BUBBLE = 17; const val SUCKER = 18; const val SHELL = 19; const val PAN = 20; const val POT = 21; const val GEL = 22
    const val DART = 23; const val ONIGIRI = 24; const val BIGCORK = 25; const val GLUEBOMB = 26; const val SNOWBALL = 27
    const val ROCK = 28; const val TRASH = 29; const val METEOR = 30; const val LASER = 31
}

class Projectile(val m: Match) {
    var x = 0f; var y = 0f; var z = 24f
    var vx = 0f; var vy = 0f; var vz = 0f
    var r = 8f
    var dmg = 0f
    var owner: Body? = null
    var team = TEAM_NEUTRAL
    var life = 1f
    var vis = PV.BULLET
    var color = 0xFFFFE14D.toInt()
    var lob = false
    var explodeR = 0f
    var pierce = false
    var wallPierce = 0
    var bounces = 0
    var knock = 0f
    var pull = 0f
    var status = St.NONE
    var statusT = 0f
    var boomerang = false
    var returning = false
    var travel = 0f
    var maxTravel = 400f
    var heal = 0f
    var grapple = false
    var foam = false
    var sticky = false
    var chain = 0
    var gravity = GRAV
    var alive = true
    var hits: ArrayList<Body>? = null
    var rot = 0f
    var reflectable = true
    var tileDmg = 1f
    var onLand: ((Projectile) -> Unit)? = null
    var onHit: ((Projectile, Body) -> Unit)? = null
    var hitsAllies = false
    var big = 1f

    val ownerF: Fighter? get() = owner as? Fighter ?: owner?.ownerF

    /** environmental shots have no owner: use the world's neutral body */
    private fun srcStub(): Body = m.envBody

    fun update(dt: Float) {
        life -= dt
        rot += dt * 12f
        if (lob) {
            x += vx * dt; y += vy * dt
            vz -= gravity * m.gravity * dt; z += vz * dt
            val g = m.map.groundAt(x, y)
            if (vz < 0 && z <= g) { z = maxOf(g, z); land(); return }
            if (life <= 0) { land(); return }
            if (vis == PV.FLAME && com.starguys.battle.core.chance(0.3f)) Unit
            return
        }
        if (life <= 0) { expire(); return }
        // boomerang turnaround
        val step = len(vx, vy) * dt
        travel += step
        if (boomerang) {
            val o = owner
            if (!returning && travel >= maxTravel) { returning = true; hits?.clear() }
            if (returning && o != null) {
                val a = ang(x, y, o.x, o.y)
                val sp = len(vx, vy)
                vx = cosf(a) * sp; vy = sinf(a) * sp
                if (com.starguys.battle.core.dist(x, y, o.x, o.y) < o.radius + r) { alive = false; return }
            }
        } else if (travel >= maxTravel) { expire(); return }
        // sub-stepped movement with tile collision
        val n = maxOf(1, (step / 10f).toInt() + 1)
        val sx = vx * dt / n; val sy = vy * dt / n
        for (i in 0 until n) {
            x += sx; y += sy
            val tx = (x / T).floorInt(); val ty = (y / T).floorInt()
            if (m.map.blocksShot(tx, ty, z)) {
                if (m.onShotTile(this, tx, ty)) return
                if (!alive) return
            }
            if (checkHits()) return
        }
    }

    /** returns true if the projectile is consumed */
    private fun checkHits(): Boolean {
        for (b in m.hittables) {
            if (!b.hittable || b === owner) continue
            val enemy = m.isEnemy(team, b)
            if (!enemy && !(hitsAllies && heal > 0f)) continue
            if (hits?.contains(b) == true) continue
            val rr = r + b.radius
            if (abs(b.x - x) > rr || abs(b.y - y) > rr) continue
            if (com.starguys.battle.core.dist2(x, y, b.x, b.y) > rr * rr) continue
            if (b !is Vehicle && (z < b.z - 14f || z > b.z + b.bodyH + 20f) && !b.flying) continue
            if (hitBody(b)) return true
        }
        return false
    }

    fun hitBody(b: Body): Boolean {
        if (!m.isEnemy(team, b)) {
            if (heal > 0f && b is Fighter) { b.heal(heal); m.fx.ring(b.x, b.y - b.z * ZS, 30f, 0xFF7CFF8A.toInt()) }
            if (!pierce) { alive = false; return true }
            (hits ?: ArrayList<Body>().also { hits = it }).add(b); return false
        }
        val a = atan2(vy, vx)
        val kx = cosf(a) * knock; val ky = sinf(a) * knock
        val applied = b.hurt(dmg, owner ?: this.srcStub(), kx, ky)
        if (applied) {
            if (status != St.NONE) b.applyStatus(status, statusT, owner)
            if (pull != 0f) { val o = owner; if (o != null) { val pa = ang(b.x, b.y, o.x, o.y); b.knock(cosf(pa) * pull, sinf(pa) * pull) } }
            m.fx.hit(x, y - z * ZS, color)
            onHit?.invoke(this, b)
            if (chain > 0) chainZap(b)
            if (sticky) { m.stickMine(b, ownerF, team); sticky = false }
            if (grapple) { grapplePull(b.x, b.y) }
            if (explodeR > 0f) { m.explode(x, y, explodeR, dmg * 0.6f, owner, team, knock + 300f); alive = false; return true }
        }
        if (pierce || boomerang) {
            (hits ?: ArrayList<Body>().also { hits = it }).add(b)
            return false
        }
        alive = false
        return true
    }

    private fun chainZap(from: Body) {
        var best: Body? = null; var bd = 3.2f * T
        for (o in m.hittables) {
            if (o === from || !o.hittable || !m.isEnemy(team, o)) continue
            val d = from.dist(o)
            if (d < bd) { bd = d; best = o }
        }
        val b = best ?: return
        b.hurt(dmg * 0.5f, owner, 0f, 0f)
        m.fx.zap(from.x, from.y - from.z * ZS - 25f, b.x, b.y - b.z * ZS - 25f, color)
    }

    private fun grapplePull(tx: Float, ty: Float) {
        val o = owner as? Fighter ?: return
        val a = ang(o.x, o.y, tx, ty)
        val d = com.starguys.battle.core.dist(o.x, o.y, tx, ty)
        val sp = minOf(1400f, d * 3.2f)
        o.vx = cosf(a) * sp; o.vy = sinf(a) * sp; o.vz = 260f
        o.ragdollT = 0f
        o.dashT = 0.35f
        m.fx.line(o.x, o.y - o.z * ZS - 25f, tx, ty - 25f, 0xFFB0B0B0.toInt(), 0.25f)
    }

    fun land() {
        alive = false
        onLand?.invoke(this)
        if (foam) {
            val tx = (x / T).floorInt(); val ty = (y / T).floorInt()
            for (dy in 0..1) for (dx in 0..1) m.map.placeFoam(tx + dx - (if ((x % T) < T / 2) 1 else 0), ty + dy - (if ((y % T) < T / 2) 1 else 0), 9f)
            m.fx.smoke(x, y, 4, 0xFFFFFFFF.toInt())
            Sfx.play(Sfx.BUBBLE, m.volAt(x, y))
        }
        if (explodeR > 0f) {
            if (bounces > 0) {
                bounces--; alive = true; lob = true
                vz = 330f; vx *= 0.6f; vy *= 0.6f; life = 0.8f
                Sfx.play(Sfx.BOING, m.volAt(x, y) * 0.6f)
                return
            }
            m.explode(x, y, explodeR, dmg, owner, team, knock + 200f)
            if (heal > 0f) m.healArea(x, y, explodeR * 1.1f, heal, team)
        } else if (heal > 0f) {
            m.healArea(x, y, T * 1.4f, heal, team)
        } else if (status == St.GLUE) {
            m.addZone(Zone(m, Zone.GLUE, x, y, T * 1.3f, 5f, owner, team))
        }
    }

    private fun expire() {
        alive = false
        if (explodeR > 0f) m.explode(x, y, explodeR, dmg, owner, team, knock + 200f)
        else if (grapple) grapplePull(x, y)
    }

    fun reflect(newOwner: Body, newTeam: Int) {
        vx = -vx * 1.15f; vy = -vy * 1.15f
        owner = newOwner; team = newTeam
        life = maxOf(life, 1f); travel = 0f; hits?.clear()
        if (boomerang) returning = false
    }

    fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 2f
        val a = atan2(vy, vx)
        when (vis) {
            PV.BULLET -> { G.circle(c, x, sy, r, color) }
            PV.GUM -> { G.circle(c, x, sy, r, 0xFFFF7EC4.toInt()); G.circle(c, x - r * 0.3f, sy - r * 0.3f, r * 0.3f, 0xFFFFC6E6.toInt(), false) }
            PV.BOLT -> {
                G.line(c, x - vx * 0.03f, sy - vy * 0.03f, x, sy, r * 1.2f, 0x88FFE14D.toInt())
                G.star(c, x, sy, r * 1.4f, r * 0.6f, 4, rot, 0xFFFFF27A.toInt())
            }
            PV.PIXEL -> { G.rect(c, x - r, sy - r, x + r, sy + r, color) }
            PV.RICE -> { G.circle(c, x, sy, r, 0xFFFFFFFF.toInt()); G.rect(c, x - r * 0.9f, sy, x + r * 0.9f, sy + r * 0.6f, 0xFF2D4A2D.toInt(), false) }
            PV.CORK, PV.BIGCORK -> {
                c.save(); c.translate(x, sy); c.rotate(rot * 30f)
                G.rrect(c, -r, -r * 0.7f, r, r * 0.7f, r * 0.4f, 0xFFD2A36A.toInt()); G.rect(c, -r * 0.3f, -r * 0.7f, r * 0.3f, r * 0.7f, 0xFFB8864F.toInt(), false)
                c.restore()
            }
            PV.HAIL -> { G.circle(c, x, sy, r, 0xFFDDF4FF.toInt()); G.circle(c, x - 2, sy - 2, r * 0.35f, 0xFFFFFFFF.toInt(), false) }
            PV.DIRT -> { G.circle(c, x, sy, r, 0xFF8B5A3C.toInt()); G.circle(c, x + 3, sy - 2, r * 0.4f, 0xFF6B4028.toInt(), false) }
            PV.SOAP, PV.BUBBLE -> { G.circle(c, x, sy, r, if (vis == PV.SOAP) 0x88B8F0FF.toInt() else 0x8890E0FF.toInt()); G.circle(c, x - r * 0.35f, sy - r * 0.35f, r * 0.28f, 0xCCFFFFFF.toInt(), false) }
            PV.UMBRELLA -> { G.line(c, x - vx * 0.02f, sy - vy * 0.02f, x, sy, 5f, 0xFFB46CFF.toInt()); G.circle(c, x, sy, r, 0xFFFFFFFF.toInt()) }
            PV.TOAST -> {
                c.save(); c.translate(x, sy); c.rotate(rot * 40f)
                G.rrect(c, -r, -r, r, r, r * 0.5f, 0xFFE0A050.toInt()); G.rrect(c, -r * 0.65f, -r * 0.65f, r * 0.65f, r * 0.65f, r * 0.3f, 0xFFF5D08A.toInt(), false)
                c.restore()
            }
            PV.MAGNET -> {
                c.save(); c.translate(x, sy); c.rotate(Math.toDegrees(a.toDouble()).toFloat())
                G.rect(c, -r, -r * 0.8f, r * 0.2f, r * 0.8f, 0xFFE84545.toInt()); G.rect(c, r * 0.2f, -r * 0.8f, r, r * 0.8f, 0xFFD8D8E0.toInt())
                c.restore()
            }
            PV.FOAM, PV.GLUEBOMB -> { G.circle(c, x, sy, r, if (vis == PV.FOAM) 0xFFF4F7FF.toInt() else 0xFFB6E35A.toInt()); G.circle(c, x + r * 0.4f, sy - r * 0.2f, r * 0.5f, if (vis == PV.FOAM) 0xFFFFFFFF.toInt() else 0xFFD8F58A.toInt(), false) }
            PV.ARROW -> {
                G.line(c, x - cosf(a) * 26f, sy - sinf(a) * 26f, x, sy, 4f, 0xFF8B5A3C.toInt())
                G.circle(c, x, sy, 5f, 0xFFB0B0B8.toInt())
            }
            PV.FLAME -> {
                val k = 1f - life / 0.6f
                G.circle(c, x, sy, r * (0.7f + k), G.mix(0xFFFFE14D.toInt(), 0xFFFF5EC4.toInt(), k), false)
                G.star(c, x, sy, r * 0.8f, r * 0.3f, 4, rot, 0xFFFFFFFF.toInt(), false)
            }
            PV.BOOMERANG -> {
                c.save(); c.translate(x, sy); c.rotate(rot * 50f)
                G.ow = 2.5f
                G.poly(c, 0xFFFF9A3D.toInt(), true, -r, -r * 0.2f, r * 0.2f, -r * 0.2f, r * 0.2f, r, -r * 0.2f, r, -r * 0.2f, r * 0.2f, -r, r * 0.2f)
                c.restore()
            }
            PV.CONFETTI -> { G.rect(c, x - r, sy - r * 0.5f, x + r, sy + r * 0.5f, color, false) }
            PV.SUCKER -> { G.circle(c, x, sy, r, 0xFFFF5E7E.toInt()); G.circle(c, x, sy, r * 0.5f, 0xFFFF9AB0.toInt(), false) }
            PV.SHELL -> { G.circle(c, x, sy, r, 0xFF5D6B3A.toInt()); G.circle(c, x - 3, sy - 3, r * 0.35f, 0xFF9AAA6A.toInt(), false) }
            PV.PAN -> {
                c.save(); c.translate(x, sy); c.rotate(rot * 40f)
                G.line(c, 0f, 0f, r * 1.6f, 0f, 6f, 0xFF3A3A44.toInt()); G.circle(c, 0f, 0f, r, 0xFF4A4A55.toInt()); G.circle(c, 0f, 0f, r * 0.7f, 0xFF6A6A78.toInt(), false)
                c.restore()
            }
            PV.POT -> { G.rrect(c, x - r, sy - r * 0.8f, x + r, sy + r * 0.8f, 4f, 0xFFB0B8C8.toInt()); G.rect(c, x - r * 1.2f, sy - r * 0.9f, x + r * 1.2f, sy - r * 0.6f, 0xFF808898.toInt()) }
            PV.GEL -> { G.circle(c, x, sy, r, 0xCC7BE84A.toInt()); G.circle(c, x - r * 0.3f, sy - r * 0.3f, r * 0.3f, 0xFFD8FFB0.toInt(), false) }
            PV.DART -> { G.line(c, x - cosf(a) * 14f, sy - sinf(a) * 14f, x, sy, 3f, 0xFF6BD3FF.toInt()) }
            PV.ONIGIRI -> {
                G.poly(c, 0xFFFFFFFF.toInt(), true, x, sy - r, x + r, sy + r * 0.8f, x - r, sy + r * 0.8f)
                G.rect(c, x - r * 0.5f, sy + r * 0.1f, x + r * 0.5f, sy + r * 0.8f, 0xFF2D4A2D.toInt(), false)
            }
            PV.SNOWBALL -> G.circle(c, x, sy, r, 0xFFFFFFFF.toInt())
            PV.ROCK -> G.circle(c, x, sy, r, 0xFF8A8494.toInt())
            PV.TRASH -> { G.rrect(c, x - r, sy - r, x + r, sy + r, 3f, 0xFF6E8C5A.toInt()) }
            PV.METEOR -> { G.circle(c, x, sy, r, 0xFFFF7A3D.toInt()); G.circle(c, x - r * 0.3f, sy - r * 0.3f, r * 0.4f, 0xFFFFD23F.toInt(), false) }
            PV.LASER -> { G.line(c, x - vx * 0.03f, sy - vy * 0.03f, x, sy, r, color) }
            else -> G.circle(c, x, sy, r, color)
        }
    }

    fun shadow(c: Canvas) {
        if (!lob && z < 30f) return
        val g = m.map.groundAt(x, y)
        if (g < -100f) return
        G.oval(c, x, y - g * ZS, r * 0.9f, r * 0.4f, 0x33000000, false)
    }

    companion object {
        fun randomConfettiColor(): Int = intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFB46CFF.toInt())[(rf() * 5).toInt().coerceIn(0, 4)]
    }
}
