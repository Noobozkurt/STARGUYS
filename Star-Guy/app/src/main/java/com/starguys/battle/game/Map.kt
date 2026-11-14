package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.clamp
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.sinf

const val T = 64f          // tile size (world units)
const val LH = 28f         // height of one elevation level
const val WALL_H = 90f     // wall height above its ground
const val ZS = 0.62f       // screen-space factor applied to heights
const val STEP = 8f        // max height a body walks up without jumping
const val VOID_Z = -9999f
const val GRAV = 1400f
const val TEAM_MOBS = 99
const val TEAM_NEUTRAL = 98

/** Tile types */
object Tl {
    const val VOID = 0; const val FLOOR = 1; const val WALL = 2; const val BOX = 3; const val GLASS = 4
    const val BUSH = 5; const val WATER = 6; const val LAVA = 7; const val ICE = 8; const val CONVEYOR = 9
    const val PAINT = 10; const val SPEED = 11; const val COLLAPSE = 12; const val FOAM = 13; const val LOW = 14
    const val LADDER = 15; const val BRICK = 16; const val DOOR = 17; const val GOAL = 18; const val RAIL = 19
    const val GLUE = 20; const val COUNT = 21

    val names = arrayOf("Vide", "Sol", "Mur", "Carton", "Vitre", "Buisson", "Eau", "Lave jelly", "Glace", "Tapis",
        "Peinture", "Plaque vitesse", "Sol fragile", "Mousse", "Caisse basse", "Échelle", "Briques", "Porte", "Arrivée", "Rails", "Glu")

    fun isWallLike(t: Int) = t == WALL || t == BOX || t == GLASS || t == BRICK || t == DOOR
    fun destructible(t: Int) = t == BOX || t == GLASS || t == BRICK || t == LOW || t == FOAM
    fun liquid(t: Int) = t == WATER || t == LAVA || t == PAINT
    fun maxHp(t: Int) = when (t) { BOX -> 1400f; GLASS -> 350f; BRICK -> 3000f; LOW -> 2000f; FOAM -> 900f; else -> 0f }
}

