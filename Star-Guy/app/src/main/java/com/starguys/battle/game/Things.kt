package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.cosf
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf

/** Area effects: Volt field, rain cloud, glue, slime puddles, meteor impacts, water jets... */
class Zone(val m: Match, val kind: Int, var x: Float, var y: Float, var r: Float, var life: Float, val owner: Body?, val team: Int) {
    var alive = true
    var t = 0f
    var follow: Body? = null
    var tick = 0f
    var dir = 0f
    val maxLife = life

    companion object {
        const val VOLT = 0; const val RAIN = 1; const val GLUE = 2; const val SLIME = 3; const val METEOR = 4; const val JET = 5
        const val FIRE = 6
    }

    fun update(dt: Float) {
        t += dt; life -= dt
        follow?.let { x = it.x; y = it.y; if (it is Fighter && it.out) life = 0f }
        tick -= dt
        val doTick = tick <= 0f
        if (doTick) tick = 0.25f
        when (kind) {
            VOLT -> if (doTick) for (b in m.hittables) {
                if (com.starguys.battle.core.dist(x, y, b.x, b.y) > r) continue
                if (m.isEnemy(team, b)) b.hurt(320f * 0.25f, owner, 0f, 0f)
                else if (b is Fighter && b !== owner) b.chargeSuper(0.11f * 0.25f * 4f)
            }
            RAIN -> {
                for (b in m.hittables) {
                    if (com.starguys.battle.core.dist(x, y, b.x, b.y) > r) continue
                    if (b is Actor) b.clearBurn()
                    if (m.isEnemy(team, b)) {
                        if (b is Actor) { b.slowT = maxOf(b.slowT, 0.3f); b.slowAmt = maxOf(b.slowAmt, 0.5f) }
                        if (doTick) b.hurt(230f * 0.25f, owner, 0f, 0f)
                    }
                }
                for (z in m.zones) if (z.kind == FIRE && com.starguys.battle.core.dist(x, y, z.x, z.y) < r + z.r) z.life = 0f
            }
            GLUE -> for (b in m.hittables) {
                if (b is Actor && m.isEnemy(team, b) && com.starguys.battle.core.dist(x, y, b.x, b.y) < r) b.glueT = maxOf(b.glueT, 0.3f)
            }
            SLIME -> for (b in m.hittables) {
                if (b is Actor && b.team != TEAM_MOBS && com.starguys.battle.core.dist(x, y, b.x, b.y) < r) { b.slowT = maxOf(b.slowT, 0.3f); b.slowAmt = maxOf(b.slowAmt, 0.45f) }
            }
            FIRE -> if (doTick) for (b in m.hittables) {
                if (b is Actor && com.starguys.battle.core.dist(x, y, b.x, b.y) < r && b.z < 20f) b.applyStatus(St.BURN, 1.2f, owner ?: m.envBody)
            }
            METEOR -> if (life <= 0f) {
                m.explode(x, y, r, 1700f, m.envBody, TEAM_NEUTRAL, 900f, color = 0xFFFF7A3D.toInt())
                m.addZone(Zone(m, FIRE, x, y, r * 0.6f, 4f, null, TEAM_NEUTRAL))
            }
            JET -> {
                dir += dt * 0.6f
                for (b in m.hittables) {
                    val d = com.starguys.battle.core.dist(x, y, b.x, b.y)
                    if (d > r || d < 4f) continue
                    val a = ang(x, y, b.x, b.y)
                    if (kotlin.math.abs(com.starguys.battle.core.angleDiff(dir, a)) < 0.35f) {
                        b.knock(cosf(dir) * 1500f * dt, sinf(dir) * 1500f * dt)
                        if (b is Actor) b.clearBurn()
                    }
                }
                if (com.starguys.battle.core.chance(0.6f)) m.fx.drop(x, y, Fx.GND + 30f, dir)
            }
        }
        if (life <= 0f) alive = false
    }

