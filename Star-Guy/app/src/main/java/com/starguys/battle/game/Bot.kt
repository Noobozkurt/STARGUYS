package com.starguys.battle.game

import com.starguys.battle.core.Save
import com.starguys.battle.core.ang
import com.starguys.battle.core.angleDiff
import com.starguys.battle.core.chance
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.len
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.core.sinf
import kotlin.math.abs
import com.starguys.battle.core.atan2

/** Bot brain for AI-controlled heroes. */
class Bot(val f: Fighter) {
    private val m = f.m
    private val diff = f.m.cfg.difficulty
    private val reaction = floatArrayOf(0.42f, 0.26f, 0.14f)[diff]
    private val aimErr = floatArrayOf(0.28f, 0.14f, 0.05f)[diff]
    private val vision = 9f * T
    private var think = rf() * 0.3f
    var target: Body? = null
    val goal = Goal()
    private val pf = PathFollower(m)
    private var strafe = if (chance(0.5f)) 1f else -1f
    private var strafeT = 0f
    private var stuckT = 0f
    private var lastX = 0f; private var lastY = 0f
    private var pingX = 0f; private var pingY = 0f; private var pingT = 0f
    var wantsCannon = false
    private var wanderX = -1f; private var wanderY = -1f
    private var dodgeT = 0f; private var dodgeA = 0f
    private var holdFire = 0f
    private var personality = rf()

    fun ping(x: Float, y: Float) { pingX = x; pingY = y; pingT = 6f }

    fun update(dt: Float) {
        if (f.out) return
        think -= dt; strafeT -= dt; pingT -= dt; dodgeT -= dt; holdFire -= dt
        if (think <= 0f) { think = reaction + rf() * 0.15f; decide() }
        move(dt)
        combat()
    }

    private fun visibleEnemies(): Body? {
        var best: Body? = null; var bs = Float.MAX_VALUE
        for (b in m.hittables) {
            if (b === f || !m.isEnemy(f.team, b) || b is Breakable || b is Ball) continue
            if (b is Vehicle && b.driver == null) continue
            if (b is Fighter && !m.canSee(f, b)) continue
            val d = dist(f.x, f.y, b.x, b.y)
            if (d > vision) continue
            var score = d
            if (b is Summon && b.kind == Summon.DECOY) score *= 0.4f
            if (b is Actor) score *= 0.6f + 0.4f * (b.hp / b.maxHp)
            if (b === target) score *= 0.75f
            if (b is Fighter && b.human && diff == 0) score *= 1.3f
            if (score < bs && m.losClear(f.x, f.y, b.x, b.y, f.z + 26f)) { bs = score; best = b }
        }
        return best
    }

