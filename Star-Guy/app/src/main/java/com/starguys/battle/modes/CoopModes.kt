package com.starguys.battle.modes

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.chance
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.core.sinf
import com.starguys.battle.game.Body
import com.starguys.battle.game.Boss
import com.starguys.battle.game.CastleCore
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.GameMode
import com.starguys.battle.game.Gadgets
import com.starguys.battle.game.Goal
import com.starguys.battle.game.Hud
import com.starguys.battle.game.MapGen
import com.starguys.battle.game.Match
import com.starguys.battle.game.Mob
import com.starguys.battle.game.Portal
import com.starguys.battle.game.T
import com.starguys.battle.game.Tl
import com.starguys.battle.game.ZS

// ============================================================================ Castle defense (tower defense)
class CastleMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 5f
    lateinit var core: CastleCore
    private val portals = ArrayList<Portal>()
    var wave = 0
    val maxWave = 10
    private var buildT = 12f
    private var toSpawn = ArrayList<Int>()
    private var spawnT = 0f
    private val bricks = HashMap<Fighter, Int>()

    override fun build() {
        val biome = Modes.pickBiome(m, com.starguys.battle.game.Biome.SPACE)
        val mp = MapGen.castle(m, biome)
        val cx = MapGen.tc(mp.w / 2) - T / 2; val cy = MapGen.tc(mp.h / 2) - T / 2
        core = CastleCore(m, cx, cy); m.addProp(core)
        for (p in listOf(floatArrayOf(2.5f, 2.5f), floatArrayOf(mp.w - 3.5f, 2.5f), floatArrayOf(2.5f, mp.h - 3.5f), floatArrayOf(mp.w - 3.5f, mp.h - 3.5f))) {
            val tx = p[0].toInt(); val ty = p[1].toInt()
            mp.fill(tx - 1, ty - 1, tx + 1, ty + 1, Tl.FLOOR)
            val po = Portal(m, MapGen.tc(tx), MapGen.tc(ty)); portals.add(po); m.addProp(po)
        }
        m.props.removeAll { it !is Portal && it !is CastleCore && portals.any { po -> dist(po.x, po.y, it.x, it.y) < 2.5f * T } }
        m.spawnPlayer(0, cx + 2 * T, cy)
        m.spawnBots(3, { 0 }, { i -> floatArrayOf(cx + cosf(i * 2.1f) * 2.5f * T, cy + sinf(i * 2.1f) * 2.5f * T) })
        giveBuild()
        m.hud.big("CONSTRUIS TES DÉFENSES !", 0xFFFFD23F.toInt(), 3f)
    }

    private fun giveBuild() {
        for (f in m.fighters) { bricks[f] = 6; if (f.gadget == null) { f.gadget = Gadgets.list[Gadgets.TURRET]; f.gadgetCharges = 1 } }
    }

    override fun respawnPoint(f: Fighter) = floatArrayOf(core.x + rf(-2f * T, 2f * T), core.y + 1.5f * T)

    override fun mobTarget(mob: Mob): Body? {
        if (mob.isBoss) return null
        val nf = m.nearestFighter(mob.x, mob.y, 3f * T)
        return nf ?: (if (core.alive) core else null)
    }

    override fun update(dt: Float) {
        if (!core.alive) { m.finish(false, "Le château est tombé…"); return }
        if (buildT > 0f) {
            buildT -= dt
            // bots build barricades near gates
            for (f in m.fighters) if (!f.human && !f.out && (bricks[f] ?: 0) > 0 && chance(dt * 0.6f)) {
                val gi = ri(4); val gx = core.x + floatArrayOf(0f, 0f, -7f * T, 7f * T)[gi]; val gy = core.y + floatArrayOf(-5f * T, 5f * T, 0f, 0f)[gi]
                placeBrick(f, gx + rf(-2f * T, 2f * T), gy + rf(-2f * T, 2f * T))
            }
            if (buildT <= 0f) startWave()
            return
        }
        spawnT -= dt
        if (toSpawn.isNotEmpty() && spawnT <= 0f) {
            spawnT = maxOf(0.35f, 1.3f - wave * 0.08f)
            val k = toSpawn.removeAt(0)
            val po = portals[ri(portals.size)]
            val mob = if (k >= 10) Boss(m, k) else Mob(m, k)
            mob.x = po.x + rf(-20f, 20f); mob.y = po.y + rf(-20f, 20f)
            if (k >= 10) mob.scaleHp(1.3f) else mob.scaleHp(1f + wave * 0.08f)
            m.addMob(mob)
        }
        if (toSpawn.isEmpty() && m.mobs.none { it.team == com.starguys.battle.game.TEAM_MOBS }) {
            if (wave >= maxWave) { m.finish(true, "Le château tient bon !"); return }
            buildT = 15f; giveBuild()
            m.hud.big("VAGUE $wave REPOUSSÉE ! RECONSTRUIS !", 0xFF7CFF8A.toInt(), 2.5f)
            core.hp = minOf(core.maxHp, core.hp + 2000f)
        }
    }

    private fun startWave() {
        wave++
        toSpawn.clear()
        repeat(2 + wave) { toSpawn.add(Mob.GLUANT) }
        repeat(wave / 2) { toSpawn.add(Mob.DRONE) }
        repeat(wave / 3) { toSpawn.add(Mob.TRASHBOT) }
        repeat((wave + 1) / 2) { toSpawn.add(Mob.GNOME) }
        toSpawn.shuffle(com.starguys.battle.core.RNG)
        if (wave == 5) toSpawn.add(Mob.BOSS_HAMMER)
        if (wave == maxWave) toSpawn.add(Mob.BOSS_JELLY)
        m.hud.big("VAGUE $wave !", 0xFFFF5E7E.toInt(), 2f)
        Sfx.play(Sfx.ALARM)
    }

    private fun placeBrick(f: Fighter, x: Float, y: Float): Boolean {
        val n = bricks[f] ?: 0
        if (n <= 0) return false
        val tx = (x / T).floorInt(); val ty = (y / T).floorInt()
        if (m.map.t(tx, ty) != Tl.FLOOR && m.map.t(tx, ty) != Tl.BUSH) return false
        if (m.bodyOnTile(tx, ty)) return false
        if (dist(MapGen.tc(tx), MapGen.tc(ty), core.x, core.y) < 1.6f * T) return false
        m.map.set(tx, ty, Tl.BRICK)
        bricks[f] = n - 1
        m.fx.smoke(MapGen.tc(tx), MapGen.tc(ty), 3, 0xFFE07B4A.toInt()); m.sound(Sfx.THUD, x, y, 0.6f)
        return true
    }

    override fun interactLabel(f: Fighter): String? = if (buildT > 0f && (bricks[f] ?: 0) > 0) "Barricade (${bricks[f]})" else null
    override fun interact(f: Fighter): Boolean = placeBrick(f, f.x + cosf(f.face) * T * 1.1f, f.y + sinf(f.face) * T * 1.1f)

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        if (dist(f.x, f.y, core.x, core.y) > 6f * T) return g.set(core.x + rf(-3f * T, 3f * T), core.y + rf(-3f * T, 3f * T), 0.6f)
        return false
    }

    override fun onMobDeath(mob: Mob, killer: Fighter?) { if (killer != null) bricks[killer] = (bricks[killer] ?: 0) + if (chance(0.3f)) 1 else 0 }
    override fun ranking(): List<Fighter> = m.fighters.sortedByDescending { it.score }
    override fun scoreText(f: Fighter) = "${f.score.toInt()} ennemis"
    override fun playerWon() = m.won

    override fun drawHud(c: Canvas, h: Hud) {
        if (buildT > 0f) h.topPill(c, "Construction : ${buildT.toInt() + 1} s   ·   Prochaine vague ${wave + 1}/$maxWave", 0xCC6B4A10.toInt())
        else h.topPill(c, "Vague $wave/$maxWave   ·   Ennemis : ${m.mobs.size + toSpawn.size}", 0xCC2A2140.toInt())
        h.topText(c, "Château : ${core.hp.toInt()} PV", 2, 0xFF7CFF8A.toInt(), 20f)
    }

    override fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) {
        G.circle(c, mx + core.x * s, my + core.y * s, 5f, 0xFF7CFF8A.toInt(), false)
        for (mb in m.mobs) G.circle(c, mx + mb.x * s, my + mb.y * s, 2f, 0xFFFF7A3D.toInt(), false)
    }
}

