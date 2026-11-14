package com.starguys.battle.modes

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Save
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.chance
import com.starguys.battle.core.dist
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.GameMode
import com.starguys.battle.game.Goal
import com.starguys.battle.game.Hud
import com.starguys.battle.game.LH
import com.starguys.battle.game.MapGen
import com.starguys.battle.game.Match
import com.starguys.battle.game.T
import com.starguys.battle.game.Tl
import com.starguys.battle.game.VOID_Z
import com.starguys.battle.game.Zone
import kotlin.math.sqrt

/** Battle royale (and its meteor-shower variant). */
class RoyaleMode(m: Match, val meteors: Boolean) : GameMode(m) {
    override val dropOnDeath = true
    private var phase = 0
    private var phaseT = 30f
    private var shrinking = false
    private var fromR = 0f; private var toR = 0f; private var fromX = 0f; private var fromY = 0f; private var toX = 0f; private var toY = 0f
    private var shrinkT = 0f
    private var meteorT = 6f
    private var aliveCount = 0
    private val radii = floatArrayOf(0.62f, 0.4f, 0.24f, 0.12f, 0.03f)

    override fun build() {
        val biome = Modes.pickBiome(m)
        val n = if (meteors) 16 else m.cfg.brSize.coerceIn(10, 40)
        val size = if (meteors) 46 else (36 + n).coerceAtMost(72)
        MapGen.royale(m, biome, size)
        val p = m.randomSpawn(6f * T)
        m.spawnPlayer(0, p[0], p[1])
        m.spawnBots(n - 1, { it + 1 }, { m.randomSpawn(6f * T) })
        for (f in m.fighters) { f.z += 900f + rf(0f, 200f); f.gravScale = 0.1f; f.onGround = false; f.parachutes = 0 }
        m.stormX = m.map.pxW / 2; m.stormY = m.map.pxH / 2
        m.stormR = sqrt(m.map.pxW * m.map.pxW + m.map.pxH * m.map.pxH.toDouble()).toFloat() / 2f + T * 2
        if (meteors) phaseT = 25f
        m.hud.big(if (meteors) "PLUIE DE MÉTÉORITES !" else "ATTERRIS OÙ TU VEUX !", 0xFFFFE14D.toInt(), 3f)
    }

    override fun afterMove(f: Fighter, dt: Float) {
        if (f.gravScale < 1f && (f.onGround || f.z <= f.gz + 1f)) f.gravScale = 1f
    }

    override fun update(dt: Float) {
        // storm phases
        if (!shrinking) {
            phaseT -= dt
            if (phaseT <= 0f && phase < radii.size) {
                shrinking = true; shrinkT = 0f
                fromR = m.stormR; fromX = m.stormX; fromY = m.stormY
                val full = maxOf(m.map.pxW, m.map.pxH) / 2f
                toR = full * radii[phase]
                val slack = fromR - toR
                val a = rf() * 6.28f; val d = rf() * slack * 0.5f
                toX = (fromX + com.starguys.battle.core.cosf(a) * d).coerceIn(toR * 0.5f, m.map.pxW - toR * 0.5f)
                toY = (fromY + com.starguys.battle.core.sinf(a) * d).coerceIn(toR * 0.5f, m.map.pxH - toR * 0.5f)
                m.hud.big("LA ZONE RÉTRÉCIT !", 0xFFE0B0FF.toInt(), 2f)
                Sfx.play(Sfx.ALARM, 0.6f)
            }
        } else {
            shrinkT += dt
            val k = (shrinkT / 22f).coerceIn(0f, 1f)
            m.stormR = fromR + (toR - fromR) * k
            m.stormX = fromX + (toX - fromX) * k; m.stormY = fromY + (toY - fromY) * k
            if (k >= 1f) { shrinking = false; phase++; phaseT = if (meteors) 22f else 32f }
        }
        val dps = 250f + phase * 220f
        aliveCount = 0
        for (f in m.fighters) {
            if (f.out) continue
            aliveCount++
            if (dist(f.x, f.y, m.stormX, m.stormY) > m.stormR) f.hurt(dps * dt, m.envBody, 0f, 0f)
        }
        if (meteors) {
            meteorT -= dt
            if (meteorT <= 0f) {
                meteorT = rf(0.4f, 1.1f)
                val alive = m.fighters.filter { !it.out }
                val tg = if (alive.isNotEmpty() && chance(0.6f)) alive[ri(alive.size)] else null
                val x = (tg?.x ?: (m.stormX + rf(-m.stormR, m.stormR))) + rf(-3f * T, 3f * T)
                val y = (tg?.y ?: (m.stormY + rf(-m.stormR, m.stormR))) + rf(-3f * T, 3f * T)
                m.addZone(Zone(m, Zone.METEOR, x.coerceIn(T, m.map.pxW - T), y.coerceIn(T, m.map.pxH - T), rf(1.1f, 1.8f) * T, 1.8f, null, com.starguys.battle.game.TEAM_NEUTRAL))
                if (chance(0.3f)) Sfx.play(Sfx.METEOR, 0.4f)
            }
        }
        if (aliveCount <= 1) {
            val w = m.fighters.firstOrNull { !it.out }
            m.finish(w != null && w === m.player, if (w != null) "${w.name} remporte la partie !" else "Égalité !")
        }
    }

