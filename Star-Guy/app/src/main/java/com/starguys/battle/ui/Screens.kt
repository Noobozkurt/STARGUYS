package com.starguys.battle.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.starguys.battle.core.Btn
import com.starguys.battle.core.G
import com.starguys.battle.core.GameApp
import com.starguys.battle.core.Save
import com.starguys.battle.core.Screen
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.TouchEvt
import com.starguys.battle.core.cosf
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf
import com.starguys.battle.creative.EditorMenu
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Decor
import com.starguys.battle.game.Fighter
import com.starguys.battle.game.HeroArt
import com.starguys.battle.game.Heroes
import com.starguys.battle.game.Match
import com.starguys.battle.game.MatchConfig
import com.starguys.battle.modes.Modes

private const val GOLD = 0xFFFFD23F.toInt()
private const val PURPLE = 0xFF6A3DE8.toInt()
private const val GREEN = 0xFF7CC243.toInt()
private const val ORANGE = 0xFFFF9A3D.toInt()
private const val PINK = 0xFFFF5E7E.toInt()
private const val BLUE = 0xFF35A0F4.toInt()
private const val GREY = 0xFF5B4B8A.toInt()

fun drawCoins(c: Canvas, x: Float, y: Float, u: Float, align: Paint.Align = Paint.Align.LEFT) {
    G.ow = 2.5f * u
    G.circle(c, x, y, 13f * u, GOLD); G.text(c, "★", x, y + 6f * u, 15f * u, 0xFFB8860B.toInt(), outline = false)
    G.text(c, "${Save.coins}", x + 20f * u, y + 9f * u, 26f * u, G.WHITE, align)
}

fun drawTrophies(c: Canvas, x: Float, y: Float, u: Float) {
    G.ow = 2.5f * u
    G.poly(c, GOLD, true, x - 12f * u, y - 12f * u, x + 12f * u, y - 12f * u, x + 8f * u, y + 2f * u, x - 8f * u, y + 2f * u)
    G.rect(c, x - 3f * u, y + 2f * u, x + 3f * u, y + 9f * u, GOLD); G.rect(c, x - 9f * u, y + 9f * u, x + 9f * u, y + 13f * u, GOLD)
    G.text(c, "${Save.trophies}", x + 22f * u, y + 9f * u, 26f * u, G.WHITE, Paint.Align.LEFT)
}

