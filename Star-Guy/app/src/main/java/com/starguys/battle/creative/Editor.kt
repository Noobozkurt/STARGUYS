package com.starguys.battle.creative

import android.graphics.Canvas
import android.graphics.Paint
import com.starguys.battle.core.Btn
import com.starguys.battle.core.G
import com.starguys.battle.core.GameApp
import com.starguys.battle.core.Save
import com.starguys.battle.core.Screen
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.TouchEvt
import com.starguys.battle.core.clamp
import com.starguys.battle.core.dist
import com.starguys.battle.core.floorInt
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Decor
import com.starguys.battle.game.LH
import com.starguys.battle.game.MatchConfig
import com.starguys.battle.game.T
import com.starguys.battle.game.TileMap
import com.starguys.battle.game.Tl
import com.starguys.battle.game.ZS
import com.starguys.battle.modes.Modes
import com.starguys.battle.ui.GameScreen
import com.starguys.battle.ui.MainMenu
import com.starguys.battle.ui.drawModeIcon
import com.starguys.battle.ui.panel

private const val GREY = 0xFF5B4B8A.toInt()

/** Workshop home: 6 save slots, play / share / import. */
class EditorMenu(app: GameApp) : Screen(app) {
    private var t = 0f

    override fun layout() {
        val u = app.u; val w = app.w
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, GREY) { app.go(MainMenu(app)) })
        btns.add(Btn("Importer un code", w - 360f * u, 20f * u, 330f * u, 70f * u, 0xFF35A0F4.toInt()) { importCode() }.also { it.textSize = 24f })
        val cw = (w - 100f * u) / 2; val ch = 165f * u
        for (s in 0 until Save.SLOTS) {
            val x = 40f * u + (s % 2) * (cw + 20f * u); val y = 110f * u + (s / 2) * (ch + 14f * u)
            val has = Save.creative(s).isNotEmpty()
            val bw = (cw - 50f * u) / 4
            val by = y + ch - 70f * u
            btns.add(Btn(if (has) "Éditer" else "Créer", x + 10f * u, by, bw, 58f * u, 0xFF7CC243.toInt()) { app.go(EditorScreen(app, s)) }.also { it.textSize = 22f })
            if (has) {
                btns.add(Btn("Arène ▶", x + 20f * u + bw, by, bw, 58f * u, 0xFFFF5E7E.toInt()) { play(s, 0) }.also { it.textSize = 20f })
                btns.add(Btn("Course ▶", x + 30f * u + bw * 2, by, bw, 58f * u, 0xFFFF9A3D.toInt()) { play(s, 1) }.also { it.textSize = 20f })
                btns.add(Btn("Partager", x + 40f * u + bw * 3, by, bw, 58f * u, 0xFF35A0F4.toInt()) { share(s) }.also { it.textSize = 20f })
            }
        }
    }

    private fun play(s: Int, kind: Int) {
        app.go(GameScreen(app, MatchConfig(Modes.CREATIVE_PLAY, Save.hero, null, 0, Save.creative(s), kind)))
    }

    private fun share(s: Int) {
        val code = Save.creative(s)
        app.copyText(code)
        app.shareText("Star-Guys Battle", "Joue à ma création « ${Save.creativeName(s)} » dans Star-Guys Battle ! Code (Atelier ▸ Importer) :\n$code")
        app.toast("Code copié ! Partage-le à tes amis")
    }

    private fun importCode() {
        app.readClipboard { text ->
            val lv = CreativeLevel.decode(text)
            if (lv == null) { app.toast("Aucun code valide dans le presse-papiers"); return@readClipboard }
            var slot = (0 until Save.SLOTS).firstOrNull { Save.creative(it).isEmpty() } ?: (Save.SLOTS - 1)
            Save.setCreative(slot, lv.encode()); Save.setCreativeName(slot, lv.name)
            app.toast("Création « ${lv.name} » importée !"); Sfx.play(Sfx.PICKUP)
            layout()
        }
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w
        app.menuBackground(c, 0xFF1F5A3A.toInt(), 0xFF7CC243.toInt())
        G.text(c, "ATELIER JOUEUR", w / 2, 66f * u, 46f * u, G.WHITE)
        val cw = (w - 100f * u) / 2; val ch = 165f * u
        for (s in 0 until Save.SLOTS) {
            val x = 40f * u + (s % 2) * (cw + 20f * u); val y = 110f * u + (s / 2) * (ch + 14f * u)
            panel(c, x, y, x + cw, y + ch, u, 0xCC1B3A2A.toInt())
            val has = Save.creative(s).isNotEmpty()
            drawModeIcon(c, Modes.CREATIVE, x + 50f * u, y + 46f * u, 40f * u, t)
            G.text(c, if (has) Save.creativeName(s) else "Emplacement vide", x + 90f * u, y + 45f * u, 28f * u, if (has) G.WHITE else 0xAAFFFFFF.toInt(), Paint.Align.LEFT)
            G.text(c, if (has) "Construis, teste, partage !" else "Crée ton propre parcours ou arène", x + 90f * u, y + 75f * u, 18f * u, 0xFFFFF3B0.toInt(), Paint.Align.LEFT)
        }
        drawButtons(c)
    }

    override fun back() { app.go(MainMenu(app)) }
}