    fun drawUnder(c: Canvas) {
        val fade = (life / 0.4f).coerceIn(0f, 1f)
        G.ow = 3f
        when (kind) {
            VOLT -> {
                G.circle(c, x, y, r, G.alpha(0xFFFFE14D.toInt(), 0.16f * fade), false)
                G.ring(c, x, y, r * (0.9f + 0.1f * sinf(t * 10f)), 4f, G.alpha(0xFFFFE14D.toInt(), 0.8f * fade))
                for (k in 0 until 6) { val a = t * 2f + k * 1.047f; G.line(c, x + cosf(a) * r * 0.5f, y + sinf(a) * r * 0.5f, x + cosf(a + 0.3f) * r * 0.85f, y + sinf(a + 0.3f) * r * 0.85f, 3f, G.alpha(0xFFFFF27A.toInt(), fade)) }
            }
            RAIN -> G.circle(c, x, y, r, G.alpha(0xFF4FB5F0.toInt(), 0.25f * fade), false)
            GLUE -> { G.circle(c, x, y, r, G.alpha(0xFFB6E35A.toInt(), 0.55f * fade), false); G.circle(c, x - r * 0.3f, y, r * 0.25f, G.alpha(0xFFD8F58A.toInt(), fade), false) }
            SLIME -> { G.circle(c, x, y, r, G.alpha(0xFF7BE84A.toInt(), 0.5f * fade), false); G.circle(c, x + r * 0.2f, y - r * 0.2f, r * 0.3f, G.alpha(0xFFB5FF8A.toInt(), fade), false) }
            METEOR -> {
                val k = 1f - life / maxLife
                G.circle(c, x, y, r, G.alpha(0xFFFF3D3D.toInt(), 0.15f + 0.2f * k), false)
                G.ring(c, x, y, r, 4f, G.alpha(0xFFFF3D3D.toInt(), 0.8f))
                G.circle(c, x, y, r * k, G.alpha(0xFFFF7A3D.toInt(), 0.3f), false)
            }
            FIRE -> for (k in 0 until 5) { val a = k * 1.256f + t; G.circle(c, x + cosf(a) * r * 0.5f, y + sinf(a) * r * 0.4f - 6f, 9f + sinf(t * 12 + k) * 3f, G.alpha(0xFFFF7A3D.toInt(), fade), false) }
        }
    }

    fun drawOver(c: Canvas) {
        val fade = (life / 0.4f).coerceIn(0f, 1f)
        when (kind) {
            RAIN -> {
                val cy = y - 150f
                G.ow = 3f
                G.fade = fade
                for (k in 0 until 12) {
                    val px = x + ((k * 37.3f) % (r * 1.6f)) - r * 0.8f
                    val py = cy + 20f + ((t * 500f + k * 53f) % 140f)
                    G.line(c, px, py, px - 4f, py + 14f, 2.5f, 0xAA6BD3FF.toInt())
                }
                G.circle(c, x - r * 0.4f, cy, r * 0.32f, 0xFF8A93A8.toInt()); G.circle(c, x + r * 0.4f, cy, r * 0.32f, 0xFF8A93A8.toInt())
                G.circle(c, x, cy - r * 0.15f, r * 0.42f, 0xFF9AA3B8.toInt())
                G.fade = 1f
            }
            METEOR -> {
                val k = 1f - life / maxLife
                val h = (1f - k) * 700f
                G.ow = 3f
                G.circle(c, x + h * 0.3f, y - h, 26f, 0xFFFF7A3D.toInt())
                G.circle(c, x + h * 0.3f - 6f, y - h - 6f, 10f, 0xFFFFD23F.toInt(), false)
                G.line(c, x + h * 0.3f + 20f, y - h - 40f, x + h * 0.3f + 60f, y - h - 160f, 10f, 0x66FF7A3D)
            }
            JET -> {
                for (k in 0 until 6) {
                    val d = ((t * 400f + k * 60f) % r)
                    G.circle(c, x + cosf(dir) * d, y + sinf(dir) * d - 30f, 8f + d * 0.05f, 0xAA6BD3FF.toInt(), false)
                }
            }
        }
    }
}

/** Glitch clones, bean decoys, toy turrets. */
class Summon(m: Match, val kind: Int, val owner: Fighter) : Actor(m) {
    var life = 0f
    var dir = 0f
    var cd = 0.5f
    var animT = rf() * 5f

