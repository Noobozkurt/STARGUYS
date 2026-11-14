package com.starguys.battle.game

import com.starguys.battle.core.chance
import com.starguys.battle.core.pick
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Procedural map generators for every mode and biome. */
object MapGen {
    fun tc(t: Int) = t * T + T / 2

    fun isOpenFloor(mp: TileMap, tx: Int, ty: Int, r: Int = 1): Boolean {
        for (y in ty - r..ty + r) for (x in tx - r..tx + r) {
            val tp = mp.t(x, y)
            if (tp != Tl.FLOOR && tp != Tl.ICE) return false
            if (mp.e(x, y) != mp.e(tx, ty)) return false
        }
        return true
    }

    fun blob(mp: TileMap, cx: Int, cy: Int, r: Float, tp: Int, only: Int = Tl.FLOOR) {
        val ri = r.toInt() + 1
        for (y in cy - ri..cy + ri) for (x in cx - ri..cx + ri) {
            val d = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toFloat()) + rf(-0.6f, 0.6f)
            if (d <= r && mp.inside(x, y) && (only < 0 || mp.t(x, y) == only)) mp.set(x, y, tp)
        }
    }

    /** Walkable regions not connected to (sx, sy) are filled so nobody spawns there. */
    fun sealUnreachable(mp: TileMap, sx: Int, sy: Int) {
        val seen = BooleanArray(mp.w * mp.h)
        val q = ArrayDeque<Int>()
        var start = mp.idx(sx, sy)
        if (!mp.walkable(sx, sy)) {
            var found = false
            for (r in 1..10) { for (dy in -r..r) for (dx in -r..r) if (!found && mp.walkable(sx + dx, sy + dy) && mp.inside(sx + dx, sy + dy)) { start = mp.idx(sx + dx, sy + dy); found = true }; if (found) break }
        }
        q.add(start); seen[start] = true
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            val x = i % mp.w; val y = i / mp.w
            for (d in 0 until 4) {
                val nx = x + Nav.DX[d]; val ny = y + Nav.DY[d]
                if (!mp.inside(nx, ny)) continue
                val ni = mp.idx(nx, ny)
                if (seen[ni]) continue
                val tp = mp.t(nx, ny)
                val pass = mp.walkable(nx, ny) || Tl.destructible(tp)
                if (!pass) continue
                seen[ni] = true; q.add(ni)
            }
        }
        for (y in 0 until mp.h) for (x in 0 until mp.w) {
            val i = mp.idx(x, y)
            if (!seen[i] && mp.walkable(x, y) && mp.t(x, y) != Tl.WATER) mp.set(x, y, Tl.WALL)
        }
    }

    // ------------------------------------------------------------------ arena
    fun arena(m: Match, biome: Biome, w: Int, h: Int, density: Float = 1f, symmetric: Boolean = true, hazards: Boolean = true, vehicles: Int = 0): TileMap {
        val mp = TileMap(w, h, biome)
        m.map = mp
        mp.rectWall(0, 0, w - 1, h - 1)
        val n = ((w * h) / 34f * density).toInt()
        for (i in 0 until n) {
            val x = ri(2, if (symmetric) w / 2 else w - 3); val y = ri(2, h - 3)
            val tp = when (ri(10)) { 0, 1, 2, 3, 4 -> Tl.WALL; 5, 6, 7 -> Tl.BOX; 8 -> Tl.LOW; else -> if (chance(0.5f)) Tl.GLASS else Tl.BUSH }
            val shape = ri(4)
            val cells = when (shape) {
                0 -> listOf(0 to 0, 1 to 0, 2 to 0)
                1 -> listOf(0 to 0, 0 to 1, 0 to 2)
                2 -> listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)
                else -> listOf(0 to 0, 1 to 0, 1 to 1)
            }
            for ((dx, dy) in cells) {
                val px = x + dx; val py = y + dy
                if (px < 2 || py < 2 || px > w - 3 || py > h - 3) continue
                mp.set(px, py, tp)
                if (symmetric) mp.set(w - 1 - px, h - 1 - py, tp)
            }
        }
        // bushes
        for (i in 0 until (w * h / 120)) {
            val x = ri(3, w - 4); val y = ri(3, h - 4)
            blob(mp, x, y, rf(1f, 2.2f), Tl.BUSH)
            if (symmetric) blob(mp, w - 1 - x, h - 1 - y, rf(1f, 2.2f), Tl.BUSH)
        }
        if (hazards) biomeFeatures(m, mp, symmetric)
        // keep centre and edges a little open
        sealUnreachable(mp, w / 2, h / 2)
        decorate(m, mp, (w * h / 70f).toInt())
        interactives(m, mp, (w * h / 160f).toInt().coerceAtLeast(2))
        if (vehicles > 0) spawnVehicles(m, mp, vehicles)
        return mp
    }

    fun biomeFeatures(m: Match, mp: TileMap, symmetric: Boolean) {
        val w = mp.w; val h = mp.h
        fun mirrorBlob(cx: Int, cy: Int, r: Float, tp: Int) { blob(mp, cx, cy, r, tp); if (symmetric) blob(mp, w - 1 - cx, h - 1 - cy, r, tp) }
        when (mp.biome) {
            Biome.SNOW -> { for (i in 0 until 3) mirrorBlob(ri(4, w / 2), ri(4, h - 5), rf(2f, 3.5f), Tl.ICE) }
            Biome.BEACH -> { for (i in 0 until 2) mirrorBlob(ri(4, w / 2), ri(4, h - 5), rf(1.8f, 3f), Tl.WATER) }
            Biome.VOLCANO -> { for (i in 0 until 2) mirrorBlob(ri(4, w / 2 - 2), ri(4, h - 5), rf(1.2f, 2.2f), Tl.LAVA) }
            Biome.SWAMP -> { for (i in 0 until 3) mirrorBlob(ri(4, w / 2), ri(4, h - 5), rf(1.5f, 2.8f), Tl.WATER) }
            Biome.FACTORY -> {
                for (i in 0 until 2) {
                    val y = ri(3, h - 4); val x0 = ri(3, w / 2 - 6); val len = ri(4, 7); val dir = if (chance(0.5f)) 0 else 2
                    for (x in x0 until x0 + len) if (mp.t(x, y) == Tl.FLOOR) { mp.set(x, y, Tl.CONVEYOR); mp.setAux(x, y, dir); if (symmetric) { mp.set(w - 1 - x, h - 1 - y, Tl.CONVEYOR); mp.setAux(w - 1 - x, h - 1 - y, (dir + 2) % 4) } }
                }
                mirrorBlob(ri(4, w / 2 - 2), ri(4, h - 5), rf(1f, 1.6f), Tl.PAINT)
                mp.paintColor = listOf(0xFF35C6F4.toInt(), 0xFFFF5EC4.toInt(), 0xFFFFC23D.toInt()).pick()
            }
            Biome.SPACE -> { for (i in 0 until 2) mirrorBlob(ri(5, w / 2 - 2), ri(4, h - 5), rf(1f, 1.8f), Tl.VOID) }
            Biome.PARK -> { mirrorBlob(ri(4, w / 2), ri(4, h - 5), 1.5f, Tl.SPEED) }
            else -> {}
        }
    }

    fun decorate(m: Match, mp: TileMap, n: Int) {
        val kinds = when (mp.biome) {
            Biome.MEADOW -> intArrayOf(Decor.FLOWER, Decor.ROCK, Decor.MUSHROOM, Decor.PINE, Decor.BENCH, Decor.LAMP, Decor.STATUE, Decor.FLOWER)
            Biome.PARK -> intArrayOf(Decor.BALLOONS, Decor.PENNANT, Decor.NEON, Decor.STALL, Decor.BENCH, Decor.LAMP, Decor.VENDING, Decor.STATUE, Decor.BALLOONS)
            Biome.FACTORY -> intArrayOf(Decor.CONTAINER, Decor.TOYBLOCK, Decor.CONE, Decor.BARREL, Decor.CRATE, Decor.VENDING, Decor.GIFT)
            Biome.SNOW -> intArrayOf(Decor.PINE, Decor.PINE, Decor.SNOWMAN, Decor.ROCK, Decor.TENT, Decor.LAMP)
            Biome.BEACH -> intArrayOf(Decor.PALM, Decor.PALM, Decor.BOAT, Decor.ROCK, Decor.TENT, Decor.BALLOONS)
            Biome.KITCHEN -> intArrayOf(Decor.PLATE, Decor.FORK, Decor.POT, Decor.CUP, Decor.PLATE, Decor.CUP)
            Biome.SPACE -> intArrayOf(Decor.DISH, Decor.CONTAINER, Decor.TOWER, Decor.CRATE, Decor.NEON, Decor.CONE)
            Biome.VOLCANO -> intArrayOf(Decor.CRYSTAL, Decor.ROCK, Decor.CRYSTAL, Decor.DEADTREE, Decor.WRECK)
            Biome.SWAMP -> intArrayOf(Decor.DEADTREE, Decor.GRAVE, Decor.LANTERN, Decor.LANTERN, Decor.MUSHROOM, Decor.GRAVE)
        }
        var placed = 0; var tries = 0
        while (placed < n && tries < n * 12) {
            tries++
            val x = ri(2, mp.w - 3); val y = ri(2, mp.h - 3)
            val k = kinds.pick()
            val big = Decor.SOLID[k] > 30f
            if (!isOpenFloor(mp, x, y, if (big) 1 else 0)) continue
            if (Decor.SOLID[k] > 0f && !isOpenFloor(mp, x, y, 1)) continue
            m.addProp(Decor(m, tc(x) + rf(-10f, 10f), tc(y) + rf(-10f, 10f), k))
            placed++
        }
        // biome landmarks
        if (mp.biome == Biome.PARK && mp.w > 30) { val p = findOpen(mp, 2) ; if (p != null) m.addProp(Decor(m, tc(p[0]), tc(p[1]), Decor.FERRIS)) }
        if (mp.biome == Biome.BEACH) for (i in 0 until max(1, mp.w * mp.h / 500)) { val p = findOpen(mp, 1) ?: continue; val c = Mob(m, Mob.CRAB); c.x = tc(p[0]); c.y = tc(p[1]); m.addMob(c) }
    }

    fun findOpen(mp: TileMap, r: Int, x0: Int = 2, y0: Int = 2, x1: Int = mp.w - 3, y1: Int = mp.h - 3): IntArray? {
        for (i in 0 until 200) {
            val x = ri(x0, x1); val y = ri(y0, y1)
            if (isOpenFloor(mp, x, y, r)) return intArrayOf(x, y)
        }
        return null
    }

    fun interactives(m: Match, mp: TileMap, n: Int) {
        for (i in 0 until n) {
            val p = findOpen(mp, 1) ?: continue
            val x = tc(p[0]); val y = tc(p[1])
            when (ri(9)) {
                0 -> m.addProp(JumpPad(m, x, y))
                1, 2 -> m.addProp(Breakable(m, x, y, 0))
                3 -> m.addProp(Breakable(m, x, y, 1))
                4 -> m.addProp(Breakable(m, x, y, 2))
                5 -> m.addProp(Fan(m, x, y, ri(4) * 1.5708f, cycle = true))
                6 -> m.addProp(MouseTrap(m, x, y))
                7 -> m.addProp(Breakable(m, x, y, 3))
                8 -> if (mp.biome == Biome.FACTORY) m.addProp(Press(m, x, y, 3f, rf() * 3f)) else m.addProp(JumpPad(m, x, y))
            }
        }
        if (mp.biome == Biome.FACTORY) for (i in 0 until 3) { val p = findOpen(mp, 1) ?: continue; m.addProp(Press(m, tc(p[0]), tc(p[1]), 3f, rf() * 3f)) }
        if (mp.biome == Biome.SNOW) m.addProp(AvalancheSpawner(m))
        if (mp.biome == Biome.SPACE || mp.biome == Biome.FACTORY) {
            val a = findOpen(mp, 1); val b = findOpen(mp, 1)
            if (a != null && b != null && (abs(a[0] - b[0]) + abs(a[1] - b[1])) > 10) {
                val ta = Teleporter(m, tc(a[0]), tc(a[1]), 0xFF7CF0FF.toInt()); val tb = Teleporter(m, tc(b[0]), tc(b[1]), 0xFF7CF0FF.toInt())
                ta.partner = tb; tb.partner = ta; m.addProp(ta); m.addProp(tb)
            }
        }
    }

    fun spawnVehicles(m: Match, mp: TileMap, n: Int) {
        val kinds = when (mp.biome) {
            Biome.PARK -> intArrayOf(Vehicle.BUMPER, Vehicle.BUMPER, Vehicle.BALLOON, Vehicle.UNICYCLE)
            Biome.BEACH -> intArrayOf(Vehicle.DUCK, Vehicle.DUCK, Vehicle.UNICYCLE, Vehicle.BALLOON)
            Biome.FACTORY -> intArrayOf(Vehicle.CRANE, Vehicle.TANK, Vehicle.UNICYCLE)
            else -> intArrayOf(Vehicle.BUMPER, Vehicle.UNICYCLE, Vehicle.CRANE, Vehicle.BALLOON, Vehicle.TANK)
        }
        for (i in 0 until n) {
            val k = kinds.pick()
            if (k == Vehicle.DUCK) {
                // next to water
                var placed = false
                for (t in 0 until 200) {
                    val x = ri(2, mp.w - 3); val y = ri(2, mp.h - 3)
                    if (mp.t(x, y) == Tl.WATER) { m.addVehicle(Vehicle(m, k, tc(x), tc(y))); placed = true; break }
                }
                if (placed) continue
            }
            val p = findOpen(mp, 1) ?: continue
            m.addVehicle(Vehicle(m, if (k == Vehicle.DUCK) Vehicle.BUMPER else k, tc(p[0]), tc(p[1])))
        }
    }

    // ------------------------------------------------------------------ battle royale
    fun royale(m: Match, biome: Biome, size: Int): TileMap {
        val w = size; val h = (size * 0.8f).toInt()
        val mp = TileMap(w, h, biome)
        m.map = mp
        mp.rectWall(0, 0, w - 1, h - 1)
        // lakes
        val liquid = when (biome) { Biome.VOLCANO -> Tl.LAVA; Biome.SPACE -> Tl.VOID; Biome.SNOW -> Tl.ICE; else -> Tl.WATER }
        for (i in 0 until 3 + w / 30) blob(mp, ri(6, w - 7), ri(6, h - 7), rf(2.5f, 4.5f), liquid)
        if (biome == Biome.BEACH) { for (y in 1 until h - 1) for (x in 1 until 6 + (com.starguys.battle.core.sinf(y * 0.3f) * 2f).toInt()) mp.set(x, y, Tl.WATER) }
        // villages
        val nb = 6 + w / 16
        for (i in 0 until nb) {
            val bw = ri(5, 9); val bh = ri(4, 7)
            val bx = ri(3, w - bw - 4); val by = ri(3, h - bh - 4)
            var ok = true
            for (y in by - 1..by + bh + 1) for (x in bx - 1..bx + bw + 1) if (mp.t(x, y) != Tl.FLOOR) ok = false
            if (!ok) continue
            val wallT = if (chance(0.5f)) Tl.WALL else Tl.BOX
            mp.rectWall(bx, by, bx + bw, by + bh, wallT)
            // windows & doors
            for (k in 0 until 3) { val x = ri(bx + 1, bx + bw - 1); mp.set(x, if (chance(0.5f)) by else by + bh, if (chance(0.5f)) Tl.GLASS else Tl.FLOOR) }
            mp.set(bx + bw / 2, by + bh, Tl.FLOOR); mp.set(bx + bw / 2 + 1, by + bh, Tl.FLOOR)
            mp.set(bx, by + bh / 2, Tl.FLOOR); mp.set(bx + bw, by + bh / 2 + (if (chance(0.5f)) 0 else -1), Tl.FLOOR)
            // loot inside
            m.addProp(Breakable(m, tc(bx + 1 + ri(bw - 2)), tc(by + 1 + ri(bh - 2)), 3))
            if (chance(0.6f)) m.dropLoot(tc(bx + bw / 2), tc(by + bh / 2), 1)
            if (chance(0.3f)) m.addProp(LootMachine(m, tc(bx + 1), tc(by + 1) + 10f))
        }
        // cover clusters
        for (i in 0 until w * h / 40) {
            val x = ri(2, w - 3); val y = ri(2, h - 3)
            if (mp.t(x, y) != Tl.FLOOR) continue
            val tp = when (ri(6)) { 0, 1 -> Tl.WALL; 2, 3 -> Tl.BOX; 4 -> Tl.LOW; else -> Tl.GLASS }
            mp.set(x, y, tp); if (chance(0.5f) && mp.t(x + 1, y) == Tl.FLOOR) mp.set(x + 1, y, tp)
        }
        // forests
        for (i in 0 until w / 6) blob(mp, ri(3, w - 4), ri(3, h - 4), rf(1.5f, 3f), Tl.BUSH)
        biomeFeatures(m, mp, false)
        sealUnreachable(mp, w / 2, h / 2)
        decorate(m, mp, w * h / 45)
        interactives(m, mp, w * h / 110)
        for (i in 0 until 4) { val p = findOpen(mp, 1) ?: continue; m.addProp(LootMachine(m, tc(p[0]), tc(p[1]))) }
        // cannons & ziplines
        for (i in 0 until 2) {
            val a = findOpen(mp, 1) ?: continue; val b = findOpen(mp, 1) ?: continue
            m.addProp(Cannon(m, tc(a[0]), tc(a[1]), tc(b[0]), tc(b[1])))
        }
        for (i in 0 until 2) {
            val a = findOpen(mp, 1) ?: continue
            val dx = ri(-10, 10); val dy = ri(-8, 8)
            if (isOpenFloor(mp, a[0] + dx, a[1] + dy, 0) && abs(dx) + abs(dy) > 6) m.addProp(Zipline(m, tc(a[0]), tc(a[1]), tc(a[0] + dx), tc(a[1] + dy)))
        }
        if (biome == Biome.PARK) addCoaster(m, mp)
        spawnVehicles(m, mp, 5 + w / 12)
        // ground loot
        for (i in 0 until w * h / 55) { val p = findOpen(mp, 0) ?: continue; m.dropLoot(tc(p[0]), tc(p[1]), 1) }
        return mp
    }

    fun addCoaster(m: Match, mp: TileMap) {
        // rectangular rail loop on open ground
        for (t in 0 until 40) {
            val rw = ri(10, 16); val rh = ri(6, 9)
            val x0 = ri(3, mp.w - rw - 4); val y0 = ri(3, mp.h - rh - 4)
            var ok = true
            for (x in x0..x0 + rw) for (y in intArrayOf(y0, y0 + rh)) if (!mp.walkable(x, y) || mp.t(x, y) == Tl.WATER) ok = false
            for (y in y0..y0 + rh) for (x in intArrayOf(x0, x0 + rw)) if (!mp.walkable(x, y) || mp.t(x, y) == Tl.WATER) ok = false
            if (!ok) continue
            for (x in x0..x0 + rw) { mp.set(x, y0, Tl.RAIL); mp.set(x, y0 + rh, Tl.RAIL) }
            for (y in y0..y0 + rh) { mp.set(x0, y, Tl.RAIL); mp.set(x0 + rw, y, Tl.RAIL) }
            val path = floatArrayOf(tc(x0), tc(y0), tc(x0 + rw), tc(y0), tc(x0 + rw), tc(y0 + rh), tc(x0), tc(y0 + rh))
            val v = Vehicle(m, Vehicle.COASTER, tc(x0), tc(y0)); v.path = path
            m.addVehicle(v)
            return
        }
    }

    // ------------------------------------------------------------------ rising slime hills
    fun slimeHills(m: Match, biome: Biome): TileMap {
        val w = 40; val h = 30
        val mp = TileMap(w, h, biome)
        m.map = mp
        val hills = ArrayList<FloatArray>()
        hills.add(floatArrayOf(w / 2f + rf(-3f, 3f), h / 2f + rf(-2f, 2f), 7.5f, 9f))
        for (i in 0 until 5) hills.add(floatArrayOf(rf(5f, w - 6f), rf(5f, h - 6f), rf(3f, 5.5f), rf(4f, 7f)))
        for (y in 0 until h) for (x in 0 until w) {
            var e = 0f
            for (hl in hills) { val d = sqrt((x - hl[0]) * (x - hl[0]) + (y - hl[1]) * (y - hl[1])); e = max(e, hl[2] * (1f - d / hl[3])) }
            e += rf(-0.4f, 0.4f)
            mp.elev[mp.idx(x, y)] = e.toInt().coerceIn(0, 8).toByte()
        }
        // smooth so that every step is climbable with a jump
        for (pass in 0 until 6) for (y in 0 until h) for (x in 0 until w) {
            var mn = 99
            for (d in 0 until 4) { val nx = x + Nav.DX[d]; val ny = y + Nav.DY[d]; if (mp.inside(nx, ny)) mn = min(mn, mp.e(nx, ny)) }
            if (mp.e(x, y) > mn + 1) mp.elev[mp.idx(x, y)] = (mn + 1).toByte()
        }
        mp.rectWall(0, 0, w - 1, h - 1)
        for (i in 0 until 40) { val x = ri(2, w - 3); val y = ri(2, h - 3); if (mp.t(x, y) == Tl.FLOOR && mp.e(x, y) < 4) mp.set(x, y, if (chance(0.5f)) Tl.LOW else Tl.BOX) }
        for (i in 0 until 6) blob(mp, ri(3, w - 4), ri(3, h - 4), 1.3f, Tl.BUSH)
        sealUnreachable(mp, w / 2, h / 2)
        for (i in 0 until 6) { val x = ri(2, w - 3); val y = ri(2, h - 3); if (mp.t(x, y) == Tl.FLOOR) m.addProp(JumpPad(m, tc(x), tc(y), 1000f)) }
        for (i in 0 until 8) { val x = ri(2, w - 3); val y = ri(2, h - 3); if (mp.t(x, y) == Tl.FLOOR) m.addPickup(Pickup(m, Pickup.WEAPON, if (i < 4) Weapons.FOAM else Weapons.pickupable.pick(), tc(x), tc(y))) }
        // an elevator near the peak
        val px = hills[0][0].toInt(); val py = hills[0][1].toInt()
        m.addProp(MovingPlatform(m, tc(px) - 5 * T, tc(py), tc(px) - 5 * T, tc(py), T * 0.9f, T * 0.9f, 7f, 0f, mp.e(px, py) * LH))
        return mp
    }

    // ------------------------------------------------------------------ space station (zero-G)
    fun spaceStation(m: Match): TileMap {
        val w = 42; val h = 30
        val mp = TileMap(w, h, Biome.SPACE)
        m.map = mp
        mp.oobVoid = true
        for (i in mp.type.indices) mp.type[i] = Tl.VOID.toByte()
        val islands = listOf(intArrayOf(15, 10, 12, 10), intArrayOf(3, 3, 8, 6), intArrayOf(31, 3, 8, 6), intArrayOf(3, 21, 8, 6), intArrayOf(31, 21, 8, 6))
        for (isl in islands) mp.fill(isl[0], isl[1], isl[0] + isl[2] - 1, isl[1] + isl[3] - 1, Tl.FLOOR)
        // bridges
        fun bridge(x0: Int, y0: Int, x1: Int, y1: Int) { var x = x0; var y = y0; while (x != x1 || y != y1) { mp.set(x, y, Tl.FLOOR); if (x != x1) x += if (x1 > x) 1 else -1 else y += if (y1 > y) 1 else -1 }; mp.set(x1, y1, Tl.FLOOR) }
        for (o in -1..1) { bridge(10, 6 + o, 15, 12 + o); bridge(31, 6 + o, 26, 12 + o); bridge(10, 24 + o, 15, 18 + o); bridge(31, 24 + o, 26, 18 + o) }
        // cover
        for (isl in islands) for (k in 0 until 3) { val x = isl[0] + 1 + ri(isl[2] - 2); val y = isl[1] + 1 + ri(isl[3] - 2); mp.set(x, y, if (chance(0.5f)) Tl.LOW else Tl.BOX) }
        mp.set(20, 14, Tl.WALL); mp.set(21, 15, Tl.WALL)
        // moving platforms across the void
        m.addProp(MovingPlatform(m, tc(12), tc(15), tc(29), tc(15), T * 1.2f, T * 0.9f, 8f))
        m.addProp(MovingPlatform(m, tc(20), tc(3), tc(20), tc(26), T * 1.0f, T * 1.0f, 9f))
        for (isl in islands.drop(1)) { val x = isl[0] + isl[2] / 2; val y = isl[1] + isl[3] / 2; m.addProp(JumpPad(m, tc(x), tc(y), 700f)) }
        m.addProp(Decor(m, tc(18), tc(12), Decor.DISH)); m.addProp(Decor(m, tc(24), tc(18), Decor.TOWER))
        return mp
    }

    // ------------------------------------------------------------------ heist (4v4)
    fun heist(m: Match, biome: Biome): TileMap {
        val w = 46; val h = 28
        val mp = arena(m, biome, w, h, 0.5f, symmetric = false, hazards = false)
        // clear vault area & truck area
        mp.fill(33, 7, 43, 20, Tl.FLOOR); mp.fill(2, 9, 7, 18, Tl.FLOOR)
        mp.rectWall(34, 8, 43, 19, Tl.WALL)
        for (y in 12..15) mp.set(34, y, Tl.FLOOR)
        mp.set(38, 8, Tl.FLOOR); mp.set(39, 8, Tl.FLOOR); mp.set(38, 19, Tl.FLOOR); mp.set(39, 19, Tl.FLOOR)
        mp.set(36, 10, Tl.LOW); mp.set(41, 17, Tl.LOW); mp.set(41, 10, Tl.GLASS); mp.set(36, 17, Tl.GLASS)
        sealUnreachable(mp, w / 2, h / 2)
        m.props.removeAll { (it.x > tc(33) && it.y > tc(7) && it.y < tc(20)) || (it.x < tc(8) && it.y > tc(9) && it.y < tc(18)) }
        return mp
    }

    // ------------------------------------------------------------------ capture the flag duel
    fun ctf(m: Match, biome: Biome): TileMap {
        val w = 62; val h = 22
        val mp = TileMap(w, h, biome)
        m.map = mp
        mp.rectWall(0, 0, w - 1, h - 1)
        // arena cover in the middle
        for (i in 0 until 26) {
            val x = ri(22, 30); val y = ri(2, h - 3)
            val tp = if (chance(0.6f)) Tl.WALL else Tl.BOX
            mp.set(x, y, tp); mp.set(w - 1 - x, h - 1 - y, tp)
        }
        for (i in 0 until 4) { val x = ri(22, 29); val y = ri(3, h - 4); blob(mp, x, y, 1.4f, Tl.BUSH); blob(mp, w - 1 - x, h - 1 - y, 1.4f, Tl.BUSH) }
        // obstacle courses (left x 5..17, right mirrored)
        course(m, mp, 5, 17, false)
        course(m, mp, w - 18, w - 6, true)
        return mp
    }

    private fun course(m: Match, mp: TileMap, x0: Int, x1: Int, mirror: Boolean) {
        val h = mp.h
        for (y in 1 until h - 1) for (x in x0..x1) mp.set(x, y, Tl.VOID)
        val cy = h / 2
        // main path: 3 tiles wide with 1-tile gaps
        for (x in x0..x1) for (y in cy - 1..cy + 1) mp.set(x, y, Tl.FLOOR)
        val gaps = if (mirror) intArrayOf(x1 - 2, x1 - 7) else intArrayOf(x0 + 2, x0 + 7)
        for (g in gaps) for (y in cy - 1..cy + 1) mp.set(g, y, Tl.VOID)
        val mid = (x0 + x1) / 2
        m.addProp(Pendulum(m, tc(mid), tc(cy), 1.5708f, 1.6f * T, 2.1f))
        m.addProp(SpinBar(m, tc(if (mirror) x0 + 2 else x1 - 2), tc(cy), 1.5f * T, if (mirror) -1.4f else 1.4f))
        // side route: collapsing floor bridge
        for (x in x0..x1) { mp.set(x, cy - 5, Tl.COLLAPSE); mp.set(x, cy + 5, Tl.COLLAPSE) }
        for (y in cy - 5..cy - 2) { mp.set(x0, y, Tl.FLOOR); mp.set(x1, y, Tl.FLOOR) }
        for (y in cy + 2..cy + 5) { mp.set(x0, y, Tl.FLOOR); mp.set(x1, y, Tl.FLOOR) }
        m.addProp(MovingPlatform(m, tc(x0 + 2), tc(cy + 8), tc(x1 - 2), tc(cy + 8), T * 1.1f, T * 0.8f, 6f))
        for (y in cy + 6..cy + 8) { mp.set(x0, y, Tl.FLOOR); mp.set(x1, y, Tl.FLOOR) }
    }

    // ------------------------------------------------------------------ football
    fun football(m: Match, biome: Biome): TileMap {
        val w = 40; val h = 24
        val mp = TileMap(w, h, biome)
        m.map = mp
        mp.rectWall(0, 0, w - 1, h - 1)
        // goals: openings in side walls leading to goal boxes
        for (y in h / 2 - 3..h / 2 + 2) { mp.set(0, y, Tl.GOAL); mp.set(w - 1, y, Tl.GOAL) }
        // a few obstacles mirrored
        for (p in listOf(intArrayOf(10, 5), intArrayOf(10, 18), intArrayOf(15, 11))) {
            mp.set(p[0], p[1], Tl.LOW); mp.set(w - 1 - p[0], h - 1 - p[1], Tl.LOW)
        }
        blob(mp, 6, 3, 1.2f, Tl.BUSH); blob(mp, w - 7, h - 4, 1.2f, Tl.BUSH)
        m.addProp(JumpPad(m, tc(w / 2), tc(3))); m.addProp(JumpPad(m, tc(w / 2), tc(h - 4)))
        return mp
    }

    // ------------------------------------------------------------------ castle defense
    fun castle(m: Match, biome: Biome): TileMap {
        val w = 44; val h = 34
        val mp = arena(m, biome, w, h, 0.35f, symmetric = true, hazards = false)
        val cx = w / 2; val cy = h / 2
        mp.fill(cx - 9, cy - 7, cx + 9, cy + 7, Tl.FLOOR)
        m.props.removeAll { abs(it.x - tc(cx)) < 10 * T && abs(it.y - tc(cy)) < 8 * T }
        mp.rectWall(cx - 7, cy - 5, cx + 7, cy + 5, Tl.WALL)
        for (k in -1..0) { mp.set(cx + k, cy - 5, Tl.FLOOR); mp.set(cx + k, cy + 5, Tl.FLOOR); mp.set(cx - 7, cy + k, Tl.FLOOR); mp.set(cx + 7, cy + k, Tl.FLOOR) }
        return mp
    }

    // ------------------------------------------------------------------ escape (procedural run)
    fun escape(m: Match, seedChunks: Int = 12): TileMap {
        val cw = 11
        val w = cw * (seedChunks + 2) + 4; val h = 13
        val mp = TileMap(w, h, Biome.FACTORY)
        m.map = mp
        mp.rectWall(0, 0, w - 1, h - 1)
        val types = ArrayList<Int>()
        for (i in 0 until seedChunks) types.add(ri(10))
        for ((i, tp) in types.withIndex()) {
            val x0 = 2 + cw * (i + 1); val x1 = x0 + cw - 1
            val cy = h / 2
            when (tp) {
                0 -> { for (g in intArrayOf(x0 + 3, x0 + 7)) for (y in 1 until h - 1) mp.set(g, y, Tl.VOID) }
                1 -> { for (x in x0..x1) for (y in 3..h - 4) { mp.set(x, y, Tl.CONVEYOR); mp.setAux(x, y, 2) } }
                2 -> { for (k in 0 until 4) for (y in intArrayOf(cy - 2, cy, cy + 2)) m.addProp(Press(m, tc(x0 + 2 + k * 2), tc(y), 2.4f, k * 0.6f + y * 0.2f)) }
                3 -> { m.addProp(Pendulum(m, tc(x0 + 3), tc(cy), 1.5708f, 4f * T, 2f)); m.addProp(Pendulum(m, tc(x0 + 7), tc(cy), 1.5708f, 4f * T, 2.4f)) }
                4 -> { for (x in x0 + 1..x1 - 1) for (y in 1 until h - 1) mp.set(x, y, Tl.COLLAPSE) }
                5 -> { for (x in x0 + 1..x1 - 1) for (y in 1 until h - 1) mp.set(x, y, Tl.VOID)
                    m.addProp(MovingPlatform(m, tc(x0 + 2), tc(cy - 3), tc(x1 - 2), tc(cy - 3), T * 1.2f, T * 1.0f, 4.5f))
                    m.addProp(MovingPlatform(m, tc(x1 - 2), tc(cy + 3), tc(x0 + 2), tc(cy + 3), T * 1.2f, T * 1.0f, 5f)) }
                6 -> { mp.rectWall(x0 + 2, 1, x0 + 2, h - 2, Tl.BOX); mp.set(x0 + 2, cy, Tl.FLOOR)
                    for (k in 0 until 3) { val g = Mob(m, if (k == 0) Mob.TRASHBOT else Mob.GNOME); g.x = tc(x0 + 6 + k); g.y = tc(2 + ri(h - 4)); m.addMob(g) } }
                7 -> { m.addProp(Fan(m, tc(x0 + 5), tc(1) + 20f, 1.5708f, 10f * T, 1300f)); m.addProp(Fan(m, tc(x0 + 8), tc(h - 2) - 20f, -1.5708f, 10f * T, 1300f)) }
                8 -> m.addProp(SpinBar(m, tc(x0 + 5), tc(cy), 4.6f * T, 1.3f, 2))
                9 -> { for (x in x0 + 1..x0 + 4) for (y in 1 until h - 1) mp.set(x, y, Tl.FLOOR, 1); for (x in x0 + 5..x0 + 8) for (y in 1 until h - 1) mp.set(x, y, Tl.FLOOR, 2)
                    for (y in 1 until h - 1) mp.set(x0 + 9, y, Tl.FLOOR, 1)
                    m.addProp(JumpPad(m, tc(x0), tc(cy - 2))); m.addProp(JumpPad(m, tc(x0), tc(cy + 2))) }
            }
            if (chance(0.4f)) m.addPickup(Pickup(m, Pickup.ITEM, ri(4), tc(x1), tc(1 + ri(h - 3))))
        }
        // exit
        for (y in 1 until h - 1) for (x in w - 4 until w - 1) mp.set(x, y, Tl.GOAL)
        return mp
    }

    // ------------------------------------------------------------------ raid dungeon
    fun raid(m: Match): TileMap {
        val rw = 17; val rh = 15; val cor = 4
        val w = rw * 4 + cor * 3 + 2; val h = rh + 2
        val mp = TileMap(w, h, Biome.VOLCANO)
        m.map = mp
        for (i in mp.type.indices) mp.type[i] = Tl.WALL.toByte()
        val biomes = arrayOf(Biome.FACTORY, Biome.PARK, Biome.KITCHEN, Biome.VOLCANO)
        for (r in 0 until 4) {
            val x0 = 1 + r * (rw + cor)
            mp.fill(x0, 1, x0 + rw - 1, rh, Tl.FLOOR)
            for (k in 0 until 6) { val x = x0 + 2 + ri(rw - 4); val y = 2 + ri(rh - 3); if (abs(x - (x0 + rw / 2)) > 2 || abs(y - h / 2) > 2) mp.set(x, y, if (chance(0.5f)) Tl.LOW else Tl.BOX) }
            if (r < 3) {
                val cx0 = x0 + rw
                for (x in cx0 until cx0 + cor) for (y in h / 2 - 1..h / 2 + 1) mp.set(x, y, Tl.FLOOR)
                for (y in h / 2 - 1..h / 2 + 1) mp.set(cx0 + cor / 2, y, Tl.DOOR)
            }
            m.addProp(Decor(m, tc(x0 + 1), tc(1), if (r == 3) Decor.CRYSTAL else Decor.LAMP))
            m.addProp(Decor(m, tc(x0 + rw - 2), tc(1), if (r == 3) Decor.CRYSTAL else Decor.LAMP))
        }
        if (biomes.isNotEmpty()) mp.biome = Biome.VOLCANO
        return mp
    }

    // ------------------------------------------------------------------ prop hunt
    fun propHunt(m: Match, biome: Biome): TileMap {
        val mp = arena(m, biome, 36, 26, 0.6f, symmetric = false, hazards = false)
        for (i in 0 until 70) {
            val p = findOpen(mp, 0) ?: continue
            m.addProp(Decor(m, tc(p[0]) + rf(-12f, 12f), tc(p[1]) + rf(-12f, 12f), Decor.DISGUISES.pick()))
        }
        return mp
    }
}

/** Snow biome: giant snowballs roll across the map periodically. */
class AvalancheSpawner(m: Match) : Prop(m, -500f, -500f) {
    var next = 9f
    override fun update(dt: Float) {
        next -= dt
        if (next > 0f) return
        next = rf(8f, 13f)
        val horiz = chance(0.6f)
        if (horiz) {
            val y = MapGen.tc(ri(2, m.map.h - 3))
            val left = chance(0.5f)
            m.addProp(Snowball(m, if (left) -60f else m.map.pxW + 60f, y, if (left) 520f else -520f, 0f))
        } else {
            val x = MapGen.tc(ri(2, m.map.w - 3))
            m.addProp(Snowball(m, x, -60f, 0f, 480f))
        }
        m.hud.big("AVALANCHE !", 0xFFBFE8FF.toInt(), 1.5f)
        m.sound(com.starguys.battle.core.Sfx.THUD, m.cam.x, m.cam.y)
    }
}