enum class Biome(
    val label: String, val floorA: Int, val floorB: Int, val wallTop: Int, val wallSide: Int,
    val cliff: Int, val bgTop: Int, val bgBottom: Int, val bush: Int, val accent: Int, val desc: String
) {
    MEADOW("Prairie étoilée", 0xFF8FD86E.toInt(), 0xFF82CD62.toInt(), 0xFF5DB665.toInt(), 0xFF2F7D43.toInt(),
        0xFF9C6B44.toInt(), 0xFF6BB8F0.toInt(), 0xFF2C5FB8.toInt(), 0xFF3E9B47.toInt(), 0xFFFFD23F.toInt(), "L'arène classique"),
    PARK("Parc d'attractions", 0xFFF4DCAE.toInt(), 0xFFEBCD97.toInt(), 0xFFEF6A82.toInt(), 0xFFB33952.toInt(),
        0xFF9B6A4C.toInt(), 0xFFB070E8.toInt(), 0xFF4A1F7A.toInt(), 0xFF4CB86B.toInt(), 0xFFFFD23F.toInt(), "Grande roue, montagnes russes, autos-tamponneuses"),
    FACTORY("Usine à jouets", 0xFFBAC4D8.toInt(), 0xFFAEB9CE.toInt(), 0xFFFFC23D.toInt(), 0xFFC48A12.toInt(),
        0xFF5C6378.toInt(), 0xFF3B3F5C.toInt(), 0xFF1D1F33.toInt(), 0xFF57B86B.toInt(), 0xFF35C6F4.toInt(), "Tapis roulants, presses, cuves de peinture"),
    SNOW("Montagne enneigée", 0xFFF3F8FF.toInt(), 0xFFE5EDF9.toInt(), 0xFF9DC2E4.toInt(), 0xFF5E86B0.toInt(),
        0xFF7D93B5.toInt(), 0xFFBFD9F2.toInt(), 0xFF6C8EC0.toInt(), 0xFF2F8C5A.toInt(), 0xFF6BD3FF.toInt(), "Sol glissant, avalanches, chalets"),
    BEACH("Plage tropicale", 0xFFF8E2A4.toInt(), 0xFFF1D58E.toInt(), 0xFFB9A58E.toInt(), 0xFF7F6C58.toInt(),
        0xFFC9A66B.toInt(), 0xFF45C3EA.toInt(), 0xFF1A7FB8.toInt(), 0xFF3DAE5A.toInt(), 0xFFFF8C42.toInt(), "Bateaux, cocotiers, crabes géants"),
    KITCHEN("Cuisine géante", 0xFFFFFBF2.toInt(), 0xFFF5A3A3.toInt(), 0xFFDCE3EA.toInt(), 0xFF9AA7B4.toInt(),
        0xFFB8875A.toInt(), 0xFFF3E1C2.toInt(), 0xFFC9A77B.toInt(), 0xFF7BC86B.toInt(), 0xFFFF6B6B.toInt(), "On est minuscule : assiettes, fourchettes, casseroles"),
    SPACE("Station spatiale", 0xFFCBD1E2.toInt(), 0xFFBCC3D7.toInt(), 0xFF7383B3.toInt(), 0xFF4A5480.toInt(),
        0xFF39405E.toInt(), 0xFF0B0B24.toInt(), 0xFF1E1046.toInt(), 0xFF59C98A.toInt(), 0xFF7CF0FF.toInt(), "Faible gravité"),
    VOLCANO("Volcan jelly", 0xFF9A6352.toInt(), 0xFF8F5A48.toInt(), 0xFF65404E.toInt(), 0xFF3A2430.toInt(),
        0xFF4A2A30.toInt(), 0xFF4A1530.toInt(), 0xFF1A0610.toInt(), 0xFF6E9E4A.toInt(), 0xFFFF5E7E.toInt(), "Lave en gélatine"),
    SWAMP("Marais hanté", 0xFF66804F.toInt(), 0xFF5D774A.toInt(), 0xFF4A5A46.toInt(), 0xFF263022.toInt(),
        0xFF3B3326.toInt(), 0xFF1D2B22.toInt(), 0xFF0A120D.toInt(), 0xFF2F6B3A.toInt(), 0xFFFFC94D.toInt(), "Brouillard et lanternes");

    val lowGravity get() = this == SPACE
    val fog get() = this == SWAMP
}

class TileMap(val w: Int, val h: Int, var biome: Biome) {
    val type = ByteArray(w * h) { Tl.FLOOR.toByte() }
    val elev = ByteArray(w * h)
    val aux = ByteArray(w * h)
    val under = ByteArray(w * h)
    val hp = FloatArray(w * h)
    val tmr = FloatArray(w * h)
    var oobVoid = false
    var slimeZ = -1000f
    var paintColor = 0xFF35C6F4.toInt()
    private val active = HashSet<Int>()
    private val activeTmp = ArrayList<Int>()
    var onDestroyed: ((Int, Int, Int) -> Unit)? = null
    var version = 0 // bumped on structural change (for path caches)

    val pxW get() = w * T
    val pxH get() = h * T

    fun inside(tx: Int, ty: Int) = tx in 0 until w && ty in 0 until h
    fun idx(tx: Int, ty: Int) = ty * w + tx
    fun t(tx: Int, ty: Int): Int = if (inside(tx, ty)) type[ty * w + tx].toInt() else if (oobVoid) Tl.VOID else Tl.WALL
    fun e(tx: Int, ty: Int): Int = if (inside(tx, ty)) elev[ty * w + tx].toInt() else 0
    fun tAt(x: Float, y: Float) = t((x / T).floorInt(), (y / T).floorInt())

