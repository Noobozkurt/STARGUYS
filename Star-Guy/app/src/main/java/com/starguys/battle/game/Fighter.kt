package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.angleDiff
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.len
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf
import kotlin.math.abs
import com.starguys.battle.core.atan2

/** A hero (the player or a bot). */
class Fighter(m: Match, hero: HeroDef, var name: String, team: Int, var human: Boolean, var skin: Int = 0) : Actor(m) {
    /** id of the human controlling this fighter (multiplayer), -1 for bots */
    var netId = -1
    var hero: HeroDef = hero
        private set
    var color = hero.colors[skin.coerceIn(0, hero.colors.size - 1)]
    var ammo = 3f
    var fireCd = 0f
    var superCharge = 0f
    var weapon: WeaponDef? = null
    var wAmmo = 0
    /** weapon imposed by the mode (melee mode, gun game, hunter) - infinite ammo */
    var modeWeapon: WeaponDef? = null
    var unarmed = false
    var gadget: GadgetDef? = null
    var gadgetCharges = 0
    val items = ArrayList<Int>()
    var parachutes = 0

    var speedT = 0f; var invisT = 0f; var shieldHp = 0f; var giantT = 0f
    var regenT = 0f; var regenRate = 0f; var revealT = 0f; var dashT = 0f
    var superT = 0f; var superKind = -1; var absorbStore = 0f
    private val superHits = HashMap<Body, Float>()
    var growth = 0
    var dmgPct = 0f
    var stocks = 3
    var vehicle: Vehicle? = null
    var heldBy: Vehicle? = null
    var carry = 0
    var flag: FlagProp? = null
    var infected = false
    var disguise = -1
    var mirrorT = 0f
    var spawnShield = 0f
    var hasDived = false
    var hunterBoost = 1f
    var zipline: Zipline? = null
    var zipT = 0f
    var teleCd = 0f
    var cannonT = 0f

    var kills = 0; var deaths = 0; var dmgDone = 0f; var score = 0f; var place = 0
    var out = false
    var respawnT = 0f
    var eliminatedAt = 0f
    var finished = false

    var moveX = 0f; var moveY = 0f
    var face = 0f
    var aimT = 0f
    var animT = rf() * 10f
    var brain: Bot? = null
    var emote = 0f
    var lastMoveT = 0f
    private var numAcc = 0f
    private var numT = 0f

    val scale: Float get() = (if (giantT > 0) 1.5f else 1f) * (1f + growth * 0.12f).coerceAtMost(2.4f) * (if (hero.id == 0 && superKind == 0 && superT > 0) 1.25f else 1f)

    init {
        this.team = team
        maxHp = hero.hp; hp = maxHp
        radius = 22f; bodyH = 56f
    }

    override val hittable: Boolean get() = alive && !out && heldBy == null && cannonT <= 0f
    override val solid: Boolean get() = !out
    val ready get() = superCharge >= 1f

    fun setHero(h: HeroDef) {
        hero = h; color = h.colors[skin.coerceIn(0, h.colors.size - 1)]
        maxHp = h.hp; hp = maxHp
    }

    fun currentWeapon(): WeaponDef = modeWeapon ?: weapon ?: hero.attack

    fun dmgMul(): Float {
        var d = 1f + growth * 0.15f
        if (giantT > 0) d *= 1.3f
        if (hero.id == 13) d *= 1f
        return d * hunterBoost
    }

    fun rangeMul(): Float = if (vehicle?.kind == Vehicle.BALLOON) 1.5f else 1f

    fun speedNow(): Float {
        var s = hero.speed * statusSpeedMul()
        if (speedT > 0) s *= 1.45f
        if (carry > 0) s *= 0.62f
        if (flag != null) s *= 0.88f
        if (growth > 0) s *= (1f - growth * 0.025f).coerceAtLeast(0.78f)
        val tp = m.map.t(tileX(), tileY())
        if (onGround) {
            if (tp == Tl.WATER) s *= 0.55f
            if (tp == Tl.PAINT) s *= 0.7f
            if (tp == Tl.GLUE) s *= 0.4f
        }
        s *= m.mode.speedMul(this)
        if (superKind == 10 && superT > 0) s *= 1.1f
        return s
    }

