package com.starguys.battle.game

import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Hero supers and gadgets. */
object Supers {
    fun activate(f: Fighter, a: Float, d: Float) {
        val m = f.m
        when (f.hero.id) {
            0 -> { // Gumbo: bouncing bubble
                f.superKind = 0; f.superT = 2.6f
                f.vx = cosf(a) * 560f; f.vy = sinf(a) * 560f; f.vz = 260f
                m.sound(Sfx.BOING, f.x, f.y)
            }
            1 -> { // Volt: recharge field
                m.addZone(Zone(m, Zone.VOLT, f.x, f.y, 3f * T, 5f, f, f.team).also { it.follow = f })
                m.sound(Sfx.ZAP, f.x, f.y)
            }
            2 -> { // Mama Tortue: shell ball
                f.superKind = 2; f.superT = 1.4f
                f.vx = cosf(a) * 760f; f.vy = sinf(a) * 760f
            }
            3 -> { // Pixel: teleport + invisibility
                val dd = clamp(d, T, 4.5f * T)
                var tx = f.x + cosf(a) * dd; var ty = f.y + sinf(a) * dd
                var steps = 0
                while (steps < 20 && !m.canStand(tx, ty, f.radius)) { tx -= cosf(a) * 12f; ty -= sinf(a) * 12f; steps++ }
                if (steps >= 20) { tx = f.x; ty = f.y }
                m.fx.pixels(f.x, f.y - f.z * ZS - 25f, 0xFF35C6F4.toInt())
                f.x = tx; f.y = ty; f.z = maxOf(0f, m.map.groundAt(tx, ty)); f.vx = 0f; f.vy = 0f
                m.fx.pixels(f.x, f.y - f.z * ZS - 25f, 0xFF35C6F4.toInt())
                f.invisT = 2.2f
                m.sound(Sfx.TELEPORT, f.x, f.y)
            }
            4 -> { // Chef Boulette: onigiris
                for (k in 0 until 3) {
                    val aa = a + (k - 1) * 0.25f
                    Fire.lob(f, aa, d + rf(-20f, 20f), 6f, 300f, 0f, PV.ONIGIRI, 0xFFFFFFFF.toInt(), 0.75f) {
                        heal = 1500f; r = 14f
                        onLand = { p -> if (rf() < 0.5f) m.addPickup(Pickup(m, Pickup.HEAL, 0, p.x, p.y)); m.fx.ring(p.x, p.y, 60f, 0xFF7CFF8A.toInt()); m.sound(Sfx.HEAL, p.x, p.y) }
                    }
                }
            }
            5 -> { // Captain Cork: giant cork
                Fire.lob(f, a, d, 7.5f, 2200f, 2.1f, PV.BIGCORK, 0xFFD2A36A.toInt(), 0.85f) { r = 22f; knock = 900f; tileDmg = 3f }
                m.sound(Sfx.POP, f.x, f.y)
            }
            6 -> { // Nuage: rain
                val dd = clamp(d, 0f, 6f * T)
                m.addZone(Zone(m, Zone.RAIN, f.x + cosf(a) * dd, f.y + sinf(a) * dd, 2.6f * T, 5f, f, f.team))
            }
            7 -> { // Rex: instant brick wall
                val dd = clamp(d, T * 1.2f, 4f * T)
                val cx = f.x + cosf(a) * dd; val cy = f.y + sinf(a) * dd
                val px = -sinf(a); val py = cosf(a)
                var placed = 0
                for (k in -2..2) {
                    val wx = cx + px * k * T * 0.9f; val wy = cy + py * k * T * 0.9f
                    val tx = (wx / T).floorInt(); val ty = (wy / T).floorInt()
                    if (!m.map.inside(tx, ty)) continue
                    val tp = m.map.t(tx, ty)
                    if (tp != Tl.FLOOR && tp != Tl.BUSH && tp != Tl.ICE) continue
                    if (m.bodyOnTile(tx, ty)) continue
                    m.map.set(tx, ty, Tl.BRICK); placed++
                    m.fx.smoke(tx * T + T / 2, ty * T + T / 2, 3, 0xFFE07B4A.toInt())
                }
                m.sound(Sfx.THUD, cx, cy)
                if (placed == 0) f.chargeSuper(0.5f)
            }
            8 -> { f.superKind = 8; f.superT = 2.6f; f.absorbStore = 0f; m.sound(Sfx.BUBBLE, f.x, f.y) }
            9 -> { // Glitch clones
                for (k in 0 until 3) {
                    val s = Summon(m, Summon.CLONE, f)
                    s.x = f.x + rf(-10f, 10f); s.y = f.y + rf(-10f, 10f); s.z = f.z
                    s.dir = a + (k - 1) * 1.6f + rf(-0.3f, 0.3f)
                    m.addSummon(s)
                }
                m.fx.pixels(f.x, f.y - 30f, 0xFFB46CFF.toInt())
                m.sound(Sfx.TELEPORT, f.x, f.y)
            }
            10 -> { // Mamie: umbrella shield + glide
                f.superKind = 10; f.superT = 4f
                f.vz = 520f; f.vx = cosf(a) * 380f; f.vy = sinf(a) * 380f
                f.onGround = false
            }
            11 -> { // Bolt: explosive toasts
                for (k in 0 until 5) {
                    Fire.lob(f, a + rf(-0.35f, 0.35f), d + rf(-50f, 50f), 6f, 900f, 1.0f, PV.TOAST, 0xFFE0A050.toInt(), rf(0.5f, 0.8f)) { r = 13f }
                }
            }
            12 -> { f.dashT = 0.35f; f.vx = cosf(a) * 650f; f.vy = sinf(a) * 650f; f.ragdollT = 0f }
            13 -> { f.vz = 520f; f.vx = cosf(a) * 700f; f.vy = sinf(a) * 700f; f.dashT = 0.5f }
        }
    }

