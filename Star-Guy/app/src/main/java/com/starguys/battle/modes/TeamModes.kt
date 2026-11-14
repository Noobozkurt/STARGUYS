package com.starguys.battle.modes

import com.starguys.battle.game.Fx
import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.pickOrNull
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.chance
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.pick
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.core.sinf
import com.starguys.battle.game.Ball
import com.starguys.battle.game.Body
import com.starguys.battle.game.Decor
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.FlagProp
import com.starguys.battle.game.GameMode
import com.starguys.battle.game.Goal
import com.starguys.battle.game.Heroes
import com.starguys.battle.game.Hud
import com.starguys.battle.game.MapGen
import com.starguys.battle.game.Match
import com.starguys.battle.game.Pickup
import com.starguys.battle.game.Prop
import com.starguys.battle.game.Projectile
import com.starguys.battle.game.T
import com.starguys.battle.game.Tl
import com.starguys.battle.game.Weapons
import com.starguys.battle.game.ZS

// ============================================================================ Hunter & prey
class HunterMode(m: Match) : GameMode(m) {
    override val timeLimit = 180f
    override val autoHeal = true
    var hunter: Fighter? = null

    override fun build() {
        val biome = Modes.pickBiome(m)
        MapGen.arena(m, biome, 44, 32, 0.9f)
        val role = when (m.cfg.role) { 0 -> 0; 1 -> 1; else -> ri(2) }
        val mp = m.map
        // hidden weapon caches inside bushes
        var hidden = 0
        for (t in 0 until 400) {
            if (hidden >= 5) break
            val x = ri(2, mp.w - 3); val y = ri(2, mp.h - 3)
            if (mp.t(x, y) == Tl.BUSH) { m.addPickup(Pickup(m, Pickup.WEAPON, Weapons.pickupable.pick(), MapGen.tc(x), MapGen.tc(y))); hidden++ }
        }
        while (hidden < 5) { val p = MapGen.findOpen(mp, 0) ?: break; m.addPickup(Pickup(m, Pickup.WEAPON, Weapons.pickupable.pick(), MapGen.tc(p[0]), MapGen.tc(p[1]))); hidden++ }
        val hp = m.randomSpawn()
        if (role == 1) {
            val pl = m.spawnPlayer(1, hp[0], hp[1]); makeHunter(pl)
            m.spawnBots(15, { 0 }, { m.randomSpawn(5f * T) }, listOf(Heroes.bean)).forEach { makePrey(it) }
        } else {
            val pl = m.spawnPlayer(0, hp[0], hp[1], Heroes.bean); makePrey(pl)
            m.spawnBots(14, { 0 }, { m.randomSpawn(5f * T) }, listOf(Heroes.bean)).forEach { makePrey(it) }
            val h = m.spawnBots(1, { 1 }, { m.randomSpawn(8f * T) })[0]; makeHunter(h)
        }
        m.hud.big(if (role == 1) "TU ES LE CHASSEUR !" else "SURVIS 3 MINUTES !", 0xFFFF5E7E.toInt(), 3f)
    }

    private fun makeHunter(f: Fighter) {
        hunter = f
        f.maxHp = 22000f; f.hp = f.maxHp
        f.modeWeapon = Weapons.list[Weapons.HUNTER]
        f.spawnShield = 4f
    }

    private fun makePrey(f: Fighter) { f.unarmed = true; f.skin = ri(3); f.color = Heroes.bean.colors[f.skin] }

    override fun speedMul(f: Fighter) = if (f === hunter) 1.08f else 1f

    override fun update(dt: Float) {
        for (f in m.fighters) if (!f.out && f !== hunter) f.chargeSuper(dt / 25f)
        val h = hunter
        val prey = m.fighters.count { !it.out && it !== h }
        if (h == null || h.out) { m.finish(m.player !== h, "Le chasseur est tombé !"); return }
        if (prey == 0) { m.finish(m.player === h, "Toutes les proies sont attrapées !"); return }
        if (tickTimer(dt)) m.finish(m.player !== h, "Les proies ont survécu !")
    }

    override fun canPickup(f: Fighter, p: Pickup) = f !== hunter || p.kind != Pickup.WEAPON

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        val h = hunter ?: return false
        if (f === h) return false
        val d = dist(f.x, f.y, h.x, h.y)
        if (f.unarmed && f.weapon == null) {
            if (d < 6f * T && m.canSee(f, h)) { g.target = h; g.flee = true; g.has = true; g.urgency = 1f; return true }
            val wpn = m.pickups.filter { it.alive && it.kind == Pickup.WEAPON }.minByOrNull { dist(f.x, f.y, it.x, it.y) }
            if (wpn != null) return g.set(wpn.x, wpn.y, 0.8f)
        } else if (d < 7f * T && f.hp < f.maxHp * 0.4f) { g.target = h; g.flee = true; g.has = true; return true }
        return false
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (it.out) 1 else 0 }, { -it.eliminatedAt }, { -it.kills }))
    override fun playerWon(): Boolean = m.won

    override fun drawHud(c: Canvas, h: Hud) {
        val prey = m.fighters.count { !it.out && it !== hunter }
        h.topPill(c, "${timeStr()}   ·   Proies : $prey", 0xCCB33952.toInt())
        val hn = hunter
        if (hn != null) h.topText(c, "Chasseur : ${hn.name} (${hn.hp.toInt()} PV)", 2, 0xFFFF9AB0.toInt(), 20f)
    }
}

