package com.starguys.battle.game

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.TW
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
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
    MEADOW("Prairie étoilée", TW.LIME_400, TW.LIME_500, TW.EMERALD_400, TW.EMERALD_700,
        TW.AMBER_700, TW.SKY_400, TW.BLUE_700, TW.GREEN_500, TW.YELLOW_400, "L'arène classique"),
    PARK("Parc d'attractions", TW.AMBER_100, TW.AMBER_200, TW.ROSE_400, TW.ROSE_700,
        TW.AMBER_800, TW.FUCHSIA_400, TW.VIOLET_800, TW.EMERALD_500, TW.YELLOW_400, "Grande roue, montagnes russes, autos-tamponneuses"),
    FACTORY("Usine à jouets", TW.SKY_200, TW.SLATE_300, TW.AMBER_400, TW.AMBER_700,
        TW.SLATE_600, TW.SLATE_700, TW.SLATE_900, TW.EMERALD_500, TW.CYAN_400, "Tapis roulants, presses, cuves de peinture"),
    SNOW("Montagne enneigée", TW.SLATE_50, TW.SKY_100, TW.SKY_300, TW.SKY_700,
        TW.SLATE_400, TW.SKY_200, TW.BLUE_500, TW.EMERALD_600, TW.CYAN_300, "Sol glissant, avalanches, chalets"),
    BEACH("Plage tropicale", TW.AMBER_200, TW.YELLOW_200, TW.STONE_400, TW.STONE_600,
        TW.AMBER_700, TW.CYAN_400, TW.SKY_700, TW.GREEN_500, TW.ORANGE_400, "Bateaux, cocotiers, crabes géants"),
    KITCHEN("Cuisine géante", TW.WHITE, TW.RED_300, TW.SLATE_200, TW.SLATE_500,
        TW.AMBER_700, TW.ORANGE_200, TW.AMBER_600, TW.LIME_500, TW.RED_400, "On est minuscule : assiettes, fourchettes, casseroles"),
    SPACE("Station spatiale", TW.SLATE_300, TW.INDIGO_300, TW.INDIGO_500, TW.INDIGO_800,
        TW.SLATE_700, TW.INDIGO_950, TW.SLATE_950, TW.TEAL_400, TW.CYAN_300, "Faible gravité"),
    VOLCANO("Volcan jelly", TW.STONE_600, TW.STONE_700, TW.PURPLE_700, TW.PURPLE_900,
        TW.STONE_700, TW.ROSE_700, TW.PURPLE_950, TW.LIME_600, TW.PINK_500, "Lave en gélatine"),
    SWAMP("Marais hanté", TW.GREEN_700, TW.GREEN_800, TW.TEAL_800, TW.EMERALD_900,
        TW.STONE_700, TW.EMERALD_900, TW.SLATE_950, TW.GREEN_600, TW.AMBER_400, "Brouillard et lanternes");

    val lowGravity get() = this == SPACE
    val fog get() = this == SWAMP
}

/** Procedurally painted ground textures (one pair per biome), drawn as bitmaps for detail at low cost. */
object TileArt {
    private val cache = HashMap<Biome, Array<Bitmap?>>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private const val S = 96

    fun floor(b: Biome, chk: Boolean): Bitmap? {
        val arr = cache.getOrPut(b) { arrayOf(make(b, b.floorA, 1), make(b, b.floorB, 2)) }
        return arr[if (chk) 0 else 1]
    }

    fun draw(c: Canvas, bmp: Bitmap, l: Float, t: Float, rot: Int) {
        dst.set(l, t, l + T, t + T)
        if (rot == 0) { c.drawBitmap(bmp, null, dst, paint); return }
        c.save(); c.rotate(rot * 90f, l + T / 2, t + T / 2); c.drawBitmap(bmp, null, dst, paint); c.restore()
    }

