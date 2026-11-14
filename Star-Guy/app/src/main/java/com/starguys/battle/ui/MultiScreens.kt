package com.starguys.battle.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.starguys.battle.core.Btn
import com.starguys.battle.core.G
import com.starguys.battle.core.GameApp
import com.starguys.battle.core.Save
import com.starguys.battle.core.Screen
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.TW
import com.starguys.battle.core.sinf
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Heroes
import com.starguys.battle.game.MatchConfig
import com.starguys.battle.game.NetPlayer
import com.starguys.battle.modes.Modes
import com.starguys.battle.net.Bt
import com.starguys.battle.net.ClientSession
import com.starguys.battle.net.FoundHost
import com.starguys.battle.net.HostSession
import com.starguys.battle.net.Lan
import com.starguys.battle.net.Net
import com.starguys.battle.net.NetStart

fun wifiIcon(c: Canvas, x: Float, y: Float, s: Float, t: Float) {
    G.ow = s * 0.08f
    for (k in 0..2) {
        val r = s * (0.35f + k * 0.28f)
        val a = 0.55f + 0.45f * sinf(t * 4f - k)
        G.arc(c, x, y + s * 0.45f, r, 225f, 90f, s * 0.13f, G.alpha(TW.WHITE, a))
    }
    G.circle(c, x, y + s * 0.42f, s * 0.12f, TW.WHITE, false)
}

fun btIcon(c: Canvas, x: Float, y: Float, s: Float) {
    val w = s * 0.12f
    G.line(c, x - s * 0.35f, y - s * 0.3f, x + s * 0.3f, y + s * 0.28f, w, TW.WHITE)
    G.line(c, x - s * 0.35f, y + s * 0.3f, x + s * 0.3f, y - s * 0.28f, w, TW.WHITE)
    G.line(c, x, y - s * 0.65f, x, y + s * 0.65f, w, TW.WHITE)
    G.line(c, x, y - s * 0.65f, x + s * 0.3f, y - s * 0.28f, w, TW.WHITE)
    G.line(c, x, y + s * 0.65f, x + s * 0.3f, y + s * 0.28f, w, TW.WHITE)
}

/** Global multiplayer event pump (runs every frame on the game thread). */
object NetUi {
    fun cfgFrom(st: NetStart, localId: Int): MatchConfig {
        val me = st.players.firstOrNull { it.id == localId }
        return MatchConfig(st.mode, me?.hero ?: Save.hero, if (st.biome < 0) null else Biome.values()[st.biome.coerceIn(0, Biome.values().size - 1)],
            st.role, seed = st.seed, difficulty = st.difficulty, brSize = st.brSize, players = st.players, localId = localId, versus = st.versus)
    }

    fun pump(app: GameApp) {
        val s = Net.session ?: return
        while (true) {
            val e = s.events.poll() ?: break
            when {
                e is NetStart && s is ClientSession -> { s.pendingStart = null; app.go(GameScreen(app, cfgFrom(e, s.myId))) }
                e == "closed" -> {
                    if (Net.session === s) Net.session = null
                    app.toast(if (s.isHost) "Session fermée" else "Connexion perdue avec l'hôte")
                    if (app.screen !is ResultScreen) app.go(MultiMenu(app))
                }
                e == "full" -> { Net.leave(); app.toast("Impossible de rejoindre : partie pleine, lancée ou version différente"); app.go(MultiMenu(app)) }
            }
        }
    }
}

private fun kindName(k: Int) = if (k == Net.WIFI) "Wi-Fi local" else "Bluetooth"