    // ------------------------------------------------------------------ update
    override fun update(dt: Float) {
        animT += dt
        if (out) return
        tickStatus(dt)
        if (spawnShield > 0) spawnShield -= dt
        if (speedT > 0) speedT -= dt
        if (invisT > 0) invisT -= dt
        if (giantT > 0) giantT -= dt
        if (revealT > 0) revealT -= dt
        if (mirrorT > 0) mirrorT -= dt
        if (dashT > 0) dashT -= dt
        if (teleCd > 0) teleCd -= dt
        if (emote > 0) emote -= dt
        if (regenT > 0) { regenT -= dt; heal(regenRate * dt, false) }
        fireCd -= dt; aimT += dt
        radius = 22f * scale.coerceAtMost(2f)
        bodyH = 56f * scale

        // reload hero attack
        val w = hero.attack
        if (ammo < 3f) ammo = minOf(3f, ammo + dt / w.reload * m.mode.reloadMul(this))
        // auto-heal when out of combat
        if (m.mode.autoHeal && lastHurtT > 3.2f && aimT > 3.2f && hp < maxHp && burnT <= 0) heal(maxHp * 0.12f * dt, false)

        updateSuper(dt)

        if (cannonT > 0) { cannonT -= dt; updateCannonFlight(dt); return }
        val zl = zipline
        if (zl != null) { zl.ride(this, dt); return }
        val hb = heldBy
        if (hb != null) { x = hb.clawX(); y = hb.clawY(); z = hb.z + 40f; vx = 0f; vy = 0f; return }
        val v = vehicle
        if (v != null) {
            x = v.x; y = v.y; z = v.z + v.seatZ; gz = v.gz
            if (moveX != 0f || moveY != 0f) face = atan2(moveY, moveX)
            return
        }

        val canControl = stunT <= 0 && bubbleT <= 0 && ragdollT <= 0
        var mx = moveX; var my = moveY
        if (m.mode.mirrorActive(this)) { mx = -mx; my = -my }
        if (!canControl) { mx = 0f; my = 0f }
        val sp = speedNow()
        val rolling = superT > 0 && (superKind == 0 || superKind == 2)
        if (!rolling && dashT <= 0) {
            val tile = m.map.t(tileX(), tileY())
            val accel = when {
                ragdollT > 0 -> 2.2f
                m.zeroG -> 3.2f
                onGround && tile == Tl.ICE -> 1.6f
                !onGround -> 5f
                else -> 16f
            }
            val k = minOf(1f, accel * dt)
            vx += (mx * sp - vx) * k
            vy += (my * sp - vy) * k
        }
        if (mx != 0f || my != 0f) {
            lastMoveT = 0f
            if (aimT > 0.6f) face = atan2(my, mx)
        } else lastMoveT += dt
        // conveyors
        if (onGround) {
            val tp = m.map.t(tileX(), tileY())
            if (tp == Tl.CONVEYOR) {
                val d = m.map.aux(tileX(), tileY())
                x += DIRX[d] * 150f * dt; y += DIRY[d] * 150f * dt
            } else if (tp == Tl.SPEED) {
                val d = m.map.aux(tileX(), tileY())
                speedT = maxOf(speedT, 1.2f)
                vx += DIRX[d] * 900f * dt; vy += DIRY[d] * 900f * dt
            } else if (tp == Tl.COLLAPSE) m.map.stepOn(tileX(), tileY())
            else if (tp == Tl.LAVA) {
                if (vz <= 0f) { vz = 420f; hurt(700f, m.envBody, 0f, 0f); applyStatus(St.BURN, 1.5f, m.envBody); m.sound(Sfx.BOING, x, y, 0.7f) }
            } else if (tp == Tl.WATER || tp == Tl.PAINT) { if (burnT > 0) clearBurn() }
        }
        val pl = platform
        if (pl != null && onGround) { x += pl.dx; y += pl.dy }
        moveCollide(dt)
        if (rolling && hitWall) {
            // bounce off walls during rolling supers
            val dot = vx * wallNx + vy * wallNy
            vx -= 2 * dot * wallNx; vy -= 2 * dot * wallNy
            val sp2 = if (superKind == 0) 560f else 760f
            val l = len(vx, vy).coerceAtLeast(1f); vx = vx / l * sp2; vy = vy / l * sp2
            m.sound(Sfx.BOING, x, y, 0.6f)
        }
        val gs = gravScale
        if (superKind == 10 && superT > 0 && vz < 0) gravScale = 0.22f
        val impact = physicsZ(dt)
        gravScale = gs
        if (impact > 0f) {
            if (hasDived) { ragdollT = maxOf(ragdollT, 0.25f); hasDived = false }
            if (impact > 650f) { m.fx.smoke(x, y, z, 3, 0xFFFFFFFF.toInt()); m.sound(Sfx.THUD, x, y, 0.5f) }
        }
        m.mode.afterMove(this, dt)
    }