    /** Returns true if a charge was consumed. */
    fun gadget(f: Fighter, id: Int, a: Float): Boolean {
        val m = f.m
        when (id) {
            Gadgets.TRAMPOLINE -> {
                m.addProp(JumpPad(m, f.x, f.y, temp = 10f))
                f.vz = 900f; f.onGround = false
                m.sound(Sfx.BOING, f.x, f.y)
            }
            Gadgets.WALL -> {
                val cx = f.x + cosf(a) * T * 1.3f; val cy = f.y + sinf(a) * T * 1.3f
                val px = -sinf(a); val py = cosf(a)
                var n = 0
                for (k in -1..1) {
                    val tx = ((cx + px * k * T) / T).floorInt(); val ty = ((cy + py * k * T) / T).floorInt()
                    val tp = m.map.t(tx, ty)
                    if ((tp == Tl.FLOOR || tp == Tl.BUSH || tp == Tl.ICE) && !m.bodyOnTile(tx, ty)) { m.map.set(tx, ty, Tl.BOX); m.map.hp[m.map.idx(tx, ty)] = 900f; n++ }
                }
                m.sound(Sfx.BUBBLE, cx, cy)
                if (n == 0) return false
            }
            Gadgets.DECOY -> {
                val s = Summon(m, Summon.DECOY, f)
                s.x = f.x + cosf(a) * 40f; s.y = f.y + sinf(a) * 40f
                m.addSummon(s)
            }
            Gadgets.TURRET -> {
                val s = Summon(m, Summon.TURRET, f)
                s.x = f.x + cosf(a) * 36f; s.y = f.y + sinf(a) * 36f
                m.addSummon(s)
            }
            Gadgets.JETPACK -> {
                val ma = if (f.moveX != 0f || f.moveY != 0f) atan2(f.moveY, f.moveX) else f.face
                f.vz = 560f; f.vx = cosf(ma) * 520f; f.vy = sinf(ma) * 520f; f.onGround = false
                m.fx.smoke(f.x, f.y - f.z * ZS, 4, 0xFFFFC94D.toInt())
                m.sound(Sfx.ENGINE, f.x, f.y)
            }
            Gadgets.PARACHUTE -> { if (f.human) m.hud.toastSmall("Le parachute s'active tout seul en cas de chute !"); return false }
            Gadgets.BANANA -> {
                m.addProp(BananaPeel(m, f.x - cosf(f.face) * 40f, f.y - sinf(f.face) * 40f, f.team, f))
                m.sound(Sfx.SWING, f.x, f.y, 0.5f)
            }
            Gadgets.GLUE -> {
                Fire.lob(f, a, 4f * T, 5f, 150f, 0f, PV.GLUEBOMB, 0xFFB6E35A.toInt(), 0.6f) { status = St.GLUE; statusT = 3f; r = 12f }
            }
            Gadgets.RADAR -> {
                m.reveal(f.team, f.x, f.y, 13f * T, 8f)
                m.fx.ring(f.x, f.y, 13f * T, 0xFF35C6F4.toInt(), 1.2f)
                m.sound(Sfx.ZAP, f.x, f.y, 0.7f, 0.6f)
            }
        }
        return true
    }

    fun gadgetIconLabel(id: Int) = Gadgets.list[id].name.split(" ")[0]
    @Suppress("unused") fun pct(v: Float) = (v * 100).roundToInt()
    @Suppress("unused") fun angTo(f: Fighter, b: Body) = ang(f.x, f.y, b.x, b.y)
}