/** Small symbolic icon for each mode. */
fun drawModeIcon(c: Canvas, id: Int, x: Float, y: Float, s: Float, t: Float) {
    c.save(); c.translate(x, y); c.scale(s / 40f, s / 40f)
    G.ow = 3f
    when (id) {
        Modes.ROYALE -> { G.pie(c, 0f, -6f, 30f, 180f, 180f, 0xFFFF5E7E.toInt()); G.line(c, -28f, -6f, -4f, 22f, 2f, G.INK); G.line(c, 28f, -6f, 4f, 22f, 2f, G.INK); G.circle(c, 0f, 26f, 8f, 0xFFFFC94D.toInt()) }
        Modes.RISING -> { G.poly(c, 0xFF8B5A3C.toInt(), true, -30f, 30f, -6f, -24f, 14f, 6f, 30f, -10f, 30f, 30f); G.rect(c, -32f, 12f + sinf(t * 2f) * 4f, 32f, 32f, 0xCC7BE84A.toInt()) }
        Modes.ZEROG -> { G.circle(c, 0f, 0f, 20f, 0xFF7383B3.toInt()); G.oval(c, 0f, 0f, 34f, 9f, 0x00000000); G.ring(c, 0f, 0f, 0f, 0f, 0); G.arc(c, 0f, 0f, 30f, 200f, 140f, 4f, 0xFF7CF0FF.toInt()); G.star(c, 22f, -24f, 6f, 2.5f, 4, t, G.WHITE) }
        Modes.HUNTER -> { G.ring(c, 0f, 0f, 22f, 4f, 0xFFFF3D3D.toInt()); G.line(c, -32f, 0f, 32f, 0f, 3f, 0xFFFF3D3D.toInt()); G.line(c, 0f, -32f, 0f, 32f, 3f, 0xFFFF3D3D.toInt()); G.oval(c, 0f, 4f, 8f, 13f, 0xFFFFC94D.toInt()) }
        Modes.KOTH -> { G.poly(c, GOLD, true, -24f, 14f, -24f, -12f, -12f, 0f, 0f, -20f, 12f, 0f, 24f, -12f, 24f, 14f); G.circle(c, 0f, -4f, 4f, 0xFFFF5E7E.toInt()) }
        Modes.HEIST -> { G.poly(c, GOLD, true, -26f, 16f, 26f, 16f, 18f, -6f, -18f, -6f); G.poly(c, 0xFFFFE680.toInt(), true, -14f, -6f, 14f, -6f, 10f, -20f, -10f, -20f) }
        Modes.GUNGAME -> { for (k in 0 until 3) { G.rrect(c, -26f + k * 6f, 16f - k * 16f, 14f + k * 6f, 24f - k * 16f, 3f, intArrayOf(0xFFE84545.toInt(), 0xFFB46CFF.toInt(), GOLD)[k]) } }
        Modes.CTF -> { G.line(c, -10f, 30f, -10f, -28f, 4f, G.INK); G.poly(c, BLUE, true, -10f, -28f, 26f, -18f, -10f, -6f) }
        Modes.INFECTION -> { G.oval(c, 0f, 4f, 26f, 22f, 0xFF7BE84A.toInt()); G.eyes(c, 0f, 0f, 9f, 6f, 0f, 0.2f, false, true) }
        Modes.FOOTBALL -> { G.circle(c, 0f, 0f, 24f, G.WHITE); G.star(c, 0f, 0f, 10f, 6f, 5, 0f, 0xFF2A2433.toInt(), false); G.star(c, 18f, -22f, 8f, 3f, 4, t * 3, 0xFFFF7A3D.toInt()) }
        Modes.PROPHUNT -> { c.save(); c.translate(0f, 22f); Decor.drawKind(c, Decor.CRATE, 0f, Biome.MEADOW); c.restore(); G.eyes(c, 0f, -2f, 7f, 5f, 0.5f, 0f) }
        Modes.CASTLE -> { G.rect(c, -24f, -10f, 24f, 26f, 0xFFB0B8C8.toInt()); for (k in 0..2) G.rect(c, -24f + k * 18f, -20f, -12f + k * 18f, -8f, 0xFFB0B8C8.toInt()); G.rect(c, -6f, 8f, 6f, 26f, 0xFF6B4028.toInt()) }
        Modes.ESCAPE -> { G.circle(c, -14f, 0f, 20f, 0xFFFF7A3D.toInt()); G.circle(c, -14f, 0f, 10f, GOLD, false); G.poly(c, G.WHITE, true, 4f, -10f, 22f, -10f, 22f, -20f, 36f, 0f, 22f, 20f, 22f, 10f, 4f, 10f) }
        Modes.RAID -> { G.oval(c, 0f, 6f, 26f, 22f, 0xCC7BE84A.toInt()); G.poly(c, GOLD, true, -18f, -12f, -12f, -28f, -4f, -16f, 0f, -30f, 4f, -16f, 12f, -28f, 18f, -12f); G.eyes(c, 0f, 4f, 9f, 5f, 0f, 0f, false, true) }
        Modes.GIANT -> { G.rect(c, -6f, 2f, 6f, 26f, 0xFFFFF3E0.toInt()); G.pie(c, 0f, 4f, 26f, 180f, 180f, 0xFFE84545.toInt()); G.circle(c, -10f, -8f, 4f, G.WHITE, false); G.circle(c, 8f, -12f, 3f, G.WHITE, false) }
        Modes.METEOR -> { G.line(c, 26f, -26f, 4f, -4f, 10f, 0x88FF7A3D.toInt()); G.circle(c, -2f, 2f, 16f, 0xFFFF7A3D.toInt()); G.circle(c, -6f, -2f, 6f, GOLD, false) }
        Modes.MELEE -> { G.line(c, -22f, 22f, 22f, -22f, 7f, 0xFFDDEEFF.toInt()); G.line(c, 22f, 22f, -6f, -6f, 6f, 0xFF8B5A3C.toInt()); G.rrect(c, -22f, -24f, 2f, -8f, 3f, 0xFF8A93A8.toInt()) }
        Modes.MIRROR -> { G.oval(c, 0f, 0f, 20f, 28f, 0xFFBFE8FF.toInt()); G.line(c, -8f, -10f, 6f, 8f, 3f, G.WHITE); G.text(c, "⇄", 0f, 40f, 22f, G.WHITE) }
        else -> { G.rect(c, -24f, 4f, -4f, 24f, 0xFF6BD3FF.toInt()); G.rect(c, 2f, -4f, 24f, 24f, 0xFFFF5E7E.toInt()); G.line(c, -20f, -22f, 4f, -2f, 6f, 0xFF8B5A3C.toInt()); G.rrect(c, -28f, -30f, -10f, -18f, 3f, 0xFF8A93A8.toInt()) }
    }
    c.restore()
}

fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, u: Float, col: Int = 0xCC2A1F4A.toInt()) {
    G.ow = 3f * u
    G.rrect(c, l, t, r, b, 22f * u, col)
}