    fun set(tx: Int, ty: Int, tp: Int, el: Int = -1) {
        if (!inside(tx, ty)) return
        val i = idx(tx, ty)
        type[i] = tp.toByte()
        if (el >= 0) elev[i] = el.toByte()
        hp[i] = Tl.maxHp(tp)
        tmr[i] = 0f
        if (tp == Tl.COLLAPSE) aux[i] = 0
        version++
    }

    fun setAux(tx: Int, ty: Int, a: Int) { if (inside(tx, ty)) aux[idx(tx, ty)] = a.toByte() }
    fun aux(tx: Int, ty: Int): Int = if (inside(tx, ty)) aux[idx(tx, ty)].toInt() else 0

    fun fill(x0: Int, y0: Int, x1: Int, y1: Int, tp: Int, el: Int = -1) {
        for (y in y0..y1) for (x in x0..x1) set(x, y, tp, el)
    }

    fun rectWall(x0: Int, y0: Int, x1: Int, y1: Int, tp: Int = Tl.WALL) {
        for (x in x0..x1) { set(x, y0, tp); set(x, y1, tp) }
        for (y in y0..y1) { set(x0, y, tp); set(x1, y, tp) }
    }

    /** Height of the surface you would stand on (or wall top). */
    fun groundZ(tx: Int, ty: Int): Float {
        val tp = t(tx, ty)
        val base = e(tx, ty) * LH
        return when (tp) {
            Tl.VOID -> VOID_Z
            Tl.WALL, Tl.BOX, Tl.GLASS, Tl.BRICK, Tl.DOOR -> base + WALL_H
            Tl.LOW, Tl.FOAM -> base + LH
            Tl.COLLAPSE -> if (inside(tx, ty) && aux[idx(tx, ty)].toInt() == 1) VOID_Z else base
            else -> base
        }
    }

    fun groundAt(x: Float, y: Float) = groundZ((x / T).floorInt(), (y / T).floorInt())

    /** Does this tile stop a body standing at height z? */
    fun blocksBody(tx: Int, ty: Int, z: Float): Boolean {
        val tp = t(tx, ty)
        if (tp == Tl.LADDER) return false
        return groundZ(tx, ty) > z + STEP
    }

    fun blocksShot(tx: Int, ty: Int, z: Float): Boolean {
        val tp = t(tx, ty)
        if (tp == Tl.VOID) return false
        return groundZ(tx, ty) > z
    }

    fun isRaised(tx: Int, ty: Int): Boolean {
        val tp = t(tx, ty)
        if (tp == Tl.VOID) return false
        return e(tx, ty) > 0 || tp == Tl.WALL || tp == Tl.BOX || tp == Tl.GLASS || tp == Tl.LOW || tp == Tl.FOAM ||
                tp == Tl.BRICK || tp == Tl.DOOR
    }

    fun walkable(tx: Int, ty: Int): Boolean {
        val tp = t(tx, ty)
        return !(tp == Tl.VOID || Tl.isWallLike(tp) || tp == Tl.LAVA)
    }

    /** Returns true if destroyed. */
    fun damage(tx: Int, ty: Int, d: Float): Boolean {
        if (!inside(tx, ty)) return false
        val i = idx(tx, ty)
        val tp = type[i].toInt()
        if (!Tl.destructible(tp)) return false
        hp[i] -= d
        tmr[i] = maxOf(tmr[i], 0f)
        if (hp[i] <= 0f) {
            val restore = if (tp == Tl.FOAM) under[i].toInt() else Tl.FLOOR
            type[i] = restore.toByte(); hp[i] = 0f
            version++
            onDestroyed?.invoke(tx, ty, tp)
            return true
        }
        return false
    }

    fun placeFoam(tx: Int, ty: Int, life: Float) {
        if (!inside(tx, ty)) return
        val i = idx(tx, ty)
        val tp = type[i].toInt()
        if (Tl.isWallLike(tp) || tp == Tl.FOAM || tp == Tl.LOW) return
        under[i] = tp.toByte()
        type[i] = Tl.FOAM.toByte(); hp[i] = Tl.maxHp(Tl.FOAM); tmr[i] = life
        active.add(i); version++
    }

