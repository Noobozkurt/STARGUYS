package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.clamp
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.len
import kotlin.math.ceil
import kotlin.math.sqrt

object St { const val NONE = 0; const val BURN = 1; const val SLOW = 2; const val STUN = 3; const val BUBBLE = 4; const val BLIND = 5; const val GLUE = 6; const val SLIP = 7 }

/** Anything with a position that lives in the world (fighters, mobs, props, vehicles, pickups...). */
open class Body(val m: Match) {
    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var radius = 20f
    var mass = 1f
    var team = TEAM_NEUTRAL
    var alive = true
    var onGround = true
    var gz = 0f
    var flying = false
    var falling = false
    var safeX = 0f; var safeY = 0f
    var hitWall = false
    var wallNx = 0f; var wallNy = 0f
    var bodyH = 50f
    var gravScale = 1f
    var platform: MovingPlatform? = null
    /** fighter that gets credit for this body (summons, turrets) */
    open val ownerF: Fighter? get() = null
    open val hittable: Boolean get() = false
    open val solid: Boolean get() = false
    open val sortY: Float get() = y

    open fun update(dt: Float) {}
    open fun draw(c: Canvas) {}
    open fun hurt(d: Float, src: Body?, kx: Float = 0f, ky: Float = 0f): Boolean = false
    open fun onFallOut() { alive = false }
    open fun knock(kx: Float, ky: Float, kz: Float = 0f) { vx += kx / mass; vy += ky / mass; if (kz > 0f) vz = maxOf(vz, kz / mass) }
    open fun applyStatus(s: Int, t: Float, src: Body?) {}

    fun tileX() = (x / T).floorInt()
    fun tileY() = (y / T).floorInt()
    fun dist(o: Body) = com.starguys.battle.core.dist(x, y, o.x, o.y)

    /** Move by velocity with circle-vs-tile collision. */
    fun moveCollide(dt: Float) {
        hitWall = false
        val dx = vx * dt; val dy = vy * dt
        val l = len(dx, dy)
        val n = maxOf(1, ceil(l / (radius * 0.5f)).toInt())
        val sx = dx / n; val sy = dy / n
        for (i in 0 until n) {
            x += sx; y += sy
            if (!flying) collideTiles()
        }
        if (!flying && m.map.oobVoid.not()) {
            x = clamp(x, radius, m.map.pxW - radius); y = clamp(y, radius, m.map.pxH - radius)
        }
        if (hitWall) {
            val dot = vx * wallNx + vy * wallNy
            if (dot < 0f) { vx -= wallNx * dot; vy -= wallNy * dot }
        }
    }

    open fun blocks(tx: Int, ty: Int): Boolean = m.map.blocksBody(tx, ty, z)

    fun collideTiles() {
        val r = radius
        val tx0 = ((x - r) / T).floorInt(); val tx1 = ((x + r) / T).floorInt()
        val ty0 = ((y - r) / T).floorInt(); val ty1 = ((y + r) / T).floorInt()
        for (ty in ty0..ty1) for (tx in tx0..tx1) {
            if (!blocks(tx, ty)) continue
            val l = tx * T; val t = ty * T
            val cx = clamp(x, l, l + T); val cy = clamp(y, t, t + T)
            val ddx = x - cx; val ddy = y - cy
            val d2 = ddx * ddx + ddy * ddy
            if (d2 < r * r) {
                if (d2 > 0.0001f) {
                    val d = sqrt(d2); val push = r - d
                    val nx = ddx / d; val ny = ddy / d
                    x += nx * push; y += ny * push
                    wallNx = nx; wallNy = ny
                } else {
                    // center inside the tile: push out along the shortest axis
                    val left = x - l; val right = l + T - x; val top = y - t; val bot = t + T - y
                    val mn = minOf(minOf(left, right), minOf(top, bot))
                    when (mn) {
                        left -> { x = l - r; wallNx = -1f; wallNy = 0f }
                        right -> { x = l + T + r; wallNx = 1f; wallNy = 0f }
                        top -> { y = t - r; wallNx = 0f; wallNy = -1f }
                        else -> { y = t + T + r; wallNx = 0f; wallNy = 1f }
                    }
                }
                hitWall = true
            }
        }
    }

    /** Ground height under the body, forgiving at edges (Fall-Guys-like). */
    fun sampleGround(): Float {
        var g = m.map.groundAt(x, y)
        if (g <= VOID_Z + 1) {
            val e = radius * 0.45f
            for (k in 0 until 4) {
                val ox = if (k == 0) e else if (k == 1) -e else 0f
                val oy = if (k == 2) e else if (k == 3) -e else 0f
                val gg = m.map.groundAt(x + ox, y + oy)
                if (gg > g && gg <= z + STEP) g = gg
            }
        }
        // walls under a body (e.g. spawned on top) count as their top only if we are above
        if (g > z + STEP && !flying) g = z
        val p = m.platformAt(x, y, z)
        platform = p
        if (p != null && p.topZ > g) g = p.topZ
        if (m.map.t((x / T).floorInt(), (y / T).floorInt()) == Tl.LADDER) {
            // climb
        }
        return g
    }