// ============================================================================ main menu
class MainMenu(app: GameApp) : Screen(app) {
    private var t = 0f

    override fun enter() { Sfx.music(1) }

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("JOUER", w - 420f * u, h - 220f * u, 380f * u, 120f * u, ORANGE) { app.go(LobbyScreen(app, Save.lastMode)) }.also { it.sub = Modes.def(Save.lastMode).name; it.textSize = 52f })
        btns.add(Btn("MODES", w - 420f * u, h - 360f * u, 180f * u, 110f * u, PURPLE) { app.go(ModeSelect(app)) }.also { it.icon = { c, x, y, s -> drawModeIcon(c, 0, x, y, s * 1.2f, t) } })
        btns.add(Btn("HÉROS", w - 220f * u, h - 360f * u, 180f * u, 110f * u, PINK) { app.go(HeroSelect(app) { MainMenu(app) }) }.also { it.icon = { c, x, y, s -> HeroArt.portrait(c, Save.hero, Heroes.byId(Save.hero).colors[Save.skin(Save.hero)], x, y - s * 0.2f, s * 1.1f, t) } })
        btns.add(Btn("ATELIER", w - 420f * u, h - 500f * u, 180f * u, 110f * u, GREEN) { app.go(EditorMenu(app)) }.also { it.icon = { c, x, y, s -> drawModeIcon(c, Modes.CREATIVE, x, y, s * 1.2f, t) } })
        btns.add(Btn("RÉGLAGES", w - 220f * u, h - 500f * u, 180f * u, 110f * u, GREY) { app.go(SettingsScreen(app)) }.also { it.icon = { c, x, y, s -> G.star(c, x, y, s, s * 0.6f, 8, t * 0.5f, 0xFFDDDDEE.toInt()); G.circle(c, x, y, s * 0.35f, GREY) } })
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c)
        // floor
        G.ow = 3f * u
        G.oval(c, w * 0.3f, h * 0.84f, 300f * u, 60f * u, 0x55000000, false)
        G.oval(c, w * 0.3f, h * 0.82f, 270f * u, 50f * u, 0xFF8FD86E.toInt())
        // title
        val ty = 92f * u + sinf(t * 2f) * 4f * u
        G.text(c, "STAR-GUYS", w * 0.3f, ty, 86f * u, GOLD)
        G.text(c, "BATTLE", w * 0.3f, ty + 70f * u, 60f * u, 0xFFFF8CC6.toInt())
        G.star(c, w * 0.3f - 250f * u, ty - 20f * u, 26f * u, 11f * u, 5, t, GOLD)
        G.star(c, w * 0.3f + 250f * u, ty - 26f * u, 20f * u, 9f * u, 5, -t, 0xFF6BD3FF.toInt())
        // selected hero + friends
        val hid = Save.hero
        val hero = Heroes.byId(hid)
        c.save(); c.translate(w * 0.3f, h * 0.82f); c.scale(3.6f * u, 3.6f * u)
        G.ow = 3f
        HeroArt.draw(c, hid, hero.colors[Save.skin(hid)], t, false, 0.3f + sinf(t) * 0.2f, 0.1f)
        c.restore()
        for (k in 0 until 2) {
            val oid = (hid + 3 + k * 4) % 12
            c.save(); c.translate(w * 0.3f + (if (k == 0) -200f else 200f) * u, h * 0.8f); c.scale(2.2f * u * (if (k == 0) 1f else -1f), 2.2f * u)
            G.fade = 0.9f
            HeroArt.draw(c, oid, Heroes.byId(oid).colors[0], t + k, false, 0.5f, 0f)
            G.fade = 1f
            c.restore()
        }
        G.text(c, hero.name, w * 0.3f, h * 0.95f, 34f * u, G.WHITE)
        // profile
        panel(c, w - 430f * u, 24f * u, w - 30f * u, 150f * u, u)
        G.circle(c, w - 390f * u, 70f * u, 30f * u, PURPLE)
        G.text(c, "${Save.level()}", w - 390f * u, 81f * u, 30f * u, G.WHITE)
        G.text(c, Save.playerName, w - 345f * u, 64f * u, 26f * u, G.WHITE, Paint.Align.LEFT)
        G.rrect(c, w - 345f * u, 76f * u, w - 60f * u, 92f * u, 8f * u, 0xFF1B1530.toInt())
        G.rrect(c, w - 345f * u, 76f * u, w - 345f * u + 285f * u * Save.levelProgress(), 92f * u, 8f * u, 0xFF6BD3FF.toInt(), false)
        drawCoins(c, w - 380f * u, 122f * u, u)
        drawTrophies(c, w - 220f * u, 120f * u, u)
        G.text(c, "Victoires : ${Save.wins}   ·   Parties : ${Save.matches}   ·   K.O. : ${Save.kills}", w - 230f * u, h - 40f * u, 18f * u, 0xCCFFFFFF.toInt())
        drawButtons(c)
    }

    override fun back() { app.quit() }
}