    fun stepOn(tx: Int, ty: Int) {
        if (!inside(tx, ty)) return
        val i = idx(tx, ty)
        if (type[i].toInt() == Tl.COLLAPSE && aux[i].toInt() == 0 && tmr[i] <= 0f) {
            tmr[i] = 0.9f; aux[i] = 2; active.add(i)
        }
    }

    fun update(dt: Float) {
        if (active.isEmpty()) return
        activeTmp.clear(); activeTmp.addAll(active)
        for (i in activeTmp) {
            val tp = type[i].toInt()
            tmr[i] -= dt
            if (tp == Tl.FOAM) {
                if (tmr[i] <= 0f) { type[i] = under[i]; active.remove(i); version++ }
            } else if (tp == Tl.COLLAPSE) {
                val st = aux[i].toInt()
                if (tmr[i] <= 0f) {
                    if (st == 2) { aux[i] = 1; tmr[i] = 5f; version++ }
                    else if (st == 1) { aux[i] = 0; tmr[i] = 0f; active.remove(i); version++ }
                }
            } else active.remove(i)
        }
    }

    // --------------------------------------------------------------------- rendering
    private fun topColor(tp: Int, tx: Int, ty: Int): Int {
        val b = biome
        val chk = (tx + ty) and 1 == 0
        return when (tp) {
            Tl.WALL -> b.wallTop
            Tl.BOX -> 0xFFD9A066.toInt()
            Tl.GLASS -> 0xAACDEBFF.toInt()
            Tl.LOW -> 0xFFC28A4E.toInt()
            Tl.FOAM -> 0xFFF4F7FF.toInt()
            Tl.BRICK -> 0xFFE07B4A.toInt()
            Tl.DOOR -> 0xFF8A93A8.toInt()
            Tl.ICE -> if (chk) 0xFFCFEFFF.toInt() else 0xFFC2E8FB.toInt()
            Tl.WATER -> 0xFF4FB5F0.toInt()
            Tl.LAVA -> 0xFFFF5E7E.toInt()
            Tl.PAINT -> paintColor
            Tl.CONVEYOR -> 0xFF4A4F63.toInt()
            Tl.GOAL -> if (chk) 0xFFFFFFFF.toInt() else 0xFF26213A.toInt()
            Tl.GLUE -> 0xFFB6E35A.toInt()
            else -> if (chk) b.floorA else b.floorB
        }
    }