    private fun updateCannonFlight(dt: Float) {
        x += vx * dt; y += vy * dt
        vz -= GRAV * 0.6f * m.gravity * dt; z += vz * dt
        val g = m.map.groundAt(x, y)
        if (z <= g && vz < 0) {
            z = maxOf(g, z); cannonT = 0f; vx *= 0.2f; vy *= 0.2f
            m.fx.smoke(x, y, Fx.GND, 5, 0xFFFFFFFF.toInt()); m.sound(Sfx.THUD, x, y)
        }
        x = clamp(x, 20f, m.map.pxW - 20f); y = clamp(y, 20f, m.map.pxH - 20f)
        if (cannonT <= 0f) { z = maxOf(z, m.map.groundAt(x, y).coerceAtLeast(0f)) }
    }

    private fun updateSuper(dt: Float) {
        if (superT <= 0) return
        superT -= dt
        when (superKind) {
            0, 2 -> { // rolling contact damage
                val dmg = if (superKind == 0) 820f else 1500f
                val kn = if (superKind == 0) 950f else 1100f
                for (b in m.hittables) {
                    if (b === this || !m.isEnemy(team, b)) continue
                    if (dist(b) > radius + b.radius + 6f) continue
                    val last = superHits[b] ?: -9f
                    if (m.time - last < 0.45f) continue
                    superHits[b] = m.time
                    val a = ang(x, y, b.x, b.y)
                    b.hurt(dmg * dmgMul(), this, cosf(a) * kn, sinf(a) * kn)
                    if (b is Actor) b.ragdollT = maxOf(b.ragdollT, 0.5f)
                    m.fx.ring(b.x, b.y, b.z + 20f, 30f, 0xFFFFFFFF.toInt())
                    m.sound(Sfx.BOING, x, y)
                    if (superKind == 0) { vz = 300f }
                }
                if (superT <= 0) { superHits.clear(); vx *= 0.3f; vy *= 0.3f }
            }
            8 -> if (superT <= 0) { // Lady Mousse release
                val d = maxOf(700f, absorbStore * 1.1f)
                m.explode(x, y, 3f * T, d * dmgMul(), this, team, 900f, tiles = false, color = 0xFFFFE14D.toInt())
                m.fx.text(x, y, z + 70f, "RENVOI ${d.toInt()}", 0xFFFFE14D.toInt())
                absorbStore = 0f
            }
        }
        if (superT <= 0) superKind = -1
    }

    // ------------------------------------------------------------------ actions
    fun canAct() = !out && stunT <= 0 && bubbleT <= 0 && heldBy == null && cannonT <= 0f && zipline == null

    fun canAttack(): Boolean {
        if (!canAct() || unarmed && weapon == null) return false
        if (fireCd > 0f) return false
        val v = vehicle
        if (v != null && !v.allowsShooting) return v.kind == Vehicle.TANK || v.kind == Vehicle.CRANE
        if (modeWeapon != null || weapon != null) return true
        return ammo >= 1f
    }

    fun attack(a: Float, d: Float) {
        if (!canAttack()) return
        val v = vehicle
        if (v != null && (v.kind == Vehicle.TANK || v.kind == Vehicle.CRANE)) { v.fire(this, a, d); fireCd = v.fireCd(); return }
        val w = currentWeapon()
        when {
            modeWeapon != null -> {}
            weapon != null -> { wAmmo--; }
            else -> ammo -= 1f
        }
        fireCd = w.cooldown
        face = a; aimT = 0f
        if (invisT > 0 && hero.id == 3) invisT = minOf(invisT, 0.3f)
        if (disguise >= 0) disguise = -1
        w.fire(this, a, d)
        if (m.zeroG && w.recoil > 0f) knock(-cosf(a) * w.recoil * 0.9f, -sinf(a) * w.recoil * 0.9f)
        if (weapon != null && wAmmo <= 0) { weapon = null; if (isLocal) m.hud.toastSmall("Plus de munitions !") }
    }

