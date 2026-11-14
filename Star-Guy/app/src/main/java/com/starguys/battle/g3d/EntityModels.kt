package com.starguys.battle.g3d

import com.starguys.battle.g3d.MB.Companion.INK
import com.starguys.battle.g3d.MB.Companion.WHITE
import com.starguys.battle.g3d.MB.Companion.light
import com.starguys.battle.g3d.MB.Companion.shade
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Boss
import com.starguys.battle.game.Decor
import com.starguys.battle.game.Gadgets
import com.starguys.battle.game.Items
import com.starguys.battle.game.Mob
import com.starguys.battle.game.PV
import com.starguys.battle.game.Vehicle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 3D models for everything that is not a hero. */
object EntityModels {
    private val CONF = intArrayOf(0xFFFF5E7E.toInt(), 0xFFFFE14D.toInt(), 0xFF6BD3FF.toInt(), 0xFF7CFF8A.toInt(), 0xFFB46CFF.toInt(), 0xFFFF9A3D.toInt())

    fun mob(b: MB, mb: Mob) {
        val t = mb.animT
        when (mb.kind) {
            Mob.GLUANT -> {
                val r = mb.radius
                val sq = if (!mb.onGround) 1.15f else 1f + sin(t * 8f) * 0.06f
                b.ell(0f, r * sq, 0f, r * 2.2f / sq, r * 2f * sq, r * 2.2f / sq, 0xFF7BE84A.toInt())
                b.ball(-r * 0.3f, r * 1.6f, -r * 0.4f, r * 0.5f, 0xFFD8FFC0.toInt(), Cat.FLAT)
                b.eyes(r * 0.95f, r * 1.15f, r * 0.35f, r * 0.5f, false, angry = true)
            }
            Mob.DRONE -> {
                val w = sin(t * 60f) * 0.5f
                b.ball(0f, 8f, 0f, 26f, 0xFF3A3A44.toInt())
                b.emissive(Meshes.SPHERE, 11f, 10f, 0f, 6f, 12f, 12f, 0xFFFF3D3D.toInt())
                b.glass(Meshes.SPHERE, -2f, 20f, -14f, 10f, 2f, 22f, WHITE, 0.55f)
                b.part(Meshes.SPHERE, -2f, 20f, 14f, 10f, 2f, 22f, WHITE, rx = w, cat = Cat.GLASS, a = 0.55f)
                b.seg(Meshes.CYL, 0f, 0f, -6f, 0f, -10f, -10f, 2f, INK); b.seg(Meshes.CYL, 0f, 0f, 6f, 0f, -10f, 10f, 2f, INK)
            }
            Mob.TRASHBOT -> {
                val bo = HeroModels.bob(t, if (mb.vx * mb.vx + mb.vy * mb.vy > 400f) 1f else 0f)
                b.box(0f, 4f, -10f, 14f, 8f, 10f, 0xFF3A3A44.toInt()); b.box(0f, 4f, 10f, 14f, 8f, 10f, 0xFF3A3A44.toInt())
                b.cyl(0f, 8f + bo, 0f, 44f, 56f + bo, 0xFF6E8C5A.toInt())
                b.cyl(0f, 55f + bo, 0f, 50f, 63f + bo, 0xFF5A7448.toInt())
                b.box(21f, 39f + bo, 0f, 3f, 14f, 26f, 0xFF1E2A3A.toInt(), cat = Cat.FLAT)
                b.emissive(Meshes.SPHERE, 22.5f, 40f + bo, -6f, 2f, 6f, 6f, 0xFFFF3D3D.toInt()); b.emissive(Meshes.SPHERE, 22.5f, 40f + bo, 6f, 2f, 6f, 6f, 0xFFFF3D3D.toInt())
                b.seg(Meshes.BOX, 0f, 38f + bo, -22f, 12f, 20f + bo, -32f, 6f, 0xFF5A7448.toInt()); b.seg(Meshes.BOX, 0f, 38f + bo, 22f, 12f, 20f + bo, 32f, 6f, 0xFF5A7448.toInt())
            }
            Mob.GNOME -> {
                val bo = HeroModels.bob(t, 1f)
                b.ell(2f, 3f, -6f, 12f, 6f, 8f, 0xFF6B4028.toInt()); b.ell(2f, 3f, 6f, 12f, 6f, 8f, 0xFF6B4028.toInt())
                b.ball(0f, 15f + bo, 0f, 24f, 0xFF3D7AE8.toInt())
                b.ball(1f, 28f + bo, 0f, 18f, HeroModels.SKIN)
                b.cone(6f, 13f + bo, 0f, 14f, 14f, WHITE, rz = 3.1416f)
                b.cone(0f, 33f + bo, 0f, 20f, 24f, 0xFFE84545.toInt())
                b.eyes(8.5f, 30f + bo, 3.5f, 4f, false)
                if ((t * 8).toInt() % 2 == 0) b.glowBall(12f, 42f + bo, 10f, 8f, 0xFFFFE14D.toInt())
            }
            Mob.CRAB -> {
                val wk = sin(t * 10f) * 4f
                for (s in intArrayOf(-1, 1)) for (k in -1..1) b.seg(Meshes.CYL, k * 9f, 12f, s * 16f, k * 12f, 0f, s * (30f + wk * s), 4f, 0xFFE84545.toInt())
                b.ell(0f, 18f, 0f, 36f, 26f, 52f, 0xFFFF6B4A.toInt())
                for (s in intArrayOf(-1, 1)) { b.seg(Meshes.CYL, 10f, 20f, s * 20f, 28f, 30f, s * 28f, 6f, 0xFFE84545.toInt()); b.ell(32f, 32f, s * 30f, 22f, 16f, 14f, 0xFFFF6B4A.toInt()) }
                for (s in intArrayOf(-1, 1)) { b.seg(Meshes.CYL, 10f, 26f, s * 6f, 12f, 40f, s * 8f, 2.5f, INK); b.ball(12f, 42f, s * 8f, 9f, WHITE, Cat.FLAT); b.ball(15f, 42f, s * 8f, 4f, INK, Cat.FLAT) }
            }
        }
    }