// ============================================================================ Escape (procedural run from a self-destructing base)
class EscapeMode(m: Match) : GameMode(m) {
    override val autoHeal = true
    override val fixedBiome = com.starguys.battle.game.Biome.FACTORY
    var waveX = -3f * T
    private var waveSpeed = 55f
    var countdown = 130f
    private var order = 0

    override fun build() {
        val mp = MapGen.escape(m, 11 + ri(3))
        val y = MapGen.tc(mp.h / 2)
        m.spawnPlayer(0, MapGen.tc(4), y)
        m.spawnBots(3, { 0 }, { i -> floatArrayOf(MapGen.tc(3 + i % 2), y + (i - 1) * T) })
        m.hud.big("AUTODESTRUCTION ! FONCE VERS LA SORTIE →", 0xFFFF3D3D.toInt(), 3f)
        Sfx.play(Sfx.ALARM)
    }

    override fun saveFromFall(f: Fighter): Boolean {
        // fall = respawn a bit back (if still ahead of the explosion)
        val mp = m.map
        var tx = (maxOf(f.safeX, waveX + 2f * T) / T).toInt()
        val ty0 = (f.safeY / T).toInt()
        while (tx < mp.w - 1) {
            for (dy in intArrayOf(0, -1, 1, -2, 2, -3, 3, -4, 4)) {
                val ty = ty0 + dy
                if (mp.walkable(tx, ty) && mp.t(tx, ty) != Tl.COLLAPSE && mp.groundZ(tx, ty) > -100f) { f.safeX = MapGen.tc(tx); f.safeY = MapGen.tc(ty); return true }
            }
            tx++
        }
        return false
    }