    fun useSuper(a: Float, d: Float) {
        if (!ready || !canAct() || vehicle != null) return
        superCharge = 0f
        face = a; aimT = 0f
        Supers.activate(this, a, d)
        m.sound(Sfx.SUPER, x, y)
        if (isLocal) Sfx.buzz(30)
    }

    fun chargeSuper(amount: Float) { superCharge = minOf(1f, superCharge + amount) }

    fun onDealt(d: Float, victim: Body) {
        dmgDone += d
        if (victim is Fighter || victim is Mob) chargeSuper(d / hero.superCost * m.mode.superMul)
        if (isLocal) m.hud.hitMarker()
    }

    fun jump() {
        if (!canAct()) return
        val v = vehicle
        if (v != null) { if (v.kind == Vehicle.COASTER || v.kind == Vehicle.BALLOON) m.exitVehicle(this); return }
        if (onGround || (z - gz < 6f && platform != null)) {
            vz = 420f * (if (giantT > 0) 1.1f else 1f)
            onGround = false
            m.sound(Sfx.JUMP, x, y, 0.6f)
            m.fx.smoke(x, y, z, 2, 0xFFFFFFFF.toInt())
        } else if (!hasDived && ragdollT <= 0) {
            // Dive!
            hasDived = true
            val a = if (moveX != 0f || moveY != 0f) atan2(moveY, moveX) else face
            vx = cosf(a) * speedNow() * 1.7f; vy = sinf(a) * speedNow() * 1.7f
            vz = maxOf(vz, 160f)
            m.sound(Sfx.SWING, x, y, 0.5f)
        }
    }

    fun useGadget(a: Float) {
        val g = gadget ?: return
        if (!canAct() || gadgetCharges <= 0) return
        if (Supers.gadget(this, g.id, a)) {
            gadgetCharges--
            if (gadgetCharges <= 0) gadget = null
        }
    }

    fun useItem() {
        if (items.isEmpty() || !canAct()) return
        val it = items.removeAt(0)
        when (it) {
            Items.SODA -> { speedT = 6f; m.fx.text(x, y, z + 70f, "VITESSE !", 0xFFFF5E7E.toInt()) }
            Items.LOLLIPOP -> { regenT = 5f; regenRate = maxHp * 0.1f; m.sound(Sfx.HEAL, x, y) }
            Items.SHIELD -> { shieldHp = 2000f; m.sound(Sfx.BUBBLE, x, y) }
            Items.MUSHROOM -> { giantT = 10f; m.sound(Sfx.SUPER, x, y, 0.6f, 0.7f) }
        }
        m.sound(Sfx.PICKUP, x, y, 0.5f)
    }