/** The level editor itself. */
class EditorScreen(app: GameApp, val slot: Int) : Screen(app) {
    private val level: CreativeLevel = CreativeLevel.decode(Save.creative(slot)) ?: CreativeLevel.blank().also { it.name = NAMES[slot % NAMES.size] }
    private val prev = TileMap(level.w, level.h, level.biome)
    private var camX = level.w * T / 2; private var camY = level.h * T / 2; private var zoom = 0.6f
    private var tab = 0
    private var page = 0
    private var tool = Tl.WALL      // tile id, or prop id (tab 1), or decor kind (tab 2)
    private var special = 0         // 0 none, 1 elev+, 2 elev-, 3 eraser, 4 pan
    private var param = 0
    private var t = 0f
    private val pointers = HashMap<Int, FloatArray>()
    private var pinchD = 0f
    private var paintPid = -1
    private var dirty = false

    companion object {
        val NAMES = arrayOf("Parcours fou", "Arène des bulles", "Le grand saut", "Château de cartes", "Toboggan infernal", "Île flottante", "Labyrinthe gluant", "Course de haricots")
        val TILE_TOOLS = intArrayOf(Tl.FLOOR, Tl.WALL, Tl.BOX, Tl.GLASS, Tl.LOW, Tl.BUSH, Tl.WATER, Tl.VOID, Tl.LAVA, Tl.ICE, Tl.CONVEYOR, Tl.SPEED, Tl.COLLAPSE, Tl.GOAL, Tl.LADDER)
    }

    init { syncAll() }

    private fun syncAll() {
        prev.biome = level.biome
        System.arraycopy(level.type, 0, prev.type, 0, level.type.size)
        System.arraycopy(level.elev, 0, prev.elev, 0, level.elev.size)
        System.arraycopy(level.aux, 0, prev.aux, 0, level.aux.size)
        for (i in prev.hp.indices) prev.hp[i] = Tl.maxHp(prev.type[i].toInt())
    }