    override fun update(dt: Float) {
        countdown -= dt
        if (countdown < 125f) { waveX += waveSpeed * dt; waveSpeed += dt * 1.1f }
        for (f in m.fighters) {
            if (f.out || f.finished) continue
            if (f.x < waveX) { f.hurt(99999f, m.envBody, 0f, 0f) }
            if (m.map.t(f.tileX(), f.tileY()) == Tl.GOAL) {
                f.finished = true; f.out = true; order++; f.place = order; f.score = countdown
                m.fx.confetti(f.x, f.y - 40f, 40); Sfx.play(Sfx.PICKUP)
                m.hud.feed("${f.name} s'est échappé ! (#$order)", 0xFF7CFF8A.toInt())
                if (f.isLocal) m.hud.big("ÉCHAPPÉ !", 0xFF7CFF8A.toInt(), 2f)
            }
        }
        for (mb in m.mobs) if (mb.x < waveX) mb.hurt(99999f, m.envBody, 0f, 0f)
        if (com.starguys.battle.core.chance(dt * 3f)) m.cam.shake(2f)
        val p = m.player
        val running = m.fighters.count { !it.out && !it.finished }
        if (countdown <= 0f) {
            for (f in m.fighters) if (!f.out) { f.hurt(99999f, m.envBody, 0f, 0f) }
            m.finish(p?.finished == true, if (p?.finished == true) "Tu t'es échappé !" else "BOUM ! La base a explosé")
            return
        }
        if (running == 0) m.finish(p?.finished == true, if (p?.finished == true) "Tu t'es échappé !" else "Tu n'as pas pu fuir…")
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (it.finished) 0 else 1 }, { if (it.finished) it.place else 0 }, { -it.x }))
    override fun playerWon() = m.player?.finished == true
    override fun scoreText(f: Fighter) = if (f.finished) "Échappé #${f.place}" else "Pris dans l'explosion"

    override fun botGoal(f: Fighter, g: Goal): Boolean = g.set(m.map.pxW - 2f * T, f.y.coerceIn(2f * T, m.map.pxH - 2f * T), 1f)

    override fun drawOver(c: Canvas) {
        // explosion wall
        val x = waveX
        G.rect(c, x - 2000f, -200f, x, m.map.pxH + 200f, 0xCCFF5E3D.toInt(), false)
        for (k in 0 until 14) {
            val yy = (k / 13f) * m.map.pxH
            val r = 40f + sinf(m.time * 9f + k) * 14f
            G.circle(c, x + sinf(m.time * 5f + k * 2f) * 16f, yy, r, if (k % 2 == 0) 0xFFFFD23F.toInt() else 0xFFFF7A3D.toInt(), false)
        }
    }

    override fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) { G.rect(c, mx, my, mx + maxOf(0f, waveX) * s, my + m.map.pxH * s, 0xAAFF5E3D.toInt(), false) }

    override fun drawHud(c: Canvas, h: Hud) {
        val p = m.player ?: return
        val prog = ((p.x / m.map.pxW) * 100).toInt().coerceIn(0, 100)
        h.topPill(c, "AUTODESTRUCTION : ${countdown.toInt()} s   ·   Progression $prog%", 0xCCB33030.toInt())
    }
}

