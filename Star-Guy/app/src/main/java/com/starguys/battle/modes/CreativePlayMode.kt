package com.starguys.battle.modes

import com.starguys.battle.game.Fx
import android.graphics.Canvas
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ri
import com.starguys.battle.creative.CreativeLevel
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.GameMode
import com.starguys.battle.game.Goal
import com.starguys.battle.game.Hud
import com.starguys.battle.game.MapGen
import com.starguys.battle.game.Match
import com.starguys.battle.game.T
import com.starguys.battle.game.Tl

/** Plays a workshop level as an arena (deathmatch) or an obstacle course (race). */
class CreativePlayMode(m: Match) : GameMode(m) {
    private val course get() = m.cfg.creativeKind == 1
    override val respawns get() = !course
    override val respawnDelay = 2.5f
    override val timeLimit = 240f
    private var level: CreativeLevel? = null
    private var spawns = ArrayList<FloatArray>()
    private var order = 0
    private var goalX = 0f; private var goalY = 0f

    override fun build() {
        val lv = CreativeLevel.decode(m.cfg.creative ?: "") ?: CreativeLevel.blank()
        level = lv
        val mp = lv.build(m)
        for (s in lv.spawns()) spawns.add(floatArrayOf(MapGen.tc(s[1]), MapGen.tc(s[2])))
        if (spawns.isEmpty()) spawns.add(m.randomSpawn())
        // goal for races
        var gx = 0f; var gy = 0f; var n = 0
        for (y in 0 until mp.h) for (x in 0 until mp.w) if (mp.t(x, y) == Tl.GOAL) { gx += MapGen.tc(x); gy += MapGen.tc(y); n++ }
        if (n > 0) { goalX = gx / n; goalY = gy / n } else { goalX = mp.pxW - 2 * T; goalY = mp.pxH / 2 }
        val s0 = spawnAt(0)
        m.spawnPlayer(0, s0[0], s0[1])
        m.spawnBots(7, { if (course) 0 else it + 1 }, { i -> spawnAt(i + 1) })
        if (course) for (f in m.fighters) f.unarmed = true
        m.hud.big(if (course) "COURSE : ATTEINS L'ARRIVÉE !" else "ARÈNE : 10 K.O. POUR GAGNER", 0xFFFFE14D.toInt(), 3f)
    }

    private fun spawnAt(i: Int): FloatArray {
        val s = spawns[i % spawns.size]
        return floatArrayOf(s[0] + ((i / spawns.size) % 3 - 1) * 20f, s[1] + ((i / spawns.size) / 3 - 1) * 20f)
    }

    override fun respawnPoint(f: Fighter) = spawns[ri(spawns.size)]
    override fun canHurt(a: Fighter, b: Fighter) = !course && a.team != b.team
    override fun saveFromFall(f: Fighter) = course

    override fun update(dt: Float) {
        if (course) {
            for (f in m.fighters) {
                if (f.out || f.finished) continue
                if (m.map.t(f.tileX(), f.tileY()) == Tl.GOAL) {
                    f.finished = true; f.out = true; order++; f.place = order
                    m.fx.confetti(f.x, f.y, Fx.GND + 40f, 40); Sfx.play(Sfx.PICKUP)
                    m.hud.feed("${f.name} arrive #$order", 0xFF7CFF8A.toInt())
                }
            }
            val p = m.player
            val humansLeft = m.fighters.any { it.human && !it.finished }
            if (order >= 3 || !humansLeft || tickTimer(dt)) {
                val title = when { p == null || !p.finished -> if (order >= 3) "Le podium est complet !" else "Temps écoulé !"; p.place == 1 -> "Victoire !"; else -> "Arrivé #${p.place}" }
                m.finish(p != null && p.finished && p.place <= 3, title)
            }
        } else {
            val lead = m.fighters.maxByOrNull { it.kills }
            if (lead != null && lead.kills >= 10) { m.finish(lead === m.player, "${lead.name} gagne !"); return }
            if (tickTimer(dt)) m.finish(ranking().firstOrNull() === m.player, "Temps écoulé !")
        }
    }

    override fun ranking(): List<Fighter> = if (course)
        m.fighters.sortedWith(compareBy<Fighter>({ if (it.finished) 0 else 1 }, { if (it.finished) it.place else 0 }, { com.starguys.battle.core.dist(it.x, it.y, goalX, goalY) }))
    else m.fighters.sortedWith(compareBy<Fighter>({ -it.kills }, { it.deaths }))

    override fun playerWon() = m.won
    override fun scoreText(f: Fighter) = if (course) (if (f.finished) "#${f.place}" else "—") else "${f.kills} K.O."

    override fun botGoal(f: Fighter, g: Goal): Boolean = if (course) g.set(goalX, goalY, 1f) else false

    override fun drawHud(c: Canvas, h: Hud) {
        val name = level?.name ?: "Création"
        if (course) h.topPill(c, "$name   ·   ${timeStr()}   ·   Arrivés : $order", 0xCC2A2140.toInt())
        else h.topPill(c, "$name   ·   ${timeStr()}   ·   K.O. ${m.player?.kills ?: 0}/10", 0xCC2A2140.toInt())
    }
}