    /** Vertical physics. Returns impact speed when landing (0 otherwise). */
    fun physicsZ(dt: Float): Float {
        if (flying) return 0f
        gz = sampleGround()
        var impact = 0f
        val tile = m.map.t(tileX(), tileY())
        if (tile == Tl.LADDER && gz > z) { z = minOf(gz, z + 220f * dt); vz = 0f; onGround = true; return 0f }
        if (z > gz + 0.5f || vz > 0f) {
            vz -= GRAV * gravScale * m.gravity * dt
            z += vz * dt
            onGround = false
            if (z <= gz) { impact = -vz; z = gz; vz = 0f; onGround = true }
        } else {
            z = gz; vz = 0f; onGround = true
        }
        if (onGround && gz > VOID_Z + 1) {
            val tx = tileX(); val ty = tileY()
            val tp = m.map.t(tx, ty)
            if (tp != Tl.COLLAPSE && tp != Tl.FOAM && tp != Tl.LAVA && m.map.walkable(tx, ty) && platform == null) { safeX = x; safeY = y }
        }
        if (z < -140f) { falling = false; onFallOut() } else falling = z < gz - 1f && gz <= VOID_Z + 1
        return impact
    }

    fun shadow(c: Canvas, r: Float = radius) {
        if (gz <= VOID_Z + 1) return
        val h = clamp((z - gz) / 300f, 0f, 0.6f)
        G.softShadow(c, x + 3f, y - gz * ZS + 3f, r * (1.35f - h), r * 0.55f * (1.35f - h), 1f - h)
    }
}

/** Something with hp and status effects (fighters, mobs, summons). */
abstract class Actor(m: Match) : Body(m) {
    var hp = 1000f
    var maxHp = 1000f
    var burnT = 0f; var burnDps = 0f; var burnSrc: Body? = null
    var slowT = 0f; var slowAmt = 0f
    var stunT = 0f
    var bubbleT = 0f
    var blindT = 0f
    var ragdollT = 0f
    var glueT = 0f
    var hitFlash = 0f
    var spin = 0f
    var lastAttacker: Fighter? = null
    var lastAttackerT = 99f
    var lastHurtT = 99f

    override val hittable: Boolean get() = alive

    override fun applyStatus(s: Int, t: Float, src: Body?) {
        when (s) {
            St.BURN -> { burnT = maxOf(burnT, t); burnDps = 260f; burnSrc = src }
            St.SLOW -> { slowT = maxOf(slowT, t); slowAmt = maxOf(slowAmt, 0.35f) }
            St.STUN -> stunT = maxOf(stunT, t)
            St.BUBBLE -> { bubbleT = maxOf(bubbleT, t); vz = maxOf(vz, 120f) }
            St.BLIND -> blindT = maxOf(blindT, t)
            St.GLUE -> { glueT = maxOf(glueT, t) }
            St.SLIP -> { ragdollT = maxOf(ragdollT, t); stunT = maxOf(stunT, t * 0.7f) }
        }
    }

    fun tickStatus(dt: Float) {
        if (hitFlash > 0) hitFlash -= dt
        lastAttackerT += dt; lastHurtT += dt
        if (slowT > 0) { slowT -= dt; if (slowT <= 0) slowAmt = 0f }
        if (stunT > 0) stunT -= dt
        if (bubbleT > 0) { bubbleT -= dt; vx *= 0.9f; vy *= 0.9f }
        if (blindT > 0) blindT -= dt
        if (glueT > 0) glueT -= dt
        if (ragdollT > 0) { ragdollT -= dt; spin += dt * 14f } else spin = 0f
        if (burnT > 0) {
            burnT -= dt
            val src = burnSrc
            hurt(burnDps * dt, src ?: this, 0f, 0f)
            if (com.starguys.battle.core.chance(dt * 8f)) m.fx.flameBit(x, y, z + bodyH * 0.6f)
        }
    }

    fun clearBurn() { burnT = 0f }

    open fun statusSpeedMul(): Float {
        var s = 1f
        if (slowT > 0) s *= (1f - slowAmt)
        if (glueT > 0) s *= 0.45f
        return s
    }

    fun drawStatusFx(c: Canvas, topY: Float) {
        val t = m.time
        if (stunT > 0 || ragdollT > 0.25f) {
            for (k in 0 until 3) {
                val a = t * 6f + k * 2.1f
                G.ow = 1.5f
                G.star(c, x + com.starguys.battle.core.cosf(a) * 18f, topY - 6f + com.starguys.battle.core.sinf(a) * 5f, 7f, 3f, 5, a, 0xFFFFE14D.toInt())
            }
        }
        if (bubbleT > 0) {
            G.ow = 2f
            G.circle(c, x, topY + bodyH * 0.45f, radius * 1.6f, 0x5580D8FF, true)
            G.circle(c, x - radius * 0.6f, topY + bodyH * 0.2f, radius * 0.3f, 0x88FFFFFF.toInt(), false)
        }
        if (glueT > 0) G.oval(c, x, y - z * ZS, radius * 1.3f, radius * 0.5f, 0x99B6E35A.toInt(), false)
        if (slowT > 0 && slowAmt > 0.3f) {
            G.ow = 1.5f
            for (k in 0..2) G.circle(c, x - 12f + k * 12f, topY - 4f + ((t * 3 + k) % 1f) * 10f, 3f, 0xFF7CC7FF.toInt(), false)
        }
    }
}