// ============================================================================ King of the moving hill
class KothMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 3f
    override val timeLimit = 180f
    private var path = floatArrayOf()
    private var pos = 0f
    var hx = 0f; var hy = 0f
    private val hr = 2.1f * T
    private var holder: Fighter? = null
    private var tick = 0f
    private val goalPts = 60

    override fun build() {
        val biome = Modes.pickBiome(m)
        val mp = MapGen.arena(m, biome, 40, 30, 0.7f)
        val x0 = 6; val y0 = 6; val x1 = mp.w - 7; val y1 = mp.h - 7
        for (x in x0..x1) for (y in intArrayOf(y0, y1)) { mp.set(x, y, Tl.RAIL); mp.set(x, y - 1, Tl.FLOOR); mp.set(x, y + 1, Tl.FLOOR) }
        for (y in y0..y1) for (x in intArrayOf(x0, x1)) { mp.set(x, y, Tl.RAIL); mp.set(x - 1, y, Tl.FLOOR); mp.set(x + 1, y, Tl.FLOOR) }
        m.props.removeAll { pr -> val tx = pr.tileX(); val ty = pr.tileY(); (ty in y0 - 1..y0 + 1 || ty in y1 - 1..y1 + 1 || tx in x0 - 1..x0 + 1 || tx in x1 - 1..x1 + 1) }
        path = floatArrayOf(MapGen.tc(x0), MapGen.tc(y0), MapGen.tc(x1), MapGen.tc(y0), MapGen.tc(x1), MapGen.tc(y1), MapGen.tc(x0), MapGen.tc(y1))
        hx = path[0]; hy = path[1]
        val sp = m.randomSpawn()
        m.spawnPlayer(0, sp[0], sp[1])
        m.spawnBots(7, { it + 1 }, { m.randomSpawn() })
    }

    override fun update(dt: Float) {
        // move the hill around the loop
        val n = path.size / 2
        var rem = 70f * dt
        while (rem > 0f) {
            val i = pos.toInt() % n; val j = (i + 1) % n
            val seg = dist(path[i * 2], path[i * 2 + 1], path[j * 2], path[j * 2 + 1])
            val frac = pos - pos.toInt()
            val left = seg * (1f - frac)
            if (rem < left) { pos += rem / seg; rem = 0f } else { pos = ((pos.toInt() + 1) % n).toFloat(); rem -= left }
        }
        val i = pos.toInt() % n; val j = (i + 1) % n; val fr = pos - pos.toInt()
        hx = path[i * 2] + (path[j * 2] - path[i * 2]) * fr; hy = path[i * 2 + 1] + (path[j * 2 + 1] - path[i * 2 + 1]) * fr
        val inside = m.fighters.filter { !it.out && dist(it.x, it.y, hx, hy) < hr }
        holder = if (inside.size == 1) inside[0] else null
        tick -= dt
        if (tick <= 0f) { tick = 1f; holder?.let { it.score += 1f; if (it.isLocal) Sfx.play(Sfx.COIN, 0.4f) } }
        val lead = m.fighters.maxByOrNull { it.score }
        if (lead != null && lead.score >= goalPts) { m.finish(lead === m.player, "${lead.name} est le roi !"); return }
        if (tickTimer(dt)) { m.finish(ranking().firstOrNull() === m.player, "Temps écoulé !") }
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ -it.score }, { -it.kills }))
    override fun scoreText(f: Fighter) = "${f.score.toInt()} pts"

    override fun botGoal(f: Fighter, g: Goal): Boolean = g.set(hx + rf(-T, T), hy + rf(-T, T), 0.85f)

    override fun scene3d(s: com.starguys.battle.g3d.Scene) {
        val col = holder?.let { if (it === m.player) 0xFF4ADE80.toInt() else 0xFFF43F5E.toInt() } ?: 0xFFFFE14D.toInt()
        s.groundDisc(hx, hy, hr, col, 0.25f); s.groundRing(hx, hy, hr, col, 0.9f)
        val b = s.mb; val z = s.ground(hx, hy)
        b.at(hx, hy, z, 0f)
        b.box(0f, 12f, 0f, 140f, 16f, 60f, 0xFF8B5A3C.toInt()); b.box(0f, 21f, 0f, 120f, 3f, 44f, 0xFFC28A4E.toInt())
        for (k in intArrayOf(-1, 1)) for (q in intArrayOf(-1, 1)) b.part(com.starguys.battle.g3d.Meshes.CYL, k * 52f, 8f, q * 32f, 18f, 6f, 18f, 0xFF3A3A44.toInt(), rx = 1.5708f)
        b.cyl(0f, 20f, 0f, 5f, 150f, 0xFF1B1530.toInt())
        b.part(com.starguys.battle.g3d.Meshes.BOX, 0f, 132f, 26f, 2f, 30f, 50f, col)
        s.bill(true, hx, hy, z + 175f, com.starguys.battle.g3d.Spr.STAR, 18f, 0xFFFFE14D.toInt(), 1f, m.time * 2f)
    }

    override fun drawUnder(c: Canvas) {
        val col = holder?.let { if (it === m.player) 0xFF7CFF8A.toInt() else 0xFFFF5E7E.toInt() } ?: 0xFFFFE14D.toInt()
        G.circle(c, hx, hy, hr, G.alpha(col, 0.22f), false)
        G.ow = 3f
        G.ring(c, hx, hy, hr, 6f, G.alpha(col, 0.9f))
        // the rolling cart
        G.rrect(c, hx - 70f, hy - 30f, hx + 70f, hy + 30f, 12f, 0xFF8B5A3C.toInt())
        G.rect(c, hx - 60f, hy - 22f, hx + 60f, hy + 22f, 0xFFC28A4E.toInt(), false)
        for (k in -1..1 step 2) for (s in -1..1 step 2) G.circle(c, hx + k * 52f, hy + s * 32f, 10f, 0xFF3A3A44.toInt())
        G.line(c, hx, hy, hx, hy - 120f, 4f, G.INK)
        G.poly(c, col, true, hx, hy - 120f, hx + 46f, hy - 106f, hx, hy - 92f)
        G.star(c, hx, hy - 136f, 12f, 5f, 5, m.time * 2f, 0xFFFFE14D.toInt())
    }

    override fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) { G.circle(c, mx + hx * s, my + hy * s, hr * s, 0xAAFFE14D.toInt(), false) }

    override fun drawHud(c: Canvas, h: Hud) {
        val p = m.player ?: return
        val lead = m.fighters.maxByOrNull { it.score }
        h.topPill(c, "${timeStr()}   ·   Tes points : ${p.score.toInt()}/$goalPts", 0xCC2A2140.toInt())
        if (lead != null) h.topText(c, "Roi : ${lead.name} (${lead.score.toInt()})", 2, 0xFFFFE14D.toInt(), 20f)
        val hd = holder
        h.topText(c, if (hd == null) "Colline libre ou disputée" else "Sur la colline : ${hd.name}", 3, 0xFFFFF3B0.toInt(), 18f)
    }
}