    companion object { const val CLONE = 0; const val DECOY = 1; const val TURRET = 2 }

    init {
        team = owner.team
        when (kind) {
            CLONE -> { hp = 1200f; life = 6f; radius = 22f }
            DECOY -> { hp = 2400f; life = 12f; radius = 22f }
            TURRET -> { hp = 1500f; life = 10f; radius = 20f; bodyH = 40f }
        }
        maxHp = hp
    }

    override val ownerF: Fighter? get() = owner
    override val hittable: Boolean get() = alive

    override fun update(dt: Float) {
        animT += dt
        tickStatus(dt)
        life -= dt
        if (life <= 0f || hp <= 0f) { alive = false; m.fx.smoke(x, y, Fx.GND + 20f, 5, 0xFFFFFFFF.toInt()); return }
        cd -= dt
        when (kind) {
            CLONE -> {
                if (rf() < dt * 0.7f) dir += rf(-1.5f, 1.5f)
                val sp = owner.hero.speed
                vx += (cosf(dir) * sp - vx) * minOf(1f, 10f * dt); vy += (sinf(dir) * sp - vy) * minOf(1f, 10f * dt)
                moveCollide(dt)
                if (hitWall) dir += 2.2f
                physicsZ(dt)
                if (cd <= 0f) {
                    val tg = m.nearestEnemyBody(this, 5f * T)
                    if (tg != null) { Fire.bullet(this, ang(x, y, tg.x, tg.y), 900f, 5f, 150f, 8f, PV.BULLET, owner.color); cd = 0.8f }
                }
            }
            DECOY -> { physicsZ(dt) }
            TURRET -> {
                physicsZ(dt)
                if (cd <= 0f) {
                    val tg = m.nearestEnemyBody(this, 7f * T)
                    if (tg != null && m.losClear(x, y, tg.x, tg.y, z + 24f)) {
                        dir = ang(x, y, tg.x, tg.y)
                        Fire.bullet(this, dir, 1000f, 7f, 240f, 7f, PV.BULLET, 0xFF7CFF8A.toInt())
                        m.sound(Sfx.SHOOT, x, y, 0.4f, 1.6f)
                        cd = 0.33f
                    } else cd = 0.2f
                }
            }
        }
    }

    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        if (!alive) return false
        hp -= d; hitFlash = 0.1f
        if (kind == CLONE) knock(kx, ky)
        m.fx.number(x, y, z + 60f, d, 0xFFFFFFFF.toInt())
        (src as? Fighter ?: src?.ownerF)?.let { if (it !== owner) it.dmgDone += 0f }
        if (hp <= 0f) { alive = false; m.fx.confetti(x, y, Fx.GND + 30f, 10) }
        return true
    }

    override fun draw(c: Canvas) {
        shadow(c)
        val sy = y - z * ZS
        c.save(); c.translate(x, sy)
        G.flash = if (hitFlash > 0) 0.6f else 0f
        G.ow = 3f
        when (kind) {
            CLONE -> {
                val lx = cosf(dir)
                if (lx < 0) c.scale(-1f, 1f)
                HeroArt.draw(c, owner.hero.id, owner.color, animT, true, kotlin.math.abs(lx), sinf(dir))
            }
            DECOY -> {
                HeroArt.draw(c, 12, 0xFFE8D8B0.toInt(), animT, false, 0.3f, 0f)
                G.line(c, 0f, 0f, 0f, 8f, 4f, 0xFF8B5A3C.toInt())
                G.circle(c, 0f, -28f, 10f, 0x00000000); G.ring(c, 0f, -28f, 9f, 3f, 0xFFFF5E7E.toInt()); G.ring(c, 0f, -28f, 4f, 3f, 0xFFFF5E7E.toInt())
            }
            TURRET -> {
                G.rrect(c, -16f, -16f, 16f, 0f, 5f, 0xFF4FB86B.toInt())
                c.save(); c.rotate(Math.toDegrees(dir.toDouble()).toFloat(), 0f, -24f)
                G.rect(c, 0f, -28f, 26f, -20f, 0xFF3A3A44.toInt())
                c.restore()
                G.circle(c, 0f, -24f, 12f, 0xFF7CFF8A.toInt())
                G.circle(c, 3f, -26f, 3.5f, G.INK, false)
            }
        }
        G.flash = 0f
        c.restore()
        // life bar
        val w = 40f
        G.rect(c, x - w / 2, sy - bodyH - 16f, x + w / 2, sy - bodyH - 11f, 0xAA1B1530.toInt(), false)
        G.rect(c, x - w / 2, sy - bodyH - 16f, x - w / 2 + w * (hp / maxHp).coerceIn(0f, 1f), sy - bodyH - 11f, 0xFFFFFFFF.toInt(), false)
    }
}

