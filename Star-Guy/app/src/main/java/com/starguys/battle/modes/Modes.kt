package com.starguys.battle.modes

import android.graphics.Canvas
import com.starguys.battle.core.G
import com.starguys.battle.core.Save
import com.starguys.battle.core.pick
import com.starguys.battle.core.ri
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.GameMode
import com.starguys.battle.game.Heroes
import com.starguys.battle.game.Hud
import com.starguys.battle.game.MapGen
import com.starguys.battle.game.Match
import com.starguys.battle.game.ModeDef
import com.starguys.battle.game.T
import com.starguys.battle.game.Weapons

object Modes {
    const val ROYALE = 0; const val RISING = 1; const val ZEROG = 2; const val HUNTER = 3; const val KOTH = 4; const val HEIST = 5
    const val GUNGAME = 6; const val CTF = 7; const val INFECTION = 8; const val FOOTBALL = 9; const val PROPHUNT = 10
    const val CASTLE = 11; const val ESCAPE = 12; const val RAID = 13; const val GIANT = 14; const val METEOR = 15
    const val MELEE = 16; const val MIRROR = 17; const val CREATIVE = 18; const val CREATIVE_PLAY = 19

    val CATS = arrayOf("Classique", "Compétitifs (PvP)", "Coopératifs (PvE)", "Fun et événements", "Créatif")
    val CAT_COLORS = intArrayOf(0xFFFF9A3D.toInt(), 0xFFFF5E7E.toInt(), 0xFF35C6F4.toInt(), 0xFFB46CFF.toInt(), 0xFF7CC243.toInt())

    val defs = listOf(
        ModeDef(ROYALE, "Battle Royale", 0, "Parachute, loot, zone qui rétrécit : le dernier debout gagne.", "25 joueurs"),
        ModeDef(RISING, "Zone inversée", 1, "La carte se remplit de slime qui monte. Il faut grimper : le dernier en hauteur gagne.", "12 joueurs"),
        ModeDef(ZEROG, "Gravité zéro", 1, "Station spatiale : on flotte, les tirs font reculer. Éjecte les autres dans le vide !", "8 joueurs · 3 vies", biomes = false),
        ModeDef(HUNTER, "Chasseur et proies", 1, "1 chasseur surarmé contre 15 haricots désarmés : survivre 3 minutes ou trouver les armes cachées.", "16 joueurs", roles = arrayOf("Proie", "Chasseur", "Aléatoire")),
        ModeDef(KOTH, "Roi de la colline mouvante", 1, "La zone à tenir se déplace sur une plateforme qui roule à travers la carte.", "8 joueurs"),
        ModeDef(HEIST, "Braquage", 1, "4v4 : une équipe vole des lingots dans le coffre-fort, l'autre défend. Porter un lingot ralentit.", "4 contre 4", roles = arrayOf("Braqueurs", "Défenseurs", "Aléatoire")),
        ModeDef(GUNGAME, "Arène des armes", 1, "Chaque K.O. change ton arme. Le premier à finir la liste gagne.", "8 joueurs"),
        ModeDef(CTF, "Capture du drapeau", 1, "Les drapeaux sont posés au bout de parcours d'obstacles. 3 captures pour gagner.", "3 contre 3"),
        ModeDef(INFECTION, "Infection slime", 1, "Un infecté contamine les autres au contact. Les survivants sont armés, les infectés rapides.", "12 joueurs", roles = arrayOf("Survivant", "Infecté")),
        ModeDef(FOOTBALL, "Football explosif", 1, "Marque des buts en tirant sur un ballon géant !", "3 contre 3"),
        ModeDef(PROPHUNT, "Cache-cache props", 1, "Les cachés se déguisent en objets du décor, les chasseurs les traquent.", "10 joueurs", roles = arrayOf("Caché", "Chasseur", "Aléatoire")),
        ModeDef(CASTLE, "Défense de château", 2, "Des vagues d'ennemis attaquent : construis des barricades entre les vagues.", "4 joueurs coop"),
        ModeDef(ESCAPE, "Évasion", 2, "Fuis une base qui s'autodétruit. Le parcours change à chaque partie !", "4 joueurs coop", biomes = false),
        ModeDef(RAID, "Raid multi-boss", 2, "Un donjon de 3 salles avec un mini-boss chacune, puis le boss final.", "4 joueurs coop", biomes = false),
        ModeDef(GIANT, "Mode géant", 3, "Chaque K.O. te fait grossir : plus de dégâts, mais une cible plus facile.", "10 joueurs"),
        ModeDef(METEOR, "Pluie de météorites", 3, "Battle royale sous une pluie de météorites !", "16 joueurs"),
        ModeDef(MELEE, "Tous au corps à corps", 3, "Pas d'armes à feu : marteaux, épées et poêles.", "10 joueurs"),
        ModeDef(MIRROR, "Mode miroir", 3, "Les contrôles s'inversent quelques secondes à des moments aléatoires.", "10 joueurs"),
        ModeDef(CREATIVE, "Atelier joueur", 4, "Construis tes propres parcours et arènes, joue-les et partage-les.", "Éditeur", biomes = false)
    )