    private fun decide() {
        target = visibleEnemies()
        goal.reset()
        m.mode.botGoal(f, goal)
        goal.target?.let { target = it }
        val tg = target
        // consumables
        if (f.items.isNotEmpty()) {
            val it = f.items[0]
            when {
                it == Items.LOLLIPOP && f.hp < f.maxHp * 0.5f -> f.useItem()
                it == Items.SHIELD && tg != null && f.hp < f.maxHp * 0.8f -> f.useItem()
                (it == Items.SODA || it == Items.MUSHROOM) && tg != null && dist(f.x, f.y, tg.x, tg.y) < 6f * T -> f.useItem()
                it == Items.SODA && goal.has && goal.urgency > 0.8f -> f.useItem()
            }
        }
        // gadgets
        val g = f.gadget
        if (g != null && f.gadgetCharges > 0) {
            val d = if (tg != null) dist(f.x, f.y, tg.x, tg.y) else 9999f
            when (g.id) {
                Gadgets.TURRET, Gadgets.DECOY -> if (d < 6f * T) f.useGadget(if (tg != null) ang(f.x, f.y, tg.x, tg.y) else f.face)
                Gadgets.GLUE -> if (d < 4.5f * T && tg != null) f.useGadget(ang(f.x, f.y, tg.x, tg.y))
                Gadgets.RADAR -> if (tg == null && chance(0.1f)) f.useGadget(f.face)
                Gadgets.BANANA -> if (tg != null && d < 3f * T && chance(0.3f)) f.useGadget(f.face)
                Gadgets.WALL -> if (tg != null && f.hp < f.maxHp * 0.5f && f.lastHurtT < 0.5f) f.useGadget(ang(f.x, f.y, tg.x, tg.y))
                Gadgets.JETPACK -> if (stuckT > 1f) f.useGadget(f.face)
                Gadgets.TRAMPOLINE -> if (stuckT > 1.5f || (tg == null && chance(0.02f))) f.useGadget(f.face)
            }
        }
        // super
        if (f.ready && f.vehicle == null) {
            val h = f.hero
            if (h.superAim == Aim.SELF) {
                val near = tg != null && dist(f.x, f.y, tg.x, tg.y) < h.superRange * T * 1.2f
                val allyNear = h.id == 1 && m.fighters.any { it !== f && !it.out && it.team == f.team && dist(it.x, it.y, f.x, f.y) < 3f * T }
                if (near || allyNear || (h.id == 8 && f.lastHurtT < 0.3f)) f.useSuper(f.face, 0f)
            } else if (tg != null) {
                val d = dist(f.x, f.y, tg.x, tg.y)
                if (h.id == 4) {
                    val hurt = m.fighters.firstOrNull { !it.out && it.team == f.team && it.hp < it.maxHp * 0.55f && dist(it.x, it.y, f.x, f.y) < h.superRange * T }
                    if (hurt != null) f.useSuper(ang(f.x, f.y, hurt.x, hurt.y), dist(f.x, f.y, hurt.x, hurt.y))
                } else if (h.id == 3 && f.hp < f.maxHp * 0.35f) {
                    f.useSuper(ang(tg.x, tg.y, f.x, f.y), h.superRange * T)
                } else if (d < h.superRange * T * 1.05f) {
                    val a = ang(f.x, f.y, tg.x + tg.vx * 0.3f, tg.y + tg.vy * 0.3f) + rf(-aimErr, aimErr)
                    f.useSuper(a, d)
                }
            }
        }
        // vehicles
        if (f.vehicle == null && tg == null && !goal.has && chance(0.15f + personality * 0.2f)) {
            for (v in m.vehicles) if (v.alive && v.driver == null && dist(f.x, f.y, v.x, v.y) < 1.4f * T && v.kind != Vehicle.COASTER) { m.enterVehicle(f, v); break }
        }
        val v = f.vehicle
        if (v != null) {
            if ((v.kind == Vehicle.DUCK && m.map.t(v.tileX(), v.tileY()) != Tl.WATER && chance(0.2f)) || (v.hp < v.maxHp * 0.25f) || (goal.has && goal.urgency > 0.85f && v.kind == Vehicle.CRANE) || chance(0.01f)) m.exitVehicle(f)
        }
        // loot machines when unarmed
        if (f.weapon == null && f.modeWeapon == null && tg == null) {
            for (p in m.props) if (p is LootMachine && p.cd <= 0f && p.anim <= 0f && dist(f.x, f.y, p.x, p.y) < 1.4f * T) { p.interact(f); break }
        }
        // swap pickups occasionally
        if (chance(0.1f)) for (p in m.pickups) if (p.alive && dist(f.x, f.y, p.x, p.y) < T * 0.8f && (p.kind == Pickup.WEAPON || p.kind == Pickup.GADGET)) { m.interact(f); break }
        wantsCannon = !goal.has && tg == null && chance(0.3f)
        if (strafeT <= 0f) { strafe = -strafe; strafeT = rf(0.6f, 1.6f) }
    }