// ============================================================================ Heist 4v4
class HeistMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 4f
    override val timeLimit = 180f
    private var delivered = 0
    private val goal = 5
    private val vaultX get() = MapGen.tc(39)
    private val vaultY get() = MapGen.tc(13) + T / 2
    private val truckX get() = MapGen.tc(4)
    private val truckY get() = MapGen.tc(13) + T / 2

    override fun build() {
        val biome = Modes.pickBiome(m)
        MapGen.heist(m, biome)
        for (i in 0 until 10) m.addPickup(Pickup(m, Pickup.GOLD, 0, MapGen.tc(36 + i % 5) + rf(-8f, 8f), MapGen.tc(11 + i / 5 * 3) + rf(-8f, 8f)))
        val playerTeam = when (m.cfg.role) { 0 -> 0; 1 -> 1; else -> ri(2) }
        val p = spawnPoint(playerTeam)
        m.spawnPlayer(playerTeam, p[0], p[1])
        m.spawnBots(7, { if (it < 3) playerTeam else 1 - playerTeam }, { i -> spawnPoint(if (i < 3) playerTeam else 1 - playerTeam) })
        m.hud.big(if (playerTeam == 0) "VOLE 5 LINGOTS !" else "DÉFENDS LE COFFRE !", 0xFFFFD23F.toInt(), 3f)
    }

    private fun spawnPoint(team: Int) = if (team == 0) spawnIn(m, 2, 9, 7, 18) else spawnIn(m, 30, 8, 33, 19)
    override fun respawnPoint(f: Fighter) = spawnPoint(f.team)

    override fun canPickup(f: Fighter, p: Pickup): Boolean {
        if (p.kind != Pickup.GOLD) return true
        if (f.team == 0) {
            if (f.carry == 0) { f.carry = 1; p.alive = false; Sfx.play(Sfx.COIN, 0.5f); if (f.isLocal) m.hud.toastSmall("Ramène le lingot au camion !") }
            return false
        } else {
            // defenders return dropped gold to the vault
            if (dist(p.x, p.y, vaultX, vaultY) > 4f * T) { p.x = vaultX + rf(-60f, 60f); p.y = vaultY + rf(-40f, 40f); m.fx.stars(p.x, p.y, Fx.GND, 5, 0xFFFFD23F.toInt()) }
            return false
        }
    }

    override fun update(dt: Float) {
        for (f in m.fighters) {
            if (f.out || f.team != 0 || f.carry == 0) continue
            if (f.x < MapGen.tc(8) && f.y > MapGen.tc(8) && f.y < MapGen.tc(19)) {
                delivered += f.carry; f.carry = 0
                m.fx.confetti(f.x, f.y, Fx.GND + 40f, 20); Sfx.play(Sfx.COIN)
                m.hud.feed("${f.name} livre un lingot ! ($delivered/$goal)", 0xFFFFD23F.toInt())
            }
        }
        val pt = m.player?.team ?: 0
        if (delivered >= goal) { m.finish(pt == 0, "Braquage réussi !"); return }
        val leftGold = m.pickups.count { it.alive && it.kind == Pickup.GOLD } + m.fighters.sumOf { it.carry }
        if (leftGold + delivered < goal) { m.finish(pt == 1, "Plus assez d'or !"); return }
        if (tickTimer(dt)) m.finish(pt == 1, "Le coffre est sauvé !")
    }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        if (f.team == 0) {
            if (f.carry > 0) return g.set(truckX, truckY, 0.95f)
            val gold = m.pickups.filter { it.alive && it.kind == Pickup.GOLD }.minByOrNull { dist(f.x, f.y, it.x, it.y) } ?: return false
            return g.set(gold.x, gold.y, 0.7f)
        } else {
            val carrier = m.fighters.filter { !it.out && it.team == 0 && it.carry > 0 }.minByOrNull { dist(f.x, f.y, it.x, it.y) }
            if (carrier != null) { g.target = carrier; return g.set(carrier.x, carrier.y, 0.8f) }
            return g.set(MapGen.tc(31 + ri(3)), MapGen.tc(10 + ri(8)), 0.35f)
        }
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (it.team == winTeam()) 0 else 1 }, { -it.kills }))
    private fun winTeam() = if (delivered >= goal) 0 else 1
    override fun playerWon() = m.won

    override fun scene3d(s: com.starguys.battle.g3d.Scene) {
        s.groundRect((MapGen.tc(2) + MapGen.tc(7)) / 2, (MapGen.tc(9) + MapGen.tc(18)) / 2, (MapGen.tc(7) - MapGen.tc(2)) / 2 + T / 2, (MapGen.tc(18) - MapGen.tc(9)) / 2 + T / 2, 0xFF35A0F4.toInt(), 0.25f)
        val b = s.mb; b.at(truckX, truckY, s.ground(truckX, truckY), 0f)
        b.box(-10f, 40f, 0f, 120f, 64f, 70f, 0xFF35A0F4.toInt()); b.box(56f, 30f, 0f, 30f, 44f, 64f, 0xFF2563EB.toInt())
        b.box(71.5f, 38f, 0f, 1f, 20f, 50f, 0xFFBFE8FF.toInt(), cat = com.starguys.battle.g3d.Cat.FLAT)
        for (k in intArrayOf(-40, 40)) for (q in intArrayOf(-1, 1)) b.part(com.starguys.battle.g3d.Meshes.CYL, k.toFloat(), 14f, q * 36f, 28f, 8f, 28f, 0xFF3A3A44.toInt(), rx = 1.5708f)
        s.bill(true, vaultX, MapGen.tc(8), s.ground(vaultX, MapGen.tc(8)) + 120f, com.starguys.battle.g3d.Spr.GLOW, 60f, 0xFFFFD23F.toInt(), 0.5f)
    }

    override fun drawUnder(c: Canvas) {
        G.ow = 3f
        // truck
        val tx = truckX; val ty = truckY
        G.rect(c, MapGen.tc(2) - T / 2, MapGen.tc(9) - T / 2, MapGen.tc(7) + T / 2, MapGen.tc(18) + T / 2, 0x2235A0F4, false)
        G.rrect(c, tx - 70f, ty - 60f, tx + 70f, ty + 10f, 10f, 0xFF35A0F4.toInt())
        G.rect(c, tx + 40f, ty - 54f, tx + 66f, ty - 30f, 0xFFBFE8FF.toInt())
        G.circle(c, tx - 40f, ty + 10f, 14f, 0xFF3A3A44.toInt()); G.circle(c, tx + 40f, ty + 10f, 14f, 0xFF3A3A44.toInt())
        G.text(c, "CAMION", tx, ty - 22f, 20f, G.WHITE)
        G.text(c, "COFFRE-FORT", vaultX, MapGen.tc(8) - 40f, 22f, 0xFFFFD23F.toInt())
    }

    override fun drawHud(c: Canvas, h: Hud) {
        h.scoreBar(c, "$delivered/$goal", timeStr(), teamColor(0), teamColor(1), "Lingots")
        val p = m.player ?: return
        h.topText(c, if (p.team == 0) "Tu es Braqueur" else "Tu es Défenseur", 2, teamColor(p.team), 20f)
    }

    override fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) {
        G.circle(c, mx + truckX * s, my + truckY * s, 5f, 0xFF35A0F4.toInt(), false)
        for (p in m.pickups) if (p.alive && p.kind == Pickup.GOLD) G.circle(c, mx + p.x * s, my + p.y * s, 2.5f, 0xFFFFD23F.toInt(), false)
    }
}

