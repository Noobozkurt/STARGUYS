package com.starguys.battle.game

import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Grid A* used by bots and mobs. Walking to a neighbour costs 1, climbing one level costs 2 (needs a jump),
 * and a 1-tile gap (void/lava) can be jumped over in a straight line (cost 4).
 */
class Nav(private val map: TileMap) {
    private val n = map.w * map.h
    private val g = FloatArray(n)
    private val parent = IntArray(n)
    private val stamp = IntArray(n)
    private val closed = IntArray(n)
    private var cur = 0
    private val pq = PriorityQueue<Long>(256)

    fun passable(tx: Int, ty: Int): Boolean {
        if (!map.inside(tx, ty)) return false
        val tp = map.t(tx, ty)
        if (tp == Tl.VOID || tp == Tl.LAVA || Tl.isWallLike(tp)) return false
        if (tp == Tl.COLLAPSE && map.aux(tx, ty) == 1) return false
        return true
    }

    private fun height(tx: Int, ty: Int) = map.groundZ(tx, ty)

    /** Returns tile path as flat int array [x0,y0,x1,y1...] from start to goal (exclusive of start), or null. */
    fun find(sx: Int, sy: Int, gx0: Int, gy0: Int, maxNodes: Int = 2500): IntArray? {
        if (!map.inside(sx, sy)) return null
        var gx = gx0; var gy = gy0
        if (!passable(gx, gy)) {
            // snap goal to a nearby passable tile
            var found = false
            loop@ for (r in 1..3) for (dy in -r..r) for (dx in -r..r) {
                if (passable(gx0 + dx, gy0 + dy)) { gx = gx0 + dx; gy = gy0 + dy; found = true; break@loop }
            }
            if (!found) return null
        }
        cur++
        if (cur == Int.MAX_VALUE) { cur = 1; stamp.fill(0); closed.fill(0) }
        pq.clear()
        val s = sy * map.w + sx
        val goal = gy * map.w + gx
        g[s] = 0f; stamp[s] = cur; parent[s] = -1
        pq.add(pack(h(sx, sy, gx, gy), s))
        var expanded = 0
        var best = s; var bestH = Float.MAX_VALUE
        while (pq.isNotEmpty()) {
            val top = pq.poll()!!
            val i = (top and 0xFFFFFFFFL).toInt()
            if (closed[i] == cur) continue
            closed[i] = cur
            val x = i % map.w; val y = i / map.w
            val hh = h(x, y, gx, gy)
            if (hh < bestH) { bestH = hh; best = i }
            if (i == goal) { best = i; break }
            if (++expanded > maxNodes) break
            val hz = height(x, y)
            for (d in 0 until 8) {
                val dx = DX[d]; val dy = DY[d]
                var nx = x + dx; var ny = y + dy
                var cost = if (d < 4) 1f else 1.42f
                if (!passable(nx, ny)) {
                    // try a gap jump (cardinal only)
                    if (d >= 4) continue
                    val tp = map.t(nx, ny)
                    if (tp != Tl.VOID && tp != Tl.LAVA) continue
                    nx += dx; ny += dy
                    if (!passable(nx, ny)) continue
                    cost = 4f
                } else if (d >= 4) {
                    // no corner cutting
                    if (!passable(x + dx, y) || !passable(x, y + dy)) continue
                }
                val nz = height(nx, ny)
                if (nz - hz > LH + 4f && map.t(nx, ny) != Tl.LADDER) continue
                if (nz - hz > STEP) cost += 1.2f
                if (map.t(nx, ny) == Tl.WATER) cost += 0.5f
                val ni = ny * map.w + nx
                if (closed[ni] == cur) continue
                val ng = g[i] + cost
                if (stamp[ni] != cur || ng < g[ni]) {
                    stamp[ni] = cur; g[ni] = ng; parent[ni] = i
                    pq.add(pack(ng + h(nx, ny, gx, gy), ni))
                }
            }
        }
        // rebuild path to goal (or best effort)
        val out = ArrayList<Int>()
        var k = best
        var guard = 0
        while (k != s && k >= 0 && guard++ < 4000) { out.add(k); k = parent[k] }
        if (out.isEmpty()) return null
        val res = IntArray(out.size * 2)
        for (j in out.indices) { val idx = out[out.size - 1 - j]; res[j * 2] = idx % map.w; res[j * 2 + 1] = idx / map.w }
        return res
    }

    private fun h(x: Int, y: Int, gx: Int, gy: Int): Float {
        val dx = abs(x - gx); val dy = abs(y - gy)
        return (dx + dy) + (1.42f - 2f) * minOf(dx, dy)
    }

    private fun pack(f: Float, i: Int): Long = (java.lang.Float.floatToIntBits(f).toLong() shl 32) or (i.toLong() and 0xFFFFFFFFL)

    companion object {
        val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val DY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
    }
}