    // ------------------------------------------------------------------ damage
    override fun hurt(d: Float, src: Body?, kx: Float, ky: Float): Boolean {
        if (out || !alive || d <= 0f) return false
        if (spawnShield > 0f || cannonT > 0f) return false
        val atk: Fighter? = (src as? Fighter) ?: src?.ownerF
        if (atk != null && atk !== this && !m.mode.canHurt(atk, this)) return false
        if (atk === this && src !== m.envBody && src !is Projectile) return false
        var dmg = d * m.mode.damageTakenMul(this)
        // umbrella blocks frontal shots
        if (superKind == 10 && superT > 0 && src != null && src !== m.envBody) {
            val a = ang(x, y, src.x, src.y)
            if (abs(angleDiff(face, a)) < 1.25f) { m.fx.ring(x + cosf(face) * 26f, y, z + 40f, 18f, 0xFFFF5E7E.toInt()); return false }
        }
        if (superKind == 8 && superT > 0) { absorbStore += dmg; m.fx.number(x, y, z + bodyH, dmg, 0xFFFFE14D.toInt(), "+"); hitFlash = 0.08f; return true }
        if (superT > 0 && superKind == 0) dmg *= 0.3f
        if (superT > 0 && superKind == 2) dmg *= 0.4f
        val v = vehicle
        if (v != null && v.protectsDriver) { return v.hurt(d, src, kx, ky) }
        if (shieldHp > 0f) {
            val ab = minOf(shieldHp, dmg)
            shieldHp -= ab; dmg -= ab
            if (shieldHp <= 0f) { m.fx.ring(x, y, z + 30f, 40f, 0xFF6BD3FF.toInt()); m.sound(Sfx.GLASS, x, y, 0.5f) }
            if (dmg <= 0f) { hitFlash = 0.05f; return true }
        }
        lastHurtT = 0f
        if (atk != null && atk !== this) { lastAttacker = atk; lastAttackerT = 0f; atk.onDealt(dmg, this) }
        hitFlash = 0.12f
        numAcc += dmg
        if (Save_dmgNumbers() && (numAcc >= 120f || dmg >= 60f || m.time - numT > 0.6f)) {
            m.fx.number(x, y, z + bodyH + 8f, numAcc, if (isLocal) 0xFFFF5E7E.toInt() else 0xFFFFFFFF.toInt())
            numAcc = 0f; numT = m.time
        }
        var kmul = 1f
        if (m.mode.percentMode) {
            dmgPct += dmg / 28f
            kmul = 1f + dmgPct / 35f
        } else hp -= dmg
        if (superT > 0 && (superKind == 0 || superKind == 2)) kmul = 0.1f
        var kkx = kx; var kky = ky
        val sk = m.mode.shotKnock
        if (sk > 0f && kx == 0f && ky == 0f && src != null && src !== m.envBody) {
            val a = ang(src.x, src.y, x, y); kkx = cosf(a) * d * sk; kky = sinf(a) * d * sk
        }
        if (kkx != 0f || kky != 0f) knock(kkx * kmul, kky * kmul)
        if (m.mode.percentMode && len(kkx, kky) * kmul > 700f) ragdollT = maxOf(ragdollT, 0.35f)
        if (isLocal) { m.cam.shake(4f); Sfx.buzz(20) }
        m.sound(Sfx.HIT, x, y, 0.6f)
        if (hp <= 0f) die(atk ?: (if (lastAttackerT < 6f) lastAttacker else null))
        return true
    }

    private fun Save_dmgNumbers() = com.starguys.battle.core.Save.damageNumbers || isLocal
    val isLocal: Boolean get() = m.player === this

    fun heal(a: Float, show: Boolean = true) {
        if (out || hp >= maxHp) return
        val before = hp
        hp = minOf(maxHp, hp + a)
        if (show && hp - before > 1f) {
            m.fx.number(x, y, z + bodyH + 8f, hp - before, 0xFF7CFF8A.toInt(), "+")
        }
    }

    override fun knock(kx: Float, ky: Float, kz: Float) {
        if (vehicle != null) return
        val massMul = scale * scale
        vx += kx / massMul; vy += ky / massMul
        if (kz > 0f) vz = maxOf(vz, kz)
    }

    override fun applyStatus(s: Int, t: Float, src: Body?) {
        if (out) return
        if (superT > 0 && (superKind == 0 || superKind == 2) && (s == St.STUN || s == St.SLIP)) return
        if (s == St.BLIND && isLocal) m.hud.blind(t)
        super.applyStatus(s, t, src)
    }

    fun die(killer: Fighter?) {
        if (out) return
        hp = 0f; out = true; deaths++
        eliminatedAt = m.time
        m.fx.confetti(x, y, z + 30f, 28)
        m.fx.ring(x, y, z + 20f, 50f, 0xFFFFFFFF.toInt())
        m.sound(Sfx.POP, x, y)
        superT = 0f; superKind = -1
        burnT = 0f; stunT = 0f; bubbleT = 0f; ragdollT = 0f; glueT = 0f; slowT = 0f
        val v = vehicle; if (v != null) m.exitVehicle(this)
        heldBy?.let { it.held = null }; heldBy = null
        zipline = null
        flag?.drop(x, y); flag = null
        if (carry > 0) { for (i in 0 until carry) m.addPickup(Pickup(m, Pickup.GOLD, 0, x + rf(-20f, 20f), y + rf(-20f, 20f))); carry = 0 }
        if (killer != null && killer !== this) { killer.kills++; m.mode.creditKill(killer, this) }
        m.onFighterDeath(this, killer)
    }

    override fun onFallOut() {
        if (out) return
        if (parachutes > 0 || m.mode.saveFromFall(this)) {
            if (parachutes > 0) { parachutes--; m.fx.text(x, y, Fx.GND + 60f, "PARACHUTE !", 0xFFFF6B6B.toInt()) }
            x = safeX; y = safeY; z = m.map.groundAt(x, y).coerceAtLeast(0f) + 260f; vz = 0f; vx = 0f; vy = 0f
            ragdollT = 0f
            return
        }
        hp = 0f
        die(if (lastAttackerT < 6f) lastAttacker else null)
    }