    private fun move(dt: Float) {
        var mx = 0f; var my = 0f
        val tg = target
        val tgAlive = tg != null && tg.alive && !(tg is Fighter && tg.out)
        if (!tgAlive) target = null
        val d = if (tgAlive) dist(f.x, f.y, tg!!.x, tg.y) else 9999f
        val pref = maxOf(1f, minOf(f.hero.prefRange, f.currentWeapon().range * 0.8f)) * T
        val lowHp = f.hp < f.maxHp * 0.3f && !m.mode.percentMode
        if (goal.stay) { f.moveX = 0f; f.moveY = 0f; return }
        if (goal.has && (target == null || goal.urgency >= 0.7f || goal.flee)) {
            if (goal.flee && tgAlive) {
                val a = ang(tg!!.x, tg.y, f.x, f.y)
                val fx = f.x + cosf(a) * 4f * T; val fy = f.y + sinf(a) * 4f * T
                pf.steer(f, fx, fy, dt)
                mx = pf.dirX + cosf(a) * 0.5f; my = pf.dirY + sinf(a) * 0.5f
            } else {
                val arrived = pf.steer(f, goal.x, goal.y, dt)
                if (!arrived) { mx = pf.dirX; my = pf.dirY }
                else if (tgAlive) { mx = -sinf(ang(f.x, f.y, tg!!.x, tg.y)) * strafe * 0.6f; my = cosf(ang(f.x, f.y, tg.x, tg.y)) * strafe * 0.6f }
            }
        } else if (tgAlive) {
            val a = ang(f.x, f.y, tg!!.x, tg.y)
            val sees = m.losClear(f.x, f.y, tg.x, tg.y, f.z + 26f)
            if (lowHp && f.hero.hp < 5000f && !(tg is Mob && tg.isBoss.not() && tg.kind == Mob.GNOME)) {
                pf.steer(f, f.x - cosf(a) * 4f * T, f.y - sinf(a) * 4f * T, dt)
                mx = pf.dirX; my = pf.dirY
            } else if (d > pref + T * 0.8f || !sees) {
                pf.steer(f, tg.x, tg.y, dt); mx = pf.dirX; my = pf.dirY
                mx += -sinf(a) * strafe * 0.35f; my += cosf(a) * strafe * 0.35f
            } else if (d < pref - T) {
                mx = -cosf(a) + -sinf(a) * strafe * 0.6f; my = -sinf(a) + cosf(a) * strafe * 0.6f
            } else {
                mx = -sinf(a) * strafe; my = cosf(a) * strafe
            }
        } else if (pingT > 0f) {
            if (!pf.steer(f, pingX, pingY, dt)) { mx = pf.dirX; my = pf.dirY }
        } else {
            // wander / look for loot
            if (wanderX < 0f || dist(f.x, f.y, wanderX, wanderY) < T || chance(dt * 0.1f)) pickWander()
            if (!pf.steer(f, wanderX, wanderY, dt)) { mx = pf.dirX; my = pf.dirY }
        }
        // dodge incoming projectiles (harder bots dodge better)
        if (dodgeT <= 0f && diff > 0) {
            for (p in m.projs) {
                if (!p.alive || p.team == f.team || p.lob) continue
                val dd = dist(p.x, p.y, f.x, f.y)
                if (dd > 3f * T || dd < 10f) continue
                val pa = atan2(p.vy, p.vx); val toMe = ang(p.x, p.y, f.x, f.y)
                if (abs(angleDiff(pa, toMe)) < 0.25f && chance(if (diff == 2) 0.5f else 0.2f)) { dodgeT = 0.35f; dodgeA = pa + 1.57f * (if (chance(0.5f)) 1f else -1f); break }
            }
        }
        if (dodgeT > 0f) { mx = cosf(dodgeA); my = sinf(dodgeA) }
        // avoid void/lava edges when not jumping on purpose
        val l = len(mx, my)
        if (l > 0.01f) {
            mx /= l; my /= l
            val ax = f.x + mx * (f.radius + 18f); val ay = f.y + my * (f.radius + 18f)
            val gtz = m.map.groundAt(ax, ay)
            val tp = m.map.tAt(ax, ay)
            if ((gtz <= VOID_Z + 1 || tp == Tl.LAVA) && !pf.needJump && f.onGround && m.platformAt(ax, ay, f.z) == null) {
                // edge: jump only if the far side is solid ground
                val bx = f.x + mx * 1.6f * T; val by = f.y + my * 1.6f * T
                if (m.map.groundAt(bx, by) > VOID_Z + 1 && m.map.tAt(bx, by) != Tl.LAVA) f.jump() else { mx = -mx * 0.3f; my = -my * 0.3f }
            }
        }
        // predictive braking: don't drift into the void (zero-G, ice, knockback)
        if (f.onGround && !pf.needJump) {
            val look = if (m.zeroG) 0.55f else 0.22f
            val px = f.x + f.vx * look; val py = f.y + f.vy * look
            if (m.map.groundAt(px, py) <= VOID_Z + 1 && m.platformAt(px, py, f.z) == null) {
                val vl = len(f.vx, f.vy).coerceAtLeast(1f)
                mx = -f.vx / vl; my = -f.vy / vl
            }
        }
        f.moveX = mx; f.moveY = my
        if (pf.needJump && f.onGround) f.jump()
        // stuck detection
        val moved = dist(f.x, f.y, lastX, lastY)
        lastX = f.x; lastY = f.y
        if (l > 0.3f && moved < 0.6f) stuckT += dt else stuckT = maxOf(0f, stuckT - dt)
        if (stuckT > 0.8f && f.onGround) { f.jump(); if (stuckT > 1.6f) { pickWander(); strafe = -strafe; stuckT = 0f } }
    }