// ============================================================================ Capture the flag duel (3v3)
class CtfMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 4f
    override val timeLimit = 240f
    val caps = IntArray(2)
    lateinit var flags: Array<FlagProp>

    override fun build() {
        val biome = Modes.pickBiome(m)
        val mp = MapGen.ctf(m, biome)
        val f0 = FlagProp(m, MapGen.tc(2), MapGen.tc(mp.h / 2), 0, teamColor(0))
        val f1 = FlagProp(m, MapGen.tc(mp.w - 3), MapGen.tc(mp.h / 2), 1, teamColor(1))
        flags = arrayOf(f0, f1)
        m.addProp(f0); m.addProp(f1)
        val p = spawnPoint(0)
        m.spawnPlayer(0, p[0], p[1])
        m.spawnBots(5, { if (it < 2) 0 else 1 }, { i -> spawnPoint(if (i < 2) 0 else 1) })
    }

    private fun spawnPoint(team: Int): FloatArray = if (team == 0) spawnIn(m, 18, 3, 20, m.map.h - 4) else spawnIn(m, m.map.w - 21, 3, m.map.w - 19, m.map.h - 4)
    override fun respawnPoint(f: Fighter) = spawnPoint(f.team)

    override fun update(dt: Float) {
        for (f in m.fighters) {
            if (f.out) continue
            val enemyFlag = flags[1 - f.team]; val ownFlag = flags[f.team]
            if (enemyFlag.carrier == null && f.flag == null && dist(f.x, f.y, enemyFlag.x, enemyFlag.y) < 40f && f.vehicle == null) {
                enemyFlag.carrier = f; enemyFlag.atHome = false; f.flag = enemyFlag
                m.hud.feed("${f.name} a pris le drapeau ${teamName(1 - f.team)} !", teamColor(f.team)); Sfx.play(Sfx.ALARM, 0.5f)
            }
            if (!ownFlag.atHome && ownFlag.carrier == null && dist(f.x, f.y, ownFlag.x, ownFlag.y) < 40f) { ownFlag.reset(); m.hud.feed("${f.name} ramène son drapeau", teamColor(f.team)) }
            val carried = f.flag
            if (carried != null && ownFlag.atHome && dist(f.x, f.y, ownFlag.homeX, ownFlag.homeY) < 70f) {
                caps[f.team]++
                carried.reset(); f.flag = null
                m.fx.confetti(f.x, f.y, Fx.GND + 50f, 40); Sfx.play(Sfx.WIN, 0.6f)
                m.hud.big("CAPTURE ${teamName(f.team).uppercase()} !", teamColor(f.team), 2f)
            }
        }
        val pt = m.player?.team ?: 0
        if (caps[0] >= 3 || caps[1] >= 3) { m.finish(caps[pt] >= 3, "${teamName(if (caps[0] >= 3) 0 else 1)} gagnent !"); return }
        if (tickTimer(dt)) m.finish(caps[pt] > caps[1 - pt], if (caps[0] == caps[1]) "Égalité !" else "Temps écoulé !")
    }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        val own = flags[f.team]; val enemy = flags[1 - f.team]
        if (f.flag != null) return g.set(own.homeX, own.homeY, 1f)
        val oc = own.carrier
        if (oc != null) { g.target = oc; return g.set(oc.x, oc.y, 0.85f) }
        if (!own.atHome) return g.set(own.x, own.y, 0.9f)
        val attacker = (m.fighters.indexOf(f) % 3) != 0
        return if (attacker) g.set(enemy.x, enemy.y, 0.75f) else g.set(own.homeX + (if (f.team == 0) 3 * T else -3 * T), own.homeY + rf(-2f * T, 2f * T), 0.4f)
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (caps[it.team] >= caps[1 - it.team]) 0 else 1 }, { -it.kills }))
    override fun playerWon() = m.won

    override fun drawHud(c: Canvas, h: Hud) { h.scoreBar(c, "${caps[0]}", "${caps[1]}", teamColor(0), teamColor(1), timeStr()) }
    override fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) { for (fl in flags) G.circle(c, mx + fl.x * s, my + fl.y * s, 4f, fl.color, false) }
}

