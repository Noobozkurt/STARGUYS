package com.starguys.battle.g3d

import com.starguys.battle.core.angleDiff
import com.starguys.battle.core.clamp
import com.starguys.battle.core.dist
import com.starguys.battle.core.floorInt
import com.starguys.battle.game.Aim
import com.starguys.battle.game.Ball
import com.starguys.battle.game.BananaPeel
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Body
import com.starguys.battle.game.Boss
import com.starguys.battle.game.Breakable
import com.starguys.battle.game.Cannon
import com.starguys.battle.game.CastleCore
import com.starguys.battle.game.Decor
import com.starguys.battle.game.Fan
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.FlagProp
import com.starguys.battle.game.Fx
import com.starguys.battle.game.JumpPad
import com.starguys.battle.game.LH
import com.starguys.battle.game.LootMachine
import com.starguys.battle.game.Match
import com.starguys.battle.game.Mob
import com.starguys.battle.game.MouseTrap
import com.starguys.battle.game.MovingPlatform
import com.starguys.battle.game.Pendulum
import com.starguys.battle.game.Pickup
import com.starguys.battle.game.Portal
import com.starguys.battle.game.Press
import com.starguys.battle.game.Prop
import com.starguys.battle.game.Snowball
import com.starguys.battle.game.SpinBar
import com.starguys.battle.game.StickyMine
import com.starguys.battle.game.Summon
import com.starguys.battle.game.SwingDoor
import com.starguys.battle.game.T
import com.starguys.battle.game.Teleporter
import com.starguys.battle.game.Tl
import com.starguys.battle.game.VOID_Z
import com.starguys.battle.game.Vehicle
import com.starguys.battle.game.WALL_H
import com.starguys.battle.game.Weapons
import com.starguys.battle.game.Zipline
import com.starguys.battle.game.Zone
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Third-person chase camera behind the local hero (SMITE-like) plus projection helpers for the HUD.
 * Presentation only: it never touches the simulation; the HUD turns it into absolute aim angles.
 */
class View3D(val m: Match) {
    /** sim angle the camera looks toward */
    var yaw = 0f
    /** downward tilt (radians) */
    var pitch = 0.3f
    var dist = 270f
    var fov = 64f
    var camX = 0f; var camY = 0f; var camZ = 0f
    var tgtX = 0f; var tgtY = 0f; var tgtZ = 0f
    var lookX = 0f; var lookY = 0f; var lookZ = 0f
    val view = FloatArray(16); val proj = FloatArray(16); val vp = FloatArray(16); val inv = FloatArray(16)
    var w = 1f; var h = 1f
    private var inited = false
    private var curDist = 270f
    private val tmp = FloatArray(3)
    var shakeX = 0f; var shakeY = 0f

    fun fwdX() = cos(yaw)
    fun fwdY() = sin(yaw)

    fun target(): Body? {
        val p = m.player ?: return null
        if (p.out) {
            if (m.killCamT > 0f) return m.killer?.takeIf { !it.out } ?: p
            if (!m.mode.respawns) return m.spectate ?: p
        }
        return p.vehicle ?: p
    }

    fun update(dt: Float, sw: Float, sh: Float) {
        w = sw; h = sh
        val t = target()
        val tx: Float; val ty: Float; val tz: Float
        if (t != null) { tx = t.x; ty = t.y; tz = maxOf(t.z, if (t.z < -100f) -100f else t.z) }
        else { tx = m.map.pxW / 2; ty = m.map.pxH / 2; tz = 0f }
        val p = m.player
        if (!inited) { inited = true; yaw = p?.face ?: 0f; tgtX = tx; tgtY = ty; tgtZ = tz }
        val k = minOf(1f, dt * 12f)
        tgtX += (tx - tgtX) * k; tgtY += (ty - tgtY) * k; tgtZ += (tz - tgtZ) * minOf(1f, dt * 7f)
        var wantDist = dist
        val v = p?.vehicle
        if (v?.kind == Vehicle.BALLOON) wantDist = dist * 1.5f
        val sc = p?.scale ?: 1f
        if (sc > 1.1f) wantDist *= 0.75f + sc * 0.25f
        val headH = 58f * sc + (if (v != null) 30f else 0f)
        // pivot above the hero's head, camera behind and above
        val px = tgtX; val py = tgtY; val pz = tgtZ + headH
        val cp = cos(pitch); val spch = sin(pitch)
        var d = wantDist
        // camera collision: pull in when a wall is between hero and camera
        val steps = 24
        for (i in 1..steps) {
            val kk = i.toFloat() / steps
            val sx = px - fwdX() * cp * wantDist * kk; val sy = py - fwdY() * cp * wantDist * kk
            val sz = pz + spch * wantDist * kk
            val g = m.map.groundAt(sx, sy)
            if (g > sz - 12f && g > VOID_Z + 1) { d = maxOf(60f, wantDist * (kk - 1f / steps)); break }
        }
        curDist += (d - curDist) * minOf(1f, dt * (if (d < curDist) 18f else 3f))
        camX = px - fwdX() * cp * curDist; camY = py - fwdY() * cp * curDist; camZ = pz + spch * curDist
        lookX = px + fwdX() * 70f; lookY = py + fwdY() * 70f; lookZ = pz - 6f
        shakeX = m.cam.sx * 0.6f; shakeY = m.cam.sy * 0.6f
        Mat.lookAt(view, camX + shakeX * 0.5f, camZ + shakeY * 0.5f, camY, lookX, lookZ, lookY, 0f, 1f, 0f)
        Mat.perspective(proj, fov, w / h, 8f, 9000f)
        Mat.mul(vp, proj, view)
        Mat.invert(inv, vp)
    }

    /** world (sim x, sim y, height) to screen pixels; returns false if behind the camera */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        if (!Mat.project(vp, x, z, y, tmp)) return false
        out[0] = (tmp[0] * 0.5f + 0.5f) * w; out[1] = (0.5f - tmp[1] * 0.5f) * h
        return tmp[2] < 1f
    }

    /** distance from the camera (for text scaling) */
    fun depth(x: Float, y: Float, z: Float): Float {
        val dx = x - camX; val dy = y - camY; val dz = z - camZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Ray from a screen point onto the terrain. Returns (x, y) sim or null. */
    fun pick(sx: Float, sy: Float, maxD: Float = 4000f): FloatArray? {
        val nx = sx / w * 2f - 1f; val ny = 1f - sy / h * 2f
        val a = unproject(nx, ny, -1f); val b = unproject(nx, ny, 1f)
        var dx = b[0] - a[0]; var dy = b[1] - a[1]; var dz = b[2] - a[2]
        val l = sqrt(dx * dx + dy * dy + dz * dz); dx /= l; dy /= l; dz /= l
        var t = 0f
        while (t < maxD) {
            t += 12f
            val x = camX + dx * t; val y = camY + dz * t; val z = camZ + dy * t
            val g = m.map.groundAt(x, y)
            if (z <= maxOf(g, if (m.map.oobVoid) -9999f else 0f)) return floatArrayOf(x, y)
        }
        return null
    }

    private fun unproject(nx: Float, ny: Float, nz: Float): FloatArray {
        val x = inv[0] * nx + inv[4] * ny + inv[8] * nz + inv[12]
        val y = inv[1] * nx + inv[5] * ny + inv[9] * nz + inv[13]
        val z = inv[2] * nx + inv[6] * ny + inv[10] * nz + inv[14]
        val ww = inv[3] * nx + inv[7] * ny + inv[11] * nz + inv[15]
        return floatArrayOf(x / ww, y / ww, z / ww)
    }

    /** crosshair screen position */
    fun crossX() = w / 2
    fun crossY() = h * 0.44f
}