    override fun onDeath(f: Fighter, killer: Fighter?) {
        f.place = m.fighters.count { !it.out } + 1
    }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        val d = dist(f.x, f.y, m.stormX, m.stormY)
        val nextR = if (shrinking) toR else m.stormR * 0.85f
        if (d > m.stormR - T) return g.set(m.stormX + rf(-T, T), m.stormY + rf(-T, T), 1f)
        if (d > nextR && (shrinking || phaseT < 10f)) return g.set(if (shrinking) toX else m.stormX, if (shrinking) toY else m.stormY, 0.75f)
        return false
    }

    override fun drawHud(c: Canvas, h: Hud) {
        val alive = m.fighters.count { !it.out }
        val p = m.player
        val txt = if (shrinking) "Zone en mouvement" else "Zone : ${phaseT.toInt()} s"
        h.topPill(c, "Vivants : $alive   ·   $txt", 0xCC2A2140.toInt())
        if (p != null) h.topText(c, "K.O. : ${p.kills}", 2, 0xFFFFF3B0.toInt(), 20f)
        if (p != null && !p.out && dist(p.x, p.y, m.stormX, m.stormY) > m.stormR) h.topText(c, "HORS DE LA ZONE !", 4, 0xFFE0B0FF.toInt(), 30f)
    }
}

/** The map fills up with rising slime: climb! */
class RisingSlimeMode(m: Match) : GameMode(m) {
    override val shotKnock = 0.75f
    override fun damageTakenMul(f: Fighter) = 0.12f
    private var delay = 20f
    private var rate = LH / 9f
    private var tops = ArrayList<IntArray>()

    override fun build() {
        val biome = Modes.pickBiome(m, Biome.SPACE, Biome.SWAMP)
        val mp = MapGen.slimeHills(m, biome)
        mp.slimeZ = -30f
        val sp = m.randomSpawn(4f * T)
        m.spawnPlayer(0, sp[0], sp[1])
        m.spawnBots(11, { it + 1 }, { m.randomSpawn(4f * T) })
        for (y in 0 until mp.h) for (x in 0 until mp.w) if (mp.walkable(x, y) && mp.e(x, y) >= 4) tops.add(intArrayOf(x, y, mp.e(x, y)))
        tops.sortByDescending { it[2] }
        m.hud.big("GRIMPE ! LE SLIME MONTE !", 0xFF7BE84A.toInt(), 3f)
    }

    override fun update(dt: Float) {
        val mp = m.map
        if (delay > 0f) delay -= dt else { mp.slimeZ += rate * dt; rate += dt * 0.07f }
        for (f in m.fighters) {
            if (f.out) continue
            val inSlime = f.z < mp.slimeZ - 2f && f.vehicle?.kind != com.starguys.battle.game.Vehicle.BALLOON
            if (inSlime) {
                f.slowT = 0.3f; f.slowAmt = 0.6f
                f.hurt(950f * dt, m.envBody, 0f, 0f)
                if (chance(0.3f)) m.fx.splat(f.x, f.y - 10f, 0xFF7BE84A.toInt())
            }
        }
        val alive = m.fighters.filter { !it.out }
        if (alive.size <= 1) {
            val w = alive.firstOrNull()
            m.finish(w === m.player && w != null, if (w != null) "${w.name} est le dernier en hauteur !" else "Le slime a tout englouti !")
        }
    }