// ============================================================================ Multi-boss raid
class RaidMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 8f
    private val rw = 17; private val cor = 4
    var room = -1
    private var bossAlive = false
    private val bossKinds = intArrayOf(Mob.BOSS_HAMMER, Mob.BOSS_CLAW, Mob.BOSS_CHEF, Mob.BOSS_JELLY)
    private var cleared = 0

    override fun build() {
        val mp = MapGen.raid(m)
        val y = MapGen.tc(mp.h / 2)
        m.spawnPlayer(0, MapGen.tc(3), y)
        m.spawnBots(3, { 0 }, { i -> floatArrayOf(MapGen.tc(2 + i), y + (i - 1) * T) })
        m.hud.big("SALLE 1 : MÉGA-MARTEAU", 0xFFFF7A3D.toInt(), 3f)
    }

    private fun roomX0(r: Int) = 1 + r * (rw + cor)

    override fun respawnPoint(f: Fighter): FloatArray {
        val r = maxOf(0, room)
        return floatArrayOf(MapGen.tc(roomX0(r) + 1), MapGen.tc(m.map.h / 2))
    }

    override fun update(dt: Float) {
        val alive = m.fighters.filter { !it.out }
        if (alive.isEmpty()) { m.finish(false, "L'équipe est tombée…"); return }
        if (!bossAlive) {
            val next = room + 1
            if (next >= 4) return
            val x0 = MapGen.tc(roomX0(next)) + T
            if (alive.any { it.x > x0 }) startRoom(next)
        } else if (m.mobs.none { it.isBoss }) {
            bossAlive = false; cleared++
            m.mobs.forEach { it.hurt(99999f, m.envBody, 0f, 0f) }
            if (room >= 3) { m.finish(true, "Le donjon est vaincu !"); return }
            val dx = roomX0(room) + rw + cor / 2
            for (yy in 0 until m.map.h) if (m.map.t(dx, yy) == Tl.DOOR) m.map.set(dx, yy, Tl.FLOOR)
            m.hud.big("PORTE OUVERTE ! SALLE ${room + 2}", 0xFF7CFF8A.toInt(), 2.5f)
            Sfx.play(Sfx.WIN, 0.6f)
            for (f in m.fighters) if (f.out) { f.respawnT = 0.1f }
        }
    }

    private fun startRoom(r: Int) {
        room = r; bossAlive = true
        val b = Boss(m, bossKinds[r])
        b.x = MapGen.tc(roomX0(r) + rw - 5); b.y = MapGen.tc(m.map.h / 2)
        val party = m.fighters.size
        b.scaleHp(if (r == 3) 4.2f else 2.4f + party * 0.25f)
        m.addMob(b)
        for (k in 0 until 2 + r) { val mob = Mob(m, if (k % 2 == 0) Mob.GLUANT else Mob.GNOME, 1); mob.x = MapGen.tc(roomX0(r) + rw - 3); mob.y = MapGen.tc(2 + ri(m.map.h - 4)); m.addMob(mob) }
        m.hud.big(b.name.uppercase(), 0xFFFF7A3D.toInt(), 2.5f)
        Sfx.play(Sfx.ALARM)
    }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        val b = m.boss
        if (b != null && b.alive) return false
        val next = (room + 1).coerceAtMost(3)
        return g.set(MapGen.tc(roomX0(next) + rw / 2), MapGen.tc(m.map.h / 2) + rf(-T, T), 0.9f)
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedByDescending { it.dmgDone }
    override fun scoreText(f: Fighter) = "${(f.dmgDone / 1000f).toInt()}k dégâts"
    override fun playerWon() = m.won

    override fun drawHud(c: Canvas, h: Hud) {
        h.topPill(c, "Raid : salle ${maxOf(1, room + 1)}/4   ·   Boss vaincus : $cleared", 0xCC4A1530.toInt())
    }
}

@Suppress("unused") private fun keepCoop() { ZS; GameMode::class }
