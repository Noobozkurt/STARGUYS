package com.starguys.battle.game

import android.graphics.Canvas

/** What a bot should do according to the mode's objective. */
class Goal {
    var x = 0f; var y = 0f
    var has = false
    /** 0..1: how much the bot should prefer the objective over fighting */
    var urgency = 0.5f
    var target: Body? = null
    var flee = false
    var stay = false
    fun set(px: Float, py: Float, u: Float = 0.5f): Boolean { x = px; y = py; urgency = u; has = true; return true }
    fun reset() { has = false; urgency = 0.5f; target = null; flee = false; stay = false }
}

class ModeDef(
    val id: Int, val name: String, val cat: Int, val desc: String, val players: String,
    val biomes: Boolean = true, val roles: Array<String>? = null, val icon: Int = id
)

abstract class GameMode(val m: Match) {
    open val percentMode = false
    /** knockback applied by plain shots, as a fraction of damage */
    open val shotKnock = 0f
    open val autoHeal = true
    open val superMul = 1f
    open val respawns = false
    open val respawnDelay = 3f
    open val dropOnDeath = false
    open val fixedBiome: Biome? = null
    open val timeLimit = 0f
    open val allowSpectate = true
    var timeLeft = 0f
    var endT = -1f

    abstract fun build()
    open fun update(dt: Float) {}

    open fun speedMul(f: Fighter) = 1f
    open fun reloadMul(f: Fighter) = 1f
    open fun afterMove(f: Fighter, dt: Float) {}
    open fun mirrorActive(f: Fighter) = false
    open fun canHurt(a: Fighter, b: Fighter) = a.team != b.team
    open fun damageTakenMul(f: Fighter) = 1f
    open fun creditKill(killer: Fighter, victim: Fighter) {}
    open fun saveFromFall(f: Fighter) = false
    open fun hideBars(f: Fighter, viewer: Fighter?) = false
    open fun mobTarget(mob: Mob): Body? = null
    open fun onDeath(f: Fighter, killer: Fighter?) {}
    open fun onMobDeath(mob: Mob, killer: Fighter?) {}
    open fun interactLabel(f: Fighter): String? = null
    open fun interact(f: Fighter): Boolean = false
    open fun botGoal(f: Fighter, g: Goal): Boolean = false
    open fun drawUnder(c: Canvas) {}
    open fun drawOver(c: Canvas) {}
    /** 3D scene additions (hills, goals, trucks...) */
    open fun scene3d(s: com.starguys.battle.g3d.Scene) {}
    open fun drawHud(c: Canvas, h: Hud) {}
    open fun respawnPoint(f: Fighter): FloatArray = m.randomSpawn()
    open fun canPickup(f: Fighter, p: Pickup) = true
    /** default ranking: alive first, then by elimination time, then score */
    open fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (it.out) 1 else 0 }, { -it.eliminatedAt }, { -it.score }, { -it.kills }))
    open fun playerWon(): Boolean = ranking().firstOrNull() === m.player
    open fun scoreText(f: Fighter): String = "${f.kills} K.O."
    open fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) {}
    open fun onPropShot(p: Prop, proj: Projectile) {}

    /** Called once per frame after fighters update to handle respawns. */
    fun handleRespawns(dt: Float) {
        if (!respawns || m.over) return
        for (f in m.fighters) {
            if (!f.out || f.respawnT < 0f) continue
            f.respawnT -= dt
            if (f.respawnT <= 0f) {
                val p = respawnPoint(f)
                f.respawn(p[0], p[1])
                onRespawn(f)
            }
        }
    }

    open fun onRespawn(f: Fighter) {}

    fun tickTimer(dt: Float): Boolean {
        if (timeLimit <= 0f) return false
        timeLeft -= dt
        return timeLeft <= 0f
    }

    fun timeStr(): String {
        val t = maxOf(0, timeLeft.toInt())
        return "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
    }
}