/** Builds a [RenderFrame] from the match state (game thread). */
class Scene(val m: Match, val bus: FrameBus = FrameBus.main) {
    val view = View3D(m)
    private val world = WorldMesh()
    private val b = MB()
    private lateinit var f: RenderFrame
    private val TEAM_ME = 0xFF4ADE80.toInt(); private val TEAM_ALLY = 0xFF38BDF8.toInt(); private val TEAM_FOE = 0xFFF43F5E.toInt()

    private fun rgbInto(c: Int, out: FloatArray, k: Float = 1f) {
        out[0] = ((c shr 16) and 255) / 255f * k; out[1] = ((c shr 8) and 255) / 255f * k; out[2] = (c and 255) / 255f * k
    }

    private fun spr(sb: SpriteBatch, x: Float, y: Float, z: Float, kind: Int, b0: Float, b1: Float, b2: Float, b3: Float, col: Int, a: Float) {
        sb.add(x, z, y, kind.toFloat(), b0, b1, b2, b3, ((col shr 16) and 255) / 255f, ((col shr 8) and 255) / 255f, (col and 255) / 255f, a * (((col ushr 24) and 255) / 255f))
    }
    /** billboard */
    fun bill(add: Boolean, x: Float, y: Float, z: Float, shape: Int, size: Float, col: Int, a: Float = 1f, rot: Float = 0f, param: Float = 0f) =
        spr(if (add) f.spritesAdd else f.spritesAlpha, x, y, z, Spr.BILL + shape, size, size, rot, param, col, a)
    /** ground decal: rot = sim angle */
    fun decal(add: Boolean, x: Float, y: Float, z: Float, shape: Int, sx: Float, sy: Float, rot: Float, col: Int, a: Float = 1f, param: Float = 0f) =
        spr(if (add) f.spritesAdd else f.spritesAlpha, x, y, z + 1.5f, Spr.DECAL + shape, sx, sy, rot, param, col, a)
    fun line(add: Boolean, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, w: Float, col: Int, a: Float = 1f) {
        val sb = if (add) f.spritesAdd else f.spritesAlpha
        sb.add(x0, z0, y0, (Spr.LINE + Spr.BEAM).toFloat(), x1, z1, y1, w, ((col shr 16) and 255) / 255f, ((col shr 8) and 255) / 255f, (col and 255) / 255f, a * (((col ushr 24) and 255) / 255f))
    }
    val mb: MB get() = b

    private fun gz(x: Float, y: Float) = maxOf(0f, m.map.groundAt(x, y))

    // ------------------------------------------------------------------ frame
    fun build(sw: Float, sh: Float, dt: Float) {
        view.update(dt, sw, sh)
        f = bus.back()
        f.clear()
        b.begin(f)
        f.worldGen = world.sync(m.map, bus)
        f.drawWorld = true; f.showcase = false
        f.time = m.time
        lighting(m.map.biome)
        System.arraycopy(view.view, 0, f.view, 0, 16); System.arraycopy(view.proj, 0, f.proj, 0, 16)
        System.arraycopy(view.vp, 0, f.viewProj, 0, 16); System.arraycopy(view.inv, 0, f.invViewProj, 0, 16)
        f.camX = view.camX; f.camY = view.camZ; f.camZ = view.camY
        f.rightX = view.view[0]; f.rightY = view.view[4]; f.rightZ = view.view[8]
        f.upX = view.view[1]; f.upY = view.view[5]; f.upZ = view.view[9]
        shadowMatrix()
        tiles(); entities(); projectiles(); zones(); effects(); extras()
        m.mode.scene3d(this)
        f.valid = true
        bus.publish()
    }

    private fun lighting(bi: Biome) {
        rgbInto(bi.bgTop, f.zenith, 1f)
        val hz = MB.light(bi.bgTop, 0.55f)
        rgbInto(hz, f.horizon)
        rgbInto(bi.bgBottom, f.abyss)
        f.stars = if (bi == Biome.SPACE || m.map.oobVoid) 1f else 0f
        f.clouds = when (bi) { Biome.SPACE, Biome.SWAMP, Biome.KITCHEN, Biome.FACTORY -> 0f; else -> 1f }
        f.fogStart = 1500f; f.fogEnd = 4200f
        f.sunCol[0] = 0.72f; f.sunCol[1] = 0.68f; f.sunCol[2] = 0.6f
        f.skyAmb[0] = 0.5f; f.skyAmb[1] = 0.55f; f.skyAmb[2] = 0.66f
        f.gndAmb[0] = 0.4f; f.gndAmb[1] = 0.36f; f.gndAmb[2] = 0.38f
        when (bi) {
            Biome.SWAMP -> { f.fogStart = 120f; f.fogEnd = m.fogR.coerceAtLeast(300f) * 1.3f; rgbInto(0xFF0F2A22.toInt(), f.horizon); f.sunCol[0] = 0.35f; f.sunCol[1] = 0.42f; f.sunCol[2] = 0.4f }
            Biome.SPACE -> { rgbInto(0xFF1E1B4B.toInt(), f.horizon); f.fogStart = 2500f; f.fogEnd = 7000f }
            Biome.VOLCANO -> { rgbInto(0xFFFB7185.toInt(), f.horizon); f.sunCol[0] = 0.8f; f.sunCol[1] = 0.55f; f.sunCol[2] = 0.55f }
            Biome.KITCHEN, Biome.FACTORY -> { f.fogStart = 1200f; f.fogEnd = 3400f }
            else -> {}
        }
        if (m.map.oobVoid) { f.abyss[0] *= 0.6f; f.abyss[1] *= 0.6f; f.abyss[2] *= 0.6f }
        val l = sqrt(0.42f * 0.42f + 0.8f * 0.8f + 0.46f * 0.46f)
        f.sunDir[0] = -0.42f / l; f.sunDir[1] = 0.8f / l; f.sunDir[2] = -0.46f / l
        f.shadows = true
    }

    private val lv = FloatArray(16); private val lp = FloatArray(16)
    private fun shadowMatrix() {
        val cx = view.tgtX + view.fwdX() * 500f; val cz = view.tgtY + view.fwdY() * 500f; val cy = view.tgtZ
        val d = 2500f
        Mat.lookAt(lv, cx + f.sunDir[0] * d, cy + f.sunDir[1] * d, cz + f.sunDir[2] * d, cx, cy, cz, 0f, 1f, 0f)
        Mat.ortho(lp, -1150f, 1150f, -1150f, 1150f, 10f, 5200f)
        Mat.mul(f.lightVP, lp, lv)
    }

    private fun near(x: Float, y: Float, r: Float = 2600f): Boolean {
        val dx = x - view.tgtX; val dy = y - view.tgtY
        if (dx * dx + dy * dy > r * r) return false
        // behind the camera far enough: skip
        val fx = dx * view.fwdX() + dy * view.fwdY()
        return fx > -700f
    }