// ============================================================================ mode select
class ModeSelect(app: GameApp) : Screen(app) {
    private var t = 0f
    override fun layout() {
        val u = app.u; val w = app.w
        btns.clear()
        val cols = 5
        val gap = 14f * u
        val cw = (w - 80f * u - gap * (cols - 1)) / cols
        val ch = 128f * u
        var i = 0
        for (d in Modes.defs) {
            val col = i % cols; val row = i / cols
            val x = 40f * u + col * (cw + gap); val y = 110f * u + row * (ch + gap + 4f * u)
            val b = Btn(d.name, x, y, cw, ch, Modes.CAT_COLORS[d.cat]) {
                if (d.id == Modes.CREATIVE) app.go(EditorMenu(app)) else { Save.lastMode = d.id; app.go(LobbyScreen(app, d.id)) }
            }
            b.textSize = 22f
            b.icon = { c, cx, cy, s -> drawModeIcon(c, d.id, cx, cy - 4f * u, s * 1.15f, t) }
            btns.add(b)
            i++
        }
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, GREY) { app.go(MainMenu(app)) })
    }
    override fun update(dt: Float) { t += dt }
    override fun draw(c: Canvas) {
        val u = app.u
        app.menuBackground(c, 0xFF2A1F6A.toInt(), 0xFF6A3DE8.toInt())
        G.text(c, "CHOISIS UN MODE", app.w / 2, 66f * u, 46f * u, G.WHITE)
        drawButtons(c)
        // legend
        var lx = 140f * u
        for ((i, name) in Modes.CATS.withIndex()) {
            G.circle(c, lx, 95f * u, 8f * u, Modes.CAT_COLORS[i])
            G.text(c, name, lx + 14f * u, 101f * u, 16f * u, G.WHITE, Paint.Align.LEFT)
            lx += G.textW(name, 16f * u) + 50f * u
        }
    }
    override fun back() { app.go(MainMenu(app)) }
}