    fun boss(b: MB, bs: Boss) {
        val t = bs.animT
        when (bs.kind) {
            Mob.BOSS_JELLY -> {
                val sq = 1f + sin(t * 4f) * 0.05f
                b.box(0f, 15f, 0f, 140f, 30f, 120f, 0xFF6A6A78.toInt())
                for (k in 0..3) for (s in intArrayOf(-1, 1)) b.part(Meshes.CYL, -52f + k * 35f, 8f, s * 60f, 22f, 8f, 22f, 0xFF3A3A44.toInt(), rx = 1.5708f)
                b.ell(0f, 30f + 70f * sq, 0f, 148f / sq, 140f * sq, 140f / sq, 0xFF5FCB3A.toInt())
                b.glass(Meshes.SPHERE, 0f, 30f + 70f * sq, 0f, 160f / sq, 152f * sq, 152f / sq, 0xFF9BFF6A.toInt(), 0.35f)
                for (k in 0 until 7) { val a = k * 0.8976f; b.cone(cos(a) * 34f, 168f * sq, sin(a) * 34f, 18f, 30f, 0xFFFFD23F.toInt()) }
                b.part(Meshes.TORUS, 0f, 170f * sq, 0f, 92f, 40f, 92f, 0xFFFFD23F.toInt())
                if (!bs.phase2) { b.cyl(0f, 180f, 0f, 8f, 200f, 0xFF8A93A8.toInt()); b.emissive(Meshes.SPHERE, 0f, 204f, 0f, 16f, 16f, 16f, 0xFFFF3D3D.toInt()) }
                b.eyes(66f, 110f, 22f, 30f, false, angry = true)
                b.box(70f, 75f, 0f, 8f, 12f, 50f, 0xFF2A4A1A.toInt(), cat = Cat.FLAT)
            }
            Mob.BOSS_HAMMER -> {
                b.push()
                if (bs.state == 5) b.move(0f, 0f, 0f, ry = bs.spinA)
                b.cyl(0f, 0f, 0f, 22f, 110f, 0xFF8B5A3C.toInt())
                b.box(0f, 135f, 0f, 76f, 76f, 150f, 0xFF8A93A8.toInt())
                b.box(0f, 135f, -70f, 82f, 82f, 14f, 0xFFB0B8C8.toInt()); b.box(0f, 135f, 70f, 82f, 82f, 14f, 0xFFB0B8C8.toInt())
                b.eyes(39f, 145f, 22f, 26f, false, angry = true)
                b.box(39f, 115f, 0f, 3f, 8f, 50f, INK, cat = Cat.FLAT)
                b.pop()
                for (s in intArrayOf(-1, 1)) b.seg(Meshes.CYL, 0f, 40f, s * 10f, 0f, 0f, s * 40f, 12f, 0xFF8B5A3C.toInt())
            }
            Mob.BOSS_CLAW -> {
                for (k in 0 until 6) {
                    val a = k * 1.047f + sin(t * 2 + k) * 0.2f + (if (bs.state == 4) bs.spinA else 0f)
                    val ex = cos(a) * 90f; val ez = sin(a) * 90f
                    b.seg(Meshes.CYL, 0f, 40f, 0f, ex, 8f + sin(t * 3f + k) * 6f, ez, 18f, 0xFFB46CFF.toInt())
                    b.ball(ex, 8f, ez, 20f, 0xFFD8A8FF.toInt())
                }
                b.box(0f, 100f, 0f, 112f, 120f, 112f, 0xFFFF5EC4.toInt())
                b.glass(Meshes.BOX, 0f, 108f, 0f, 116f, 78f, 116f, 0xFFBFE8FF.toInt(), 0.35f)
                for (k in 0 until 5) b.ball(-30f + k * 15f, 80f + (k % 2) * 14f, (k - 2) * 12f, 20f, CONF[k])
                b.box(0f, 166f, 0f, 120f, 14f, 120f, 0xFFFFD23F.toInt())
                b.eyes(58f, 130f, 24f, 26f, false, angry = true)
            }
            else -> { // chef
                val bo = HeroModels.bob(t, if (bs.vx * bs.vx + bs.vy * bs.vy > 400f) 1f else 0f) * 2f
                b.ell(4f, 8f, -26f, 40f, 18f, 30f, 0xFF3A3A44.toInt()); b.ell(4f, 8f, 26f, 40f, 18f, 30f, 0xFF3A3A44.toInt())
                b.box(0f, 66f + bo, 0f, 90f, 112f, 100f, WHITE)
                for (k in 0..2) b.ball(46f, 100f + bo - k * 26f, -16f, 8f, INK, Cat.FLAT)
                b.ball(4f, 150f + bo, 0f, 72f, HeroModels.SKIN)
                b.ell(38f, 136f + bo, 0f, 10f, 14f, 52f, 0xFF6B4028.toInt())
                b.cyl(0f, 178f + bo, 0f, 60f, 216f + bo, WHITE)
                for (k in -1..1) b.ball(0f, 226f + bo, k * 22f, 40f, WHITE)
                b.eyes(36f, 158f + bo, 14f, 18f, false, angry = true)
                b.seg(Meshes.CYL, 20f, 90f + bo, 50f, 70f, 120f + bo, 70f, 10f, WHITE)
                b.part(Meshes.CYL, 90f, 128f + bo, 76f, 46f, 6f, 46f, 0xFF4A4A55.toInt())
            }
        }
    }