// ============================================================================ Slime infection
class InfectionMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 4f
    override val timeLimit = 150f

    override fun build() {
        val biome = Modes.pickBiome(m)
        MapGen.arena(m, biome, 42, 30, 1f)
        val sp = m.randomSpawn()
        val pl = m.spawnPlayer(0, sp[0], sp[1])
        val bots = m.spawnBots(11, { 0 }, { m.randomSpawn() })
        for (f in m.fighters) { f.weapon = Weapons.list[Weapons.pickupable.pick()]; f.wAmmo = f.weapon!!.ammo }
        if (m.cfg.role == 1) infect(pl) else infect(bots[ri(bots.size)])
        for (i in 0 until 12) { val p = MapGen.findOpen(m.map, 0) ?: continue; m.dropLoot(MapGen.tc(p[0]), MapGen.tc(p[1]), 1) }
    }

    fun infect(f: Fighter) {
        f.infected = true; f.team = 1
        f.setHero(Heroes.slime); f.weapon = null; f.items.clear(); f.gadget = null
        f.spawnShield = 1f
        m.fx.splat(f.x, f.y, Fx.GND + 30f, 0xFF7BE84A.toInt()); Sfx.play(Sfx.SPLASH, 0.6f)
        m.hud.feed("${f.name} est infecté !", 0xFF7BE84A.toInt())
        if (f.isLocal) m.hud.big("TU ES INFECTÉ ! CONTAMINE-LES !", 0xFF7BE84A.toInt(), 2.5f)
    }

    override fun canPickup(f: Fighter, p: Pickup) = !f.infected

    override fun update(dt: Float) {
        for (a in m.fighters) {
            if (a.out || !a.infected || a.spawnShield > 0) continue
            for (b in m.fighters) {
                if (b.out || b.infected || b.spawnShield > 0) continue
                if (dist(a.x, a.y, b.x, b.y) < a.radius + b.radius + 6f) { a.kills++; infect(b) }
            }
        }
        val surv = m.fighters.count { !it.infected }
        val pt = m.player?.team ?: 0
        if (surv == 0) { m.finish(pt == 1, "Tout le monde est infecté !"); return }
        if (tickTimer(dt)) m.finish(pt == 0, "Les survivants tiennent bon !")
    }

    override fun onDeath(f: Fighter, killer: Fighter?) { if (!f.infected) infect(f) }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        if (f.infected) {
            val t = m.fighters.filter { !it.out && !it.infected }.minByOrNull { dist(f.x, f.y, it.x, it.y) } ?: return false
            g.target = t; return g.set(t.x, t.y, 0.95f)
        }
        val z = m.fighters.filter { !it.out && it.infected }.minByOrNull { dist(f.x, f.y, it.x, it.y) } ?: return false
        if (dist(f.x, f.y, z.x, z.y) < 2.5f * T) { g.target = z; g.flee = true; g.has = true; g.urgency = 1f; return true }
        return false
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (it.infected) 1 else 0 }, { -it.kills }))
    override fun playerWon() = m.won
    override fun drawHud(c: Canvas, h: Hud) {
        val surv = m.fighters.count { !it.infected }
        h.scoreBar(c, "$surv", "${m.fighters.size - surv}", 0xFF35A0F4.toInt(), 0xFF5DBE3A.toInt(), timeStr())
        h.topText(c, "Survivants  ·  Infectés", 2, G.WHITE, 18f)
    }
}