// ============================================================================ lobby (mode details, hero, biome, role)
class LobbyScreen(app: GameApp, val modeId: Int) : Screen(app) {
    private var t = 0f
    private val def = Modes.def(modeId)
    private var biome = Save.biome
    private var role = 0

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, GREY) { app.go(ModeSelect(app)) })
        btns.add(Btn("JOUER !", w - 380f * u, h - 150f * u, 340f * u, 120f * u, ORANGE) { start() }.also { it.textSize = 52f })
        btns.add(Btn("Changer de héros", 80f * u, h - 120f * u, 330f * u, 80f * u, PINK) { app.go(HeroSelect(app) { LobbyScreen(app, modeId) }) }.also { it.textSize = 26f })
        if (def.biomes) {
            btns.add(Btn("◀", w * 0.5f - 40f * u, 140f * u, 70f * u, 70f * u, GREY) { biome = if (biome <= -1) Biome.values().size - 1 else biome - 1; Save.biome = biome })
            btns.add(Btn("▶", w - 120f * u, 140f * u, 70f * u, 70f * u, GREY) { biome = if (biome >= Biome.values().size - 1) -1 else biome + 1; Save.biome = biome })
        }
        val roles = def.roles
        if (roles != null) {
            for ((i, r) in roles.withIndex()) {
                val bw = 170f * u
                btns.add(Btn(r, w * 0.5f + i * (bw + 12f * u) - 40f * u, 330f * u, bw, 64f * u, BLUE) { role = i }.also { it.textSize = 24f })
            }
        }
        val dn = arrayOf("Facile", "Normal", "Difficile")
        btns.add(Btn("Bots : ${dn[Save.difficulty]}", w * 0.5f - 40f * u, 450f * u, 260f * u, 64f * u, GREY) {
            Save.difficulty = (Save.difficulty + 1) % 3; layout()
        }.also { it.textSize = 22f })
    }

    private fun start() {
        val b = if (biome < 0) null else Biome.values()[biome]
        Save.lastMode = modeId
        app.go(GameScreen(app, MatchConfig(modeId, Save.hero, b, role)))
    }

    override fun update(dt: Float) {
        t += dt
        val roles = def.roles
        if (roles != null) for (b in btns) { val i = roles.indexOf(b.label); if (i >= 0) b.selected = i == role }
    }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, 0xFF1F2A6A.toInt(), Modes.CAT_COLORS[def.cat])
        panel(c, 40f * u, 110f * u, w * 0.45f, h - 140f * u, u)
        drawModeIcon(c, modeId, 110f * u, 180f * u, 60f * u, t)
        G.text(c, def.name, 170f * u, 175f * u, 38f * u, G.WHITE, Paint.Align.LEFT)
        G.text(c, Modes.CATS[def.cat] + "  ·  " + def.players, 170f * u, 210f * u, 20f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
        var y = 270f * u
        for (line in G.wrap(def.desc, 24f * u, w * 0.45f - 120f * u)) { G.text(c, line, 80f * u, y, 24f * u, G.WHITE, Paint.Align.LEFT); y += 32f * u }
        // hero preview
        val hero = Heroes.byId(Save.hero)
        c.save(); c.translate(w * 0.24f, h - 200f * u); c.scale(2.2f * u, 2.2f * u)
        G.ow = 3f
        HeroArt.draw(c, hero.id, hero.colors[Save.skin(hero.id)], t, false, 0.4f, 0.1f)
        c.restore()
        G.text(c, "${hero.name} · ${hero.cls}", w * 0.24f, h - 165f * u, 22f * u, G.WHITE)
        // right side
        if (def.biomes) {
            panel(c, w * 0.5f - 60f * u, 100f * u, w - 40f * u, 260f * u, u)
            G.text(c, "Biome", w * 0.5f - 30f * u, 132f * u, 24f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
            val bName = if (biome < 0) "Aléatoire" else Biome.values()[biome].label
            val bDesc = if (biome < 0) "Un biome surprise à chaque partie" else Biome.values()[biome].desc
            if (biome >= 0) {
                val bm = Biome.values()[biome]
                G.rrect(c, w * 0.5f + 50f * u, 150f * u, w * 0.5f + 130f * u, 220f * u, 10f * u, bm.floorA)
                G.rrect(c, w * 0.5f + 70f * u, 160f * u, w * 0.5f + 110f * u, 190f * u, 6f * u, bm.wallTop)
            } else G.text(c, "?", w * 0.5f + 90f * u, 205f * u, 50f * u, GOLD)
            G.text(c, bName, w * 0.5f + 150f * u, 180f * u, 30f * u, G.WHITE, Paint.Align.LEFT)
            G.text(c, bDesc, w * 0.5f + 150f * u, 210f * u, 18f * u, 0xFFDDDDFF.toInt(), Paint.Align.LEFT)
        } else {
            G.text(c, "Arène fixe pour ce mode", w * 0.72f, 190f * u, 26f * u, 0xFFFFF3B0.toInt())
        }
        if (def.roles != null) G.text(c, "Rôle", w * 0.5f - 40f * u, 318f * u, 24f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
        G.text(c, "Difficulté des bots", w * 0.5f - 40f * u, 438f * u, 24f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
        drawButtons(c)
        drawCoins(c, w - 220f * u, 50f * u, u)
    }

    override fun back() { app.go(ModeSelect(app)) }
}

// ============================================================================ hero select
class HeroSelect(app: GameApp, val backTo: () -> Screen) : Screen(app) {
    private var t = 0f
    private var sel = Save.hero

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, GREY) { app.go(backTo()) })
        val cols = 4
        val cw = 132f * u; val ch = 132f * u; val gap = 10f * u
        for (i in 0 until 12) {
            val hero = Heroes.list[i]
            val x = 30f * u + (i % cols) * (cw + gap); val y = 110f * u + (i / cols) * (ch + gap)
            val b = Btn(hero.name, x, y, cw, ch, 0xFF3A2E6A.toInt()) { sel = i; layout() }
            b.textSize = 18f
            b.selected = i == sel
            b.icon = { c, cx, cy, s -> HeroArt.portrait(c, i, hero.colors[Save.skin(i)], cx, cy + s * 0.15f, s * 1.05f, t) }
            btns.add(b)
        }
        btns.add(Btn("CHOISIR", w - 330f * u, h - 130f * u, 290f * u, 100f * u, ORANGE) { Save.hero = sel; app.go(backTo()) }.also { it.textSize = 40f })
        // skins
        val hero = Heroes.list[sel]
        for (s in 0 until hero.colors.size) {
            val owned = Save.skinOwned(sel, s)
            val b = Btn(if (owned) "" else "100", w * 0.62f + s * 92f * u, h - 130f * u, 80f * u, 80f * u, hero.colors[s]) {
                if (Save.skinOwned(sel, s)) { Save.setSkin(sel, s); layout() }
                else if (Save.coins >= 100) { Save.coins -= 100; Save.buySkin(sel, s); Save.setSkin(sel, s); Sfx.play(Sfx.COIN); layout() }
                else app.toast("Pas assez de pièces !")
            }
            b.selected = Save.skin(sel) == s
            if (!owned) b.icon = { c, cx, cy, sz -> G.circle(c, cx, cy - sz * 0.3f, sz * 0.5f, GOLD) }
            btns.add(b)
        }
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, 0xFF3A1F6A.toInt(), 0xFFFF5E9E.toInt())
        G.text(c, "HÉROS", 310f * u, 70f * u, 46f * u, G.WHITE)
        drawButtons(c)
        val hero = Heroes.list[sel]
        val px = w * 0.54f
        panel(c, px, 30f * u, w - 30f * u, h - 160f * u, u)
        // big preview
        c.save(); c.translate(px + 130f * u, 330f * u); c.scale(3.3f * u, 3.3f * u)
        G.ow = 3f
        HeroArt.draw(c, sel, hero.colors[Save.skin(sel)], t, (t % 4f) > 2f, 0.4f + sinf(t * 1.3f) * 0.3f, 0.1f, superOn = (t % 6f) > 5f)
        c.restore()
        val tx = px + 260f * u
        G.text(c, hero.name, tx, 90f * u, 40f * u, G.WHITE, Paint.Align.LEFT)
        G.text(c, "${hero.cls} · ${hero.look}", tx, 122f * u, 19f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
        stat(c, tx, 160f * u, "Vie", hero.hp / 6000f, "${hero.hp.toInt()}", 0xFF7CFF8A.toInt())
        stat(c, tx, 196f * u, "Vitesse", (hero.speed - 200f) / 80f, "${hero.speed.toInt()}", 0xFF6BD3FF.toInt())
        val tw = w - 60f * u - tx
        var y = 250f * u
        G.text(c, "Attaque : ${hero.attack.name}", tx, y, 22f * u, ORANGE, Paint.Align.LEFT); y += 26f * u
        for (l in G.wrap(hero.attack.desc, 18f * u, tw)) { G.text(c, l, tx, y, 18f * u, G.WHITE, Paint.Align.LEFT); y += 22f * u }
        y += 10f * u
        G.text(c, "Super : ${hero.superName}", tx, y, 22f * u, GOLD, Paint.Align.LEFT); y += 26f * u
        for (l in G.wrap(hero.superDesc, 18f * u, tw)) { G.text(c, l, tx, y, 18f * u, G.WHITE, Paint.Align.LEFT); y += 22f * u }
        y += 14f * u
        G.text(c, "Trophées : ${Save.heroTrophies(sel)}", tx, y, 20f * u, 0xFFFFE14D.toInt(), Paint.Align.LEFT)
        G.text(c, "Couleurs", w * 0.62f, h - 142f * u, 20f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
        drawCoins(c, w - 260f * u, 60f * u + 0f, u)
    }

    private fun stat(c: Canvas, x: Float, y: Float, name: String, frac: Float, v: String, col: Int) {
        val u = app.u
        G.text(c, name, x, y + 8f * u, 18f * u, G.WHITE, Paint.Align.LEFT)
        G.rrect(c, x + 90f * u, y - 6f * u, x + 290f * u, y + 12f * u, 8f * u, 0xFF1B1530.toInt())
        G.rrect(c, x + 90f * u, y - 6f * u, x + 90f * u + 200f * u * frac.coerceIn(0.08f, 1f), y + 12f * u, 8f * u, col, false)
        G.text(c, v, x + 300f * u, y + 9f * u, 18f * u, G.WHITE, Paint.Align.LEFT)
    }

    override fun back() { app.go(backTo()) }
}