// ============================================================================ multiplayer menu
class MultiMenu(app: GameApp) : Screen(app) {
    private var t = 0f
    override fun enter() { Net.leave() }

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, TW.INDIGO_700) { app.go(MainMenu(app)) })
        val cw = (w - 130f * u) / 2
        for (k in 0..1) {
            val x = 45f * u + k * (cw + 40f * u)
            val col = if (k == 0) TW.CYAN_500 else TW.BLUE_600
            btns.add(Btn("Créer une partie", x + 30f * u, h - 300f * u, cw - 60f * u, 92f * u, if (k == 0) TW.EMERALD_500 else TW.VIOLET_600) { app.go(HostLobby(app, k)) }.also { it.textSize = 30f })
            btns.add(Btn("Rejoindre", x + 30f * u, h - 190f * u, cw - 60f * u, 92f * u, col) { app.go(JoinScreen(app, k)) }.also { it.textSize = 30f })
        }
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, TW.SKY_700, TW.VIOLET_600)
        gradText(c, "MULTIJOUEUR LOCAL", w / 2, 72f * u, 46f * u, TW.CYAN_200, TW.SKY_400)
        G.text(c, "Jouez entre amis à côté de vous — sans Internet", w / 2, 108f * u, 20f * u, TW.SKY_100)
        val cw = (w - 130f * u) / 2
        for (k in 0..1) {
            val x = 45f * u + k * (cw + 40f * u)
            panel(c, x, 135f * u, x + cw, h - 70f * u, u)
            val icx = x + 90f * u; val icy = 215f * u
            G.depth = true; G.circle(c, icx, icy, 48f * u, if (k == 0) TW.CYAN_500 else TW.BLUE_600); G.depth = false
            if (k == 0) wifiIcon(c, icx, icy - 14f * u, 42f * u, t) else btIcon(c, icx, icy, 50f * u)
            G.text(c, kindName(k), x + 160f * u, 205f * u, 34f * u, TW.WHITE, Paint.Align.LEFT)
            val desc = if (k == 0) "Tous les téléphones sur le même Wi-Fi (ou le partage de connexion de l'un d'eux). Jusqu'à 8 joueurs."
                else "Sans Wi-Fi : connexion directe Bluetooth. Jusqu'à 4 joueurs. L'hôte doit être visible."
            var y = 240f * u
            for (l in G.wrap(desc, 19f * u, cw - 190f * u)) { G.text(c, l, x + 160f * u, y, 19f * u, TW.SLATE_200, Paint.Align.LEFT); y += 25f * u }
        }
        drawButtons(c)
    }

    override fun back() { app.go(MainMenu(app)) }
}

// ============================================================================ shared lobby drawing
private fun drawPlayers(c: Canvas, app: GameApp, players: List<NetPlayer>, myId: Int, x: Float, y: Float, w: Float, t: Float) {
    val u = app.u
    val cols = 4
    val cw = (w - (cols - 1) * 12f * u) / cols
    val ch = 150f * u
    for ((i, p) in players.withIndex()) {
        val cx = x + (i % cols) * (cw + 12f * u); val cy = y + (i / cols) * (ch + 12f * u)
        G.glass(c, cx, cy, cx + cw, cy + ch, 18f * u, u, if (p.id == myId) TW.EMERALD_900 else TW.INDIGO_950)
        val h = Heroes.byId(p.hero.coerceIn(0, 11))
        pedestal(c, cx + cw / 2, cy + ch * 0.68f, 34f * u, if (p.id == 0) TW.AMBER_400 else TW.SKY_400, t + i)
        hero3d(c, h.id, h.colors[p.skin.coerceIn(0, 2)], cx + cw / 2, cy + ch * 0.68f, 1.2f * u, t + i)
        G.text(c, p.name + if (p.id == 0) " ★" else "", cx + cw / 2, cy + ch - 26f * u, 18f * u, if (p.id == myId) TW.EMERALD_300 else TW.WHITE)
        G.text(c, h.name, cx + cw / 2, cy + ch - 8f * u, 14f * u, TW.AMBER_200)
    }
}

// ============================================================================ host lobby
class HostLobby(app: GameApp, val kind: Int) : Screen(app) {
    private var t = 0f
    private var role = 0
    private var error: String? = null
    private val host: HostSession? get() = Net.session as? HostSession
    private var modeIdx = Save.lastMode.let { if (it == Modes.CREATIVE) 0 else it }

    override fun enter() {
        val s = Net.session
        if (s is HostSession && !s.closed && s.kind == kind) { modeIdx = s.lobby.mode; return }
        Net.leave()
        if (kind == Net.WIFI) startWifi() else {
            app.requestPermissions(Bt.permissions()) { ok ->
                if (!ok) { error = "Permission Bluetooth refusée"; return@requestPermissions }
                startBt()
            }
        }
    }