// ============================================================================ Explosive football (3v3)
class FootballMode(m: Match) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 3f
    override val timeLimit = 180f
    val goals = IntArray(2)
    lateinit var ball: Ball
    private var resetT = 0f

    override fun build() {
        val biome = Modes.pickBiome(m, com.starguys.battle.game.Biome.SPACE)
        MapGen.football(m, biome)
        ball = Ball(m, m.map.pxW / 2, m.map.pxH / 2)
        m.addProp(ball)
        val p = spawnPoint(0, 0)
        m.spawnPlayer(0, p[0], p[1])
        m.spawnBots(5, { if (it < 2) 0 else 1 }, { i -> spawnPoint(if (i < 2) 0 else 1, i % 3 + 1) })
    }

    private fun spawnPoint(team: Int, k: Int): FloatArray {
        val x = if (team == 0) MapGen.tc(6) else MapGen.tc(m.map.w - 7)
        val y = MapGen.tc(4 + (k % 3) * 7)
        return floatArrayOf(x, y)
    }

    override fun respawnPoint(f: Fighter) = spawnPoint(f.team, ri(3))

    override fun update(dt: Float) {
        if (resetT > 0f) {
            resetT -= dt
            if (resetT <= 0f) {
                ball.x = m.map.pxW / 2; ball.y = m.map.pxH / 2; ball.vx = 0f; ball.vy = 0f; ball.z = 0f
                for ((i, f) in m.fighters.withIndex()) { val p = spawnPoint(f.team, i); if (!f.out) { f.x = p[0]; f.y = p[1]; f.vx = 0f; f.vy = 0f } }
            }
            return
        }
        val gy0 = MapGen.tc(m.map.h / 2 - 3) - T / 2; val gy1 = MapGen.tc(m.map.h / 2 + 2) + T / 2
        if (ball.y > gy0 && ball.y < gy1) {
            val scorer = when { ball.x < T * 1.1f -> 1; ball.x > m.map.pxW - T * 1.1f -> 0; else -> -1 }
            if (scorer >= 0) {
                goals[scorer]++
                m.explode(ball.x, ball.y, 2.5f * T, 0f, m.envBody, com.starguys.battle.game.TEAM_NEUTRAL, 0f)
                m.fx.confetti(ball.x, ball.y, Fx.GND + 60f, 60)
                Sfx.play(Sfx.WHISTLE)
                m.hud.big("BUT DES ${teamName(scorer).uppercase()} !", teamColor(scorer), 2f)
                resetT = 2f
                ball.vx = 0f; ball.vy = 0f
            }
        }
        // keep ball inside if it leaves through the goal walls elsewhere
        ball.x = ball.x.coerceIn(10f, m.map.pxW - 10f)
        val pt = m.player?.team ?: 0
        if (goals[0] >= 3 || goals[1] >= 3) { m.finish(goals[pt] >= 3, "${teamName(if (goals[0] >= 3) 0 else 1)} gagnent !"); return }
        if (tickTimer(dt)) m.finish(goals[pt] > goals[1 - pt], if (goals[0] == goals[1]) "Match nul !" else "Coup de sifflet final !")
    }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        val ex = if (f.team == 0) m.map.pxW else 0f
        val ey = m.map.pxH / 2
        val a = com.starguys.battle.core.ang(ball.x, ball.y, ex, ey)
        val bx = ball.x - cosf(a) * 2.2f * T; val by = ball.y - sinf(a) * 2.2f * T
        val defender = m.fighters.indexOf(f) % 3 == 0
        if (defender && dist(ball.x, ball.y, if (f.team == 0) 0f else m.map.pxW, ey) > m.map.pxW * 0.55f) {
            return g.set(if (f.team == 0) 5f * T else m.map.pxW - 5f * T, ey + rf(-2f * T, 2f * T), 0.6f)
        }
        if (dist(f.x, f.y, bx, by) < 1.4f * T) { g.target = ball; return g.set(ball.x, ball.y, 0.5f) }
        return g.set(bx, by, 0.85f)
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (goals[it.team] >= goals[1 - it.team]) 0 else 1 }, { -it.kills }))
    override fun playerWon() = m.won

    override fun scene3d(s: com.starguys.battle.g3d.Scene) {
        val gy0 = MapGen.tc(m.map.h / 2 - 3) - T / 2; val gy1 = MapGen.tc(m.map.h / 2 + 2) + T / 2
        val b = s.mb
        for (side in 0..1) {
            val gx = if (side == 0) T * 0.55f else m.map.pxW - T * 0.55f
            s.groundRect(gx, (gy0 + gy1) / 2, T * 0.55f, (gy1 - gy0) / 2, teamColor(side), 0.4f)
            b.at(gx, gy0, s.ground(gx, gy0), 0f); b.cyl(0f, 0f, 0f, 8f, 130f, 0xFFFFFFFF.toInt())
            b.at(gx, gy1, s.ground(gx, gy1), 0f); b.cyl(0f, 0f, 0f, 8f, 130f, 0xFFFFFFFF.toInt())
            b.at(gx, (gy0 + gy1) / 2, s.ground(gx, gy0) + 130f, 0f); b.box(0f, 0f, 0f, 8f, 8f, gy1 - gy0, 0xFFFFFFFF.toInt())
            b.glass(com.starguys.battle.g3d.Meshes.BOX, 0f, -65f, 0f, 2f, 130f, gy1 - gy0, teamColor(side), 0.2f)
        }
        s.groundRing(m.map.pxW / 2, m.map.pxH / 2, 3f * T, 0xFFFFFFFF.toInt(), 0.5f)
        s.groundRect(m.map.pxW / 2, m.map.pxH / 2, 3f, m.map.pxH / 2 - T, 0xFFFFFFFF.toInt(), 0.5f)
    }

    override fun drawUnder(c: Canvas) {
        val gy0 = MapGen.tc(m.map.h / 2 - 3) - T / 2; val gy1 = MapGen.tc(m.map.h / 2 + 2) + T / 2
        G.rect(c, 0f, gy0, T * 1.1f, gy1, G.alpha(teamColor(0), 0.35f), false)
        G.rect(c, m.map.pxW - T * 1.1f, gy0, m.map.pxW, gy1, G.alpha(teamColor(1), 0.35f), false)
        G.ow = 3f
        G.line(c, m.map.pxW / 2, T, m.map.pxW / 2, m.map.pxH - T, 4f, 0x88FFFFFF.toInt())
        G.ring(c, m.map.pxW / 2, m.map.pxH / 2, 3f * T, 4f, 0x88FFFFFF.toInt())
    }

    override fun minimapExtra(c: Canvas, mx: Float, my: Float, s: Float) { G.circle(c, mx + ball.x * s, my + ball.y * s, 5f, G.WHITE) }
    override fun drawHud(c: Canvas, h: Hud) { h.scoreBar(c, "${goals[0]}", "${goals[1]}", teamColor(0), teamColor(1), timeStr()) }
}