    fun respawn(px: Float, py: Float) {
        out = false; alive = true
        x = px; y = py; z = maxOf(0f, m.map.groundAt(px, py)); vx = 0f; vy = 0f; vz = 0f
        hp = maxHp; ammo = 3f; spawnShield = 2.2f; shieldHp = 0f
        safeX = px; safeY = py
        burnT = 0f; stunT = 0f; slowT = 0f; bubbleT = 0f; ragdollT = 0f; giantT = 0f; invisT = 0f; dmgPct = 0f
        superT = 0f; superKind = -1; hasDived = false
        m.fx.ring(x, y, Fx.GND, 40f, 0xFFFFFFFF.toInt())
    }

    // ------------------------------------------------------------------ draw
    override fun draw(c: Canvas) {
        if (out || cannonT > 0 && z > 400f) return
        val v = vehicle
        if (v != null && !v.showsDriver) return
        val viewer = m.player
        val ally = viewer != null && !m.isEnemy(viewer.team, this)
        val inv = invisT > 0 && hero.id == 3
        if (inv && !ally && revealT <= 0) return
        shadow(c, radius)
        val sy = y - z * ZS
        val s = scale
        // team ring
        if (heldBy == null && v == null) {
            val rc = when { this === viewer -> 0xFF7CFF8A.toInt(); ally -> 0xFF6BD3FF.toInt(); else -> 0xFFFF5E7E.toInt() }
            G.ow = 2f
            G.oval(c, x, y - gz.coerceAtLeast(-50f) * ZS + 2f, radius * 1.1f, radius * 0.48f, G.alpha(rc, 0.35f), false)
            G.stroke.strokeWidth = 2.5f
        }
        c.save()
        c.translate(x, sy)
        var shrink = 1f
        if (falling) shrink = clamp(1f + (z - gz.coerceAtLeast(-200f)) / 160f, 0.2f, 1f).let { if (gz <= VOID_Z + 1) clamp(1f + z / 140f, 0.2f, 1f) else it }
        val lookA = face
        var lx = cosf(lookA); val ly = sinf(lookA)
        val flip = lx < 0f
        if (flip) lx = -lx
        // squash & stretch
        val sq = if (!onGround) clamp(1f + vz / 2400f, 0.85f, 1.15f) else 1f
        c.scale(s * shrink * (if (flip) -1f else 1f) / sq, s * shrink * sq)
        if (ragdollT > 0) c.rotate((spin * 57f) % 360f, 0f, -26f)
        else if (!onGround && hasDived) c.rotate(if (flip) -60f else 60f, 0f, -20f)
        else if (onGround) {
            // lean into the run (3D feel)
            val lean = clamp(vx / maxOf(hero.speed, 1f), -1f, 1f) * 9f
            c.rotate(if (flip) -lean else lean, 0f, -6f)
        }
        G.flash = if (hitFlash > 0) 0.65f else 0f
        G.fade = if (inv) 0.35f else if (spawnShield > 0 && ((m.time * 10).toInt() % 2 == 0)) 0.55f else 1f
        G.ow = 3f
        val moving = len(vx, vy) > 40f && onGround
        if (disguise >= 0) {
            Decor.drawDisguise(c, disguise)
        } else {
            val sup = superT > 0 && (superKind == hero.id) && (hero.id == 0 || hero.id == 2 || hero.id == 10)
            // facing away from the camera: show the hero's back
            G.noFace = ly < -0.55f && (moving || aimT < 0.5f)
            HeroArt.draw(c, hero.id, color, animT, moving, lx, ly, sup, stunT > 0)
            G.noFace = false
            if (emote > 0) drawEmote(c)
        }
        G.flash = 0f; G.fade = 1f
        c.restore()
        val top = sy - bodyH - 6f
        if (!onGround && gravScale < 0.5f && cannonT <= 0f) {
            // parachute during the drop
            G.ow = 3f
            G.line(c, x - 30f, top - 40f, x - 8f, top + 10f, 2f, G.INK); G.line(c, x + 30f, top - 40f, x + 8f, top + 10f, 2f, G.INK)
            G.pie(c, x, top - 38f, 44f, 180f, 180f, 0xFFFF6B6B.toInt())
            G.pie(c, x, top - 38f, 44f, 240f, 60f, 0xFFFFFFFF.toInt())
            G.arc(c, x, top - 38f, 44f, 180f, 180f, 3f, G.INK); G.line(c, x - 44f, top - 38f, x + 44f, top - 38f, 3f, G.INK)
        }
        // extra overlays
        if (shieldHp > 0f) { G.ow = 2f; G.circle(c, x, sy - bodyH * 0.45f, radius * 1.7f, 0x446BD3FF, true) }
        if (superKind == 8 && superT > 0) { G.ow = 3f; G.ring(c, x, sy - bodyH * 0.45f, radius * 1.8f + kotlin.math.sin(m.time * 20f) * 3f, 4f, 0xCCFFE14D.toInt()) }
        if (carry > 0) for (i in 0 until carry) G.rrect(c, x - 14f, top - 10f - i * 9f, x + 14f, top - 2f - i * 9f, 3f, 0xFFFFD23F.toInt())
        if (flag != null) { G.line(c, x + 14f, sy - 20f, x + 14f, sy - 80f, 3f, G.INK); G.poly(c, flag!!.color, true, x + 14f, sy - 80f, x + 44f, sy - 70f, x + 14f, sy - 60f) }
        if (infected && hero.id != 13) G.circle(c, x, top, 6f, 0xFF7BE84A.toInt())
        drawStatusFx(c, top)
        if (disguise < 0 || ally) drawBars(c, top, viewer, ally)
    }