    private fun startWifi() {
        try {
            val h = HostSession(Net.WIFI, Save.playerName, Save.hero, Save.skin(Save.hero), 8)
            h.acceptor = Lan.Host(h)
            Net.session = h
            push()
        } catch (e: Throwable) { error = "Impossible d'ouvrir la partie Wi-Fi : ${e.message}" }
    }

    private fun startBt() {
        val ad = Bt.adapter(app.act)
        if (ad == null) { error = "Bluetooth indisponible sur cet appareil"; return }
        try { if (!ad.isEnabled) { Bt.requestEnable(app.act); error = "Active le Bluetooth puis reviens ici" ; return } } catch (_: Throwable) { }
        val h = HostSession(Net.BT, Save.playerName, Save.hero, Save.skin(Save.hero), 4)
        h.acceptor = Bt.Host(app.act, h)
        Net.session = h
        Bt.requestDiscoverable(app.act)
        push()
    }

    private fun push() { host?.setLobby(modeIdx, Save.biome, host?.lobby?.versus ?: false, Save.difficulty) }

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, TW.INDIGO_700) { Net.leave(); app.go(MultiMenu(app)) })
        val rx = w * 0.58f
        btns.add(Btn("‹", rx, 120f * u, 60f * u, 64f * u, TW.INDIGO_500) { modeIdx = (modeIdx + Modes.defs.size - 2) % (Modes.defs.size - 1); role = 0; push(); layout() }.also { it.textSize = 40f })
        btns.add(Btn("›", w - 100f * u, 120f * u, 60f * u, 64f * u, TW.INDIGO_500) { modeIdx = (modeIdx + 1) % (Modes.defs.size - 1); role = 0; push(); layout() }.also { it.textSize = 40f })
        btns.add(Btn("‹", rx, 214f * u, 60f * u, 56f * u, TW.INDIGO_500) { Save.biome = if (Save.biome <= -1) Biome.values().size - 1 else Save.biome - 1; push() }.also { it.textSize = 34f })
        btns.add(Btn("›", w - 100f * u, 214f * u, 60f * u, 56f * u, TW.INDIGO_500) { Save.biome = if (Save.biome >= Biome.values().size - 1) -1 else Save.biome + 1; push() }.also { it.textSize = 34f })
        btns.add(Btn(if (host?.lobby?.versus == true) "Humains : adversaires" else "Humains : coéquipiers", rx, 292f * u, 300f * u, 58f * u, TW.ROSE_500) {
            host?.let { it.setLobby(modeIdx, Save.biome, !it.lobby.versus, Save.difficulty) }; layout()
        }.also { it.textSize = 20f })
        btns.add(Btn("Bots : " + arrayOf("Facile", "Normal", "Difficile")[Save.difficulty], rx + 315f * u, 292f * u, w - rx - 355f * u, 58f * u, TW.SLATE_600) {
            Save.difficulty = (Save.difficulty + 1) % 3; push(); layout()
        }.also { it.textSize = 20f })
        val roles = Modes.def(modeIdx).roles
        if (roles != null) for ((i, r) in roles.withIndex()) {
            val bw = (w - rx - 40f * u - (roles.size - 1) * 10f * u) / roles.size
            btns.add(Btn(r, rx + i * (bw + 10f * u), 372f * u, bw, 52f * u, TW.BLUE_500) { role = i; layout() }.also { it.textSize = 20f; it.selected = i == role })
        }
        btns.add(Btn("Mon héros", 40f * u, h - 108f * u, 230f * u, 80f * u, TW.PINK_500) { app.go(HeroSelect(app) { HostLobby(app, kind) }) }.also { it.textSize = 26f })
        if (kind == Net.BT) btns.add(Btn("Être visible", 290f * u, h - 108f * u, 230f * u, 80f * u, TW.BLUE_600) { Bt.requestDiscoverable(app.act) }.also { it.textSize = 24f })
        btns.add(Btn("LANCER !", w - 340f * u, h - 130f * u, 300f * u, 104f * u, TW.ORANGE_500) { launch() }.also { it.textSize = 44f })
    }

    private fun launch() {
        val h = host ?: run { app.toast("Session non prête"); return }
        Save.lastMode = modeIdx
        val st = h.startMatch(role, Save.royaleSize)
        app.go(GameScreen(app, NetUi.cfgFrom(st, 0)))
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, TW.EMERALD_700, TW.SKY_600)
        val hs = host
        gradText(c, "SALON — ${kindName(kind).uppercase()}", w * 0.29f, 66f * u, 38f * u, TW.WHITE, TW.CYAN_200)
        val info = when {
            error != null -> error!!
            hs == null -> "Ouverture…"
            kind == Net.WIFI -> "Adresse : ${Lan.localIp()}   ·   Les autres touchent « Rejoindre »"
            else -> "Les autres touchent « Rejoindre » puis ton téléphone"
        }
        G.text(c, info, w * 0.29f, 100f * u, 19f * u, if (error != null) TW.ROSE_300 else TW.SKY_100)
        panel(c, 30f * u, 120f * u, w * 0.56f, h - 125f * u, u)
        G.text(c, "Joueurs (${hs?.playerCount ?: 0}/${hs?.maxPlayers ?: 0})", 52f * u, 152f * u, 22f * u, TW.AMBER_200, Paint.Align.LEFT)
        if (hs != null) drawPlayers(c, app, hs.playersSnapshot(), 0, 50f * u, 166f * u, w * 0.56f - 70f * u, t)
        G.text(c, "Les places restantes sont prises par des bots", w * 0.29f, h - 140f * u, 16f * u, TW.SLATE_300)
        // settings column
        val rx = w * 0.58f
        panel(c, rx - 14f * u, 100f * u, w - 26f * u, 440f * u, u)
        val d = Modes.def(modeIdx)
        drawModeIcon(c, modeIdx, rx + 100f * u, 152f * u, 30f * u, t)
        G.text(c, d.name, (rx + w - 40f * u) / 2 + 30f * u, 160f * u, 25f * u, TW.WHITE)
        val bName = if (Save.biome < 0) "Biome aléatoire" else Biome.values()[Save.biome].label
        G.text(c, bName, (rx + w - 40f * u) / 2, 250f * u, 22f * u, TW.LIME_300)
        if (d.roles != null) G.text(c, "Ton rôle", rx, 366f * u, 16f * u, TW.AMBER_200, Paint.Align.LEFT)
        drawButtons(c)
    }

    override fun back() { Net.leave(); app.go(MultiMenu(app)) }
}