// ============================================================================ settings
class SettingsScreen(app: GameApp) : Screen(app) {
    private val names = arrayOf("Moi", "Étoile", "Haricot", "Champion", "Ninja", "Fennec", "Tornade", "Comète", "Biscotte", "Pixelito")
    override fun layout() {
        val u = app.u; val w = app.w
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, GREY) { app.go(MainMenu(app)) })
        val items = listOf<Pair<String, () -> Unit>>(
            ("Musique : " + onOff(Save.music)) to { Save.music = !Save.music; Sfx.refreshMusic(); if (Save.music) Sfx.music(1) },
            ("Sons : " + onOff(Save.sfx)) to { Save.sfx = !Save.sfx },
            ("Vibrations : " + onOff(Save.haptics)) to { Save.haptics = !Save.haptics },
            ("Chiffres de dégâts : " + onOff(Save.damageNumbers)) to { Save.damageNumbers = !Save.damageNumbers },
            ("Bots : " + arrayOf("Facile", "Normal", "Difficile")[Save.difficulty]) to { Save.difficulty = (Save.difficulty + 1) % 3 },
            ("Joueurs Battle Royale : ${Save.royaleSize}") to { Save.royaleSize = when (Save.royaleSize) { 15 -> 25; 25 -> 35; 35 -> 40; else -> 15 } },
            ("Taille des joysticks : " + arrayOf("Petite", "Normale", "Grande")[Save.stickSize]) to { Save.stickSize = (Save.stickSize + 1) % 3 },
            ("Afficher les FPS : " + onOff(Save.showFps)) to { Save.showFps = !Save.showFps },
            ("Pseudo : ${Save.playerName}") to { val i = names.indexOf(Save.playerName); Save.playerName = names[(i + 1) % names.size] }
        )
        for ((i, it) in items.withIndex()) {
            val col = i % 2; val row = i / 2
            btns.add(Btn(it.first, w / 2 - 520f * u + col * 530f * u, 120f * u + row * 100f * u, 510f * u, 80f * u, if (i % 3 == 0) PURPLE else if (i % 3 == 1) BLUE else GREY) { it.second(); layout() }.also { b -> b.textSize = 26f })
        }
    }
    private fun onOff(b: Boolean) = if (b) "OUI" else "NON"
    override fun draw(c: Canvas) {
        val u = app.u
        app.menuBackground(c, 0xFF2A2A4A.toInt(), 0xFF4A3D8A.toInt())
        G.text(c, "RÉGLAGES", app.w / 2, 70f * u, 46f * u, G.WHITE)
        drawButtons(c)
        G.text(c, "Star-Guys Battle · jeu 100 % hors-ligne avec bots · sons et graphismes générés par le code", app.w / 2, app.h - 30f * u, 18f * u, 0xAAFFFFFF.toInt())
    }
    override fun back() { app.go(MainMenu(app)) }
}