    fun def(id: Int): ModeDef = if (id == CREATIVE_PLAY) defs[CREATIVE] else defs[id.coerceIn(0, defs.size - 1)]

    fun create(id: Int, m: Match): GameMode = when (id) {
        ROYALE -> RoyaleMode(m, false)
        RISING -> RisingSlimeMode(m)
        ZEROG -> ZeroGMode(m)
        HUNTER -> HunterMode(m)
        KOTH -> KothMode(m)
        HEIST -> HeistMode(m)
        GUNGAME -> GunGameMode(m)
        CTF -> CtfMode(m)
        INFECTION -> InfectionMode(m)
        FOOTBALL -> FootballMode(m)
        PROPHUNT -> PropHuntMode(m)
        CASTLE -> CastleMode(m)
        ESCAPE -> EscapeMode(m)
        RAID -> RaidMode(m)
        GIANT -> GiantMode(m)
        METEOR -> RoyaleMode(m, true)
        MELEE -> MeleeMode(m)
        MIRROR -> MirrorMode(m)
        else -> CreativePlayMode(m)
    }

    fun pickBiome(m: Match, vararg exclude: Biome): Biome {
        val b = m.cfg.biome
        if (b != null) return b
        return Biome.values().filter { it !in exclude }.pick()
    }
}

/** Free-for-all deathmatch with respawns, used by several fun modes. */
abstract class DeathmatchMode(m: Match, val target: Int, val players: Int) : GameMode(m) {
    override val respawns = true
    override val respawnDelay = 2.5f
    override val timeLimit = 180f
    open val mapW = 38
    open val mapH = 26

    override fun build() {
        val biome = Modes.pickBiome(m)
        MapGen.arena(m, biome, mapW, mapH, 0.9f, vehicles = 1)
        val sp = m.randomSpawn()
        m.spawnPlayer(0, sp[0], sp[1])
        m.spawnBots(players - 1, { it + 1 }, { m.randomSpawn() })
        for (f in m.fighters) onRespawn(f)
        scatterLoot()
    }

    open fun scatterLoot() {
        for (i in 0 until 10) { val p = MapGen.findOpen(m.map, 0) ?: continue; m.dropLoot(MapGen.tc(p[0]), MapGen.tc(p[1]), 1) }
    }

    override fun update(dt: Float) {
        val leader = m.fighters.maxByOrNull { it.kills }
        if (leader != null && leader.kills >= target) { m.finish(leader === m.player, "${leader.name} gagne !"); return }
        if (tickTimer(dt)) { val r = ranking(); m.finish(r.firstOrNull() === m.player, "Temps écoulé !") }
    }

    override fun ranking(): List<Fighter> = m.fighters.sortedWith(compareBy<Fighter>({ -it.kills }, { it.deaths }, { -it.dmgDone }))

    override fun drawHud(c: Canvas, h: Hud) {
        val p = m.player ?: return
        val lead = m.fighters.maxByOrNull { it.kills }
        h.topPill(c, "${timeStr()}   ·   K.O. ${p.kills}/$target", 0xCC2A2140.toInt())
        if (lead != null) h.topText(c, "En tête : ${lead.name} (${lead.kills})", 2, if (lead === p) 0xFF7CFF8A.toInt() else 0xFFFFF3B0.toInt(), 20f)
    }
}