/** Items lying on the ground. */
class Pickup(m: Match, val kind: Int, val id: Int, px: Float, py: Float) : Body(m) {
    var t = rf() * 5f
    var life = -1f
    var amount = 1

    companion object { const val WEAPON = 0; const val GADGET = 1; const val ITEM = 2; const val HEAL = 3; const val GOLD = 4; const val STAR = 5 }

    init { x = px; y = py; radius = 22f; z = maxOf(0f, m.map.groundAt(px, py)) }

    override fun update(dt: Float) {
        t += dt
        if (life > 0f) { life -= dt; if (life <= 0f) alive = false }
        if (!onGround || z > gz + 1f) physicsZ(dt)
    }

    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        val b = sinf(t * 3f) * 4f
        G.oval(c, x, sy + 2f, 18f, 7f, 0x33000000, false)
        val glow = when (kind) { WEAPON -> 0xFFFFE14D.toInt(); GADGET -> 0xFF6BD3FF.toInt(); ITEM -> 0xFFFF8CC6.toInt(); HEAL -> 0xFF7CFF8A.toInt(); GOLD -> 0xFFFFD23F.toInt(); else -> 0xFFFFFFFF.toInt() }
        G.circle(c, x, sy - 22f + b, 21f, G.alpha(glow, 0.25f + 0.1f * sinf(t * 5f)), false)
        G.ow = 2.5f
        when (kind) {
            WEAPON -> Icons.weapon(c, id, x, sy - 22f + b, 20f)
            GADGET -> Icons.gadget(c, id, x, sy - 22f + b, 18f)
            ITEM -> Icons.item(c, id, x, sy - 22f + b, 17f)
            HEAL -> { G.poly(c, 0xFFFFFFFF.toInt(), true, x, sy - 38f + b, x + 15f, sy - 10f + b, x - 15f, sy - 10f + b); G.rect(c, x - 8f, sy - 20f + b, x + 8f, sy - 10f + b, 0xFF2D4A2D.toInt(), false) }
            GOLD -> { G.poly(c, 0xFFFFD23F.toInt(), true, x - 16f, sy - 12f + b, x + 16f, sy - 12f + b, x + 11f, sy - 26f + b, x - 11f, sy - 26f + b); G.line(c, x - 6f, sy - 22f + b, x + 2f, sy - 22f + b, 2.5f, 0xFFFFF3B0.toInt()) }
            STAR -> G.star(c, x, sy - 22f + b, 16f, 7f, 5, t, 0xFFFFE14D.toInt())
        }
    }
}

