package com.starguys.battle.g3d

import com.starguys.battle.g3d.MB.Companion.INK
import com.starguys.battle.g3d.MB.Companion.WHITE
import com.starguys.battle.g3d.MB.Companion.light
import com.starguys.battle.g3d.MB.Companion.shade
import com.starguys.battle.game.Weapons
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** The 12 heroes (plus the bean and the infected slime) remade as chunky 3D toy figures. */
object HeroModels {
    const val SKIN = 0xFFFFD2B0.toInt()

    private fun feet(b: MB, t: Float, mv: Float, col: Int, spread: Float = 9f, len: Float = 15f) {
        val ph = sin(t * 14f) * mv
        b.ell(ph * 6f + 2f, 3.5f + max(0f, ph) * 5f, -spread, len, 8f, 11f, col)
        b.ell(-ph * 6f + 2f, 3.5f + max(0f, -ph) * 5f, spread, len, 8f, 11f, col)
    }

    fun bob(t: Float, mv: Float) = abs(sin(t * 14f)) * 3.5f * mv + sin(t * 3f) * 1.2f * (1f - mv)

    /** Draws hero [id] at the current origin (feet). */
    fun draw(b: MB, id: Int, col: Int, t: Float, mv: Float, superOn: Boolean, blink0: Boolean) {
        val bo = bob(t, mv)
        val dark = shade(col, 0.68f)
        val bl = blink0 || ((t * 0.7f) % 4f) < 0.08f
        when (id) {
            0 -> { // Gumbo: bubble-gum blob
                if (superOn) {
                    b.glass(Meshes.SPHERE, 0f, 36f, 0f, 76f, 72f, 76f, light(col, 0.3f), 0.55f)
                    b.ell(0f, 34f, 0f, 44f, 42f, 44f, col)
                    b.eyes(19f, 38f, 8f, 11f, bl)
                    return
                }
                feet(b, t, mv, dark, 11f)
                b.ell(0f, 27f + bo, 0f, 46f, 44f, 46f, col)
                b.ball(4f, 50f + bo, 9f, 18f, light(col, 0.25f))
                b.ball(8f, 54f + bo, 12f, 5f, WHITE, Cat.FLAT)
                b.ell(17f, 22f + bo, -15f, 4f, 5f, 8f, shade(col, 0.85f), cat = Cat.FLAT)
                b.ell(17f, 22f + bo, 15f, 4f, 5f, 8f, shade(col, 0.85f), cat = Cat.FLAT)
                b.eyes(20f, 32f + bo, 8f, 11f, bl)
                b.smile(23f, 20f + bo, 12f)
            }
            1 -> { // Volt: walking battery
                val ph = sin(t * 14f) * 5f * mv
                b.cyl(ph, 0f, -7f, 4f, 14f, INK); b.cyl(-ph, 0f, 7f, 4f, 14f, INK)
                b.ell(ph + 2f, 2f, -7f, 13f, 5f, 9f, 0xFF3A3A44.toInt()); b.ell(-ph + 2f, 2f, 7f, 13f, 5f, 9f, 0xFF3A3A44.toInt())
                b.cyl(0f, 13f + bo, 0f, 30f, 33f + bo, 0xFF2E2B3A.toInt())
                b.cyl(0f, 33f + bo, 0f, 30f, 55f + bo, col)
                b.cyl(0f, 55f + bo, 0f, 11f, 61f + bo, 0xFFB0B8C8.toInt())
                b.box(15f, 26f + bo, -2f, 2f, 10f, 4f, 0xFFFFE14D.toInt(), rx = 0.5f, cat = Cat.FLAT)
                b.box(15f, 20f + bo, 2f, 2f, 10f, 4f, 0xFFFFE14D.toInt(), rx = 0.5f, cat = Cat.FLAT)
                b.eyes(14f, 44f + bo, 6f, 9f, bl)
                if (superOn || (t * 3f) % 1f < 0.15f) { b.glowBall(-6f, 66f + bo, -14f, 6f, 0xFFFFF27A.toInt()); b.glowBall(4f, 62f + bo, 16f, 5f, 0xFFFFF27A.toInt()) }
            }
            2 -> { // Mama Tortue
                if (superOn) {
                    b.push(); b.move(0f, 24f, 0f, rz = -t * 16f)
                    b.ball(0f, 0f, 0f, 46f, 0xFF3E8A3C.toInt())
                    for (k in 0 until 6) { val a = k * 1.047f; b.ell(MB.cosF(a) * 20f, MB.sinF(a) * 20f, 0f, 12f, 12f, 48f, 0xFF6CC56A.toInt()) }
                    b.pop(); return
                }
                val ph = sin(t * 10f) * 4f * mv
                for (s in intArrayOf(-1, 1)) {
                    val fx = s * ph
                    b.box(fx + 2f, 5f, s * 9f, 18f, 7f, 9f, 0xFFFF5E7E.toInt())
                    b.ball(fx - 4f, 1.5f, s * 9f, 6f, 0xFFFFE14D.toInt(), Cat.FLAT); b.ball(fx + 8f, 1.5f, s * 9f, 6f, 0xFFFFE14D.toInt(), Cat.FLAT)
                }
                b.ell(-8f, 28f + bo, 0f, 24f, 36f, 38f, 0xFF3E8A3C.toInt())
                for (k in 0 until 3) b.ell(-19f, 22f + bo + k * 9f, (k - 1) * 9f, 4f, 8f, 8f, 0xFF6CC56A.toInt(), cat = Cat.FLAT)
                b.ell(3f, 27f + bo, 0f, 24f, 32f, 30f, 0xFFF2E3B3.toInt())
                b.ball(4f, 48f + bo, 0f, 26f, col)
                b.ball(-2f, 62f + bo, 0f, 14f, 0xFFD8D8E0.toInt())
                for (s in intArrayOf(-1, 1)) b.part(Meshes.TORUS, 16f, 49f + bo, s * 5.5f, 11f, 11f, 11f, INK, rz = 1.5708f, cat = Cat.FLAT)
                b.eyes(15f, 49f + bo, 5.5f, 7f, bl)
                b.smile(16f, 41f + bo, 7f)
            }
            3 -> { // Pixel: retro voxel cube
                val ph = sin(t * 14f) * mv
                b.box(ph * 5f, 4f + max(0f, ph) * 4f, -8f, 11f, 8f, 10f, dark)
                b.box(-ph * 5f, 4f + max(0f, -ph) * 4f, 8f, 11f, 8f, 10f, dark)
                b.part(Meshes.BOX, 0f, 29f + bo, 0f, 36f, 38f, 36f, col)
                b.box(-9f, 44f + bo, -9f, 18.5f, 9.5f, 18.5f, light(col, 0.3f), cat = Cat.FLAT)
                for (s in intArrayOf(-1, 1)) {
                    if (bl) b.box(18.3f, 33f + bo, s * 7f, 1f, 2f, 8f, INK, cat = Cat.FLAT)
                    else { b.box(18.3f, 33f + bo, s * 7f, 1f, 10f, 9f, WHITE, cat = Cat.FLAT); b.box(18.8f, 32f + bo, s * 7.5f, 1f, 5f, 4f, INK, cat = Cat.FLAT) }
                }
                b.box(18.3f, 20f + bo, 0f, 1f, 3f, 8f, INK, cat = Cat.FLAT)
                b.box(0f, 52f + bo, 0f, 3f, 8f, 3f, INK); b.box(0f, 58f + bo, 0f, 7f, 6f, 7f, 0xFFFFE14D.toInt())
            }
            4 -> { // Chef Boulette: onigiri chef
                feet(b, t, mv, 0xFFE8E2D8.toInt(), 8f)
                b.ell(0f, 22f + bo, 0f, 40f, 34f, 42f, col)
                b.ell(0f, 38f + bo, 0f, 30f, 32f, 32f, col)
                b.box(0f, 14f + bo, 0f, 41f, 13f, 41f, 0xFF2D4A2D.toInt())
                b.cyl(0f, 50f + bo, 0f, 20f, 64f + bo, WHITE)
                b.ball(0f, 70f + bo, -7f, 14f, WHITE); b.ball(0f, 70f + bo, 7f, 14f, WHITE); b.ball(0f, 74f + bo, 0f, 16f, WHITE)
                b.eyes(17f, 35f + bo, 6f, 8f, bl)
                b.ell(16f, 28f + bo, -12f, 2f, 4f, 6f, 0xFFFFB0B0.toInt(), cat = Cat.FLAT); b.ell(16f, 28f + bo, 12f, 2f, 4f, 6f, 0xFFFFB0B0.toInt(), cat = Cat.FLAT)
                b.smile(19f, 27f + bo, 7f)
            }
            5 -> { // Capitaine Bouchon: pirate cork
                feet(b, t, mv, 0xFF6B4A2E.toInt(), 8f)
                b.cyl(0f, 7f + bo, 0f, 28f, 44f + bo, col)
                b.cyl(0f, 43f + bo, 0f, 34f, 50f + bo, shade(col, 0.85f))
                b.part(Meshes.TORUS, 0f, 34f + bo, 0f, 36f, 30f, 36f, 0xFFB0B8C8.toInt(), cat = Cat.FLAT)
                b.part(Meshes.WEDGE, 0f, 58f + bo, 0f, 22f, 16f, 46f, 0xFF2A2433.toInt())
                b.ball(5f, 58f + bo, 0f, 6f, WHITE, Cat.FLAT)
                if (bl) b.box(14.5f, 31f + bo, -6f, 1f, 2f, 7f, INK, cat = Cat.FLAT)
                else { b.ell(14f, 31f + bo, -6f, 4f, 9f, 8f, WHITE, cat = Cat.FLAT); b.ball(16f, 31f + bo, -6f, 4f, INK, Cat.FLAT) }
                b.part(Meshes.CYL, 14.5f, 31f + bo, 6f, 9f, 2f, 9f, INK, rz = 1.5708f, cat = Cat.FLAT)
                b.box(14.6f, 36f + bo, 0f, 1f, 2f, 28f, INK, rx = 0.08f, cat = Cat.FLAT)
                b.smile(15f, 22f + bo, 7f)
            }
            6 -> { // Nuage: grumpy cloud
                val fl = sin(t * 2.5f) * 3f + 6f
                b.ball(0f, 36f + fl, 0f, 34f, col)
                b.ball(-4f, 28f + fl, -14f, 26f, col); b.ball(-4f, 28f + fl, 14f, 26f, col)
                b.ball(6f, 24f + fl, 0f, 28f, col); b.ball(-10f, 26f + fl, 0f, 28f, col)
                b.ball(-2f, 46f + fl, 6f, 16f, light(col, 0.3f))
                b.eyes(16f, 34f + fl, 6f, 8f, bl, angry = true)
                b.smile(18f, 24f + fl, 7f, sad = true)
                if (mv > 0.1f || superOn) for (k in 0..2) {
                    val dy = ((t * 2f + k * 0.33f) % 1f) * 14f
                    b.glowBall(-4f + k * 4f, 10f + fl - dy, -8f + k * 8f, 4f, 0xFF6BD3FF.toInt(), 0.7f)
                }
            }
            7 -> { // Rex-Tracteur: dino tractor
                val wr = -t * 9f * mv
                b.box(2f, 22f + bo, 0f, 40f, 22f, 28f, col)
                b.box(-6f, 44f + bo, 0f, 20f, 22f, 24f, shade(col, 0.85f))
                b.box(4.5f, 45f + bo, 0f, 1f, 13f, 18f, 0xFFBFE8FF.toInt(), cat = Cat.FLAT)
                for (k in 0 until 4) b.cone(-14f + k * 5f, 55f + bo, 0f, 7f, 10f, 0xFFFF9A3D.toInt())
                b.cyl(15f, 33f + bo, 8f, 5f, 50f + bo, 0xFF3A3A44.toInt())
                b.eyes(5.5f, 45f + bo, 5f, 6f, bl)
                for (s in intArrayOf(-1, 1)) {
                    b.part(Meshes.CYL, -10f, 13f, s * 16f, 26f, 7f, 26f, 0xFF2E2B3A.toInt(), rx = 1.5708f, rz = wr)
                    b.part(Meshes.CYL, -10f, 13f, s * 19.5f, 10f, 1f, 10f, 0xFFFFC23D.toInt(), rx = 1.5708f, cat = Cat.FLAT)
                    b.part(Meshes.CYL, 14f, 8f, s * 14f, 16f, 6f, 16f, 0xFF2E2B3A.toInt(), rx = 1.5708f, rz = wr * 1.6f)
                }
            }
            8 -> { // Lady Mousse: chic sponge
                feet(b, t, mv, 0xFFFF5E7E.toInt(), 8f)
                b.box(0f, 29f + bo, 0f, 30f, 42f, 34f, col)
                for (k in 0 until 6) b.ell(15f, 14f + bo + (k * 7) % 30, -12f + (k * 11) % 24, 2f, 4f, 4f, shade(col, 0.75f), cat = Cat.FLAT)
                for (k in 0 until 4) b.ell(-8f + (k * 9) % 18, 18f + bo + k * 8f, 17f, 4f, 4f, 2f, shade(col, 0.75f), cat = Cat.FLAT)
                b.box(0f, 54f + bo, 0f, 32f, 8f, 36f, 0xFF4FB86B.toInt())
                b.cyl(0f, 58f + bo, 0f, 34f, 60f + bo, 0xFF2A2433.toInt())
                b.cyl(0f, 60f + bo, 0f, 18f, 73f + bo, 0xFF2A2433.toInt())
                b.box(-4f, 76f + bo, 8f, 3f, 16f, 3f, 0xFFFF5E7E.toInt(), rx = 0.5f)
                b.eyes(15.5f, 38f + bo, 6f, 8f, bl)
                for (k in -2..2) b.ball(15.5f, 25f + bo - abs(k) * 1.2f, k * 4f, 3f, WHITE, Cat.FLAT)
                b.ell(15.5f, 30f + bo, 0f, 2f, 3f, 6f, 0xFFE84545.toInt(), cat = Cat.FLAT)
            }
            9 -> { // Glitch
                val gl = ((t * 7f).toInt() % 5 == 0)
                val off = if (gl) 4f else 2f
                feet(b, t, mv, shade(col, 0.6f), 8f)
                b.glass(Meshes.SPHERE, 0f, 31f + bo, -off, 33f, 48f, 33f, 0xFF35F4F4.toInt(), 0.35f)
                b.glass(Meshes.SPHERE, 0f, 31f + bo, off, 33f, 48f, 33f, 0xFFFF3D7A.toInt(), 0.35f)
                b.ell(0f, 31f + bo, 0f, 32f, 48f, 32f, col)
                if (gl) { b.box(4f, 38f + bo, 0f, 30f, 5f, 34f, light(col, 0.4f)); b.box(-3f, 22f + bo, 4f, 34f, 3f, 30f, shade(col, 0.6f)) }
                b.box(15f, 37f + bo, -6f, 2f, 8f, 7f, WHITE, cat = Cat.FLAT); b.box(15.8f, 36f + bo, -6f, 1f, 4f, 3f, INK, cat = Cat.FLAT)
                b.ell(15f, 37f + bo, 6f, 3f, 9f, 8f, WHITE, cat = Cat.FLAT); b.ball(16.5f, 37f + bo, 6f, 4f, INK, Cat.FLAT)
                for (k in 0 until 3) b.box(15.5f, 24f + bo + (k % 2) * 2f, -4f + k * 4f, 1f, 2f, 5f, INK, rx = if (k % 2 == 0) 0.4f else -0.4f, cat = Cat.FLAT)
            }
            10 -> { // Mamie Météo
                feet(b, t, mv, 0xFF5A3A5E.toInt(), 7f)
                b.cone(0f, 6f + bo, 0f, 36f, 36f, col)
                b.part(Meshes.TORUS, 0f, 14f + bo, 0f, 34f, 20f, 34f, light(col, 0.4f), cat = Cat.FLAT)
                b.ball(2f, 46f + bo, 0f, 24f, SKIN)
                b.ball(-2f, 59f + bo, 0f, 14f, 0xFFE0E0EA.toInt()); b.ball(-2f, 50f + bo, -11f, 11f, 0xFFE0E0EA.toInt()); b.ball(-2f, 50f + bo, 11f, 11f, 0xFFE0E0EA.toInt())
                for (s in intArrayOf(-1, 1)) b.part(Meshes.TORUS, 13f, 47f + bo, s * 4.5f, 9f, 9f, 9f, INK, rz = 1.5708f, cat = Cat.FLAT)
                b.eyes(12.5f, 47f + bo, 4.5f, 5.5f, bl)
                b.smile(13f, 40f + bo, 5f)
                if (superOn) {
                    b.cyl(4f, 20f + bo, 16f, 3f, 82f + bo, 0xFF5A3A44.toInt())
                    b.part(Meshes.HEMI, 4f, 78f + bo, 16f, 66f, 28f, 66f, 0xFFFF5E7E.toInt())
                    b.part(Meshes.HEMI, 4f, 78.5f + bo, 16f, 40f, 20f, 40f, 0xFFFFE14D.toInt())
                    b.ball(4f, 92f + bo, 16f, 5f, 0xFF5A3A44.toInt())
                } else {
                    b.cyl(6f, 10f + bo, 17f, 3f, 52f + bo, 0xFF5A3A44.toInt())
                    b.cone(6f, 46f + bo, 17f, 10f, 22f, 0xFFFF5E7E.toInt())
                }
            }
            11 -> { // Bolt le Toaster
                val ph = sin(t * 14f) * 3f * mv
                b.box(ph, 6f, -9f, 6f, 12f, 6f, 0xFF555066.toInt()); b.box(-ph, 6f, 9f, 6f, 12f, 6f, 0xFF555066.toInt())
                b.box(0f, 52f + bo, -7f, 24f, 18f, 5f, 0xFFE0A050.toInt()); b.box(0f, 52f + bo, 7f, 24f, 18f, 5f, 0xFFE0A050.toInt())
                b.box(0f, 30f + bo, 0f, 32f, 38f, 40f, col)
                b.box(16f, 31f + bo, 0f, 2f, 18f, 26f, 0xFF1E2A3A.toInt(), cat = Cat.FLAT)
                if (bl) { b.emissive(Meshes.BOX, 17.2f, 31f + bo, -5f, 1f, 1.5f, 6f, 0xFF7CF0FF.toInt()); b.emissive(Meshes.BOX, 17.2f, 31f + bo, 5f, 1f, 1.5f, 6f, 0xFF7CF0FF.toInt()) }
                else { b.emissive(Meshes.SPHERE, 17f, 32f + bo, -5f, 2f, 7f, 7f, 0xFF7CF0FF.toInt()); b.emissive(Meshes.SPHERE, 17f, 32f + bo, 5f, 2f, 7f, 7f, 0xFF7CF0FF.toInt()) }
                b.box(-4f, 36f + bo, 21f, 6f, 4f, 4f, 0xFF3A3A44.toInt())
            }
            12 -> { // Haricot (bean)
                feet(b, t, mv, shade(col, 0.7f), 8f)
                b.ell(0f, 29f + bo, 0f, 30f, 48f, 30f, col)
                b.ell(4f, 42f + bo, -6f, 6f, 10f, 6f, light(col, 0.4f), cat = Cat.FLAT)
                b.eyes(13f, 36f + bo, 5.5f, 8f, bl)
                b.smile(14.5f, 25f + bo, 7f)
            }
            13 -> { // infected slime
                val w = sin(t * 6f) * 2f
                b.ell(0f, 21f + bo, 0f, 44f + w, 40f - w, 44f + w, col)
                b.ball(-6f, 6f, -18f, 10f, col); b.ball(4f, 5f, 18f, 8f, col)
                b.ball(-6f, 34f + bo, -8f, 8f, light(col, 0.5f), Cat.FLAT)
                b.eyes(19f, 26f + bo, 7f, 9f, false, angry = true)
                b.box(21f, 15f + bo, 0f, 1.5f, 2.5f, 12f, INK, cat = Cat.FLAT)
            }
            else -> b.ball(0f, 26f, 0f, 40f, col)
        }
    }

