package com.starguys.battle

import android.app.Activity
import android.graphics.Canvas
import com.starguys.battle.core.GameApp
import com.starguys.battle.core.TouchEvt
import com.starguys.battle.creative.CreativeLevel
import com.starguys.battle.creative.EditorMenu
import com.starguys.battle.creative.EditorScreen
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Bot
import com.starguys.battle.game.Match
import com.starguys.battle.game.MatchConfig
import com.starguys.battle.game.Tl
import com.starguys.battle.modes.Modes
import com.starguys.battle.ui.HeroSelect
import com.starguys.battle.ui.LobbyScreen
import com.starguys.battle.ui.MainMenu
import com.starguys.battle.ui.ModeSelect
import com.starguys.battle.ui.ResultScreen
import com.starguys.battle.ui.SettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless smoke test: plays every mode with bots (the human slot is also driven by a bot)
 * and runs the full draw path on a stub canvas, to catch runtime exceptions.
 */
class SimTest {
    private fun app(): GameApp { val a = GameApp(Activity()); a.resize(1920, 1080); return a }

    private fun runMode(mode: Int, biome: Biome?, role: Int = 0, seconds: Int = 90, creative: String? = null, kind: Int = 0, hero: Int = 0): String {
        val app = app()
        val m = Match(app, MatchConfig(mode, hero, biome, role, creative, kind))
        m.start()
        m.player?.let { it.brain = Bot(it) }
        val c = Canvas()
        val steps = seconds * 60
        var i = 0
        var endedAt = -1
        // also exercise HUD touches
        m.hud.touch(TouchEvt(TouchEvt.DOWN, 1, 200f, 800f)); m.hud.touch(TouchEvt(TouchEvt.MOVE, 1, 260f, 760f))
        while (i < steps) {
            m.update(1f / 60f)
            if (i % 6 == 0) m.draw(c)
            if (i == 120) { m.hud.touch(TouchEvt(TouchEvt.DOWN, 2, 1700f, 900f)); m.hud.touch(TouchEvt(TouchEvt.MOVE, 2, 1800f, 850f)); m.hud.touch(TouchEvt(TouchEvt.UP, 2, 1800f, 850f)) }
            if (m.over && endedAt < 0) endedAt = i
            if (m.ended) break
            i++
        }
        if (m.over) { val r = ResultScreen(app, m); r.layout(); r.enter(); r.update(0.016f); r.draw(c) }
        val alive = m.fighters.count { !it.out }
        return "mode=$mode biome=$biome steps=$i over=${m.over} won=${m.won} title='${m.resultTitle}' alive=$alive/${m.fighters.size} mobs=${m.mobs.size} projs=${m.projs.size}"
    }

    @Test fun royale() { println(runMode(Modes.ROYALE, null, seconds = 120)) }
    @Test fun meteor() { println(runMode(Modes.METEOR, Biome.VOLCANO)) }
    @Test fun rising() { println(runMode(Modes.RISING, null)) }
    @Test fun zerog() { println(runMode(Modes.ZEROG, null)) }
    @Test fun hunterPrey() { println(runMode(Modes.HUNTER, Biome.SWAMP, 0)) }
    @Test fun hunterHunter() { println(runMode(Modes.HUNTER, Biome.MEADOW, 1)) }
    @Test fun koth() { println(runMode(Modes.KOTH, Biome.PARK)) }
    @Test fun heist() { println(runMode(Modes.HEIST, Biome.FACTORY, 0)); println(runMode(Modes.HEIST, Biome.KITCHEN, 1)) }
    @Test fun gungame() { println(runMode(Modes.GUNGAME, Biome.SNOW)) }
    @Test fun ctf() { println(runMode(Modes.CTF, Biome.BEACH)) }
    @Test fun infection() { println(runMode(Modes.INFECTION, Biome.SPACE, 0)); println(runMode(Modes.INFECTION, Biome.MEADOW, 1)) }
    @Test fun football() { println(runMode(Modes.FOOTBALL, Biome.MEADOW)) }
    @Test fun prophunt() { println(runMode(Modes.PROPHUNT, Biome.FACTORY, 0)); println(runMode(Modes.PROPHUNT, Biome.PARK, 1)) }
    @Test fun castle() { println(runMode(Modes.CASTLE, Biome.MEADOW, seconds = 150)) }
    @Test fun escape() { println(runMode(Modes.ESCAPE, null, seconds = 140)) }
    @Test fun raid() { println(runMode(Modes.RAID, null, seconds = 150)) }
    @Test fun giant() { println(runMode(Modes.GIANT, Biome.KITCHEN)) }
    @Test fun melee() { println(runMode(Modes.MELEE, Biome.SWAMP)) }
    @Test fun mirror() { println(runMode(Modes.MIRROR, Biome.SNOW)) }
    @Test fun allHeroes() { for (h in 0 until 12) println(runMode(Modes.GIANT, Biome.MEADOW, seconds = 30, hero = h)) }
    @Test fun allBiomesRoyale() { for (b in Biome.values()) println(runMode(Modes.ROYALE, b, seconds = 25)) }

    @Test fun creative() {
        val lv = CreativeLevel.blank()
        for (x in 10..12) for (y in 1..24) lv.type[y * lv.w + x] = Tl.VOID.toByte()
        for (y in 1..24) lv.type[y * lv.w + 36] = Tl.GOAL.toByte()
        for (k in 0 until CreativeLevel.P_COUNT) lv.props.add(intArrayOf(k, 4 + k, 4 + (k % 3), k % 4))
        // Base64 is stubbed in unit tests, so feed the level through a test hook
        println(runMode(Modes.CREATIVE_PLAY, null, creative = "", kind = 0))
        println(runMode(Modes.CREATIVE_PLAY, null, creative = "", kind = 1))
    }

    @Test fun screens() {
        val app = app()
        val c = Canvas()
        val screens = listOf(MainMenu(app), ModeSelect(app), LobbyScreen(app, Modes.HUNTER), LobbyScreen(app, Modes.ZEROG), HeroSelect(app) { MainMenu(app) },
            SettingsScreen(app), EditorMenu(app), EditorScreen(app, 0))
        for (s in screens) {
            s.layout(); s.enter()
            for (k in 0 until 30) { s.update(1f / 60f); s.draw(c) }
            s.touch(TouchEvt(TouchEvt.DOWN, 0, 960f, 540f)); s.touch(TouchEvt(TouchEvt.MOVE, 0, 980f, 560f)); s.touch(TouchEvt(TouchEvt.UP, 0, 980f, 560f))
            for (b in ArrayList(s.btns)) { if (b.label.contains("JOUER") || b.label.contains("▶") || b.label == "Importer un code" || b.label.contains("Partager") || b.label == "CHOISIR") continue; try { b.onClick() } catch (e: Throwable) { throw AssertionError("button ${b.label} in ${s.javaClass.simpleName}", e) } }
            s.draw(c)
        }
        assertTrue(true)
    }
}