    private val panelH get() = 150f * app.u
    private fun itemsForTab(): Int = when (tab) { 0 -> TILE_TOOLS.size; 1 -> CreativeLevel.P_COUNT - 1; else -> Decor.COUNT - 1 }
    private fun perPage(): Int = ((app.w - 360f * app.u) / (96f * app.u)).toInt().coerceAtLeast(4)

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 16f * u, 14f * u, 80f * u, 64f * u, GREY) { save(); app.go(EditorMenu(app)) })
        btns.add(Btn(level.biome.label, 110f * u, 14f * u, 260f * u, 64f * u, 0xFF35A0F4.toInt()) {
            level.biome = Biome.values()[(level.biome.ordinal + 1) % Biome.values().size]; syncAll(); dirty = true; layout()
        }.also { it.textSize = 20f })
        btns.add(Btn(level.name, 384f * u, 14f * u, 280f * u, 64f * u, 0xFF8A6AD8.toInt()) {
            val i = NAMES.indexOf(level.name); level.name = NAMES[(i + 1) % NAMES.size]; dirty = true; layout()
        }.also { it.textSize = 20f })
        btns.add(Btn("Sauver", w - 470f * u, 14f * u, 140f * u, 64f * u, 0xFF7CC243.toInt()) { save(); app.toast("Sauvegardé !") }.also { it.textSize = 22f })
        btns.add(Btn("Arène ▶", w - 320f * u, 14f * u, 145f * u, 64f * u, 0xFFFF5E7E.toInt()) { save(); test(0) }.also { it.textSize = 22f })
        btns.add(Btn("Course ▶", w - 165f * u, 14f * u, 150f * u, 64f * u, 0xFFFF9A3D.toInt()) { save(); test(1) }.also { it.textSize = 22f })
        // tabs
        val tabs = arrayOf("Sols", "Objets", "Décor")
        for (i in 0..2) btns.add(Btn(tabs[i], 12f * u, h - panelH + 6f * u + i * 46f * u, 120f * u, 40f * u, if (tab == i) 0xFFFF9A3D.toInt() else GREY) { tab = i; page = 0; special = 0; tool = when (i) { 0 -> Tl.WALL; 1 -> 1; else -> 0 }; layout() }.also { it.textSize = 20f })
        // items
        val pp = perPage()
        val n = itemsForTab()
        val start = page * pp
        for (k in 0 until pp) {
            val idx = start + k
            if (idx >= n) break
            val id = when (tab) { 0 -> TILE_TOOLS[idx]; 1 -> idx; else -> idx }
            val label = when (tab) { 0 -> Tl.names[id]; 1 -> CreativeLevel.PROP_NAMES[id]; else -> Decor.NAMES[id] }
            val b = Btn(label, 145f * u + k * 96f * u, h - panelH + 10f * u, 90f * u, panelH - 30f * u, 0xFF3A2E6A.toInt()) { tool = id; special = 0; layout() }
            b.textSize = 13f
            b.selected = special == 0 && tool == id
            b.icon = { c, x, y, s -> drawToolIcon(c, tab, id, x, y, s) }
            btns.add(b)
        }
        if (n > pp) {
            btns.add(Btn("▶", w - 205f * u, h - panelH + 10f * u, 60f * u, 54f * u, GREY) { page = (page + 1) % ((n + pp - 1) / pp); layout() })
        }
        // special tools (right column)
        val sx = w - 135f * u
        val specials = arrayOf("Haut +", "Haut −", "Gomme", "Main")
        for (i in 0..3) {
            val b = Btn(specials[i], sx + (i % 2) * 66f * u, h - panelH + 6f * u + (i / 2) * 50f * u, 62f * u, 44f * u, if (special == i + 1) 0xFFFF9A3D.toInt() else GREY) { special = if (special == i + 1) 0 else i + 1; layout() }
            b.textSize = 15f
            btns.add(b)
        }
        btns.add(Btn("Rotation ${arrayOf("→", "↓", "←", "↑")[param]}", sx - 70f * u, h - panelH + 106f * u, 198f * u, 38f * u, 0xFF35A0F4.toInt()) { param = (param + 1) % 4; layout() }.also { it.textSize = 16f })
    }

    private fun drawToolIcon(c: Canvas, tab: Int, id: Int, x: Float, y: Float, s: Float) {
        G.ow = 2f
        when (tab) {
            0 -> {
                val col = when (id) {
                    Tl.FLOOR -> level.biome.floorA; Tl.WALL -> level.biome.wallTop; Tl.BOX -> 0xFFD9A066.toInt(); Tl.GLASS -> 0xFFCDEBFF.toInt()
                    Tl.LOW -> 0xFFC28A4E.toInt(); Tl.BUSH -> level.biome.bush; Tl.WATER -> 0xFF4FB5F0.toInt(); Tl.VOID -> 0xFF0B0B24.toInt()
                    Tl.LAVA -> 0xFFFF5E7E.toInt(); Tl.ICE -> 0xFFCFEFFF.toInt(); Tl.CONVEYOR -> 0xFF4A4F63.toInt(); Tl.SPEED -> 0xFFFFE14D.toInt()
                    Tl.COLLAPSE -> 0xFFE8B86A.toInt(); Tl.GOAL -> 0xFFFFFFFF.toInt(); Tl.LADDER -> 0xFF8B5A3C.toInt(); else -> G.WHITE
                }
                G.rrect(c, x - s * 0.8f, y - s * 0.7f, x + s * 0.8f, y + s * 0.7f, 6f, col)
                if (id == Tl.GOAL) { G.rect(c, x - s * 0.8f, y - s * 0.7f, x, y, G.INK, false); G.rect(c, x, y, x + s * 0.8f, y + s * 0.7f, G.INK, false) }
                if (id == Tl.CONVEYOR || id == Tl.SPEED) G.text(c, "»", x, y + s * 0.35f, s, G.WHITE)
            }
            1 -> {
                val cols = intArrayOf(0xFF7CFF8A.toInt(), 0xFFFF5E7E.toInt(), 0xFF6BD3FF.toInt(), 0xFFE84545.toInt(), 0xFFE84545.toInt(), 0xFF7CF0FF.toInt(), 0xFFD8DEE8.toInt(), 0xFFFF5E7E.toInt(), 0xFFC28A4E.toInt(), 0xFFFF5EC4.toInt(), 0xFF4A5480.toInt(), 0xFF7BE84A.toInt(), 0xFFE84545.toInt(), 0xFFD9A066.toInt(), 0xFFFFFFFF.toInt())
                G.circle(c, x, y, s * 0.7f, cols[id])
                G.text(c, CreativeLevel.PROP_NAMES[id].take(2), x, y + s * 0.3f, s * 0.75f, G.WHITE)
            }
            else -> { c.save(); c.translate(x, y + s * 0.8f); c.scale(s / 70f, s / 70f); Decor.drawKind(c, id, t, level.biome); c.restore() }
        }
    }

    private fun save() {
        Save.setCreative(slot, level.encode()); Save.setCreativeName(slot, level.name); dirty = false
    }

    private fun test(kind: Int) {
        if (kind == 1 && level.type.none { it.toInt() == Tl.GOAL }) { app.toast("Ajoute des cases « Arrivée » pour une course !"); return }
        app.go(GameScreen(app, MatchConfig(Modes.CREATIVE_PLAY, Save.hero, null, 0, level.encode(), kind)))
    }

    // ------------------------------------------------------------------ input
    private fun toWorld(sx: Float, sy: Float): FloatArray = floatArrayOf((sx - app.w / 2) / zoom + camX, (sy - (app.h - panelH + 90f * app.u) / 2) / zoom + camY)

    override fun touch(e: TouchEvt) {
        if (e.action == TouchEvt.DOWN && handleButtons(e)) return
        if (e.action != TouchEvt.DOWN && btns.any { it.pid == e.id }) { handleButtons(e); return }
        val inGrid = e.y > 86f * app.u && e.y < app.h - panelH
        when (e.action) {
            TouchEvt.DOWN -> {
                if (!inGrid) return
                pointers[e.id] = floatArrayOf(e.x, e.y)
                if (pointers.size == 2) {
                    paintPid = -1
                    val ps = pointers.values.toList(); pinchD = dist(ps[0][0], ps[0][1], ps[1][0], ps[1][1])
                } else if (pointers.size == 1) {
                    paintPid = e.id
                    if (special != 4) apply(e.x, e.y, true)
                }
            }
            TouchEvt.MOVE -> {
                val p = pointers[e.id] ?: return
                if (pointers.size >= 2) {
                    val ps = pointers.values.toList()
                    val ox = (ps[0][0] + ps[1][0]) / 2; val oy = (ps[0][1] + ps[1][1]) / 2
                    p[0] = e.x; p[1] = e.y
                    val ps2 = pointers.values.toList()
                    val nx = (ps2[0][0] + ps2[1][0]) / 2; val ny = (ps2[0][1] + ps2[1][1]) / 2
                    camX -= (nx - ox) / zoom; camY -= (ny - oy) / zoom
                    val nd = dist(ps2[0][0], ps2[0][1], ps2[1][0], ps2[1][1])
                    if (pinchD > 10f) zoom = clamp(zoom * nd / pinchD, 0.2f, 1.6f)
                    pinchD = nd
                } else if (e.id == paintPid) {
                    if (special == 4) { camX -= (e.x - p[0]) / zoom; camY -= (e.y - p[1]) / zoom }
                    else if (tab == 0 || special in 1..3) apply(e.x, e.y, false)
                    p[0] = e.x; p[1] = e.y
                }
            }
            TouchEvt.UP -> { pointers.remove(e.id); if (e.id == paintPid) paintPid = -1 }
        }
    }

    private var lastTile = -1

    private fun apply(sx: Float, sy: Float, first: Boolean) {
        val wp = toWorld(sx, sy)
        val tx = (wp[0] / T).floorInt(); val ty = (wp[1] / T).floorInt()
        if (tx < 0 || ty < 0 || tx >= level.w || ty >= level.h) return
        val i = ty * level.w + tx
        if (!first && i == lastTile) return
        lastTile = i
        dirty = true
        when (special) {
            1 -> { level.elev[i] = (level.elev[i] + 1).coerceAtMost(7).toByte(); prev.elev[i] = level.elev[i]; return }
            2 -> { level.elev[i] = (level.elev[i] - 1).coerceAtLeast(0).toByte(); prev.elev[i] = level.elev[i]; return }
            3 -> { level.props.removeAll { it[1] == tx && it[2] == ty }; return }
        }
        when (tab) {
            0 -> {
                level.type[i] = tool.toByte(); prev.type[i] = tool.toByte(); prev.hp[i] = Tl.maxHp(tool)
                level.aux[i] = param.toByte(); prev.aux[i] = param.toByte()
            }
            1 -> if (first) {
                level.props.removeAll { it[1] == tx && it[2] == ty }
                if (level.props.size < 590) level.props.add(intArrayOf(tool, tx, ty, param))
                Sfx.play(Sfx.CLICK)
            }
            2 -> if (first) {
                level.props.removeAll { it[1] == tx && it[2] == ty }
                level.props.add(intArrayOf(CreativeLevel.P_DECOR, tx, ty, tool))
                Sfx.play(Sfx.CLICK)
            }
        }
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        c.drawColor(level.biome.bgBottom)
        c.save()
        c.translate(w / 2, (h - panelH + 90f * u) / 2)
        c.scale(zoom, zoom)
        c.translate(-camX, -camY)
        prev.drawGround(c, 0, 0, level.w - 1, level.h - 1, t)
        for (ty in 0 until level.h) { prev.drawRaisedRow(c, ty, 0, level.w - 1, t); prev.drawBushRow(c, ty, 0, level.w - 1, t) }
        // grid
        G.ow = 1f
        for (x in 0..level.w) G.line(c, x * T, 0f, x * T, level.h * T, 1f / zoom, 0x22FFFFFF)
        for (y in 0..level.h) G.line(c, 0f, y * T, level.w * T, y * T, 1f / zoom, 0x22FFFFFF)
        // props
        for (p in level.props) {
            val x = p[1] * T + T / 2; val y = p[2] * T + T / 2 - prev.e(p[1], p[2]) * LH * ZS
            if (p[0] == CreativeLevel.P_DECOR) { c.save(); c.translate(x, y + 20f); c.scale(0.8f, 0.8f); Decor.drawKind(c, p[3], t, level.biome); c.restore() }
            else {
                drawToolIcon(c, 1, p[0], x, y, 24f)
                if (p[0] == CreativeLevel.P_FAN || p[0] == CreativeLevel.P_CANNON) G.text(c, arrayOf("→", "↓", "←", "↑")[p[3] % 4], x, y - 26f, 24f, G.WHITE)
            }
        }
        // elevation labels
        for (ty in 0 until level.h) for (tx in 0 until level.w) {
            val e = prev.e(tx, ty)
            if (e > 0 && zoom > 0.35f) G.text(c, "$e", tx * T + 10f, ty * T - e * LH * ZS + 22f, 18f, 0xCCFFFFFF.toInt(), Paint.Align.LEFT)
        }
        c.restore()
        // UI chrome
        G.rect(c, 0f, 0f, w, 88f * u, 0xEE1B1530.toInt(), false)
        G.rect(c, 0f, h - panelH, w, h, 0xEE1B1530.toInt(), false)
        drawButtons(c)
        val hint = when (special) { 1 -> "Touche des cases pour les surélever"; 2 -> "Touche des cases pour les abaisser"; 3 -> "Touche un objet pour l'effacer"; 4 -> "Glisse pour déplacer la vue"; else -> "1 doigt : poser · 2 doigts : déplacer / zoomer" }
        G.text(c, hint, w / 2, h - panelH - 12f * u, 18f * u, 0xCCFFFFFF.toInt())
        if (dirty) G.circle(c, w - 485f * u, 30f * u, 7f * u, 0xFFFF9A3D.toInt())
    }

    override fun back() { save(); app.go(EditorMenu(app)) }
}