    /** A held weapon, model space pointing +X, grip at the origin. swing 0..1 animates melee swings. */
    fun weapon(b: MB, id: Int, col: Int, swing: Float) {
        when (id) {
            Weapons.BAT, Weapons.HAMMER, Weapons.SWORD, Weapons.PAN, Weapons.GOLDPAN -> {
                b.push(); b.move(0f, 0f, 0f, rz = 1.2f - swing * 2.6f)
                when (id) {
                    Weapons.BAT -> { b.seg(Meshes.CYL, 0f, 0f, 0f, 0f, 30f, 0f, 4f, 0xFF8B5A3C.toInt()); b.seg(Meshes.CYL, 0f, 14f, 0f, 0f, 38f, 0f, 8f, col) }
                    Weapons.HAMMER -> { b.seg(Meshes.CYL, 0f, 0f, 0f, 0f, 30f, 0f, 4f, 0xFF8B5A3C.toInt()); b.box(0f, 32f, 0f, 10f, 12f, 22f, 0xFF8A93A8.toInt()) }
                    Weapons.SWORD -> { b.box(0f, 3f, 0f, 3f, 7f, 3f, 0xFF8B5A3C.toInt()); b.box(0f, 7f, 0f, 4f, 2f, 14f, 0xFFFFD23F.toInt()); b.box(0f, 24f, 0f, 2f, 32f, 7f, col) }
                    else -> { b.seg(Meshes.CYL, 0f, 0f, 0f, 0f, 16f, 0f, 3.5f, INK); b.part(Meshes.CYL, 0f, 24f, 0f, 22f, 3f, 22f, col, rz = 1.5708f) }
                }
                b.pop()
            }
            Weapons.BOOMERANG -> { b.box(4f, 0f, -4f, 18f, 3f, 6f, col, ry = 0.6f); b.box(4f, 0f, 4f, 18f, 3f, 6f, col, ry = -0.6f) }
            else -> {
                val long = id == Weapons.RAIL || id == Weapons.GRAPPLE || id == Weapons.HUNTER
                b.box(4f, 0f, 0f, 16f, 8f, 6f, col)
                b.box(-1f, -6f, 0f, 5f, 9f, 4f, shade(col, 0.6f), rz = -0.25f)
                b.part(Meshes.CYL, if (long) 18f else 14f, 1f, 0f, if (id == Weapons.CONFETTI) 8f else 5f, if (long) 22f else 12f, if (id == Weapons.CONFETTI) 8f else 5f, 0xFF3A3A44.toInt(), rz = -1.5708f)
                when (id) {
                    Weapons.MAGNET -> { b.box(24f, 4f, 0f, 6f, 3f, 10f, 0xFFB0B8C8.toInt()); b.box(24f, -2f, 0f, 6f, 3f, 10f, 0xFFB0B8C8.toInt()) }
                    Weapons.FOAM -> b.cyl(0f, 4f, 0f, 9f, 14f, WHITE)
                    Weapons.FLAME -> b.cyl(-4f, 4f, 0f, 8f, 16f, 0xFFFF5EC4.toInt())
                    Weapons.BUBBLE -> b.glass(Meshes.SPHERE, 22f, 1f, 0f, 9f, 9f, 9f, 0xFF90E0FF.toInt(), 0.5f)
                    Weapons.SUCKER -> b.part(Meshes.HEMI, 22f, 1f, 0f, 10f, 6f, 10f, 0xFFFF5E7E.toInt(), rz = -1.5708f)
                    Weapons.GRAPPLE -> b.box(14f, 2f, 0f, 3f, 3f, 22f, 0xFF8B5A3C.toInt())
                    Weapons.RAIL -> b.emissive(Meshes.BOX, 10f, 4.5f, 0f, 14f, 1.5f, 3f, 0xFF7CF0FF.toInt())
                }
            }
        }
    }
}