    fun drawGround(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int, time: Float) {
        G.ow = 2f
        for (ty in y0..y1) for (tx in x0..x1) {
            if (!inside(tx, ty)) continue
            val tp = t(tx, ty)
            if (tp == Tl.VOID || isRaised(tx, ty)) {
                continue
            }
            val l = tx * T; val tp0 = ty * T
            if (tp == Tl.COLLAPSE) {
                val st = aux(tx, ty)
                if (st == 1) continue
                val sh = if (st == 2) sinf(time * 60f) * 3f else 0f
                G.rect(c, l + sh, tp0, l + T + sh, tp0 + T, if ((tx + ty) and 1 == 0) 0xFFE8B86A.toInt() else 0xFFDDAA5A.toInt(), false)
                G.line(c, l + 10 + sh, tp0 + 14, l + 30 + sh, tp0 + 34, 2.5f, 0x66000000)
                G.line(c, l + 30 + sh, tp0 + 34, l + 52 + sh, tp0 + 26, 2.5f, 0x66000000)
                G.ow = 2f; G.rect(c, l + 2 + sh, tp0 + 2, l + T - 2 + sh, tp0 + T - 2, 0, true)
                drawCliffBelow(c, tx, ty, 0f)
                continue
            }
            G.rect(c, l, tp0, l + T, tp0 + T, topColor(tp, tx, ty), false)
            when (tp) {
                Tl.WATER -> {
                    val o = sinf(time * 2f + tx * 0.7f + ty * 0.5f) * 6f
                    G.line(c, l + 10, tp0 + 24 + o, l + 30, tp0 + 24 + o, 3f, 0x66FFFFFF)
                    G.line(c, l + 34, tp0 + 46 - o, l + 54, tp0 + 46 - o, 3f, 0x55FFFFFF)
                }
                Tl.LAVA -> {
                    val o = sinf(time * 3f + tx * 1.3f + ty) * 5f
                    G.circle(c, l + 20, tp0 + 22 + o, 7f, 0xFFFF9AB0.toInt(), false)
                    G.circle(c, l + 44, tp0 + 44 - o, 5f, 0xFFFFC2D0.toInt(), false)
                }
                Tl.PAINT -> {
                    val o = sinf(time * 2f + tx) * 4f
                    G.circle(c, l + 32, tp0 + 32 + o, 9f, G.light(paintColor, 0.35f), false)
                }
                Tl.ICE -> G.line(c, l + 12, tp0 + 48, l + 40, tp0 + 18, 3f, 0x88FFFFFF.toInt())
                Tl.CONVEYOR, Tl.SPEED -> {
                    val d = aux(tx, ty)
                    val off = ((time * (if (tp == Tl.SPEED) 50f else 70f)) % 32f)
                    if (tp == Tl.SPEED) G.rect(c, l + 4, tp0 + 4, l + T - 4, tp0 + T - 4, 0xFF2F3550.toInt(), false)
                    c.save(); c.translate(l + 32, tp0 + 32); c.rotate(d * 90f)
                    c.clipRect(-32f, -32f, 32f, 32f)
                    for (k in -1..1) {
                        val ax = -20f + off + k * 32f
                        G.line(c, ax - 8, -12f, ax + 4, 0f, 5f, if (tp == Tl.SPEED) 0xFFFFE14D.toInt() else 0xFF8C93AD.toInt())
                        G.line(c, ax + 4, 0f, ax - 8, 12f, 5f, if (tp == Tl.SPEED) 0xFFFFE14D.toInt() else 0xFF8C93AD.toInt())
                    }
                    c.restore()
                }
                Tl.RAIL -> {
                    G.line(c, l, tp0 + 20, l + T, tp0 + 20, 4f, 0xFF7A7F8F.toInt())
                    G.line(c, l, tp0 + 44, l + T, tp0 + 44, 4f, 0xFF7A7F8F.toInt())
                    for (k in 0..2) G.rect(c, l + 6 + k * 22, tp0 + 14, l + 14 + k * 22, tp0 + 50, 0xFF8B5A3C.toInt(), false)
                }
                Tl.LADDER -> {
                    G.line(c, l + 16, tp0, l + 16, tp0 + T, 5f, 0xFF8B5A3C.toInt())
                    G.line(c, l + 48, tp0, l + 48, tp0 + T, 5f, 0xFF8B5A3C.toInt())
                    for (k in 0..3) G.line(c, l + 16, tp0 + 8 + k * 16, l + 48, tp0 + 8 + k * 16, 4f, 0xFFA8754F.toInt())
                }
                Tl.GLUE -> G.circle(c, l + 30, tp0 + 30, 10f, 0xFFD8F58A.toInt(), false)
            }
            drawCliffBelow(c, tx, ty, 0f)
            if (slimeZ > -100f && 0f < slimeZ) drawSlimeTop(c, tx, ty, time)
        }
    }

    private fun drawCliffBelow(c: Canvas, tx: Int, ty: Int, top: Float) {
        val below = t(tx, ty + 1)
        val belowVoid = below == Tl.VOID || (below == Tl.COLLAPSE && aux(tx, ty + 1) == 1)
        if (belowVoid) {
            val l = tx * T; val y = (ty + 1) * T - top * ZS
            G.rect(c, l, y, l + T, y + 46f, biome.cliff, false)
            G.rect(c, l, y + 46f, l + T, y + 60f, G.shade(biome.cliff, 0.6f), false)
            G.line(c, l, y, l + T, y, 2.5f, G.INK)
        }
    }