// ============================================================================ in-game screen
class GameScreen(app: GameApp, val cfg: MatchConfig) : Screen(app) {
    lateinit var match: Match
    private var ready = false
    private var error: String? = null

    override fun enter() {
        try {
            match = Match(app, cfg)
            match.finishCb = { app.go(ResultScreen(app, match)) }
            match.start()
            ready = true
        } catch (e: Throwable) {
            e.printStackTrace()
            error = e.toString()
        }
    }

    override fun update(dt: Float) { if (ready) match.update(dt) }

    override fun draw(c: Canvas) {
        if (ready) { match.draw(c); return }
        app.menuBackground(c)
        G.text(c, if (error != null) "Erreur : $error" else "Chargement…", app.w / 2, app.h / 2, 26f * app.u, G.WHITE)
    }

    override fun touch(e: TouchEvt) { if (ready) match.hud.touch(e) else if (error != null && e.action == TouchEvt.UP) app.go(MainMenu(app)) }
    override fun back() { if (ready) match.paused = !match.paused else app.go(MainMenu(app)) }
    override fun onPause() { if (ready && !match.over) match.paused = true }
    override fun leave() { Sfx.music(1) }
}

// ============================================================================ results with podium
class ResultScreen(app: GameApp, val m: Match) : Screen(app) {
    private var t = 0f
    private val ranking: List<Fighter> = m.mode.ranking()
    private val won = m.won
    private val pl = m.player
    private val place = if (pl != null) ranking.indexOf(pl) + 1 else 0
    private var coins = 0; private var trophies = 0; private var xp = 0
    private val confetti = ArrayList<FloatArray>()