// ============================================================================ Prop hunt
class PropHuntMode(m: Match) : GameMode(m) {
    override val timeLimit = 150f
    override val autoHeal = false
    private var hideT = 18f
    private var playerTeam = 0

    override fun build() {
        val biome = Modes.pickBiome(m, com.starguys.battle.game.Biome.SWAMP)
        MapGen.propHunt(m, biome)
        playerTeam = when (m.cfg.role) { 0 -> 0; 1 -> 1; else -> ri(2) }
        val sp = m.randomSpawn()
        val pl = m.spawnPlayer(playerTeam, sp[0], sp[1])
        val hunters = if (playerTeam == 1) 2 else 3
        val hiders = if (playerTeam == 0) 6 else 7
        m.spawnBots(hiders, { 0 }, { m.randomSpawn() })
        val hs = MapGen.findOpen(m.map, 1) ?: intArrayOf(m.map.w / 2, m.map.h / 2)
        m.spawnBots(hunters, { 1 }, { floatArrayOf(MapGen.tc(hs[0]) + rf(-30f, 30f), MapGen.tc(hs[1]) + rf(-30f, 30f)) })
        if (playerTeam == 1) { pl.x = MapGen.tc(hs[0]); pl.y = MapGen.tc(hs[1]) }
        for (f in m.fighters) if (f.team == 0) { f.disguise = Decor.DISGUISES.pick(); f.unarmed = true; f.maxHp = 2500f; f.hp = 2500f }
        m.hud.big(if (playerTeam == 0) "CACHE-TOI ! (déguisement : bouton Changer)" else "ATTENDS… LES CACHÉS SE CACHENT", 0xFFFFE14D.toInt(), 3f)
    }