    fun vehicle(b: MB, v: Vehicle, drawDriver: (Float, Float, Float, Float) -> Unit) {
        val t = v.t
        when (v.kind) {
            Vehicle.BUMPER -> {
                b.box(0f, 13f, 0f, 68f, 26f, 56f, 0xFF3A3A44.toInt())
                b.box(-2f, 28f, 0f, 60f, 20f, 50f, 0xFFFF5E7E.toInt())
                b.part(Meshes.TORUS, 0f, 12f, 0f, 92f, 50f, 76f, 0xFFFF8CA8.toInt())
                b.cyl(-20f, 30f, 0f, 3f, 90f, INK); b.glowBall(-20f, 92f, 0f, 10f, 0xFFFFE14D.toInt())
                drawDriver(-2f, 24f, 0f, 0.72f)
            }
            Vehicle.DUCK -> {
                val bo = sin(t * 3f) * 2f
                b.ell(0f, 14f + bo, 0f, 76f, 34f, 56f, 0xFFFFE14D.toInt())
                b.ball(30f, 44f + bo, 0f, 30f, 0xFFFFE14D.toInt())
                b.cone(48f, 40f + bo, 0f, 12f, 16f, 0xFFFF9A3D.toInt(), rz = -1.5708f)
                b.eyes(40f, 50f + bo, 7f, 7f, false)
                drawDriver(-8f, 22f + bo, 0f, 0.72f)
            }
            Vehicle.UNICYCLE -> {
                b.push(); b.move(0f, 0f, 0f, rx = sin(t * 7f) * 0.14f)
                b.part(Meshes.TORUS, 0f, 18f, 0f, 44f, 44f, 44f, 0xFF3A3A44.toInt(), rx = 1.5708f)
                b.cyl(0f, 18f, 0f, 5f, 44f, 0xFFB0B8C8.toInt())
                b.part(Meshes.CYL, -18f, 36f, 0f, 12f, 22f, 12f, 0xFFE84545.toInt(), rz = 1.5708f)
                b.glowBall(-32f, 36f, 0f, 14f + sin(t * 30f) * 3f, 0xFFFF9A3D.toInt())
                drawDriver(0f, 44f, 0f, 0.78f)
                b.pop()
            }
            Vehicle.CRANE -> {
                b.box(0f, 16f, 0f, 70f, 26f, 50f, 0xFFFFC23D.toInt())
                for (sx in intArrayOf(-22, 22)) for (s in intArrayOf(-1, 1)) b.part(Meshes.CYL, sx.toFloat(), 10f, s * 26f, 22f, 8f, 22f, 0xFF3A3A44.toInt(), rx = 1.5708f)
                b.box(-16f, 44f, 0f, 26f, 30f, 30f, 0xFFFFC23D.toInt())
                b.box(-2.5f, 46f, 0f, 1f, 16f, 22f, 0xFFBFE8FF.toInt(), cat = Cat.FLAT)
                b.seg(Meshes.BOX, -4f, 56f, 0f, 70f, 100f, 0f, 8f, 0xFFC48A12.toInt())
                b.seg(Meshes.CYL, 70f, 100f, 0f, 70f, 60f, 0f, 2f, INK)
                b.box(70f, 56f, 0f, 14f, 6f, 14f, 0xFFB0B8C8.toInt())
                drawDriver(-16f, 34f, 0f, 0.5f)
            }
            Vehicle.BALLOON -> {
                val bo = sin(t * 1.5f) * 4f
                b.box(0f, 14f + bo, 0f, 44f, 28f, 44f, 0xFFC28A4E.toInt())
                for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) b.seg(Meshes.CYL, sx * 20f, 28f + bo, sz * 20f, sx * 30f, 110f + bo, sz * 30f, 1.6f, INK)
                b.ell(0f, 160f + bo, 0f, 100f, 116f, 100f, 0xFFFF5E7E.toInt())
                b.ell(0f, 160f + bo, 0f, 104f, 112f, 38f, 0xFFFFE14D.toInt())
                b.ell(0f, 160f + bo, 0f, 38f, 112f, 104f, 0xFF6BD3FF.toInt())
                b.glowBall(0f, 110f + bo, 0f, 14f, 0xFFFF9A3D.toInt(), 0.6f)
                drawDriver(0f, 26f + bo, 0f, 0.7f)
            }
            Vehicle.TANK -> {
                for (s in intArrayOf(-1, 1)) {
                    b.box(0f, 10f, s * 26f, 84f, 20f, 18f, 0xFF3A3A44.toInt())
                    for (k in 0..4) b.part(Meshes.CYL, -30f + k * 15f, 10f, s * 35.5f, 13f, 2f, 13f, 0xFF6A6A78.toInt(), rx = 1.5708f, cat = Cat.FLAT)
                }
                b.box(0f, 30f, 0f, 72f, 22f, 56f, 0xFF7C8C4A.toInt())
                b.push(); b.move(0f, 46f, 0f, ry = -(v.turret - v.heading))
                b.ell(0f, 0f, 0f, 40f, 22f, 40f, 0xFF8C9C5A.toInt())
                b.part(Meshes.CYL, 30f, 2f, 0f, 9f, 46f, 9f, 0xFF5D6B3A.toInt(), rz = -1.5708f)
                b.pop()
                drawDriver(-4f, 56f, 0f, 0.45f)
            }
            else -> { // coaster cart
                b.box(0f, 20f, 0f, 68f, 32f, 50f, 0xFFE84545.toInt())
                b.box(0f, 24f, 0f, 70f, 6f, 52f, 0xFFFFE14D.toInt(), cat = Cat.FLAT)
                for (sx in intArrayOf(-20, 20)) for (s in intArrayOf(-1, 1)) b.part(Meshes.CYL, sx.toFloat(), 5f, s * 24f, 14f, 6f, 14f, 0xFF3A3A44.toInt(), rx = 1.5708f)
                drawDriver(0f, 26f, 0f, 0.7f)
            }
        }
    }

    // ------------------------------------------------------------------ decorations
    fun decor(b: MB, kind: Int, t: Float, biome: Biome) {
        when (kind) {
            Decor.NEON -> { b.cyl(0f, 0f, 0f, 5f, 60f, 0xFF3A3A44.toInt()); b.box(0f, 78f, 0f, 8f, 36f, 64f, 0xFF2A2433.toInt()); b.emissive(Meshes.BOX, 4.5f, 78f, 0f, 1f, 24f, 52f, if ((t * 2).toInt() % 2 == 0) 0xFFFF5EC4.toInt() else 0xFF6BD3FF.toInt()) }
            Decor.BALLOONS -> for (k in 0 until 3) { val by = 80f + (k % 2) * 14f + sin(t * 2f + k) * 4f; val bz = -12f + k * 12f; b.seg(Meshes.CYL, 0f, 0f, 0f, 0f, by - 10f, bz, 1.2f, INK); b.ell(0f, by, bz, 20f, 24f, 20f, CONF[k]) }
            Decor.PENNANT -> { b.cyl(0f, 0f, -40f, 3f, 70f, INK); b.cyl(0f, 0f, 40f, 3f, 70f, INK); for (k in 0 until 5) b.part(Meshes.CONE, 0f, 62f, -32f + k * 16f, 12f, 14f, 2f, CONF[k], rz = 3.1416f) }
            Decor.CONE -> { b.cone(0f, 0f, 0f, 28f, 36f, 0xFFFF7A3D.toInt()); b.part(Meshes.TORUS, 0f, 16f, 0f, 22f, 10f, 22f, WHITE, cat = Cat.FLAT); b.box(0f, 1f, 0f, 30f, 2f, 30f, 0xFFFF7A3D.toInt()) }
            Decor.VENDING -> { b.box(0f, 38f, 0f, 30f, 76f, 48f, 0xFFE84545.toInt()); b.box(15.5f, 46f, -6f, 1f, 40f, 26f, 0xFFBFE8FF.toInt(), cat = Cat.FLAT); for (k in 0..2) b.ball(16f, 34f + k * 12f, -6f, 6f, CONF[k + 1], Cat.FLAT) }
            Decor.BENCH -> { b.box(0f, 20f, 0f, 18f, 4f, 60f, 0xFFC28A4E.toInt()); b.box(-8f, 34f, 0f, 3f, 14f, 60f, 0xFFC28A4E.toInt()); for (s in intArrayOf(-1, 1)) b.box(0f, 9f, s * 26f, 16f, 18f, 4f, 0xFF3A3A44.toInt()) }
            Decor.LAMP -> { b.cyl(0f, 0f, 0f, 5f, 110f, 0xFF3A3A44.toInt()); b.emissive(Meshes.SPHERE, 0f, 114f, 0f, 22f, 22f, 22f, 0xFFFFF3B0.toInt()); b.glowBall(0f, 114f, 0f, 44f, 0xFFFFE6A0.toInt(), 0.25f) }
            Decor.MUSHROOM -> { b.cyl(0f, 0f, 0f, 16f, 30f, 0xFFF5ECD8.toInt()); b.part(Meshes.HEMI, 0f, 28f, 0f, 56f, 36f, 56f, 0xFFE84545.toInt()); for (k in 0 until 4) b.ball(cos(k * 1.57f) * 16f, 40f, sin(k * 1.57f) * 16f, 9f, WHITE, Cat.FLAT) }
            Decor.STATUE -> { b.box(0f, 10f, 0f, 40f, 20f, 40f, 0xFFB0B8C8.toInt()); b.push(); b.move(0f, 20f, 0f); HeroModels.draw(b, 12, 0xFFD8DEE8.toInt(), 0f, 0f, false, false); b.pop() }
            Decor.WRECK -> { b.box(0f, 14f, 0f, 60f, 28f, 40f, 0xFF8A93A8.toInt(), ry = 0.3f, rz = 0.15f); b.box(-6f, 32f, 0f, 28f, 14f, 30f, 0xFF6A6A78.toInt(), ry = 0.4f) }
            Decor.CONTAINER -> { b.box(0f, 32f, 0f, 120f, 64f, 56f, if (biome == Biome.FACTORY) 0xFF35C6F4.toInt() else 0xFFE84545.toInt()); for (k in 0 until 6) b.box(-50f + k * 20f, 32f, 28.5f, 6f, 58f, 1f, 0x33000000 or 0x1B1530, cat = Cat.FLAT) }
            Decor.TENT -> { b.part(Meshes.WEDGE, 0f, 26f, 0f, 70f, 52f, 70f, 0xFFFF8CC6.toInt()); b.box(35.5f, 18f, 0f, 1f, 30f, 14f, 0xFF7A2E52.toInt(), cat = Cat.FLAT) }
            Decor.TOWER -> { for (s in intArrayOf(-1, 1)) for (q in intArrayOf(-1, 1)) b.seg(Meshes.BOX, s * 26f, 0f, q * 26f, s * 10f, 180f, q * 10f, 5f, 0xFF8A93A8.toInt()); b.box(0f, 184f, 0f, 36f, 10f, 36f, 0xFFB0B8C8.toInt()); b.emissive(Meshes.SPHERE, 0f, 196f, 0f, 10f, 10f, 10f, 0xFFFF3D3D.toInt()) }
            Decor.PALM -> {
                for (k in 0 until 6) b.ell(k * 2f, 12f + k * 22f, 0f, 16f - k, 24f, 16f - k, 0xFF8B5A3C.toInt())
                for (k in 0 until 6) { val a = k * 1.047f + sin(t) * 0.05f; b.part(Meshes.SPHERE, 12f + cos(a) * 30f, 140f, sin(a) * 30f, 70f, 6f, 20f, 0xFF22C55E.toInt(), ry = -a, rz = -0.35f) }
                b.ball(14f, 134f, 6f, 12f, 0xFF6B4028.toInt()); b.ball(8f, 132f, -6f, 12f, 0xFF6B4028.toInt())
            }
            Decor.PINE -> { b.cyl(0f, 0f, 0f, 14f, 30f, 0xFF6B4028.toInt()); for (k in 0 until 3) b.cone(0f, 24f + k * 30f, 0f, 76f - k * 18f, 52f, if (biome == Biome.SNOW) 0xFF16A34A.toInt() else 0xFF15803D.toInt()); if (biome == Biome.SNOW) b.cone(0f, 94f, 0f, 30f, 20f, WHITE) }
            Decor.ROCK -> { b.ell(0f, 16f, 0f, 60f, 40f, 50f, 0xFF94A3B8.toInt(), ry = 0.4f); b.ell(18f, 10f, 16f, 30f, 22f, 26f, 0xFF7C8796.toInt()) }
            Decor.BOAT -> { b.part(Meshes.WEDGE, 0f, 12f, 0f, 44f, 24f, 110f, 0xFFC28A4E.toInt(), rz = 3.1416f); b.cyl(0f, 12f, 0f, 4f, 100f, 0xFF6B4028.toInt()); b.part(Meshes.WEDGE, 4f, 64f, 0f, 2f, 60f, 50f, WHITE, ry = 1.5708f) }
            Decor.PLATE -> { b.cyl(0f, 0f, 0f, 120f, 6f, WHITE); b.part(Meshes.TORUS, 0f, 6f, 0f, 120f, 14f, 120f, 0xFFF1F5F9.toInt()) }
            Decor.FORK -> { b.box(0f, 70f, 0f, 6f, 140f, 14f, 0xFFD8DEE8.toInt()); for (k in -1..1) b.box(0f, 155f, k * 12f, 5f, 34f, 5f, 0xFFD8DEE8.toInt()); b.box(0f, 138f, 0f, 6f, 8f, 34f, 0xFFD8DEE8.toInt()) }
            Decor.POT -> { b.cyl(0f, 0f, 0f, 90f, 64f, 0xFF4A4A55.toInt()); b.cyl(0f, 58f, 0f, 80f, 62f, 0xFFF97316.toInt(), Cat.FLAT); for (s in intArrayOf(-1, 1)) b.box(0f, 54f, s * 50f, 10f, 6f, 14f, 0xFF3A3A44.toInt()) }
            Decor.SNOWMAN -> { b.ball(0f, 22f, 0f, 46f, WHITE); b.ball(0f, 56f, 0f, 34f, WHITE); b.ball(0f, 82f, 0f, 24f, WHITE); b.cone(14f, 82f, 0f, 6f, 14f, 0xFFFF7A3D.toInt(), rz = -1.5708f); b.cyl(0f, 92f, 0f, 22f, 108f, INK); b.eyes(10f, 86f, 4f, 4f, false) }
            Decor.CRATE -> { b.box(0f, 22f, 0f, 44f, 44f, 44f, 0xFFD9A066.toInt()); b.box(0f, 22f, 0f, 46f, 8f, 46f, 0xFFC08A55.toInt(), cat = Cat.FLAT) }
            Decor.BARREL -> { b.cyl(0f, 0f, 0f, 36f, 48f, 0xFF8B5A3C.toInt()); for (k in 0..1) b.part(Meshes.TORUS, 0f, 12f + k * 24f, 0f, 38f, 12f, 38f, 0xFF4A4A55.toInt(), cat = Cat.FLAT) }
            Decor.TOYBLOCK -> { b.box(0f, 18f, 0f, 36f, 36f, 36f, CONF[(t * 0f).toInt() % 6]); b.box(0f, 44f, 0f, 30f, 16f, 30f, CONF[2], ry = 0.5f) }
            Decor.GRAVE -> { b.box(0f, 26f, 0f, 12f, 52f, 36f, 0xFF94A3B8.toInt()); b.part(Meshes.CYL, 0f, 52f, 0f, 36f, 12f, 36f, 0xFF94A3B8.toInt(), rx = 1.5708f); b.box(6.5f, 34f, 0f, 1f, 18f, 4f, 0xFF64748B.toInt(), cat = Cat.FLAT); b.box(6.5f, 38f, 0f, 1f, 4f, 14f, 0xFF64748B.toInt(), cat = Cat.FLAT) }
            Decor.DEADTREE -> { b.seg(Meshes.CYL, 0f, 0f, 0f, 4f, 100f, 0f, 14f, 0xFF4B3A30.toInt()); b.seg(Meshes.CYL, 2f, 60f, 0f, 30f, 90f, 20f, 7f, 0xFF4B3A30.toInt()); b.seg(Meshes.CYL, 3f, 74f, 0f, -26f, 104f, -14f, 6f, 0xFF4B3A30.toInt()) }
            Decor.LANTERN -> { b.cyl(0f, 0f, 0f, 4f, 70f, 0xFF3A3A44.toInt()); b.box(0f, 78f, 0f, 16f, 20f, 16f, 0xFF3A3A44.toInt()); b.emissive(Meshes.SPHERE, 0f, 78f, 0f, 13f, 16f, 13f, 0xFFFFC94D.toInt()); b.glowBall(0f, 78f, 0f, 70f, 0xFFFFB347.toInt(), 0.35f + 0.1f * sin(t * 6f)) }
            Decor.CRYSTAL -> { for (k in 0 until 4) { val a = k * 1.57f; b.part(Meshes.CONE, cos(a) * 10f, 30f, sin(a) * 10f, 22f, 60f + k * 12f, 22f, 0xFF22D3EE.toInt(), rz = cos(a) * 0.3f, rx = sin(a) * 0.3f, cat = Cat.GLASS, a = 0.75f) }; b.glowBall(0f, 40f, 0f, 60f, 0xFF67E8F9.toInt(), 0.3f) }
            Decor.STALL -> { b.box(0f, 20f, 0f, 70f, 40f, 50f, 0xFFF5ECD8.toInt()); for (s in intArrayOf(-1, 1)) for (q in intArrayOf(-1, 1)) b.cyl(s * 32f, 40f, q * 22f, 4f, 80f, 0xFF8B5A3C.toInt()); b.part(Meshes.WEDGE, 0f, 92f, 0f, 80f, 24f, 60f, 0xFFFF5E7E.toInt(), ry = 1.5708f) }
            Decor.FERRIS -> {
                for (s in intArrayOf(-1, 1)) { b.seg(Meshes.BOX, 0f, 0f, s * 60f, 0f, 170f, s * 30f, 8f, 0xFF8A93A8.toInt()); b.seg(Meshes.BOX, 0f, 0f, s * 60f, 0f, 170f, s * 30f, 8f, 0xFF8A93A8.toInt()) }
                b.push(); b.move(0f, 170f, 0f, rx = 0f)
                b.part(Meshes.TORUS, 0f, 0f, 0f, 260f, 260f, 260f, 0xFFFF5EC4.toInt(), rz = 1.5708f)
                val r = t * 0.3f
                for (k in 0 until 8) { val a = r + k * 0.785f; val cy = cos(a) * 125f; val cz = sin(a) * 125f
                    b.seg(Meshes.CYL, 0f, 0f, 0f, 0f, cy, cz, 3f, 0xFFB0B8C8.toInt()); b.box(0f, cy - 14f, cz, 24f, 22f, 24f, CONF[k % 6]) }
                b.pop()
            }
            Decor.DISH -> { b.cyl(0f, 0f, 0f, 10f, 60f, 0xFF8A93A8.toInt()); b.part(Meshes.HEMI, 0f, 66f, 0f, 90f, 26f, 90f, 0xFFD8DEE8.toInt(), rz = 2.6f) }
            Decor.FLOWER -> { b.cyl(0f, 0f, 0f, 4f, 40f, 0xFF16A34A.toInt()); for (k in 0 until 6) { val a = k * 1.047f; b.ell(cos(a) * 9f, 42f, sin(a) * 9f, 12f, 4f, 12f, 0xFFFF8CC6.toInt()) }; b.ball(0f, 43f, 0f, 9f, 0xFFFFE14D.toInt(), Cat.FLAT) }
            Decor.CUP -> { b.cyl(0f, 0f, 0f, 60f, 74f, WHITE); b.cyl(0f, 70f, 0f, 54f, 75f, 0xFF6B4028.toInt(), Cat.FLAT); b.part(Meshes.TORUS, 0f, 38f, 32f, 34f, 34f, 34f, WHITE, rx = 1.5708f) }
            Decor.GIFT -> { b.box(0f, 20f, 0f, 40f, 40f, 40f, 0xFFE84545.toInt()); b.box(0f, 20f, 0f, 42f, 42f, 8f, 0xFFFFE14D.toInt(), cat = Cat.FLAT); b.box(0f, 20f, 0f, 8f, 42f, 42f, 0xFFFFE14D.toInt(), cat = Cat.FLAT); b.part(Meshes.TORUS, 0f, 44f, 0f, 18f, 18f, 18f, 0xFFFFE14D.toInt(), rx = 1.5708f) }
            else -> b.box(0f, 15f, 0f, 30f, 30f, 30f, 0xFFB0B8C8.toInt())
        }
    }

    // ------------------------------------------------------------------ pickups / icons
    fun gadget(b: MB, id: Int) {
        val c = Gadgets.list[id.coerceIn(0, Gadgets.list.size - 1)].color
        when (id) {
            Gadgets.TRAMPOLINE -> { b.cyl(0f, -4f, 0f, 26f, 2f, 0xFF3A3A44.toInt()); b.cyl(0f, 2f, 0f, 22f, 4f, c) }
            Gadgets.WALL -> b.box(0f, 0f, 0f, 8f, 20f, 26f, c)
            Gadgets.DECOY -> b.ell(0f, 0f, 0f, 14f, 22f, 14f, c)
            Gadgets.TURRET -> { b.box(0f, -6f, 0f, 16f, 10f, 16f, 0xFF4FB86B.toInt()); b.ball(0f, 4f, 0f, 14f, c) }
            Gadgets.JETPACK -> { b.cyl(0f, -10f, -6f, 9f, 10f, c); b.cyl(0f, -10f, 6f, 9f, 10f, c) }
            Gadgets.PARACHUTE -> b.part(Meshes.HEMI, 0f, -2f, 0f, 28f, 14f, 28f, c)
            Gadgets.BANANA -> b.part(Meshes.TORUS, 0f, 0f, 0f, 26f, 26f, 18f, c, rx = 1.5708f)
            Gadgets.GLUE -> { b.ball(0f, 0f, 0f, 20f, c); b.cyl(0f, 8f, 0f, 6f, 14f, 0xFF3A3A44.toInt()) }
            else -> { b.cyl(0f, -10f, 0f, 4f, 0f, 0xFF8A93A8.toInt()); b.part(Meshes.HEMI, 0f, 0f, 0f, 26f, 10f, 26f, c, rz = 0.6f) }
        }
    }

    fun item(b: MB, id: Int) {
        val c = Items.list[id.coerceIn(0, Items.list.size - 1)].color
        when (id) {
            Items.SODA -> { b.cyl(0f, -12f, 0f, 14f, 12f, c); b.cyl(0f, 12f, 0f, 12f, 14f, 0xFFB0B8C8.toInt()) }
            Items.LOLLIPOP -> { b.cyl(0f, -14f, 0f, 2.5f, 4f, WHITE); b.part(Meshes.CYL, 0f, 8f, 0f, 22f, 5f, 22f, c, rz = 1.5708f) }
            Items.SHIELD -> b.glass(Meshes.SPHERE, 0f, 0f, 0f, 24f, 24f, 24f, c, 0.6f)
            else -> { b.cyl(0f, -12f, 0f, 9f, 0f, 0xFFF5ECD8.toInt()); b.part(Meshes.HEMI, 0f, -1f, 0f, 26f, 18f, 26f, c); b.ball(4f, 8f, 4f, 5f, WHITE, Cat.FLAT) }
        }
    }

    // ------------------------------------------------------------------ projectiles (model space +X = travel direction)
    fun projectile(b: MB, vis: Int, col: Int, r: Float, rot: Float) {
        val d = r * 2f
        when (vis) {
            PV.PIXEL -> b.emissive(Meshes.BOX, 0f, 0f, 0f, d, d, d, col)
            PV.CORK, PV.BIGCORK -> { b.part(Meshes.CYL, 0f, 0f, 0f, d, d * 1.2f, d, 0xFFD2A36A.toInt(), rz = rot) }
            PV.TOAST -> b.box(0f, 0f, 0f, d * 1.2f, d * 1.2f, d * 0.4f, 0xFFE0A050.toInt(), rz = rot)
            PV.ARROW, PV.DART -> { b.box(0f, 0f, 0f, d * 3f, d * 0.35f, d * 0.35f, 0xFFB0B0B8.toInt()); b.cone(d * 1.5f, -d * 0.3f, 0f, d * 0.6f, d * 0.8f, 0xFF8A93A8.toInt(), rz = -1.5708f) }
            PV.BOOMERANG -> { b.push(); b.move(0f, 0f, 0f, ry = rot * 2f); b.box(d * 0.4f, 0f, -d * 0.3f, d * 1.4f, d * 0.25f, d * 0.4f, col, ry = 0.6f); b.box(d * 0.4f, 0f, d * 0.3f, d * 1.4f, d * 0.25f, d * 0.4f, col, ry = -0.6f); b.pop() }
            PV.SOAP, PV.BUBBLE -> b.glass(Meshes.SPHERE, 0f, 0f, 0f, d, d, d, col, 0.45f)
            PV.PAN -> b.part(Meshes.CYL, 0f, 0f, 0f, d * 1.4f, d * 0.2f, d * 1.4f, 0xFF4A4A55.toInt(), rx = rot)
            PV.POT -> b.cyl(0f, -r, 0f, d, r, 0xFF6A6A78.toInt())
            PV.ONIGIRI -> { b.ell(0f, 0f, 0f, d, d * 0.9f, d, WHITE); b.box(0f, -r * 0.5f, 0f, d * 1.02f, r * 0.6f, d * 1.02f, 0xFF2D4A2D.toInt()) }
            PV.TRASH -> b.box(0f, 0f, 0f, d, d, d, 0xFF6E8C5A.toInt(), rz = rot)
            PV.SHELL, PV.ROCK, PV.SNOWBALL, PV.GUM, PV.RICE, PV.DIRT, PV.HAIL, PV.FOAM, PV.GEL, PV.GLUEBOMB, PV.SUCKER ->
                b.ball(0f, 0f, 0f, d, col)
            else -> b.glowBall(0f, 0f, 0f, d, col)
        }
    }
}