    override fun enter() {
        val k = pl?.kills ?: 0
        coins = 15 + k * 8 + (if (won) 60 else 0) + maxOf(0, 10 - place) * 2
        trophies = if (won) 8 + minOf(k, 5) else if (place in 1..3) 3 else -3
        xp = 40 + k * 12 + (if (won) 100 else 0)
        Save.coins += coins; Save.trophies = maxOf(0, Save.trophies + trophies); Save.xp += xp
        Save.matches += 1; Save.kills += k; if (won) Save.wins += 1
        pl?.let { Save.addHeroTrophies(it.hero.id.coerceAtMost(11), trophies) }
        for (i in 0 until 80) confetti.add(floatArrayOf(rf() * app.w, -rf() * app.h, rf(60f, 180f), rf() * 6f, (i % 6).toFloat()))
        Sfx.music(1)
    }

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("REJOUER", w - 360f * u, h - 130f * u, 320f * u, 100f * u, ORANGE) { app.go(GameScreen(app, MatchConfig(m.cfg.mode, Save.hero, m.cfg.biome, m.cfg.role, m.cfg.creative, m.cfg.creativeKind))) }.also { it.textSize = 40f })
        btns.add(Btn("MENU", w - 600f * u, h - 130f * u, 220f * u, 100f * u, GREY) { app.go(MainMenu(app)) }.also { it.textSize = 36f })
    }

    override fun update(dt: Float) {
        t += dt
        if (won) for (cf in confetti) { cf[1] += cf[2] * dt; cf[3] += dt * 3f; if (cf[1] > app.h) { cf[1] = -20f; cf[0] = rf() * app.w } }
    }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, if (won) 0xFFFFA23D.toInt() else 0xFF3A2E6A.toInt(), if (won) 0xFFFF5E9E.toInt() else 0xFF1B1530.toInt())
        G.text(c, if (won) "VICTOIRE !" else if (place in 2..3) "PODIUM !" else "DÉFAITE", w * 0.3f, 90f * u, 70f * u, if (won) GOLD else G.WHITE)
        G.text(c, m.resultTitle, w * 0.3f, 130f * u, 24f * u, 0xFFFFF3B0.toInt())
        // podium
        val baseY = h * 0.8f
        val cx = w * 0.3f
        val order = intArrayOf(1, 0, 2)
        val heights = floatArrayOf(160f, 110f, 80f)
        for ((slot, idx) in order.withIndex()) {
            val f = ranking.getOrNull(idx) ?: continue
            val x = cx + (slot - 1) * 190f * u
            val ph = heights[idx] * u
            G.ow = 3f * u
            G.rrect(c, x - 80f * u, baseY - ph, x + 80f * u, baseY, 10f * u, intArrayOf(GOLD, 0xFFD8DEE8.toInt(), 0xFFE0A050.toInt())[idx])
            G.text(c, "${idx + 1}", x, baseY - ph / 2 + 22f * u, 56f * u, G.WHITE)
            c.save(); c.translate(x, baseY - ph); c.scale(1.8f * u, 1.8f * u)
            if (idx == 0) c.rotate(sinf(t * 6f) * 8f, 0f, -30f)
            G.ow = 3f
            HeroArt.draw(c, f.hero.id, f.color, t * (if (idx == 0) 2f else 1f), idx == 0 && (t % 1f) < 0.5f, 0.3f, 0.1f)
            c.restore()
            if (idx == 0) { G.star(c, x - 50f * u, baseY - ph - 120f * u + sinf(t * 5f) * 6f * u, 14f * u, 6f * u, 5, t, GOLD); G.star(c, x + 50f * u, baseY - ph - 110f * u + cosf(t * 5f) * 6f * u, 12f * u, 5f * u, 5, -t, 0xFFFF8CC6.toInt()) }
            G.text(c, f.name, x, baseY + 30f * u, 22f * u, if (f === pl) 0xFF7CFF8A.toInt() else G.WHITE)
        }
        // ranking list
        panel(c, w * 0.58f, 40f * u, w - 30f * u, h - 160f * u, u)
        G.text(c, "Classement", w * 0.58f + 30f * u, 82f * u, 28f * u, G.WHITE, Paint.Align.LEFT)
        for ((i, f) in ranking.take(9).withIndex()) {
            val y = 120f * u + i * 34f * u
            val col = if (f === pl) 0xFF7CFF8A.toInt() else G.WHITE
            G.text(c, "${i + 1}.", w * 0.58f + 30f * u, y, 22f * u, col, Paint.Align.LEFT)
            G.text(c, f.name, w * 0.58f + 70f * u, y, 22f * u, col, Paint.Align.LEFT)
            G.text(c, f.hero.name, w * 0.58f + 240f * u, y, 18f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
            G.text(c, m.mode.scoreText(f), w - 60f * u, y, 20f * u, col, Paint.Align.RIGHT)
        }
        if (place > 9 && pl != null) G.text(c, "$place. ${pl.name}  —  ${m.mode.scoreText(pl)}", w * 0.58f + 30f * u, 120f * u + 9 * 34f * u, 22f * u, 0xFF7CFF8A.toInt(), Paint.Align.LEFT)
        // rewards
        val ry = h - 200f * u
        G.text(c, "+$coins pièces   ${if (trophies >= 0) "+" else ""}$trophies trophées   +$xp XP", w * 0.58f + 20f * u, ry, 24f * u, GOLD, Paint.Align.LEFT)
        drawButtons(c)
        if (won) for (cf in confetti) {
            c.save(); c.translate(cf[0], cf[1]); c.rotate(cf[3] * 57f)
            G.rect(c, -8f * u, -4f * u, 8f * u, 4f * u, com.starguys.battle.game.Fx.CONFETTI[cf[4].toInt() % 6], false)
            c.restore()
        }
    }

    override fun back() { app.go(MainMenu(app)) }
}