    private fun make(b: Biome, base: Int, seed: Int): Bitmap? {
        val bmp = try { Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888) } catch (e: Throwable) { null } ?: return null
        val c = try { Canvas(bmp) } catch (e: Throwable) { return null }
        val r = java.util.Random(b.ordinal * 31L + seed)
        val oF = G.flash; val oA = G.fade; val oD = G.depth; val oW = G.ow
        G.flash = 0f; G.fade = 1f; G.depth = false
        G.rect(c, 0f, 0f, S.toFloat(), S.toFloat(), base, false)
        fun rf(a: Float, z: Float) = a + r.nextFloat() * (z - a)
        // fine noise
        for (i in 0 until 70) G.circle(c, rf(0f, 96f), rf(0f, 96f), rf(0.8f, 2.4f), G.alpha(if (r.nextBoolean()) TW.WHITE else TW.BLACK, rf(0.04f, 0.10f)), false)
        when (b) {
            Biome.MEADOW, Biome.SWAMP -> {
                for (i in 0 until 16) { val x = rf(4f, 92f); val y = rf(8f, 92f); val h = rf(6f, 12f); val lean = rf(-3f, 3f)
                    G.line(c, x, y, x + lean, y - h, 2.2f, G.shade(base, rf(0.68f, 0.82f))); G.line(c, x + 3, y, x + 3 + lean, y - h * 0.8f, 2f, G.light(base, 0.25f)) }
                if (b == Biome.MEADOW && seed == 1) for (i in 0 until 3) { val x = rf(10f, 86f); val y = rf(10f, 86f); G.circle(c, x, y, 2.6f, if (i % 2 == 0) TW.PINK_300 else TW.YELLOW_300, false); G.circle(c, x, y, 1f, TW.WHITE, false) }
                if (b == Biome.SWAMP) for (i in 0 until 4) G.oval(c, rf(10f, 86f), rf(10f, 86f), rf(5f, 10f), rf(3f, 6f), G.alpha(TW.EMERALD_900, 0.35f), false)
            }
            Biome.PARK -> { for (row in 0 until 3) { val y = row * 32f; G.line(c, 0f, y, 96f, y, 2.5f, G.alpha(TW.AMBER_700, 0.25f)); val off = if (row % 2 == 0) 0f else 24f
                    for (k in 0..2) G.line(c, off + k * 48f, y, off + k * 48f, y + 32f, 2.5f, G.alpha(TW.AMBER_700, 0.25f)) } }
            Biome.FACTORY, Biome.SPACE -> {
                G.rect(c, 6f, 6f, 90f, 90f, 0, false)
                G.stroke.strokeWidth = 2.5f; G.stroke.color = G.alpha(TW.SLATE_700, 0.28f); c.drawRect(5f, 5f, 91f, 91f, G.stroke)
                for (p in arrayOf(floatArrayOf(12f, 12f), floatArrayOf(84f, 12f), floatArrayOf(12f, 84f), floatArrayOf(84f, 84f))) { G.circle(c, p[0], p[1], 3.2f, G.shade(base, 0.7f), false); G.circle(c, p[0] - 1f, p[1] - 1f, 1.4f, TW.WHITE, false) }
                if (b == Biome.SPACE) G.rect(c, 20f, 44f, 76f, 52f, G.alpha(TW.CYAN_300, 0.35f), false)
                else for (k in 0 until 5) G.line(c, 16f, 24f + k * 12f, 80f, 24f + k * 12f, 1.5f, G.alpha(TW.WHITE, 0.15f))
            }
            Biome.SNOW -> { for (i in 0 until 14) G.star(c, rf(4f, 92f), rf(4f, 92f), rf(1.5f, 3f), 0.8f, 4, 0f, G.alpha(TW.WHITE, 0.9f), false)
                for (i in 0 until 3) G.oval(c, rf(10f, 86f), rf(10f, 86f), rf(12f, 20f), rf(5f, 8f), G.alpha(TW.SKY_300, 0.18f), false) }
            Biome.BEACH -> { for (i in 0 until 40) G.circle(c, rf(0f, 96f), rf(0f, 96f), rf(0.8f, 1.8f), G.alpha(TW.AMBER_600, 0.35f), false)
                for (k in 0 until 2) { G.arc(c, rf(20f, 76f), rf(20f, 76f), rf(14f, 22f), 200f, 140f, 2f, G.alpha(TW.AMBER_500, 0.35f)) } }
            Biome.KITCHEN -> { for (k in 0 until 8) { G.line(c, k * 12f, 0f, k * 12f, 96f, 1.2f, G.alpha(TW.BLACK, 0.06f)); G.line(c, 0f, k * 12f, 96f, k * 12f, 1.2f, G.alpha(TW.BLACK, 0.06f)) } }
            Biome.VOLCANO -> { for (k in 0 until 3) { var x = rf(10f, 86f); var y = rf(10f, 86f); for (j in 0 until 4) { val nx = x + rf(-16f, 16f); val ny = y + rf(-16f, 16f); G.line(c, x, y, nx, ny, 2f, G.alpha(TW.BLACK, 0.35f)); x = nx; y = ny } }
                for (i in 0 until 5) G.circle(c, rf(6f, 90f), rf(6f, 90f), rf(1.5f, 2.5f), G.alpha(TW.PINK_400, 0.7f), false) }
        }
        // bevel: light top-left, dark bottom-right
        G.line(c, 1f, 1f, 95f, 1f, 3f, G.alpha(TW.WHITE, 0.22f)); G.line(c, 1f, 1f, 1f, 95f, 3f, G.alpha(TW.WHITE, 0.16f))
        G.line(c, 1f, 95f, 95f, 95f, 3f, G.alpha(TW.BLACK, 0.14f)); G.line(c, 95f, 1f, 95f, 95f, 3f, G.alpha(TW.BLACK, 0.10f))
        G.flash = oF; G.fade = oA; G.depth = oD; G.ow = oW
        return bmp
    }
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
            Tl.BOX -> TW.AMBER_300
            Tl.GLASS -> 0xAABAE6FD.toInt()
            Tl.LOW -> TW.AMBER_600
            Tl.FOAM -> TW.SLATE_50
            Tl.BRICK -> TW.ORANGE_500
            Tl.DOOR -> TW.SLATE_400
            Tl.ICE -> if (chk) TW.SKY_100 else TW.SKY_200
            Tl.WATER -> TW.SKY_400
            Tl.LAVA -> TW.PINK_500
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
            val chk = (tx + ty) and 1 == 0
            val tex = if (tp == Tl.FLOOR || tp == Tl.BUSH || tp == Tl.RAIL || tp == Tl.LADDER) TileArt.floor(biome, chk) else null
            if (tex != null) TileArt.draw(c, tex, l, tp0, (tx * 7 + ty * 13) and 3)
            else G.rect(c, l, tp0, l + T, tp0 + T, topColor(tp, tx, ty), false)
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

    /** Soft drop shadows cast by raised blocks onto the ground (light from the top-left). */
    fun drawShadows(c: Canvas, x0: Int, y0: Int, x1: Int, y1: Int) {
        G.fill.shader = null
        for (ty in y0..y1) for (tx in x0..x1) {
            if (!isRaised(tx, ty)) continue
            val hgt = groundZ(tx, ty)
            if (hgt <= 0f) continue
            val off = minOf(34f, 6f + hgt * 0.32f)
            val l = tx * T + off; val t0 = ty * T + off * 0.55f - hgt * ZS * 0.15f
            G.fill.color = 0x2E0F172A
            c.drawRect(l, t0, l + T, t0 + T, G.fill)
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
                Tl.BOX -> TW.AMBER_600
                Tl.GLASS -> 0x887DD3FC.toInt()
                Tl.LOW -> TW.AMBER_800
                Tl.FOAM -> TW.SLATE_300
                Tl.BRICK -> TW.ORANGE_700
                Tl.DOOR -> TW.SLATE_600
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