class GiantMode(m: Match) : DeathmatchMode(m, 8, 10) {
    override fun creditKill(killer: Fighter, victim: Fighter) {
        killer.growth++
        killer.maxHp = killer.hero.hp * (1f + killer.growth * 0.15f)
        killer.heal(killer.maxHp * 0.3f)
        m.fx.text(killer.x, killer.y - 120f, "PLUS GROS !", 0xFFFFE14D.toInt())
    }
    override fun onRespawn(f: Fighter) { f.growth = 0; f.maxHp = f.hero.hp; f.hp = f.maxHp }
}

class MeleeMode(m: Match) : DeathmatchMode(m, 10, 10) {
    override fun onRespawn(f: Fighter) { f.modeWeapon = Weapons.list[Weapons.meleeOnly.pick()]; f.weapon = null }
    override fun scatterLoot() {
        for (i in 0 until 8) { val p = MapGen.findOpen(m.map, 0) ?: continue; m.addPickup(com.starguys.battle.game.Pickup(m, com.starguys.battle.game.Pickup.ITEM, ri(4), MapGen.tc(p[0]), MapGen.tc(p[1]))) }
    }
    override fun canPickup(f: Fighter, p: com.starguys.battle.game.Pickup) = p.kind != com.starguys.battle.game.Pickup.WEAPON
}

class MirrorMode(m: Match) : DeathmatchMode(m, 10, 10) {
    private var next = 8f
    private var active = 0f
    private var warn = 0f
    override fun mirrorActive(f: Fighter) = active > 0f
    override fun update(dt: Float) {
        super.update(dt)
        if (active > 0f) { active -= dt; return }
        next -= dt
        if (next <= 1.2f && warn <= 0f) { warn = 1.2f; m.hud.big("MIROIR DANS 1 S…", 0xFFE0B0FF.toInt(), 1.2f) }
        if (warn > 0f) warn -= dt
        if (next <= 0f) { active = 4.5f; next = 9f + ri(6); warn = 0f; com.starguys.battle.core.Sfx.play(com.starguys.battle.core.Sfx.TELEPORT) }
    }
}

class GunGameMode(m: Match) : DeathmatchMode(m, Weapons.gunGame.size, 8) {
    override fun onRespawn(f: Fighter) { f.modeWeapon = Weapons.list[Weapons.gunGame[f.kills.coerceIn(0, Weapons.gunGame.size - 1)]] }
    override fun creditKill(killer: Fighter, victim: Fighter) {
        if (killer.kills < Weapons.gunGame.size) {
            killer.modeWeapon = Weapons.list[Weapons.gunGame[killer.kills]]
            if (killer.isLocal) m.hud.big(killer.modeWeapon!!.name, 0xFFFFE14D.toInt(), 1.5f)
        }
    }
    override fun scatterLoot() {}
    override fun canPickup(f: Fighter, p: com.starguys.battle.game.Pickup) = p.kind != com.starguys.battle.game.Pickup.WEAPON
    override fun drawHud(c: Canvas, h: Hud) {
        val p = m.player ?: return
        h.topPill(c, "${timeStr()}   ·   Arme ${minOf(p.kills + 1, target)}/$target", 0xCC2A2140.toInt())
        val nx = Weapons.gunGame.getOrNull(p.kills + 1)
        if (nx != null) h.topText(c, "Suivante : ${Weapons.list[nx].name}", 2, 0xFFFFF3B0.toInt(), 20f)
        val lead = m.fighters.maxByOrNull { it.kills }
        if (lead != null && lead !== p) h.topText(c, "En tête : ${lead.name} (${lead.kills + 1}/$target)", 3, 0xFFFFC94D.toInt(), 18f)
    }
}

/** Helpers shared by team modes. */
fun teamColor(t: Int) = if (t == 0) 0xFF35A0F4.toInt() else 0xFFFF5E5E.toInt()
fun teamName(t: Int) = if (t == 0) "Bleus" else "Rouges"

fun spawnIn(m: Match, x0: Int, y0: Int, x1: Int, y1: Int): FloatArray = m.randomSpawn(T * 0.8f, intArrayOf(x0, y0, x1, y1))

fun Save.playerHero() = Heroes.byId(hero)

@Suppress("unused")
fun drawTeamScores(c: Canvas, h: Hud, a: Int, b: Int, center: String) = h.scoreBar(c, "$a", "$b", teamColor(0), teamColor(1), center)

@Suppress("unused")
private fun unusedG() = G.INK