    // ------------------------------------------------------------------ dynamic tiles
    private fun tiles() {
        val mp = m.map
        val r = 30
        val cx = (view.tgtX / T).floorInt(); val cy = (view.tgtY / T).floorInt()
        val t = m.time
        for (ty in maxOf(0, cy - r)..minOf(mp.h - 1, cy + r)) for (tx in maxOf(0, cx - r)..minOf(mp.w - 1, cx + r)) {
            val tp = mp.t(tx, ty)
            if (tp == Tl.FLOOR || tp == Tl.WALL) continue
            val x = tx * T + T / 2; val y = ty * T + T / 2
            if (!near(x, y)) continue
            val base = mp.e(tx, ty) * LH
            when (tp) {
                Tl.BUSH -> {
                    val sw = sin(t * 1.6f + tx * 0.8f + ty * 0.3f) * 2f
                    val bc = mp.biome.bush
                    val me = m.player
                    b.alpha = if (me != null && !me.out && abs(me.x - x) < T * 1.3f && abs(me.y - y) < T * 1.3f) 0.4f else 1f
                    b.at(x, y, base, 0f)
                    b.ball(-14f + sw, 22f, -12f, 40f, MB.shade(bc, 0.85f)); b.ball(12f + sw, 22f, 14f, 40f, MB.shade(bc, 0.9f))
                    b.ball(sw, 34f, 0f, 46f, bc); b.ball(10f + sw, 20f, -16f, 30f, MB.shade(bc, 0.8f))
                    b.ball(-6f + sw, 46f, 6f, 16f, MB.light(bc, 0.3f), Cat.FLAT)
                    b.alpha = 1f
                }
                Tl.GLASS -> {
                    b.at(x, y, base, 0f)
                    b.glass(Meshes.BOX, 0f, WALL_H / 2, 0f, T, WALL_H, T, 0xFFBAE6FD.toInt(), 0.35f)
                }
                Tl.COLLAPSE -> {
                    val st = mp.aux(tx, ty)
                    if (st == 1) continue
                    val sh = if (st == 2) sin(t * 60f) * 2.5f else 0f
                    b.at(x + sh, y, base, 0f)
                    b.box(0f, -9f, 0f, T - 2f, 18f, T - 2f, if ((tx + ty) and 1 == 0) 0xFFE8B86A.toInt() else 0xFFDDAA5A.toInt())
                    if (st == 2) b.box(0f, 0.6f, 0f, T * 0.7f, 1f, 3f, 0xFF7A4A1A.toInt(), ry = 0.7f, cat = Cat.FLAT)
                }
                Tl.CONVEYOR, Tl.SPEED -> {
                    val d = mp.aux(tx, ty)
                    val a = d * 1.5708f
                    val sp = if (tp == Tl.SPEED) 50f else 70f
                    val col = if (tp == Tl.SPEED) 0xFFFFE14D.toInt() else 0xFF8C93AD.toInt()
                    for (k in 0 until 2) {
                        val o = ((t * sp + k * 32f) % 64f) - 32f
                        decal(tp == Tl.SPEED, x + cos(a) * o, y + sin(a) * o, base, Spr.CHEVRON, 14f, 20f, a, col, 0.95f)
                    }
                }
            }
        }
        if (mp.slimeZ > -100f) {
            b.reset(); b.at(mp.pxW / 2, mp.pxH / 2, mp.slimeZ, 0f)
            b.glass(Meshes.QUAD, 0f, 0f, 0f, mp.pxW + 6000f, 1f, mp.pxH + 6000f, 0xFF7BE84A.toInt(), 0.82f)
            for (k in 0 until 18) {
                val bx = view.tgtX + sin(k * 37.1f) * 900f; val by = view.tgtY + cos(k * 23.7f) * 900f
                val ph = (t * 0.7f + k * 0.37f) % 1f
                bill(false, bx, by, mp.slimeZ + ph * 10f, Spr.DISC, 10f * (1f - ph) + 4f, 0xFFB5FF8A.toInt(), 1f - ph)
            }
        }
    }

    // ------------------------------------------------------------------ entities
    private fun teamCol(fi: Fighter): Int {
        val p = m.player
        return when { fi === p -> TEAM_ME; p != null && !m.isEnemy(p.team, fi) -> TEAM_ALLY; else -> TEAM_FOE }
    }

    fun drawHero(fi: Fighter, x: Float, y: Float, z: Float, face: Float, s: Float) {
        b.at(x, y, z, face, s)
        val mv = clamp(sqrt(fi.vx * fi.vx + fi.vy * fi.vy) / maxOf(fi.hero.speed, 1f), 0f, 1f) * (if (fi.onGround) 1f else 0.3f)
        if (fi.ragdollT > 0) { b.move(0f, 26f, 0f, rz = fi.spin); b.move(0f, -26f, 0f) }
        else if (!fi.onGround && fi.hasDived) { b.move(0f, 16f, 0f, rz = -1.2f); b.move(0f, -16f, 0f) }
        else {
            val sq = if (!fi.onGround) clamp(1f + fi.vz / 2400f, 0.85f, 1.15f) else 1f
            b.moveS(0f, 0f, 0f, 0f, -0.14f * mv, 0f, 1f / sq, sq, 1f / sq)
            if (fi.aimT < 0.12f && fi.currentWeapon().melee.not()) b.move(-2f, 0f, 0f, rz = 0.08f)
        }
        val sup = fi.superT > 0 && fi.superKind == fi.hero.id && (fi.hero.id == 0 || fi.hero.id == 2 || fi.hero.id == 10)
        if (fi.disguise >= 0) EntityModels.decor(b, fi.disguise, 0f, m.map.biome)
        else {
            HeroModels.draw(b, fi.hero.id, fi.color, fi.animT, mv, sup, fi.stunT > 0)
            val w = fi.modeWeapon ?: fi.weapon
            if (w != null && !sup) {
                b.push(); b.move(6f, 24f, 20f)
                val swing = if (w.melee && fi.aimT < 0.25f) fi.aimT / 0.25f else 0f
                HeroModels.weapon(b, w.id, w.color, swing)
                b.pop()
            }
            if (fi.emote > 0) { b.glowBall(0f, 74f + sin(m.time * 8f) * 4f, -20f, 8f, 0xFFFFE14D.toInt()); b.glowBall(0f, 70f + cos(m.time * 8f) * 4f, 20f, 7f, 0xFFFF8CC6.toInt()) }
        }
    }