// ============================================================================ join
class JoinScreen(app: GameApp, val kind: Int) : Screen(app) {
    private var t = 0f
    private var finder: AutoCloseable? = null
    private var lastCount = -1
    @Volatile private var connecting: String? = null
    @Volatile private var failMsg: String? = null
    private var error: String? = null
    private var keypad = false
    private var ip = ""
    private val hosts: Map<String, FoundHost>
        get() = when (val f = finder) { is Lan.Finder -> f.hosts; is Bt.Finder -> f.hosts; else -> emptyMap() }

    override fun enter() {
        Net.leave()
        if (kind == Net.WIFI) finder = Lan.Finder(app.act)
        else app.requestPermissions(Bt.permissions()) { ok ->
            if (!ok) { error = "Permission Bluetooth refusée"; return@requestPermissions }
            val ad = Bt.adapter(app.act)
            if (ad == null) { error = "Bluetooth indisponible"; return@requestPermissions }
            try { if (!ad.isEnabled) Bt.requestEnable(app.act) } catch (_: Throwable) { }
            finder = Bt.Finder(app.act)
        }
    }

    override fun leave() { try { finder?.close() } catch (_: Throwable) { }; finder = null }

    private fun join(fh: FoundHost) = connect(fh.name) { fh.connect() }

    private fun connect(label: String, open: () -> com.starguys.battle.net.Link) {
        if (connecting != null) return
        connecting = label; failMsg = null
        Thread {
            try {
                val link = open()
                val s = ClientSession(kind, link, Save.playerName, Save.hero, Save.skin(Save.hero))
                val t0 = System.currentTimeMillis()
                while (!s.welcomed && !s.closed && System.currentTimeMillis() - t0 < 6000) Thread.sleep(50)
                if (s.welcomed) { Net.session = s; app.post { app.go(ClientLobby(app)) } }
                else { s.close(); failMsg = s.status.ifEmpty { "L'hôte ne répond pas" } }
            } catch (e: Throwable) { failMsg = "Connexion impossible : ${e.message ?: e.javaClass.simpleName}" }
            connecting = null
        }.start()
    }

