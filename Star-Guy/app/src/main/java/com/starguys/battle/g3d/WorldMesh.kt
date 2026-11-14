package com.starguys.battle.g3d

import com.starguys.battle.game.Biome
import com.starguys.battle.game.LH
import com.starguys.battle.game.T
import com.starguys.battle.game.TileMap
import com.starguys.battle.game.Tl
import com.starguys.battle.game.WALL_H

/** Material ids stored in the world vertex colour alpha (see Shaders.worldFS). */
object WMat {
    const val GROUND = 0f; const val WATER = 0.1f; const val LAVA = 0.2f; const val GOO = 0.3f
    const val WALL = 0.5f; const val CRATE = 0.6f; const val BRICK = 0.7f; const val METAL = 0.8f; const val PLAIN = 0.9f; const val ICE = 0.95f
}

/**
 * Turns a [TileMap] into static 3D terrain, split in 16x16 tile chunks that are rebuilt only when
 * their tiles change (destroyed crates, foam platforms, collapsing floors...).
 */
class WorldMesh {
    companion object { const val CH = 16; const val CLIFF = -320f; var genCounter = 0 }

    private var map: TileMap? = null
    private var lastVersion = -1
    private var hashes = LongArray(0)
    var gen = 0
        private set
    private var cw = 0; private var chh = 0

    private val v = FloatArrayList(1 shl 15)
    private val ix = ShortArrayList(1 shl 15)
    private var bx0 = 0f; private var by0 = 0f; private var bz0 = 0f; private var bx1 = 0f; private var by1 = 0f; private var bz1 = 0f

    /** Called every frame on the game thread. Pushes rebuilt chunks into the bus. */
    fun sync(m: TileMap, bus: FrameBus): Int {
        val full = bus.wantFullWorld
        if (m !== map) {
            map = m; gen = ++genCounter; lastVersion = -1
            cw = (m.w + CH - 1) / CH; chh = (m.h + CH - 1) / CH
            hashes = LongArray(cw * chh + 1) { Long.MIN_VALUE }
        }
        if (full) { bus.wantFullWorld = false; for (i in hashes.indices) hashes[i] = Long.MIN_VALUE; lastVersion = -1 }
        if (m.version == lastVersion) return gen
        lastVersion = m.version
        for (cy in 0 until chh) for (cx in 0 until cw) {
            val h = chunkHash(m, cx, cy)
            val id = cy * cw + cx
            if (h == hashes[id]) continue
            hashes[id] = h
            buildChunk(m, cx, cy)?.let { bus.chunks.add(it) }
        }
        // skirt around the map (ground plane & rim), built once per world
        if (hashes[cw * chh] == Long.MIN_VALUE) {
            hashes[cw * chh] = 0L
            buildSkirt(m)?.let { bus.chunks.add(it) }
        }
        return gen
    }

    private fun chunkHash(m: TileMap, cx: Int, cy: Int): Long {
        var h = 1125899906842597L
        // include a 1-tile border: side faces depend on the neighbours
        for (ty in cy * CH - 1..cy * CH + CH) for (tx in cx * CH - 1..cx * CH + CH) {
            val t = m.t(tx, ty); val e = m.e(tx, ty)
            h = 31 * h + (t * 64 + e)
        }
        return h
    }

    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
    private fun shade(c: FloatArray, k: Float) = floatArrayOf(c[0] * k, c[1] * k, c[2] * k)
    private fun mixc(a: FloatArray, b: FloatArray, k: Float) = floatArrayOf(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k)

    private fun bound(x: Float, y: Float, z: Float) {
        if (x < bx0) bx0 = x; if (y < by0) by0 = y; if (z < bz0) bz0 = z
        if (x > bx1) bx1 = x; if (y > by1) by1 = y; if (z > bz1) bz1 = z
    }