    private fun fighter(fi: Fighter) {
        if (fi.out || (fi.cannonT > 0 && fi.z > 400f)) return
        val v = fi.vehicle
        if (v != null) return
        val ally = !m.isEnemy(m.player?.team ?: -1, fi) || fi === m.player
        val inv = fi.invisT > 0 && fi.hero.id == 3
        if (inv && !ally && fi.revealT <= 0) return
        val s = fi.scale
        var shrink = 1f
        if (fi.falling) shrink = if (fi.gz <= VOID_Z + 1) clamp(1f + fi.z / 140f, 0.2f, 1f) else 1f
        b.alpha = if (inv) 0.35f else if (fi.spawnShield > 0 && ((m.time * 10).toInt() % 2 == 0)) 0.55f else 1f
        b.flash = if (fi.hitFlash > 0) 0.7f else 0f
        drawHero(fi, fi.x, fi.y, fi.z, fi.face, s * shrink)
        b.alpha = 1f; b.flash = 0f
        // contact shadow + team ring
        if (fi.gz > VOID_Z + 1) {
            val hh = clamp((fi.z - fi.gz) / 300f, 0f, 0.6f)
            decal(false, fi.x, fi.y, fi.gz, Spr.BLOB, fi.radius * (1.3f - hh), fi.radius * (1.3f - hh), 0f, 0xFF000000.toInt(), 0.45f * (1f - hh))
            if (fi.heldBy == null) decal(false, fi.x, fi.y, fi.gz, Spr.RING, fi.radius * 1.35f, fi.radius * 1.35f, 0f, teamCol(fi), 0.75f, 0.14f)
        }
        val top = fi.z + fi.bodyH + 8f
        if (!fi.onGround && fi.gravScale < 0.5f && fi.cannonT <= 0f) {
            b.at(fi.x, fi.y, fi.z, fi.face)
            b.part(Meshes.HEMI, 0f, 104f, 0f, 96f, 40f, 96f, 0xFFFF6B6B.toInt())
            b.part(Meshes.HEMI, 0f, 104.5f, 0f, 50f, 28f, 50f, 0xFFFFFFFF.toInt())
            for (q in intArrayOf(-1, 1)) for (r in intArrayOf(-1, 1)) b.seg(Meshes.CYL, q * 8f, 40f, r * 8f, q * 40f, 104f, r * 40f, 1.4f, MB.INK)
        }
        if (fi.shieldHp > 0f) { b.at(fi.x, fi.y, fi.z, 0f); b.glass(Meshes.SPHERE, 0f, fi.bodyH * 0.5f, 0f, fi.radius * 3.4f, fi.bodyH * 1.5f, fi.radius * 3.4f, 0xFF6BD3FF.toInt(), 0.3f) }
        if (fi.bubbleT > 0) { b.at(fi.x, fi.y, fi.z, 0f); b.glass(Meshes.SPHERE, 0f, fi.bodyH * 0.5f, 0f, fi.radius * 3.2f, fi.radius * 3.2f, fi.radius * 3.2f, 0xFF90E0FF.toInt(), 0.4f) }
        if (fi.superKind == 8 && fi.superT > 0) decal(true, fi.x, fi.y, fi.z, Spr.RING, fi.radius * 2f, fi.radius * 2f, 0f, 0xFFFFE14D.toInt(), 0.9f, 0.2f)
        if (fi.stunT > 0 || fi.ragdollT > 0.25f) for (k in 0 until 3) {
            val a = m.time * 6f + k * 2.1f
            bill(true, fi.x + cos(a) * 20f, fi.y + sin(a) * 20f, top + 4f, Spr.STAR, 8f, 0xFFFFE14D.toInt(), 1f, a)
        }
        if (fi.glueT > 0) decal(false, fi.x, fi.y, fi.gz, Spr.DISC, fi.radius * 1.5f, fi.radius * 1.5f, 0f, 0xFFB6E35A.toInt(), 0.7f)
        if (fi.carry > 0) { b.at(fi.x, fi.y, fi.z, fi.face); for (i in 0 until fi.carry) b.box(0f, top - fi.z + 8f + i * 10f, 0f, 26f, 8f, 14f, 0xFFFFD23F.toInt()) }
        val fl = fi.flag
        if (fl != null) { b.at(fi.x, fi.y, fi.z, fi.face); b.cyl(-14f, 10f, 0f, 3f, 92f, MB.INK); b.part(Meshes.BOX, -14f, 82f, 18f, 2f, 20f, 34f, fl.color, rx = sin(m.time * 5f) * 0.1f) }
        if (fi.infected && fi.hero.id != 13) bill(true, fi.x, fi.y, top + 10f, Spr.GLOW, 10f, 0xFF7BE84A.toInt())
        if (fi.revealT > 0 && fi !== m.player) bill(true, fi.x, fi.y, top + 24f, Spr.DISC, 6f, 0xFFF43F5E.toInt())
    }

    private fun entities() {
        for (fi in m.fighters) if (near(fi.x, fi.y) && m.visibleToPlayer(fi)) fighter(fi)
        for (mb in m.mobs) {
            if (!mb.alive || !near(mb.x, mb.y) || !m.visibleToPlayer(mb)) continue
            b.flash = if (mb.hitFlash > 0) 0.7f else 0f
            b.at(mb.x, mb.y, mb.z, mb.face)
            if (mb.ragdollT > 0) { b.move(0f, mb.radius, 0f, rz = mb.spin); b.move(0f, -mb.radius, 0f) }
            if (mb is Boss) EntityModels.boss(b, mb) else EntityModels.mob(b, mb)
            b.flash = 0f
            decal(false, mb.x, mb.y, mb.gz.coerceAtLeast(0f), Spr.BLOB, mb.radius * 1.3f, mb.radius * 1.3f, 0f, 0xFF000000.toInt(), 0.4f)
            if (mb is Boss && mb.kind == Mob.BOSS_CLAW) {
                b.at(mb.clawX, mb.clawY, gz(mb.clawX, mb.clawY) + mb.clawZ, 0f)
                b.cyl(0f, 20f, 0f, 3f, 600f, MB.INK)
                b.ball(0f, 20f, 0f, 26f, 0xFFB0B8C8.toInt())
                for (k in 0 until 3) { val a = k * 2.094f; b.seg(Meshes.BOX, 0f, 14f, 0f, cos(a) * 26f, -24f, sin(a) * 26f, 6f, 0xFFB0B8C8.toInt()) }
                decal(false, mb.clawX, mb.clawY, gz(mb.clawX, mb.clawY), Spr.DISC, 34f, 34f, 0f, if (mb.state == 1) 0xFFFF3D3D.toInt() else 0xFF000000.toInt(), 0.35f)
            }
        }
        for (v in m.vehicles) {
            if (!v.alive || !near(v.x, v.y)) continue
            b.flash = if (v.flash > 0) 0.5f else 0f
            b.at(v.x, v.y, v.z, v.heading)
            val d = v.driver
            EntityModels.vehicle(b, v) { ox, oy, oz, s ->
                if (d != null) {
                    b.push(); b.moveS(ox, oy, oz, 0f, 0f, 0f, s, s, s)
                    val ofl = b.flash; b.flash = if (d.hitFlash > 0) 0.6f else ofl
                    HeroModels.draw(b, d.hero.id, d.color, d.animT, 0f, false, false)
                    b.flash = ofl
                    b.pop()
                }
            }
            if (v.kind == Vehicle.CRANE) v.held?.let { h ->
                b.at(v.clawX(), v.clawY(), v.z + 20f, v.heading, 0.85f)
                HeroModels.draw(b, h.hero.id, h.color, m.time, 0f, false, true)
            }
            b.flash = 0f
            if (v.kind != Vehicle.BALLOON) decal(false, v.x, v.y, gz(v.x, v.y), Spr.BLOB, v.radius * 1.5f, v.radius * 1.3f, v.heading, 0xFF000000.toInt(), 0.45f)
            else decal(false, v.x, v.y, gz(v.x, v.y), Spr.BLOB, 40f, 40f, 0f, 0xFF000000.toInt(), 0.25f)
        }
        for (s in m.summons) {
            if (!s.alive || !near(s.x, s.y)) continue
            b.flash = if (s.hitFlash > 0) 0.6f else 0f
            when (s.kind) {
                Summon.CLONE -> { b.at(s.x, s.y, s.z, s.dir); HeroModels.draw(b, s.owner.hero.id, s.owner.color, s.animT, 1f, false, false) }
                Summon.DECOY -> {
                    b.at(s.x, s.y, s.z, s.dir)
                    b.cyl(0f, 0f, 0f, 4f, 10f, 0xFF8B5A3C.toInt())
                    b.push(); b.move(0f, 6f, 0f); HeroModels.draw(b, 12, 0xFFE8D8B0.toInt(), s.animT, 0f, false, false); b.pop()
                    b.part(Meshes.TORUS, 15f, 34f, 0f, 16f, 16f, 16f, 0xFFFF5E7E.toInt(), rz = 1.5708f, cat = Cat.FLAT)
                }
                else -> {
                    b.at(s.x, s.y, s.z, 0f)
                    b.box(0f, 8f, 0f, 30f, 16f, 30f, 0xFF4FB86B.toInt())
                    b.push(); b.move(0f, 24f, 0f, ry = -s.dir)
                    b.ball(0f, 0f, 0f, 24f, 0xFF7CFF8A.toInt())
                    b.part(Meshes.CYL, 16f, 0f, 0f, 7f, 22f, 7f, 0xFF3A3A44.toInt(), rz = -1.5708f)
                    b.emissive(Meshes.SPHERE, 10f, 4f, 0f, 3f, 6f, 6f, 0xFFFF3D3D.toInt())
                    b.pop()
                }
            }
            b.flash = 0f
            decal(false, s.x, s.y, s.gz.coerceAtLeast(0f), Spr.BLOB, 24f, 24f, 0f, 0xFF000000.toInt(), 0.4f)
        }
        for (p in m.pickups) {
            if (!p.alive || !near(p.x, p.y, 2200f)) continue
            val bo = sin(p.t * 3f) * 4f
            val glow = when (p.kind) { Pickup.WEAPON -> 0xFFFFE14D.toInt(); Pickup.GADGET -> 0xFF6BD3FF.toInt(); Pickup.ITEM -> 0xFFFF8CC6.toInt(); Pickup.HEAL -> 0xFF7CFF8A.toInt(); Pickup.GOLD -> 0xFFFFD23F.toInt(); else -> 0xFFFFFFFF.toInt() }
            decal(true, p.x, p.y, p.z, Spr.GLOW, 30f, 30f, 0f, glow, 0.7f)
            line(true, p.x, p.y, p.z, p.x, p.y, p.z + 140f, 9f, glow, 0.25f)
            b.at(p.x, p.y, p.z + 26f + bo, p.t * 1.6f)
            when (p.kind) {
                Pickup.WEAPON -> { val w = Weapons.list[p.id.coerceIn(0, Weapons.list.size - 1)]; b.push(); b.move(-6f, 0f, 0f, s = 1.4f); HeroModels.weapon(b, w.id, w.color, 0f); b.pop() }
                Pickup.GADGET -> { b.push(); b.move(0f, 0f, 0f, s = 1.2f); EntityModels.gadget(b, p.id); b.pop() }
                Pickup.ITEM -> { b.push(); b.move(0f, 0f, 0f, s = 1.2f); EntityModels.item(b, p.id); b.pop() }
                Pickup.HEAL -> { b.box(0f, 0f, 0f, 26f, 22f, 26f, 0xFFFFFFFF.toInt()); b.box(13.2f, 0f, 0f, 1f, 14f, 5f, 0xFF16A34A.toInt(), cat = Cat.FLAT); b.box(13.2f, 0f, 0f, 1f, 5f, 14f, 0xFF16A34A.toInt(), cat = Cat.FLAT) }
                Pickup.GOLD -> b.part(Meshes.BOX, 0f, 0f, 0f, 34f, 14f, 18f, 0xFFFFD23F.toInt())
                else -> { b.glowBall(0f, 0f, 0f, 22f, 0xFFFFE14D.toInt()); bill(true, p.x, p.y, p.z + 26f + bo, Spr.STAR, 18f, 0xFFFFF3B0.toInt(), 1f, p.t) }
            }
        }
        for (pr in m.props) if (pr.alive && near(pr.x, pr.y)) prop(pr)
    }