    override fun layout() {
        val u = app.u; val w = app.w; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, TW.INDIGO_700) { app.go(MultiMenu(app)) })
        if (kind == Net.WIFI) btns.add(Btn("IP manuelle", w - 280f * u, 20f * u, 250f * u, 70f * u, TW.VIOLET_600) { keypad = !keypad; layout() }.also { it.textSize = 24f })
        else btns.add(Btn("Rechercher", w - 280f * u, 20f * u, 250f * u, 70f * u, TW.BLUE_600) { (finder as? Bt.Finder)?.scan() }.also { it.textSize = 24f })
        val list = hosts.values.sortedByDescending { it.seen }.take(8)
        for ((i, fh) in list.withIndex()) {
            val b = Btn(fh.name, 60f * u, 130f * u + i * 66f * u, w * 0.55f, 58f * u, if (kind == Net.WIFI) TW.CYAN_600 else TW.BLUE_600) { join(fh) }
            b.sub = fh.detail; b.textSize = 24f
            btns.add(b)
        }
        lastCount = list.size
        if (keypad) {
            val kx = w * 0.66f; val ky = 150f * u; val ks = 84f * u
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
            for ((i, k) in keys.withIndex()) {
                btns.add(Btn(k, kx + (i % 3) * (ks + 10f * u), ky + 70f * u + (i / 3) * (ks * 0.78f + 8f * u), ks, ks * 0.78f, TW.SLATE_600) {
                    if (k == "⌫") ip = ip.dropLast(1) else if (ip.length < 15) ip += k
                }.also { it.textSize = 32f })
            }
            btns.add(Btn("Connexion", kx, ky + 70f * u + 4 * (ks * 0.78f + 8f * u), ks * 3 + 20f * u, 70f * u, TW.EMERALD_500) {
                val target = ip
                if (target.count { it == '.' } == 3) connect(target) { Lan.connect(target) } else app.toast("Adresse IP invalide")
            }.also { it.textSize = 28f })
        }
    }

    override fun update(dt: Float) {
        t += dt
        if ((t * 2f).toInt() != ((t - dt) * 2f).toInt()) {
            val n = hosts.values.count()
            if (n != lastCount) layout()
        }
        failMsg?.let { app.toast(it); failMsg = null }
    }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, TW.SKY_800, TW.INDIGO_600)
        gradText(c, "REJOINDRE — ${kindName(kind).uppercase()}", w * 0.4f, 66f * u, 38f * u, TW.WHITE, TW.SKY_200)
        panel(c, 40f * u, 112f * u, w * 0.55f + 80f * u, h - 40f * u, u)
        if (hosts.isEmpty()) {
            val msg = error ?: if (kind == Net.WIFI) "Recherche des parties sur ton Wi-Fi…" else "Recherche des téléphones Bluetooth…"
            G.text(c, msg, (w * 0.55f + 120f * u) / 2, h * 0.45f, 24f * u, if (error != null) TW.ROSE_300 else TW.SKY_100)
            val dots = ".".repeat(1 + (t * 2f).toInt() % 3)
            G.text(c, dots, (w * 0.55f + 120f * u) / 2, h * 0.45f + 36f * u, 40f * u, TW.CYAN_300)
            if (kind == Net.WIFI) G.text(c, "Pas de partie ? Utilise « IP manuelle » avec l'adresse affichée chez l'hôte.", (w * 0.55f + 120f * u) / 2, h * 0.62f, 17f * u, TW.SLATE_300)
            else G.text(c, "Astuce : sur l'hôte, touche « Être visible ».", (w * 0.55f + 120f * u) / 2, h * 0.62f, 17f * u, TW.SLATE_300)
        }
        if (keypad) {
            panel(c, w * 0.64f, 112f * u, w - 30f * u, h - 40f * u, u)
            G.rrect(c, w * 0.66f, 140f * u, w - 50f * u, 200f * u, 14f * u, TW.SLATE_900, false)
            G.text(c, ip.ifEmpty { "192.168.x.x" }, (w * 0.66f + w - 50f * u) / 2, 182f * u, 30f * u, if (ip.isEmpty()) TW.SLATE_500 else TW.CYAN_200)
        }
        drawButtons(c)
        connecting?.let {
            G.glass(c, w / 2 - 300f * u, h / 2 - 50f * u, w / 2 + 300f * u, h / 2 + 50f * u, 22f * u, u)
            G.text(c, "Connexion à $it…", w / 2, h / 2 + 10f * u, 28f * u, TW.AMBER_200)
        }
    }

    override fun back() { app.go(MultiMenu(app)) }
}