    private fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, c: FloatArray, k: Float, mat: Float): Int {
        val i = v.size / 10
        v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz)
        v.add(c[0] * k); v.add(c[1] * k); v.add(c[2] * k); v.add(mat)
        bound(x, y, z)
        return i
    }

    private fun quad(a: Int, b: Int, c: Int, d: Int) {
        ix.add(a.toShort()); ix.add(b.toShort()); ix.add(c.toShort())
        ix.add(a.toShort()); ix.add(c.toShort()); ix.add(d.toShort())
    }

    /** horizontal quad (x0,z0)-(x1,z1) at height y, facing up, per-corner AO */
    private fun top(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, c: FloatArray, mat: Float, ao: FloatArray? = null) {
        val a = vert(x0, y, z0, 0f, 1f, 0f, c, ao?.get(0) ?: 1f, mat)
        val b = vert(x0, y, z1, 0f, 1f, 0f, c, ao?.get(1) ?: 1f, mat)
        val cc = vert(x1, y, z1, 0f, 1f, 0f, c, ao?.get(2) ?: 1f, mat)
        val d = vert(x1, y, z0, 0f, 1f, 0f, c, ao?.get(3) ?: 1f, mat)
        quad(a, b, cc, d)
    }

    /** vertical wall face on one side of a tile. dir: 0 +x, 1 +z(+y sim), 2 -x, 3 -z */
    private fun side(tx: Int, ty: Int, dir: Int, y0: Float, y1: Float, c: FloatArray, mat: Float, inset: Float = 0f) {
        if (y1 <= y0 + 0.5f) return
        val l = tx * T + inset; val r = (tx + 1) * T - inset; val t = ty * T + inset; val b = (ty + 1) * T - inset
        val botK = if (y0 <= CLIFF + 1f) 0.55f else 0.72f
        when (dir) {
            0 -> { val a = vert(r, y0, t, 1f, 0f, 0f, c, botK, mat); val bb = vert(r, y1, t, 1f, 0f, 0f, c, 1f, mat); val cc = vert(r, y1, b, 1f, 0f, 0f, c, 1f, mat); val d = vert(r, y0, b, 1f, 0f, 0f, c, botK, mat); quad(a, bb, cc, d) }
            1 -> { val a = vert(r, y0, b, 0f, 0f, 1f, c, botK, mat); val bb = vert(r, y1, b, 0f, 0f, 1f, c, 1f, mat); val cc = vert(l, y1, b, 0f, 0f, 1f, c, 1f, mat); val d = vert(l, y0, b, 0f, 0f, 1f, c, botK, mat); quad(a, bb, cc, d) }
            2 -> { val a = vert(l, y0, b, -1f, 0f, 0f, c, botK, mat); val bb = vert(l, y1, b, -1f, 0f, 0f, c, 1f, mat); val cc = vert(l, y1, t, -1f, 0f, 0f, c, 1f, mat); val d = vert(l, y0, t, -1f, 0f, 0f, c, botK, mat); quad(a, bb, cc, d) }
            else -> { val a = vert(l, y0, t, 0f, 0f, -1f, c, botK, mat); val bb = vert(l, y1, t, 0f, 0f, -1f, c, 1f, mat); val cc = vert(r, y1, t, 0f, 0f, -1f, c, 1f, mat); val d = vert(r, y0, t, 0f, 0f, -1f, c, botK, mat); quad(a, bb, cc, d) }
        }
    }

    /** axis-aligned box (decorations baked into the terrain) */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, c: FloatArray, mat: Float) {
        var a: Int; var b: Int; var cc: Int; var d: Int
        a = vert(x0, y1, z0, 0f, 1f, 0f, c, 1f, mat); b = vert(x0, y1, z1, 0f, 1f, 0f, c, 1f, mat); cc = vert(x1, y1, z1, 0f, 1f, 0f, c, 1f, mat); d = vert(x1, y1, z0, 0f, 1f, 0f, c, 1f, mat); quad(a, b, cc, d)
        a = vert(x1, y0, z0, 1f, 0f, 0f, c, 0.8f, mat); b = vert(x1, y1, z0, 1f, 0f, 0f, c, 1f, mat); cc = vert(x1, y1, z1, 1f, 0f, 0f, c, 1f, mat); d = vert(x1, y0, z1, 1f, 0f, 0f, c, 0.8f, mat); quad(a, b, cc, d)
        a = vert(x0, y0, z1, -1f, 0f, 0f, c, 0.8f, mat); b = vert(x0, y1, z1, -1f, 0f, 0f, c, 1f, mat); cc = vert(x0, y1, z0, -1f, 0f, 0f, c, 1f, mat); d = vert(x0, y0, z0, -1f, 0f, 0f, c, 0.8f, mat); quad(a, b, cc, d)
        a = vert(x1, y0, z1, 0f, 0f, 1f, c, 0.8f, mat); b = vert(x1, y1, z1, 0f, 0f, 1f, c, 1f, mat); cc = vert(x0, y1, z1, 0f, 0f, 1f, c, 1f, mat); d = vert(x0, y0, z1, 0f, 0f, 1f, c, 0.8f, mat); quad(a, b, cc, d)
        a = vert(x0, y0, z0, 0f, 0f, -1f, c, 0.8f, mat); b = vert(x0, y1, z0, 0f, 0f, -1f, c, 1f, mat); cc = vert(x1, y1, z0, 0f, 0f, -1f, c, 1f, mat); d = vert(x1, y0, z0, 0f, 0f, -1f, c, 0.8f, mat); quad(a, b, cc, d)
    }

    /** height of the visible top surface of a tile (VOID: deep cliff) */
    fun topH(m: TileMap, tx: Int, ty: Int): Float {
        if (!m.inside(tx, ty)) return if (m.oobVoid) CLIFF else WALL_H
        val tp = m.t(tx, ty)
        val base = m.e(tx, ty) * LH
        return when (tp) {
            Tl.VOID -> CLIFF
            Tl.WALL, Tl.BOX, Tl.BRICK, Tl.DOOR -> base + WALL_H
            Tl.GLASS -> base
            Tl.LOW, Tl.FOAM -> base + LH
            Tl.WATER, Tl.LAVA -> base - 7f
            Tl.COLLAPSE -> CLIFF
            else -> base
        }
    }

    /** the tile's solid body bottom for side faces of neighbours (water: its bed) */
    private fun sideFloor(m: TileMap, tx: Int, ty: Int): Float {
        val tp = m.t(tx, ty)
        if (!m.inside(tx, ty)) return if (m.oobVoid) CLIFF else 0f
        if (tp == Tl.WATER || tp == Tl.LAVA) return m.e(tx, ty) * LH - 7f
        return topH(m, tx, ty)
    }

    private fun aoAt(m: TileMap, cx: Int, cy: Int, h: Float): Float {
        // corner (cx,cy) is shared by tiles (cx-1..cx, cy-1..cy)
        var n = 0
        for (dy in -1..0) for (dx in -1..0) if (topH(m, cx + dx, cy + dy) > h + 12f) n++
        return 1f - 0.16f * n
    }

    private fun tileColor(m: TileMap, tp: Int, tx: Int, ty: Int): FloatArray {
        val b = m.biome
        val chk = (tx + ty) and 1 == 0
        return when (tp) {
            Tl.WALL -> rgb(b.wallTop)
            Tl.BOX -> rgb(0xFFF0B866.toInt())
            Tl.LOW -> rgb(0xFFD08A45.toInt())
            Tl.FOAM -> rgb(0xFFF8FAFC.toInt())
            Tl.BRICK -> rgb(0xFFF97316.toInt())
            Tl.DOOR -> rgb(0xFF94A3B8.toInt())
            Tl.ICE -> rgb(if (chk) 0xFFE0F2FE.toInt() else 0xFFBAE6FD.toInt())
            Tl.WATER -> rgb(0xFF38BDF8.toInt())
            Tl.LAVA -> rgb(0xFFEC4899.toInt())
            Tl.PAINT -> rgb(m.paintColor)
            Tl.CONVEYOR -> rgb(0xFF4A4F63.toInt())
            Tl.SPEED -> rgb(0xFF2F3550.toInt())
            Tl.GLUE -> rgb(0xFFB6E35A.toInt())
            else -> rgb(if (chk) b.floorA else b.floorB)
        }
    }

    private fun sideColor(m: TileMap, tp: Int): FloatArray = when (tp) {
        Tl.WALL -> rgb(m.biome.wallSide)
        Tl.BOX -> rgb(0xFFD99A4E.toInt())
        Tl.LOW -> rgb(0xFFB06A30.toInt())
        Tl.FOAM -> rgb(0xFFE2E8F0.toInt())
        Tl.BRICK -> rgb(0xFFC2410C.toInt())
        Tl.DOOR -> rgb(0xFF64748B.toInt())
        else -> rgb(m.biome.cliff)
    }

    private fun sideMat(tp: Int): Float = when (tp) {
        Tl.WALL -> WMat.WALL; Tl.BOX, Tl.LOW -> WMat.CRATE; Tl.BRICK -> WMat.BRICK; Tl.DOOR -> WMat.METAL
        else -> WMat.WALL
    }

    private fun topMat(tp: Int): Float = when (tp) {
        Tl.WATER -> WMat.WATER; Tl.LAVA -> WMat.LAVA; Tl.PAINT, Tl.GLUE -> WMat.GOO
        Tl.WALL -> WMat.PLAIN; Tl.BOX, Tl.LOW -> WMat.CRATE; Tl.BRICK -> WMat.BRICK; Tl.DOOR -> WMat.METAL
        Tl.ICE -> WMat.ICE; Tl.FOAM, Tl.CONVEYOR, Tl.SPEED, Tl.GOAL -> WMat.PLAIN
        else -> WMat.GROUND
    }

    private fun resetBounds() { bx0 = 1e9f; by0 = 1e9f; bz0 = 1e9f; bx1 = -1e9f; by1 = -1e9f; bz1 = -1e9f }

    private fun buildChunk(m: TileMap, cx: Int, cy: Int): ChunkMesh? {
        v.clear(); ix.clear(); resetBounds()
        val x0 = cx * CH; val y0 = cy * CH
        val x1 = minOf(m.w, x0 + CH) - 1; val y1 = minOf(m.h, y0 + CH) - 1
        val dirX = intArrayOf(1, 0, -1, 0); val dirY = intArrayOf(0, 1, 0, -1)
        for (ty in y0..y1) for (tx in x0..x1) {
            val tp = m.t(tx, ty)
            if (tp == Tl.VOID || tp == Tl.COLLAPSE) continue
            val h = topH(m, tx, ty)
            val l = tx * T; val t = ty * T; val r = l + T; val b = t + T
            val col = tileColor(m, tp, tx, ty)
            val el = m.e(tx, ty)
            val lit = if (el > 0 && !Tl.isWallLike(tp) && tp != Tl.LOW && tp != Tl.FOAM) mixc(col, floatArrayOf(1f, 1f, 1f), minOf(0.3f, el * 0.04f)) else col
            if (tp == Tl.GOAL) {
                val hh = T / 2
                for (k in 0 until 4) {
                    val sx = l + (k % 2) * hh; val sz = t + (k / 2) * hh
                    val white = ((k % 2) + (k / 2)) % 2 == 0
                    top(sx, sz, sx + hh, sz + hh, h, rgb(if (white) 0xFFFFFFFF.toInt() else 0xFF26213A.toInt()), WMat.PLAIN)
                }
            } else {
                val ao = floatArrayOf(aoAt(m, tx, ty, h), aoAt(m, tx, ty + 1, h), aoAt(m, tx + 1, ty + 1, h), aoAt(m, tx + 1, ty, h))
                top(l, t, r, b, h, lit, topMat(tp), ao)
            }
            // sides: down to each neighbour's surface
            val sc = sideColor(m, tp); val sm = sideMat(tp)
            for (d in 0 until 4) {
                val nx = tx + dirX[d]; val ny = ty + dirY[d]
                val nh = sideFloor(m, nx, ny)
                if (nh < h) {
                    // a wall-like block standing on raised ground: lower part is cliff
                    val baseTop = el * LH
                    if (Tl.isWallLike(tp) || tp == Tl.LOW || tp == Tl.FOAM) {
                        if (nh < baseTop) side(tx, ty, d, nh, baseTop, rgb(m.biome.cliff), WMat.WALL)
                        side(tx, ty, d, maxOf(nh, baseTop), h, sc, sm)
                    } else side(tx, ty, d, nh, h, if (tp == Tl.WATER || tp == Tl.LAVA) rgb(m.biome.cliff) else shade(rgb(m.biome.cliff), 1f), WMat.WALL)
                }
            }
            // decorations
            when (tp) {
                Tl.WALL -> if (m.biome != Biome.SPACE) top(l + 8, t + 8, r - 8, b - 8, h + 0.6f, mixc(col, floatArrayOf(1f, 1f, 1f), 0.16f), WMat.PLAIN)
                Tl.RAIL -> {
                    val rc = rgb(0xFF8A90A0.toInt()); val wc = rgb(0xFF8B5A3C.toInt())
                    val horiz = m.t(tx - 1, ty) == Tl.RAIL || m.t(tx + 1, ty) == Tl.RAIL || !(m.t(tx, ty - 1) == Tl.RAIL || m.t(tx, ty + 1) == Tl.RAIL)
                    if (horiz) {
                        for (k in 0..2) box(l + 6 + k * 22, h, t + 12, l + 16 + k * 22, h + 3, b - 12, wc, WMat.CRATE)
                        box(l, h + 3, t + 18, r, h + 7, t + 23, rc, WMat.METAL); box(l, h + 3, b - 23, r, h + 7, b - 18, rc, WMat.METAL)
                    } else {
                        for (k in 0..2) box(l + 12, h, t + 6 + k * 22, r - 12, h + 3, t + 16 + k * 22, wc, WMat.CRATE)
                        box(l + 18, h + 3, t, l + 23, h + 7, b, rc, WMat.METAL); box(r - 23, h + 3, t, r - 18, h + 7, b, rc, WMat.METAL)
                    }
                }
                Tl.LADDER -> {
                    val wc = rgb(0xFF8B5A3C.toInt()); val rc = rgb(0xFFA8754F.toInt())
                    box(l + 12, h, t + 4, l + 18, h + 3, b - 4, wc, WMat.CRATE); box(r - 18, h, t + 4, r - 12, h + 3, b - 4, wc, WMat.CRATE)
                    for (k in 0..3) box(l + 12, h, t + 8 + k * 15, r - 12, h + 4, t + 12 + k * 15, rc, WMat.CRATE)
                }
                Tl.DOOR -> { val yc = rgb(0xFFFACC15.toInt()); for (k in 0..2) box(l + 6 + k * 20, h, t + 4, l + 14 + k * 20, h + 2, b - 4, yc, WMat.PLAIN) }
                Tl.BOX -> { val bc = rgb(0xFFC08A55.toInt()); box(l + 27, h, t - 0.5f, l + 37, h + 1.5f, b + 0.5f, bc, WMat.CRATE) }
                Tl.CONVEYOR -> { val ec = rgb(0xFF2A2E3D.toInt()); box(l, h, t, r, h + 2, t + 5, ec, WMat.METAL); box(l, h, b - 5, r, h + 2, b, ec, WMat.METAL) }
            }
        }
        if (ix.size == 0) return ChunkMesh(gen, cy * cw + cx, FloatArray(0), ShortArray(0), 0f, 0f, 0f, 0f, 0f, 0f)
        return ChunkMesh(gen, cy * cw + cx, v.toArray(), ix.toArray(), bx0, by0, bz0, bx1, by1, bz1)
    }

    /** Ground plane and boundary rim around the playable area (unless the map floats in the void). */
    private fun buildSkirt(m: TileMap): ChunkMesh? {
        v.clear(); ix.clear(); resetBounds()
        if (m.oobVoid) return null
        val W = m.pxW; val H = m.pxH
        val far = 9000f
        val g = shade(rgb(m.biome.floorB), 0.92f)
        val gy = -2f
        top(-far, -far, W + far, 0f, gy, g, WMat.GROUND)
        top(-far, H, W + far, H + far, gy, g, WMat.GROUND)
        top(-far, 0f, 0f, H, gy, g, WMat.GROUND)
        top(W, 0f, W + far, H, gy, g, WMat.GROUND)
        // low rim walls hugging the map edge (the edge is solid in the simulation)
        val wc = rgb(m.biome.wallSide); val tc = rgb(m.biome.wallTop)
        val rh = WALL_H
        box(-T * 0.6f, gy, -T * 0.6f, W + T * 0.6f, rh, 0f, wc, WMat.WALL)
        box(-T * 0.6f, gy, H, W + T * 0.6f, rh, H + T * 0.6f, wc, WMat.WALL)
        box(-T * 0.6f, gy, 0f, 0f, rh, H, wc, WMat.WALL)
        box(W, gy, 0f, W + T * 0.6f, rh, H, wc, WMat.WALL)
        top(-T * 0.6f, -T * 0.6f, W + T * 0.6f, 0f, rh + 0.5f, tc, WMat.PLAIN)
        top(-T * 0.6f, H, W + T * 0.6f, H + T * 0.6f, rh + 0.5f, tc, WMat.PLAIN)
        top(-T * 0.6f, 0f, 0f, H, rh + 0.5f, tc, WMat.PLAIN)
        top(W, 0f, W + T * 0.6f, H, rh + 0.5f, tc, WMat.PLAIN)
        return ChunkMesh(gen, 1_000_000, v.toArray(), ix.toArray(), bx0, by0, bz0, bx1, by1, bz1)
    }
}

/** Minimal primitive lists (no boxing). */
class FloatArrayList(cap: Int) {
    private var a = FloatArray(cap)
    var size = 0
        private set
    fun add(f: Float) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = f }
    fun clear() { size = 0 }
    fun toArray(): FloatArray = a.copyOf(size)
}

class ShortArrayList(cap: Int) {
    private var a = ShortArray(cap)
    var size = 0
        private set
    fun add(s: Short) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = s }
    fun clear() { size = 0 }
    fun toArray(): ShortArray = a.copyOf(size)
}