    private fun drawEmote(c: Canvas) {
        val t = m.time
        G.ow = 2f
        G.star(c, -22f, -70f + sinf(t * 8) * 4, 7f, 3f, 5, t * 3, 0xFFFFE14D.toInt())
        G.star(c, 22f, -66f + cosf(t * 8) * 4, 6f, 2.5f, 5, -t * 3, 0xFFFF8CC6.toInt())
    }

    private fun drawBars(c: Canvas, top: Float, viewer: Fighter?, ally: Boolean) {
        if (m.mode.hideBars(this, viewer)) return
        val w = 56f; val h = 9f
        val l = x - w / 2; val t = top - 18f
        G.ow = 2f
        val frac = if (m.mode.percentMode) 1f else clamp(hp / maxHp, 0f, 1f)
        val col = when { this === viewer -> 0xFF7CFF8A.toInt(); ally -> 0xFF6BD3FF.toInt(); else -> 0xFFFF5E7E.toInt() }
        G.rrect(c, l, t, l + w, t + h, 4f, 0xAA1B1530.toInt())
        if (m.mode.percentMode) {
            val pc = G.mix(0xFFFFFFFF.toInt(), 0xFFFF3D3D.toInt(), clamp(dmgPct / 150f, 0f, 1f))
            G.text(c, "${dmgPct.toInt()}%", x, t + 8f, 18f, pc)
        } else {
            G.rrect(c, l + 1.5f, t + 1.5f, l + 1.5f + (w - 3f) * frac, t + h - 1.5f, 3f, col, false)
            if (shieldHp > 0f) G.rect(c, l + 2f, t + 1.5f, l + 2f + (w - 4f) * clamp(shieldHp / 2000f, 0f, 1f), t + 4f, 0xFF6BD3FF.toInt(), false)
        }
        G.text(c, name, x, t - 4f, 13f, if (this === viewer) 0xFF7CFF8A.toInt() else G.WHITE)
        if (this === viewer) {
            // ammo segments
            val aw = (w - 6f) / 3f
            for (i in 0 until 3) {
                val fill = clamp(ammo - i, 0f, 1f)
                val ax = l + i * (aw + 3f)
                G.rect(c, ax, t + h + 3f, ax + aw, t + h + 8f, 0xAA1B1530.toInt(), false)
                G.rect(c, ax, t + h + 3f, ax + aw * fill, t + h + 8f, if (fill >= 1f) 0xFFFF9A3D.toInt() else 0xFFB07040.toInt(), false)
            }
        }
        if (ready && (this === viewer || ally)) G.circle(c, l - 8f, t + 4f, 5f, 0xFFFFE14D.toInt())
    }

    companion object {
        val DIRX = floatArrayOf(1f, 0f, -1f, 0f)
        val DIRY = floatArrayOf(0f, 1f, 0f, -1f)
    }
}