    override fun update(dt: Float) {
        if (hideT > 0f) {
            hideT -= dt
            for (f in m.fighters) if (f.team == 1 && !f.out) { f.stunT = 0.1f; f.blindT = if (f.human) 0.1f else 0f }
            if (f0()) Unit
            if (hideT <= 0f) { m.hud.big("LA CHASSE COMMENCE !", 0xFFFF5E7E.toInt(), 2f); Sfx.play(Sfx.WHISTLE) }
            return
        }
        // keep hiders disguised (they reveal when moving fast)
        for (f in m.fighters) if (f.team == 0 && !f.out && f.disguise < 0) f.disguise = Decor.DISGUISES.pick()
        val hiders = m.fighters.count { !it.out && it.team == 0 }
        if (hiders == 0) { m.finish(m.player?.team == 1, "Tous les cachés sont trouvés !"); return }
        if (tickTimer(dt)) m.finish(m.player?.team == 0, "Les cachés ont survécu !")
    }

    private fun f0() = false

    override fun hideBars(f: Fighter, viewer: Fighter?) = f.team == 0 && viewer != null && viewer.team == 1

    override fun interactLabel(f: Fighter): String? = if (f.team == 0 && !f.out) "Changer" else null
    override fun interact(f: Fighter): Boolean {
        if (f.team != 0) return false
        val i = Decor.DISGUISES.indexOf(f.disguise)
        f.disguise = Decor.DISGUISES[(i + 1) % Decor.DISGUISES.size]
        m.fx.smoke(f.x, f.y, Fx.GND + 20f, 4, 0xFFFFFFFF.toInt()); Sfx.play(Sfx.BUBBLE, 0.5f)
        return true
    }

    /** Called when a hunter's shot is stopped by a real decor prop. */
    override fun onPropShot(p: Prop, proj: Projectile) {
        val o = proj.ownerF ?: return
        if (o.team == 1 && p is Decor && p.kind in Decor.DISGUISES) { o.hurt(140f, m.envBody, 0f, 0f); m.fx.text(o.x, o.y, Fx.GND + 80f, "-140 (raté)", 0xFFFF9AB0.toInt()) }
    }

    override fun botGoal(f: Fighter, g: Goal): Boolean {
        if (f.team == 0) {
            if (f.lastHurtT < 1.5f) { g.flee = true; g.has = true; g.target = m.fighters.firstOrNull { it.team == 1 && !it.out }; g.urgency = 1f; return true }
            if (hideT > 4f && f.brain != null) {
                if (f.lastMoveT < 0.1f || dist(f.x, f.y, f.safeX, f.safeY) >= 0f) {
                    val pr = m.props.filter { it is Decor }.pickOrNull()
                    if (pr != null && rf() < 0.03f) return g.set(pr.x + rf(-50f, 50f), pr.y + rf(-50f, 50f), 1f)
                }
            }
            g.stay = true; g.has = true
            return true
        }
        if (hideT > 0f) { g.stay = true; g.has = true; return true }
        // hunters: pick suspicious things to shoot
        val near = ArrayList<Body>()
        for (o in m.fighters) if (!o.out && o.team == 0 && dist(f.x, f.y, o.x, o.y) < 6f * T) near.add(o)
        val susp = near.firstOrNull { (it as Fighter).lastMoveT < 1.5f || it.lastHurtT < 2f }
        if (susp != null && chance(0.8f)) { g.target = susp; return g.set(susp.x, susp.y, 0.5f) }
        if (near.isNotEmpty() && chance(0.25f)) { g.target = near.pick(); return g.set(g.target!!.x, g.target!!.y, 0.5f) }
        if (chance(0.25f)) {
            val pr = m.props.filter { it is Decor && dist(f.x, f.y, it.x, it.y) < 5f * T }.pickOrNull()
            if (pr != null) { g.target = pr; return g.set(pr.x, pr.y, 0.4f) }
        }
        return false
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ if (it.out) 1 else 0 }, { -it.kills }))
    override fun playerWon() = m.won

    override fun drawHud(c: Canvas, h: Hud) {
        val hiders = m.fighters.count { !it.out && it.team == 0 }
        if (hideT > 0f) h.topPill(c, "Cachez-vous ! ${hideT.toInt() + 1} s", 0xCC2A2140.toInt())
        else h.topPill(c, "${timeStr()}   ·   Cachés restants : $hiders", 0xCC2A2140.toInt())
        h.topText(c, if (m.player?.team == 0) "Tu es caché : reste immobile !" else "Tu es chasseur : tirer sur un vrai objet coûte des PV", 2, 0xFFFFF3B0.toInt(), 18f)
    }
}

@Suppress("unused") private fun keep() { ZS; GameMode::class }