    override fun onDeath(f: Fighter, killer: Fighter?) { f.place = m.fighters.count { !it.out } + 1 }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        val slime = m.map.slimeZ
        if (tops.isEmpty()) return false
        val urgency = if (f.z - slime < LH * 2.5f) 1f else 0.8f
        // pick a high tile near us
        var best: IntArray? = null; var bs = -1e9f
        for (t in tops.take(60)) {
            val d = dist(f.x, f.y, MapGen.tc(t[0]), MapGen.tc(t[1]))
            val s = t[2] * 3f - d / T * 0.35f
            if (s > bs) { bs = s; best = t }
        }
        val b = best ?: return false
        return g.set(MapGen.tc(b[0]), MapGen.tc(b[1]), urgency)
    }

    override fun drawHud(c: Canvas, h: Hud) {
        val alive = m.fighters.count { !it.out }
        val lvl = (m.map.slimeZ / LH).coerceAtLeast(0f)
        h.topPill(c, "Vivants : $alive   ·   Slime : niveau ${"%.1f".format(lvl)}", 0xCC2A5A20.toInt())
        val p = m.player
        if (p != null && !p.out) h.topText(c, "Ta hauteur : ${"%.1f".format(p.z / LH)}", 2, 0xFFD8FFB0.toInt(), 20f)
    }
}

/** Zero gravity on a space station: knockback grows with damage, ring-outs only. */
class ZeroGMode(m: Match) : GameMode(m) {
    override val percentMode = true
    override val shotKnock = 0.12f
    override val autoHeal = false
    override val respawns = true
    override val respawnDelay = 2f
    override val timeLimit = 240f
    override val fixedBiome = Biome.SPACE

    override fun build() {
        MapGen.spaceStation(m)
        m.zeroG = true
        val spots = listOf(intArrayOf(19, 13), intArrayOf(23, 16), intArrayOf(6, 5), intArrayOf(35, 5), intArrayOf(6, 23), intArrayOf(35, 23), intArrayOf(17, 17), intArrayOf(25, 11))
        m.spawnPlayer(0, MapGen.tc(spots[0][0]), MapGen.tc(spots[0][1]))
        m.spawnBots(7, { it + 1 }, { i -> floatArrayOf(MapGen.tc(spots[i + 1][0]), MapGen.tc(spots[i + 1][1])) })
        for (f in m.fighters) f.stocks = 3
        m.hud.big("ÉJECTE-LES DANS LE VIDE !", 0xFF7CF0FF.toInt(), 3f)
    }

    override fun respawnPoint(f: Fighter): FloatArray = floatArrayOf(MapGen.tc(19 + ri(4)), MapGen.tc(12 + ri(5)))

    override fun onDeath(f: Fighter, killer: Fighter?) {
        f.stocks--
        f.dmgPct = 0f
        if (f.stocks <= 0) { f.respawnT = -1f; f.place = m.fighters.count { !it.out || it.stocks > 0 } + 1 }
        Sfx.play(Sfx.METEOR, 0.5f)
    }

    override fun update(dt: Float) {
        val inGame = m.fighters.filter { it.stocks > 0 }
        if (inGame.size <= 1) { val w = inGame.firstOrNull(); m.finish(w === m.player && w != null, if (w != null) "${w.name} reste en orbite !" else "Égalité"); return }
        if (tickTimer(dt)) { val r = ranking(); m.finish(r.firstOrNull() === m.player, "Temps écoulé !") }
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ -it.stocks }, { it.dmgPct }, { -it.kills }))

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        // stay away from edges
        val mp = m.map
        var edge = false
        for (dy in -2..2) for (dx in -2..2) if (mp.groundZ(f.tileX() + dx, f.tileY() + dy) <= VOID_Z + 1) edge = true
        if (edge && f.platform == null) return g.set(MapGen.tc(20 + ri(2)), MapGen.tc(14 + ri(2)), 0.55f)
        return false
    }

    override fun drawHud(c: Canvas, h: Hud) {
        val p = m.player ?: return
        h.topPill(c, "${timeStr()}   ·   Vies : ${"♥".repeat(p.stocks.coerceAtLeast(0))}", 0xCC1E1046.toInt())
        val alive = m.fighters.count { it.stocks > 0 }
        h.topText(c, "En lice : $alive", 2, 0xFF7CF0FF.toInt(), 20f)
    }

    override fun scoreText(f: Fighter) = "${f.stocks} vies"
}

@Suppress("unused")
private fun keepImports(c: Canvas) { G.INK; Tl.FLOOR; GameMode::class }