/** Little icons shared by pickups and the HUD. Center (x, y), half-size s. */
object Icons {
    fun weapon(c: Canvas, id: Int, x: Float, y: Float, s: Float) {
        val k = s / 20f
        c.save(); c.translate(x, y); c.scale(k, k)
        G.ow = 2.5f
        when (id) {
            Weapons.MAGNET -> { G.rect(c, -18f, -4f, 4f, 6f, 0xFF6A6A78.toInt()); G.rect(c, 4f, -10f, 12f, 12f, 0xFFE84545.toInt()); G.rect(c, 12f, -10f, 18f, 12f, 0xFFD8D8E0.toInt()) }
            Weapons.FOAM -> { G.rrect(c, -18f, -6f, 10f, 8f, 4f, 0xFF6BD3FF.toInt()); G.circle(c, 14f, 0f, 8f, 0xFFFFFFFF.toInt()) }
            Weapons.GRAPPLE -> { G.line(c, -16f, 10f, 14f, -10f, 4f, 0xFF8B5A3C.toInt()); G.line(c, -10f, -12f, 8f, 12f, 4f, 0xFF6B4028.toInt()); G.poly(c, 0xFFB0B0B8.toInt(), true, 14f, -10f, 6f, -12f, 12f, -4f) }
            Weapons.FLAME -> { G.rrect(c, -18f, -5f, 6f, 7f, 3f, 0xFFB46CFF.toInt()); G.circle(c, 12f, 0f, 8f, 0xFFFF5EC4.toInt()); G.star(c, 12f, 0f, 6f, 2.5f, 4, 0.3f, 0xFFFFFFFF.toInt(), false) }
            Weapons.BOOMERANG -> G.poly(c, 0xFFFF9A3D.toInt(), true, -16f, 10f, 0f, -14f, 16f, 10f, 10f, 12f, 0f, -2f, -10f, 12f)
            Weapons.CONFETTI -> { G.rrect(c, -18f, -4f, 6f, 6f, 3f, 0xFF8B5A3C.toInt()); G.poly(c, 0xFFB46CFF.toInt(), true, 4f, -6f, 18f, -12f, 18f, 12f, 4f, 8f); G.rect(c, 10f, -14f, 14f, -10f, 0xFFFFE14D.toInt(), false) }
            Weapons.BUBBLE -> { G.rrect(c, -16f, -3f, 4f, 7f, 3f, 0xFF35C6F4.toInt()); G.circle(c, 12f, -4f, 8f, 0x8890E0FF.toInt()) }
            Weapons.RAIL -> { G.rect(c, -18f, -5f, 18f, 5f, 0xFF4A5480.toInt()); G.line(c, -14f, 0f, 16f, 0f, 3f, 0xFF7CF0FF.toInt()) }
            Weapons.SUCKER -> { G.rrect(c, -16f, -4f, 6f, 6f, 3f, 0xFF6A6A78.toInt()); G.circle(c, 12f, 1f, 7f, 0xFFFF5E7E.toInt()) }
            Weapons.BAT -> { G.line(c, -14f, 12f, 12f, -12f, 9f, 0xFFD2A36A.toInt()); G.line(c, -14f, 12f, -8f, 6f, 5f, 0xFF6B4028.toInt()) }
            Weapons.HAMMER -> { G.line(c, -12f, 14f, 6f, -6f, 5f, 0xFF8B5A3C.toInt()); G.rrect(c, -2f, -18f, 18f, -2f, 3f, 0xFF8A93A8.toInt()) }
            Weapons.SWORD -> { G.line(c, -14f, 14f, 14f, -14f, 6f, 0xFFDDEEFF.toInt()); G.line(c, -12f, 4f, -4f, 12f, 5f, 0xFFFFD23F.toInt()) }
            Weapons.PAN, Weapons.GOLDPAN -> { G.line(c, -16f, 14f, -4f, 2f, 5f, 0xFF3A3A44.toInt()); G.circle(c, 6f, -6f, 12f, if (id == Weapons.GOLDPAN) 0xFFFFD23F.toInt() else 0xFF4A4A55.toInt()) }
            Weapons.HUNTER -> { G.rrect(c, -18f, -6f, 16f, 6f, 3f, 0xFFFF3D3D.toInt()); G.rect(c, -6f, 6f, 0f, 14f, 0xFF3A3A44.toInt()) }
            else -> G.circle(c, 0f, 0f, 12f, 0xFFFFE14D.toInt())
        }
        c.restore()
    }