    private fun drawSlimeTop(c: Canvas, tx: Int, ty: Int, time: Float) {
        val l = tx * T; val y = ty * T - slimeZ * ZS
        G.rect(c, l, y, l + T, y + T, 0xDD7BE84A.toInt(), false)
        val o = sinf(time * 3f + tx * 0.9f + ty * 1.7f) * 6f
        G.circle(c, l + 22 + o, y + 30, 6f, 0xFFB5FF8A.toInt(), false)
    }

    /** Draws raised tiles of row ty (top face + front face). */
    fun drawRaisedRow(c: Canvas, ty: Int, x0: Int, x1: Int, time: Float) {
        for (tx in x0..x1) {
            if (!isRaised(tx, ty)) continue
            val tp = t(tx, ty)
            val top = groundZ(tx, ty)
            val l = tx * T
            val topY = ty * T - top * ZS
            val bt = t(tx, ty + 1)
            val belowZ = when {
                bt == Tl.VOID -> -60f
                isRaised(tx, ty + 1) -> groundZ(tx, ty + 1)
                else -> maxOf(0f, e(tx, ty + 1) * LH)
            }
            val frontBottom = (ty + 1) * T - minOf(belowZ, top) * ZS
            val frontTop = (ty + 1) * T - top * ZS
            G.ow = 2.5f
            val side = when (tp) {
                Tl.WALL -> biome.wallSide
                Tl.BOX -> 0xFFA8733F.toInt()
                Tl.GLASS -> 0x887FB8E0.toInt()
                Tl.LOW -> 0xFF8E5C2E.toInt()
                Tl.FOAM -> 0xFFC9D2E8.toInt()
                Tl.BRICK -> 0xFFA9502C.toInt()
                Tl.DOOR -> 0xFF5D6478.toInt()
                else -> biome.cliff
            }
            if (frontBottom > frontTop + 0.5f) {
                G.rect(c, l, frontTop, l + T, frontBottom, side, true)
                if (tp == Tl.BRICK || (tp == Tl.WALL && biome == Biome.VOLCANO)) {
                    var yy = frontTop + 12f
                    while (yy < frontBottom - 4) { G.line(c, l + 2, yy, l + T - 2, yy, 2f, 0x55000000); yy += 14f }
                }
            }
            // slime covers submerged blocks
            if (slimeZ > -100f && top < slimeZ) {
                drawSlimeTop(c, tx, ty, time)
                continue
            }
            val tc = if (tp == Tl.FLOOR || tp == Tl.BUSH || tp == Tl.LADDER || tp == Tl.GOAL || tp == Tl.ICE || tp == Tl.RAIL ||
                tp == Tl.WATER || tp == Tl.LAVA || tp == Tl.CONVEYOR || tp == Tl.SPEED || tp == Tl.PAINT || tp == Tl.COLLAPSE)
                topColor(tp, tx, ty) else topColor(tp, tx, ty)
            val el = e(tx, ty)
            G.rect(c, l, topY, l + T, topY + T, if (el > 0 && !Tl.isWallLike(tp) && tp != Tl.LOW && tp != Tl.FOAM) G.light(tc, minOf(0.45f, el * 0.06f)) else tc, true)
            when (tp) {
                Tl.WALL -> G.rect(c, l + 7, topY + 7, l + T - 7, topY + T - 7, G.light(biome.wallTop, 0.18f), false)
                Tl.BOX -> {
                    G.rect(c, l + 26, topY, l + 38, topY + T, 0xFFC08A55.toInt(), false)
                    val dmg = 1f - hp[idx(tx, ty)] / Tl.maxHp(Tl.BOX)
                    if (dmg > 0.3f) G.line(c, l + 8, topY + 10, l + 26, topY + 30, 2.5f, 0x88000000.toInt())
                    if (dmg > 0.6f) G.line(c, l + 40, topY + 40, l + 58, topY + 54, 2.5f, 0x88000000.toInt())
                }
                Tl.GLASS -> { G.line(c, l + 12, topY + 46, l + 34, topY + 14, 4f, 0xAAFFFFFF.toInt()); G.line(c, l + 34, topY + 52, l + 48, topY + 32, 3f, 0x88FFFFFF.toInt()) }
                Tl.LOW -> { G.line(c, l + 6, topY + 6, l + T - 6, topY + T - 6, 4f, 0xFF8E5C2E.toInt()); G.line(c, l + T - 6, topY + 6, l + 6, topY + T - 6, 4f, 0xFF8E5C2E.toInt()) }
                Tl.FOAM -> {
                    val life = tmr[idx(tx, ty)]
                    G.circle(c, l + 18, topY + 20, 10f, 0xFFFFFFFF.toInt(), false); G.circle(c, l + 42, topY + 40, 12f, 0xFFFFFFFF.toInt(), false)
                    if (life < 2f && ((time * 8).toInt() % 2 == 0)) G.rect(c, l, topY, l + T, topY + T, 0x55FF6B6B, false)
                }
                Tl.BRICK -> {
                    G.line(c, l, topY + 21, l + T, topY + 21, 2f, 0x66000000); G.line(c, l, topY + 42, l + T, topY + 42, 2f, 0x66000000)
                    G.line(c, l + 32, topY, l + 32, topY + 21, 2f, 0x66000000); G.line(c, l + 16, topY + 21, l + 16, topY + 42, 2f, 0x66000000)
                    G.line(c, l + 48, topY + 21, l + 48, topY + 42, 2f, 0x66000000); G.line(c, l + 32, topY + 42, l + 32, topY + T, 2f, 0x66000000)
                }
                Tl.DOOR -> { for (k in 0..3) G.line(c, l + 8 + k * 16, topY + 6, l + k * 16, topY + T - 6, 5f, 0xFFFFD23F.toInt()) }
                Tl.LADDER -> {
                    G.line(c, l + 16, topY, l + 16, topY + T, 5f, 0xFF8B5A3C.toInt()); G.line(c, l + 48, topY, l + 48, topY + T, 5f, 0xFF8B5A3C.toInt())
                    for (k in 0..3) G.line(c, l + 16, topY + 8 + k * 16, l + 48, topY + 8 + k * 16, 4f, 0xFFA8754F.toInt())
                }
                Tl.WATER -> G.line(c, l + 10, topY + 24, l + 30, topY + 24, 3f, 0x66FFFFFF)
                Tl.LAVA -> G.circle(c, l + 30, topY + 30, 7f, 0xFFFF9AB0.toInt(), false)
            }
        }
    }

    /** Bushes are drawn after the entities of their row so they hide them. */
    fun drawBushRow(c: Canvas, ty: Int, x0: Int, x1: Int, time: Float) {
        for (tx in x0..x1) {
            if (t(tx, ty) != Tl.BUSH) continue
            val base = e(tx, ty) * LH
            val l = tx * T; val y = ty * T - base * ZS
            val sw = sinf(time * 1.6f + tx * 0.8f + ty * 0.3f) * 2f
            G.ow = 2.5f
            val bc = biome.bush
            G.circle(c, l + 16 + sw, y + 30, 20f, G.shade(bc, 0.85f))
            G.circle(c, l + 46 + sw, y + 30, 20f, G.shade(bc, 0.85f))
            G.circle(c, l + 32 + sw, y + 18, 22f, bc)
            G.circle(c, l + 24 + sw, y + 14, 7f, G.light(bc, 0.25f), false)
        }
    }

    fun clampX(x: Float) = clamp(x, 1f, pxW - 1f)
    fun clampY(y: Float) = clamp(y, 1f, pxH - 1f)
}