// ============================================================================ client lobby
class ClientLobby(app: GameApp) : Screen(app) {
    private var t = 0f
    private val ses get() = Net.session

    override fun enter() {
        val s = ses
        if (s is ClientSession) s.pendingStart?.let { st -> s.pendingStart = null; app.go(GameScreen(app, NetUi.cfgFrom(st, s.myId))) }
    }

    override fun layout() {
        val u = app.u; val h = app.h
        btns.clear()
        btns.add(Btn("◀", 20f * u, 20f * u, 90f * u, 70f * u, TW.INDIGO_700) { Net.leave(); app.go(MultiMenu(app)) })
        btns.add(Btn("Mon héros", 40f * u, h - 108f * u, 230f * u, 80f * u, TW.PINK_500) { app.go(HeroSelect(app) { ClientLobby(app) }) }.also { it.textSize = 26f })
    }

    override fun update(dt: Float) {
        t += dt
        if (ses == null) app.go(MultiMenu(app))
    }

    override fun draw(c: Canvas) {
        val u = app.u; val w = app.w; val h = app.h
        app.menuBackground(c, TW.BLUE_700, TW.FUCHSIA_500)
        val s = ses ?: return
        gradText(c, "SALON — ${kindName(s.kind).uppercase()}", w * 0.29f, 66f * u, 38f * u, TW.WHITE, TW.PINK_200)
        G.text(c, "En attente du lancement par l'hôte" + ".".repeat(1 + (t * 2f).toInt() % 3), w * 0.29f, 100f * u, 20f * u, TW.AMBER_200)
        panel(c, 30f * u, 120f * u, w * 0.56f, h - 125f * u, u)
        val ps = s.playersSnapshot()
        G.text(c, "Joueurs (${ps.size})", 52f * u, 152f * u, 22f * u, TW.AMBER_200, Paint.Align.LEFT)
        drawPlayers(c, app, ps, s.myId, 50f * u, 166f * u, w * 0.56f - 70f * u, t)
        val rx = w * 0.58f
        panel(c, rx - 14f * u, 120f * u, w - 26f * u, 400f * u, u)
        val m = s.lobby.mode.coerceIn(0, Modes.defs.size - 1)
        val d = Modes.def(m)
        drawModeIcon(c, m, rx + 70f * u, 190f * u, 40f * u, t)
        G.text(c, d.name, rx + 130f * u, 185f * u, 28f * u, TW.WHITE, Paint.Align.LEFT)
        var y = 250f * u
        for (l in G.wrap(d.desc, 18f * u, w - rx - 70f * u)) { G.text(c, l, rx + 10f * u, y, 18f * u, TW.SLATE_200, Paint.Align.LEFT); y += 24f * u }
        val b = s.lobby.biome
        G.text(c, if (b < 0) "Biome aléatoire" else Biome.values()[b.coerceIn(0, Biome.values().size - 1)].label, rx + 10f * u, y + 16f * u, 20f * u, TW.LIME_300, Paint.Align.LEFT)
        G.text(c, if (s.lobby.versus) "Humains : adversaires" else "Humains : coéquipiers", rx + 10f * u, y + 44f * u, 20f * u, TW.ROSE_300, Paint.Align.LEFT)
        drawButtons(c)
    }

    override fun back() { Net.leave(); app.go(MultiMenu(app)) }
}

@Suppress("unused") private fun keepSfx() = Sfx.CLICK