    fun gadget(c: Canvas, id: Int, x: Float, y: Float, s: Float) {
        val k = s / 20f
        c.save(); c.translate(x, y); c.scale(k, k)
        G.ow = 2.5f
        when (id) {
            Gadgets.TRAMPOLINE -> { G.oval(c, 0f, 4f, 18f, 8f, 0xFF5BD3FF.toInt()); G.line(c, -12f, 8f, -14f, 16f, 3f, G.INK); G.line(c, 12f, 8f, 14f, 16f, 3f, G.INK) }
            Gadgets.WALL -> { G.rrect(c, -18f, -14f, 18f, 14f, 6f, 0xFFFF8CC6.toInt()); G.line(c, -6f, -14f, -6f, 14f, 2f, 0x55000000); G.line(c, 6f, -14f, 6f, 14f, 2f, 0x55000000) }
            Gadgets.DECOY -> { G.oval(c, 0f, 0f, 10f, 16f, 0xFFE8D8B0.toInt()); G.ring(c, 0f, 0f, 6f, 2.5f, 0xFFFF5E7E.toInt()) }
            Gadgets.TURRET -> { G.rrect(c, -12f, 4f, 12f, 14f, 3f, 0xFF4FB86B.toInt()); G.circle(c, 0f, -2f, 9f, 0xFF7CFF8A.toInt()); G.rect(c, 4f, -5f, 18f, 1f, 0xFF3A3A44.toInt()) }
            Gadgets.JETPACK -> { G.rrect(c, -14f, -14f, -2f, 10f, 4f, 0xFFB0B8C8.toInt()); G.rrect(c, 2f, -14f, 14f, 10f, 4f, 0xFFB0B8C8.toInt()); G.circle(c, -8f, 14f, 4f, 0xFFFFC94D.toInt(), false); G.circle(c, 8f, 14f, 4f, 0xFFFFC94D.toInt(), false) }
            Gadgets.PARACHUTE -> { G.pie(c, 0f, 0f, 18f, 180f, 180f, 0xFFFF6B6B.toInt()); G.line(c, -16f, 0f, 0f, 16f, 2f, G.INK); G.line(c, 16f, 0f, 0f, 16f, 2f, G.INK) }
            Gadgets.BANANA -> { G.poly(c, 0xFFFFE14D.toInt(), true, -16f, 0f, 0f, 8f, 16f, 0f, 10f, 12f, 0f, 16f, -10f, 12f) }
            Gadgets.GLUE -> { G.circle(c, 0f, 2f, 13f, 0xFFB6E35A.toInt()); G.rect(c, -4f, -16f, 4f, -9f, 0xFF6A6A78.toInt()) }
            Gadgets.RADAR -> { G.circle(c, 0f, 0f, 16f, 0xFF1E2A3A.toInt()); G.ring(c, 0f, 0f, 9f, 2f, 0xFF35C6F4.toInt()); G.line(c, 0f, 0f, 12f, -10f, 2.5f, 0xFF35C6F4.toInt()) }
        }
        c.restore()
    }

    fun item(c: Canvas, id: Int, x: Float, y: Float, s: Float) {
        val k = s / 20f
        c.save(); c.translate(x, y); c.scale(k, k)
        G.ow = 2.5f
        when (id) {
            Items.SODA -> { G.rrect(c, -9f, -16f, 9f, 16f, 4f, 0xFFFF5E7E.toInt()); G.rect(c, -9f, -4f, 9f, 4f, 0xFFFFFFFF.toInt(), false) }
            Items.LOLLIPOP -> { G.line(c, 0f, 0f, 0f, 20f, 3f, 0xFFFFFFFF.toInt()); G.circle(c, 0f, -6f, 13f, 0xFFFF8CC6.toInt()); G.arc(c, 0f, -6f, 7f, 0f, 270f, 3f, 0xFFFFFFFF.toInt()) }
            Items.SHIELD -> { G.circle(c, 0f, 0f, 16f, 0x996BD3FF.toInt()); G.circle(c, -6f, -6f, 4f, 0xCCFFFFFF.toInt(), false) }
            Items.MUSHROOM -> { G.rect(c, -5f, 0f, 5f, 14f, 0xFFFFF3E0.toInt()); G.pie(c, 0f, 2f, 16f, 180f, 180f, 0xFFE84545.toInt()); G.circle(c, -6f, -6f, 3f, 0xFFFFFFFF.toInt(), false); G.circle(c, 6f, -8f, 2.5f, 0xFFFFFFFF.toInt(), false) }
        }
        c.restore()
    }
}