    private fun prop(pr: Prop) {
        val t = pr.t
        b.at(pr.x, pr.y, pr.z, 0f)
        when (pr) {
            is Decor -> { b.at(pr.x, pr.y, pr.z, (pr.x * 0.013f + pr.y * 0.007f) % 6.28f, pr.scale); EntityModels.decor(b, pr.kind, t, m.map.biome)
                if (Decor.SOLID[pr.kind] > 0f) decal(false, pr.x, pr.y, pr.z, Spr.BLOB, Decor.SOLID[pr.kind] * pr.scale * 1.5f, Decor.SOLID[pr.kind] * pr.scale * 1.5f, 0f, 0xFF000000.toInt(), 0.3f) }
            is JumpPad -> {
                val sq = if (t < 0.25f) sin(t * 25f) * 4f else 0f
                b.cyl(0f, 0f, 0f, 60f, 8f, 0xFF3A3A44.toInt())
                b.cyl(0f, 8f, 0f, 52f, 13f + sq, if (pr.temp > 0f) 0xFF5BD3FF.toInt() else 0xFFFF5E7E.toInt())
                b.emissive(Meshes.CYL, 0f, 13.5f + sq, 0f, 24f, 1f, 24f, 0xFFFFFFFF.toInt())
                decal(true, pr.x, pr.y, pr.z + 14f, Spr.RING, 34f, 34f, 0f, 0xFFFF8CC6.toInt(), 0.5f + 0.3f * sin(m.time * 6f), 0.2f)
            }
            is Fan -> {
                b.at(pr.x, pr.y, pr.z, pr.dir)
                b.cyl(0f, 0f, 0f, 30f, 10f, 0xFF4A4A55.toInt())
                b.box(0f, 20f, 0f, 10f, 24f, 10f, 0xFF4A4A55.toInt())
                b.part(Meshes.TORUS, 8f, 40f, 0f, 56f, 56f, 56f, 0xFF8A93A8.toInt(), rz = 1.5708f)
                b.push(); b.move(10f, 40f, 0f, rx = if (pr.on) m.time * 20f else 0f)
                for (k in 0 until 3) b.box(0f, 0f, 0f, 3f, 44f, 10f, 0xFF35C6F4.toInt(), rx = k * 2.094f)
                b.pop()
            }
            is SwingDoor -> {
                b.cyl(0f, 0f, 0f, 12f, 90f, 0xFF4A4A55.toInt())
                b.at(pr.x, pr.y, pr.z, pr.a)
                b.box(pr.len / 2, 40f, 0f, pr.len, 70f, 10f, 0xFFFF9A3D.toInt())
                b.box(pr.len / 2, 40f, 0f, pr.len * 0.8f, 8f, 11f, 0xFFFFE14D.toInt(), cat = Cat.FLAT)
            }
            is Cannon -> {
                val a = atan2(pr.ty - pr.y, pr.tx - pr.x)
                b.at(pr.x, pr.y, pr.z, a)
                b.box(0f, 8f, 0f, 50f, 16f, 40f, 0xFF4A4A55.toInt())
                for (s in intArrayOf(-1, 1)) b.part(Meshes.CYL, 0f, 14f, s * 24f, 26f, 6f, 26f, 0xFF8B5A3C.toInt(), rx = 1.5708f)
                b.push(); b.move(0f, 30f, 0f, rz = 0.6f)
                b.part(Meshes.CYL, 14f, 0f, 0f, 30f, 56f, 30f, 0xFFE84545.toInt(), rz = -1.5708f)
                b.part(Meshes.TORUS, 40f, 0f, 0f, 36f, 30f, 36f, 0xFFFFD23F.toInt(), rz = 1.5708f)
                b.pop()
            }
            is Breakable -> {
                b.flash = if (pr.flash > 0) 0.6f else 0f
                when (pr.kind) {
                    0 -> { b.cyl(0f, 0f, 0f, 36f, 46f, 0xFFE84545.toInt()); b.cyl(0f, 14f, 0f, 37f, 20f, 0xFFFFD23F.toInt(), Cat.FLAT); b.cyl(0f, 30f, 0f, 37f, 36f, 0xFFFFD23F.toInt(), Cat.FLAT) }
                    1 -> { b.glass(Meshes.CYL, 0f, 24f, 0f, 32f, 48f, 32f, 0xFF7BE84A.toInt(), 0.75f); b.cyl(0f, 46f, 0f, 22f, 54f, 0xFF6A6A78.toInt()) }
                    2 -> if (!pr.broken) { b.cyl(0f, 0f, 0f, 26f, 40f, 0xFFE84545.toInt()); b.part(Meshes.HEMI, 0f, 40f, 0f, 26f, 18f, 26f, 0xFFE84545.toInt()); b.part(Meshes.CYL, 0f, 28f, 0f, 10f, 40f, 10f, 0xFFB0B8C8.toInt(), rz = 1.5708f) }
                        else b.cyl(0f, 0f, 0f, 26f, 14f, 0xFFB33952.toInt())
                    else -> { b.box(0f, 18f, 0f, 40f, 36f, 40f, 0xFFD9A066.toInt()); b.box(0f, 38f, 0f, 42f, 8f, 42f, 0xFFC08A55.toInt()); b.box(0f, 21f, 0f, 41f, 42f, 8f, 0xFFFF5E7E.toInt(), cat = Cat.FLAT); bill(true, pr.x, pr.y, pr.z + 60f, Spr.STAR, 10f, 0xFFFFE14D.toInt(), 0.9f, t) }
                }
                b.flash = 0f
            }
            is Zipline -> {
                val za = pr.z; val zb = gz(pr.bx, pr.by)
                b.cyl(0f, 0f, 0f, 8f, pr.h + 10f, 0xFF6A6A78.toInt())
                b.at(pr.bx, pr.by, zb, 0f); b.cyl(0f, 0f, 0f, 8f, pr.h + 10f, 0xFF6A6A78.toInt())
                line(false, pr.x, pr.y, za + pr.h, pr.bx, pr.by, zb + pr.h, 2f, 0xFF1B1530.toInt())
            }
            is LootMachine -> {
                b.box(0f, 20f, 0f, 50f, 40f, 50f, 0xFFFF5EC4.toInt())
                b.glass(Meshes.BOX, 0f, 62f, 0f, 48f, 44f, 48f, 0xFFBFE8FF.toInt(), 0.3f)
                for (k in 0 until 4) b.ball(-12f + k * 8f, 46f, (k % 2) * 12f - 6f, 12f, intArrayOf(0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFFF9A3D.toInt())[k])
                b.box(0f, 86f, 0f, 54f, 8f, 54f, 0xFFFFD23F.toInt())
                val drop = if (pr.anim > 0) (1.3f - pr.anim) * 26f else 0f
                b.seg(Meshes.CYL, sin(t * 2f) * 12f, 82f, 0f, sin(t * 2f) * 12f, 70f - drop, 0f, 1.5f, MB.INK)
                b.ball(sin(t * 2f) * 12f, 68f - drop, 0f, 8f, 0xFFB0B8C8.toInt())
            }
            is Teleporter -> {
                b.cyl(0f, 0f, 0f, 56f, 6f, 0xFF3A3A44.toInt())
                b.emissive(Meshes.CYL, 0f, 6.5f, 0f, 44f, 1f, 44f, pr.color)
                for (k in 0 until 3) { val h = (t * 50f + k * 20f) % 60f; decal(true, pr.x, pr.y, pr.z + 6f + h, Spr.RING, 22f - h * 0.15f, 22f - h * 0.15f, 0f, pr.color, 1f - h / 60f, 0.2f) }
            }
            is MovingPlatform -> {
                b.at(pr.x, pr.y, pr.topZ, 0f)
                b.box(0f, -9f, 0f, pr.hw * 2, 18f, pr.hh * 2, 0xFFFFC23D.toInt())
                b.box(0f, -0.5f, 0f, pr.hw * 2 - 16f, 2f, pr.hh * 2 - 16f, 0xFFFFD86B.toInt(), cat = Cat.FLAT)
                b.box(0f, -22f, 0f, pr.hw * 1.6f, 10f, pr.hh * 1.6f, 0xFF7383B3.toInt())
            }
            is Pendulum -> {
                val top = pr.z + 190f
                b.reset()
                b.at(pr.x, pr.y, top, 0f); b.ball(0f, 0f, 0f, 16f, 0xFF3A3A44.toInt())
                b.at(pr.bx, pr.by, pr.z, 0f)
                b.seg(Meshes.CYL, pr.x - pr.bx, top - pr.z, pr.y - pr.by, 0f, 60f, 0f, 5f, 0xFF6A6A78.toInt())
                b.at(pr.bx, pr.by, pr.z, pr.axisAng + 1.5708f)
                b.push(); b.move(0f, 40f, 0f, rx = sin(t * pr.speed) * 0.45f)
                b.part(Meshes.WEDGE, 0f, -2f, 0f, 60f, 40f, 6f, 0xFFD8DEE8.toInt(), rz = 3.1416f)
                b.box(0f, 22f, 0f, 14f, 14f, 10f, 0xFF8B5A3C.toInt())
                b.pop()
                decal(false, pr.bx, pr.by, pr.z, Spr.BLOB, 30f, 30f, 0f, 0xFF000000.toInt(), 0.3f)
            }
            is MouseTrap -> {
                b.box(0f, 3f, 0f, 60f, 6f, 40f, 0xFFC28A4E.toInt())
                b.push(); b.move(-24f, 7f, 0f, rz = if (pr.armed) 2.9f else 0.05f)
                b.box(26f, 0f, 0f, 52f, 3f, 36f, 0xFFB0B8C8.toInt(), cat = Cat.FLAT)
                b.pop()
                b.part(Meshes.WEDGE, 14f, 14f, 0f, 14f, 12f, 14f, 0xFFFFD23F.toInt())
            }
            is SpinBar -> {
                b.cyl(0f, 0f, 0f, 26f, 40f, 0xFF4A4A55.toInt())
                b.at(pr.x, pr.y, pr.z, pr.a)
                for (k in 0 until pr.arms) b.box(pr.len / 2, 26f, 0f, pr.len, 14f, 14f, if (k % 2 == 0) 0xFFE84545.toInt() else 0xFFFFFFFF.toInt(), ry = -k * 6.2832f / pr.arms)
                b.ball(0f, 40f, 0f, 20f, 0xFFFFE14D.toInt())
            }
            is Press -> {
                val p = pr.phase
                val h = when { p < 0.6f -> 150f; p < 0.78f -> 150f - (p - 0.6f) / 0.18f * 20f; p < 0.86f -> 4f; else -> 4f + (p - 0.86f) / 0.14f * 146f }
                decal(false, pr.x, pr.y, pr.z, Spr.BAR, T / 2 - 2f, T / 2 - 2f, 0f, 0xFFFF3D3D.toInt(), if (p > 0.55f && p < 0.86f) 0.6f else 0.2f)
                b.cyl(0f, h + 30f, 0f, 14f, 300f, 0xFF8A93A8.toInt())
                b.box(0f, h + 15f, 0f, T, 30f, T, 0xFF4A5480.toInt())
                b.box(0f, h + 26f, 0f, T + 1f, 8f, T + 1f, 0xFFFFC23D.toInt(), cat = Cat.FLAT)
            }
            is BananaPeel -> { b.part(Meshes.TORUS, 0f, 3f, 0f, 30f, 14f, 22f, 0xFFFFE14D.toInt()); b.ball(0f, 4f, 0f, 10f, 0xFFF5ECD8.toInt()) }
            is FlagProp -> if (pr.carrier == null) {
                if (pr.atHome) decal(false, pr.x, pr.y, pr.z, Spr.DISC, 32f, 32f, 0f, pr.color, 0.45f)
                b.cyl(0f, 0f, 0f, 4f, 92f, MB.INK)
                b.part(Meshes.BOX, 0f, 80f, 18f, 2f, 22f, 36f, pr.color, rx = sin(t * 5f) * 0.12f)
                b.glowBall(0f, 94f, 0f, 10f, pr.color, 0.8f)
            }
            is Ball -> {
                b.at(pr.x, pr.y, pr.z + pr.radius, 0f)
                b.push(); b.move(0f, 0f, 0f, rz = -pr.rot, rx = pr.rot * 0.6f)
                b.ball(0f, 0f, 0f, pr.radius * 2f, 0xFFFFFFFF.toInt())
                for (k in 0 until 6) { val a = k * 1.047f; b.part(Meshes.SPHERE, cos(a) * pr.radius * 0.92f, sin(a) * pr.radius * 0.6f, (k % 3 - 1) * pr.radius * 0.5f, pr.radius * 0.5f, pr.radius * 0.5f, pr.radius * 0.5f, 0xFF2A2433.toInt(), cat = Cat.FLAT) }
                b.pop()
                decal(false, pr.x, pr.y, pr.gz.coerceAtLeast(0f), Spr.BLOB, pr.radius * 1.3f, pr.radius * 1.3f, 0f, 0xFF000000.toInt(), 0.4f)
            }
            is Snowball -> { b.at(pr.x, pr.y, gz(pr.x, pr.y) + pr.r, 0f); b.push(); b.move(0f, 0f, 0f, rz = -t * 6f); b.ball(0f, 0f, 0f, pr.r * 2f, 0xFFF3F8FF.toInt()); b.ball(pr.r * 0.4f, pr.r * 0.5f, 0f, pr.r * 0.5f, 0xFFD8E4F2.toInt()); b.pop() }
            is Portal -> {
                b.at(pr.x, pr.y, pr.z, 0f)
                b.part(Meshes.TORUS, 0f, 46f, 0f, 90f, 110f, 90f, 0xFFB46CFF.toInt(), rx = 1.5708f, rz = t * 0.5f)
                b.emissive(Meshes.SPHERE, 0f, 46f, 0f, 60f, 76f, 8f, 0xFF4A1F7A.toInt())
                bill(true, pr.x, pr.y, pr.z + 46f, Spr.GLOW, 60f + sin(t * 4f) * 6f, 0xFFB46CFF.toInt(), 0.8f)
            }
            is CastleCore -> {
                b.flash = if (pr.flash > 0) 0.5f else 0f
                b.cyl(0f, 0f, 0f, 84f, 70f, 0xFFB0B8C8.toInt())
                for (k in 0 until 8) { val a = k * 0.785f; b.box(cos(a) * 36f, 77f, sin(a) * 36f, 14f, 14f, 14f, 0xFFB0B8C8.toInt()) }
                b.box(42f, 15f, 0f, 2f, 30f, 22f, 0xFF6B4028.toInt(), cat = Cat.FLAT)
                b.cyl(0f, 70f, 0f, 3f, 96f, MB.INK)
                b.flash = 0f
                bill(true, pr.x, pr.y, pr.z + 120f + sin(t * 2f) * 6f, Spr.STAR, 28f, 0xFFFFE14D.toInt(), 1f, t)
                bill(true, pr.x, pr.y, pr.z + 120f, Spr.GLOW, 60f, 0xFFFFE14D.toInt(), 0.5f)
            }
            is StickyMine -> { b.at(pr.x, pr.y, pr.z, 0f); b.ball(0f, 0f, 0f, 16f, 0xFFFF5E7E.toInt()); if ((pr.t * 10).toInt() % 2 == 0) bill(true, pr.x, pr.y, pr.z + 4f, Spr.GLOW, 16f, 0xFFFFFFFF.toInt()) }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ projectiles, zones, effects
    private fun projectiles() {
        for (p in m.projs) {
            if (!p.alive || !near(p.x, p.y)) continue
            val r = p.r * p.big
            val a = atan2(p.vy, p.vx)
            val add = p.vis == com.starguys.battle.game.PV.FLAME
            if (add) { bill(true, p.x, p.y, p.z, Spr.GLOW, r * 3f, p.color, 0.8f); continue }
            b.at(p.x, p.y, p.z, a)
            EntityModels.projectile(b, p.vis, p.color, r, p.rot)
            bill(true, p.x, p.y, p.z, Spr.GLOW, r * 3.2f, p.color, 0.55f)
            if (!p.lob) line(true, p.x, p.y, p.z, p.x - p.vx * 0.045f, p.y - p.vy * 0.045f, p.z - p.vz * 0.045f, r * 0.8f, p.color, 0.5f)
            else { val g = gz(p.x, p.y); decal(false, p.x, p.y, g, Spr.BLOB, r * 1.6f, r * 1.6f, 0f, 0xFF000000.toInt(), 0.35f) }
        }
    }

    private fun zones() {
        val t = m.time
        for (z in m.zones) {
            if (!z.alive) continue
            val fade = (z.life / 0.4f).coerceIn(0f, 1f)
            val h = gz(z.x, z.y)
            when (z.kind) {
                Zone.VOLT -> {
                    decal(true, z.x, z.y, h, Spr.DISC, z.r, z.r, 0f, 0xFFFFE14D.toInt(), 0.18f * fade)
                    decal(true, z.x, z.y, h, Spr.RING, z.r, z.r, 0f, 0xFFFFE14D.toInt(), 0.8f * fade, 0.06f)
                    for (k in 0 until 4) { val a = t * 2f + k * 1.57f; line(true, z.x + cos(a) * z.r * 0.4f, z.y + sin(a) * z.r * 0.4f, h + 10f, z.x + cos(a + 0.4f) * z.r * 0.85f, z.y + sin(a + 0.4f) * z.r * 0.85f, h + 20f + sin(t * 20f + k) * 10f, 4f, 0xFFFFF27A.toInt(), fade) }
                }
                Zone.RAIN -> {
                    decal(false, z.x, z.y, h, Spr.DISC, z.r, z.r, 0f, 0xFF4FB5F0.toInt(), 0.3f * fade)
                    b.alpha = fade
                    b.at(z.x, z.y, h + 170f, 0f)
                    b.ball(-z.r * 0.4f, 0f, 0f, z.r * 0.7f, 0xFF8A93A8.toInt()); b.ball(z.r * 0.4f, 0f, z.r * 0.2f, z.r * 0.7f, 0xFF8A93A8.toInt()); b.ball(0f, z.r * 0.15f, -z.r * 0.2f, z.r * 0.85f, 0xFF9AA3B8.toInt())
                    b.alpha = 1f
                    for (k in 0 until 16) {
                        val px = z.x + ((k * 37.3f) % (z.r * 1.6f)) - z.r * 0.8f; val py = z.y + ((k * 53.7f) % (z.r * 1.6f)) - z.r * 0.8f
                        val pz = h + 160f - ((t * 500f + k * 53f) % 160f)
                        line(false, px, py, pz, px, py, pz - 16f, 1.6f, 0xAA6BD3FF.toInt(), fade)
                    }
                }
                Zone.GLUE -> decal(false, z.x, z.y, h, Spr.DISC, z.r, z.r, 0f, 0xFFB6E35A.toInt(), 0.7f * fade)
                Zone.SLIME -> decal(false, z.x, z.y, h, Spr.DISC, z.r, z.r, 0f, 0xFF7BE84A.toInt(), 0.7f * fade)
                Zone.METEOR -> {
                    val k = 1f - z.life / z.maxLife
                    decal(false, z.x, z.y, h, Spr.DISC, z.r, z.r, 0f, 0xFFFF3D3D.toInt(), 0.15f + 0.25f * k)
                    decal(true, z.x, z.y, h, Spr.RING, z.r, z.r, 0f, 0xFFFF3D3D.toInt(), 0.9f, 0.06f)
                    val hh = (1f - k) * 900f
                    b.at(z.x + hh * 0.3f, z.y, h + hh + 20f, 0f)
                    b.emissive(Meshes.SPHERE, 0f, 0f, 0f, 52f, 52f, 52f, 0xFFFF7A3D.toInt())
                    bill(true, z.x + hh * 0.3f, z.y, h + hh + 20f, Spr.GLOW, 90f, 0xFFFFB347.toInt(), 0.9f)
                    line(true, z.x + hh * 0.3f, z.y, h + hh + 20f, z.x + hh * 0.3f + 120f, z.y, h + hh + 420f, 26f, 0xFFFF7A3D.toInt(), 0.5f)
                }
                Zone.JET -> for (k in 0 until 6) { val d = ((t * 400f + k * 60f) % z.r); bill(true, z.x + cos(z.dir) * d, z.y + sin(z.dir) * d, h + 30f, Spr.GLOW, 12f + d * 0.06f, 0xFF6BD3FF.toInt(), 0.8f) }
                Zone.FIRE -> for (k in 0 until 5) { val a = k * 1.256f + t; bill(true, z.x + cos(a) * z.r * 0.5f, z.y + sin(a) * z.r * 0.4f, h + 10f + ((t * 60f + k * 9) % 30f), Spr.GLOW, 18f + sin(t * 12 + k) * 5f, 0xFFFF7A3D.toInt(), fade) }
            }
        }
        if (m.stormR > 0f) {
            b.reset(); b.at(m.stormX, m.stormY, -200f, 0f)
            b.glass(Meshes.CYL, 0f, 900f, 0f, m.stormR * 2f, 1800f, m.stormR * 2f, 0xFFB46CFF.toInt(), 0.28f)
        }
        val myTeam = m.player?.team
        for (pg in m.pings) {
            if (myTeam != null && pg.team != myTeam) continue
            val a = clamp(pg.life, 0f, 1f)
            val h = gz(pg.x, pg.y)
            decal(true, pg.x, pg.y, h, Spr.RING, 22f + (t * 50f % 34f), 22f + (t * 50f % 34f), 0f, 0xFFFBBF24.toInt(), a, 0.2f)
            line(true, pg.x, pg.y, h, pg.x, pg.y, h + 600f, 10f, 0xFFFDE68A.toInt(), 0.5f * a)
        }
    }

    private fun effects() {
        for (q in m.fx.ps) {
            val k = q.life / q.max
            when (q.kind) {
                Fx.DOT -> bill(false, q.x, q.y, q.z, Spr.DISC, q.size * (0.5f + k * 0.5f), q.color)
                Fx.CONF -> spr(f.spritesAlpha, q.x, q.y, q.z, Spr.BILL + Spr.SQUARE, q.size * abs(cos(q.rot * 3f)) + 1f, q.size * 0.45f, q.rot, 0f, q.color, 1f)
                Fx.SMOKE -> bill(false, q.x, q.y, q.z, Spr.SMOKE, q.size * (1.6f - k * 0.6f) * 1.4f, q.color, k * 0.85f)
                Fx.RING -> { val r = q.size * (1.15f - k * 0.85f).coerceAtLeast(0.05f); decal(true, q.x, q.y, q.z, Spr.RING, r, r, 0f, q.color, k, 0.12f) }
                Fx.LINE -> line(true, q.x, q.y, q.z, q.x2, q.y2, q.z2, q.size * k + 1f, q.color, k)
                Fx.STAR -> bill(true, q.x, q.y, q.z, Spr.STAR, q.size * k + 3f, q.color, 1f, q.rot)
                Fx.ARC -> {
                    val r = q.size * (1.05f - k * 0.15f)
                    var px = q.x + cos(q.x2 - q.y2) * r; var py = q.y + sin(q.x2 - q.y2) * r
                    for (i in 1..6) { val aa = q.x2 - q.y2 + q.y2 * 2f * i / 6f; val nx = q.x + cos(aa) * r; val ny = q.y + sin(aa) * r; line(true, px, py, q.z, nx, ny, q.z, 10f * k + 2f, q.color, 0.8f * k); px = nx; py = ny }
                }
                Fx.PIX -> bill(true, q.x, q.y, q.z, Spr.SQUARE, q.size * 0.5f, q.color, k)
                Fx.DEBRIS -> { b.reset(); b.at(q.x, q.y, q.z, q.rot); b.part(Meshes.BOX, 0f, 0f, 0f, q.size, q.size, q.size, q.color, rz = q.rot * 1.3f, rx = q.rot * 0.7f) }
                Fx.FLAME -> bill(true, q.x, q.y, q.z, Spr.GLOW, q.size * k * 2f + 2f, MB.light(0xFFFF5E3D.toInt(), k * 0.6f), 0.9f)
                Fx.DROP -> bill(false, q.x, q.y, q.z, Spr.DISC, q.size, q.color, k)
                Fx.GLOW -> bill(true, q.x, q.y, q.z, Spr.GLOW, q.size * (1.25f - k * 0.5f) * 1.3f, q.color, k)
            }
        }
    }

    /** backdrop hills & aim previews */
    private fun extras() {
        val mp = m.map
        if (!mp.oobVoid && mp.biome != Biome.SPACE && mp.biome != Biome.KITCHEN && mp.biome != Biome.FACTORY) {
            b.reset()
            val cx = mp.pxW / 2; val cy = mp.pxH / 2; val rr = maxOf(mp.pxW, mp.pxH) * 0.5f + 900f
            for (k in 0 until 22) {
                val a = k * 0.2856f + 0.3f
                val d = rr + (k % 3) * 500f
                val s = 600f + (k * 137 % 5) * 160f
                b.at(cx + cos(a) * d, cy + sin(a) * d, -4f, a)
                val col = when (mp.biome) { Biome.SNOW -> 0xFFE2E8F0.toInt(); Biome.BEACH -> 0xFF0EA5E9.toInt(); Biome.VOLCANO -> 0xFF57534E.toInt(); Biome.SWAMP -> 0xFF14532D.toInt(); else -> MB.shade(mp.biome.bush, 0.8f + (k % 3) * 0.08f) }
                if (mp.biome == Biome.BEACH) b.part(Meshes.QUAD, 0f, -2f, 0f, s * 2.5f, 1f, s * 2.5f, col, cat = Cat.FLAT)
                else b.part(Meshes.HEMI, 0f, 0f, 0f, s * 1.6f, s * (0.5f + (k % 4) * 0.12f), s * 1.6f, col, cat = Cat.FLAT)
            }
        }
        val pl = m.player ?: return
        if (pl.out) return
        val hud = m.hud
        val aim = hud.aimPreview ?: return
        val px = pl.x; val py = pl.y; val h = pl.z + 2f
        val col = if (aim.superAim) 0xFFFFE14D.toInt() else 0xFFFFFFFF.toInt()
        when (aim.aim) {
            Aim.LINE -> decal(false, px + cos(aim.a) * aim.range / 2, py + sin(aim.a) * aim.range / 2, h, Spr.BAR, aim.range / 2, maxOf(10f, aim.width), aim.a, col, 0.35f)
            Aim.CONE, Aim.ARC -> decal(false, px, py, h, Spr.SECTOR, aim.range, aim.range, aim.a, col, 0.35f, Math.toRadians((if (aim.spread > 0f) aim.spread else 30f) / 2.0).toFloat())
            Aim.LOB, Aim.POINT -> {
                val tx = px + cos(aim.a) * aim.d; val ty = py + sin(aim.a) * aim.d
                val tz = gz(tx, ty)
                for (k in 1..10) { val tt = k / 11f; bill(true, px + (tx - px) * tt, py + (ty - py) * tt, h + 30f + (tz - h) * tt + sin(tt * 3.14f) * aim.d * 0.3f, Spr.DISC, 4f, col, 0.8f) }
                decal(false, tx, ty, tz, Spr.DISC, maxOf(aim.area, 30f), maxOf(aim.area, 30f), 0f, col, 0.25f)
                decal(true, tx, ty, tz, Spr.RING, maxOf(aim.area, 30f), maxOf(aim.area, 30f), 0f, col, 0.8f, 0.08f)
            }
            Aim.SELF -> decal(true, px, py, h, Spr.RING, aim.range, aim.range, 0f, col, 0.6f, 0.04f)
        }
    }

    /** generic helpers used by game modes */
    fun groundDisc(x: Float, y: Float, r: Float, col: Int, a: Float) = decal(false, x, y, gz(x, y), Spr.DISC, r, r, 0f, col, a)
    fun groundRing(x: Float, y: Float, r: Float, col: Int, a: Float) = decal(true, x, y, gz(x, y), Spr.RING, r, r, 0f, col, a, 0.06f)
    fun groundRect(x: Float, y: Float, hw: Float, hh: Float, col: Int, a: Float) = decal(false, x, y, gz(x, y), Spr.BAR, hw, hh, 0f, col, a)
    fun ground(x: Float, y: Float) = gz(x, y)
}

@Suppress("unused") private fun keep(a: Float) = dist(a, a, a, a) + angleDiff(a, a)