    private fun pickWander() {
        // prefer nearby loot
        var best: Pickup? = null; var bd = 8f * T
        for (p in m.pickups) {
            if (!p.alive) continue
            val useful = when (p.kind) { Pickup.WEAPON -> f.weapon == null && f.modeWeapon == null; Pickup.GADGET -> f.gadget == null; Pickup.ITEM -> f.items.size < 3; Pickup.HEAL -> f.hp < f.maxHp * 0.8f; else -> false }
            if (!useful) continue
            val d = dist(f.x, f.y, p.x, p.y)
            if (d < bd) { bd = d; best = p }
        }
        if (best != null) { wanderX = best.x; wanderY = best.y; return }
        val p = m.randomSpawn(0f)
        wanderX = p[0]; wanderY = p[1]
    }

    private fun combat() {
        val tg = target ?: return
        if (f.out || holdFire > 0f) return
        if (goal.stay) return
        val w = f.currentWeapon()
        val d = dist(f.x, f.y, tg.x, tg.y)
        val range = w.range * T * f.rangeMul()
        if (d > range * 1.02f) return
        if (!f.canAttack()) return
        if (w.aim != Aim.LOB && !m.losClear(f.x, f.y, tg.x, tg.y, f.z + 26f)) return
        // keep the last ammo bar when the target is far
        if (f.weapon == null && f.modeWeapon == null && f.ammo < 2f && d > range * 0.8f && chance(0.5f)) { holdFire = 0.3f; return }
        val speed = when (w.aim) { Aim.LOB -> 0f; Aim.ARC -> 0f; else -> 1000f }
        val lead = if (speed > 0f) d / speed else 0.55f
        val leadMul = floatArrayOf(0.3f, 0.65f, 0.95f)[diff]
        val tx = tg.x + tg.vx * lead * leadMul; val ty = tg.y + tg.vy * lead * leadMul
        val a = ang(f.x, f.y, tx, ty) + rf(-aimErr, aimErr) * (if (f.blindT > 0f) 4f else 1f)
        f.attack(a, dist(f.x, f.y, tx, ty))
        if (!w.auto) holdFire = floatArrayOf(0.5f, 0.25f, 0.08f)[diff] + rf() * 0.2f
    }
}
