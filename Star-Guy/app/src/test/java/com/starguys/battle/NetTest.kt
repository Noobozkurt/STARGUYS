package com.starguys.battle

import android.app.Activity
import android.graphics.Canvas
import com.starguys.battle.core.GameApp
import com.starguys.battle.game.Match
import com.starguys.battle.game.MatchConfig
import com.starguys.battle.game.NetPlayer
import com.starguys.battle.game.PlayerInput
import com.starguys.battle.modes.Modes
import com.starguys.battle.net.ClientSession
import com.starguys.battle.net.HostSession
import com.starguys.battle.net.Lan
import com.starguys.battle.net.Lockstep
import com.starguys.battle.net.Net
import com.starguys.battle.net.NetStart
import com.starguys.battle.ui.NetUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * Multiplayer tests: the simulation must be deterministic no matter which player is "local",
 * and two devices connected over a real socket must stay in sync with lockstep.
 */
class NetTest {
    private fun app(): GameApp { val a = GameApp(Activity()); a.resize(1920, 1080); return a }

    private fun randomInput(r: Random, turn: Int): PlayerInput {
        val p = PlayerInput()
        p.mx = r.nextFloat() * 2f - 1f; p.my = r.nextFloat() * 2f - 1f
        if (r.nextFloat() < 0.3f) { p.aiming = true; p.aim = r.nextFloat() * 6.28f }
        if (r.nextFloat() < 0.15f) p.add(PlayerInput.ATTACK, r.nextFloat() * 6.28f, 200f + r.nextFloat() * 300f)
        if (r.nextFloat() < 0.04f) p.add(PlayerInput.JUMP)
        if (r.nextFloat() < 0.03f) p.add(PlayerInput.SUPER, r.nextFloat() * 6.28f, 250f)
        if (r.nextFloat() < 0.02f) p.add(PlayerInput.PING, 600f, 600f)
        if (turn % 50 == 7) p.add(PlayerInput.INTERACT)
        return p
    }

    /** Same match, same inputs, different "local player" and draw cadence => identical simulation. */
    @Test fun deterministicAcrossPerspectives() {
        for (mode in intArrayOf(Modes.ROYALE, Modes.HEIST, Modes.CASTLE, Modes.ZEROG, Modes.FOOTBALL, Modes.INFECTION, Modes.METEOR, Modes.PROPHUNT, Modes.RAID, Modes.HUNTER, Modes.ESCAPE, Modes.RISING, Modes.KOTH, Modes.GUNGAME, Modes.CTF, Modes.GIANT, Modes.MELEE, Modes.MIRROR, Modes.CREATIVE_PLAY)) {
            val players = listOf(NetPlayer(0, "Hôte", 3, 0), NetPlayer(1, "Ami", 7, 1), NetPlayer(2, "Copain", 10, 2))
            val sums = ArrayList<List<Long>>()
            for (local in 0..2) {
                val cfg = MatchConfig(mode, 0, null, 0, seed = 1234567L + mode, difficulty = 1, brSize = 20, players = players, localId = local)
                val m = Match(app(), cfg); m.start()
                val humans = m.fighters.filter { it.human }.sortedBy { it.netId }
                assertEquals("all humans spawned in mode $mode", 3, humans.size)
                assertEquals(local, m.player!!.netId)
                val rs = humans.map { Random(99L + it.netId) }
                val out = ArrayList<Long>()
                val c = Canvas()
                for (turn in 0 until 900) {
                    val inputs = humans.mapIndexed { i, _ -> randomInput(rs[i], turn) }
                    for (tick in 0 until 2) {
                        for ((i, f) in humans.withIndex()) m.applyInput(f, inputs[i], tick == 0)
                        m.simStep(1f / 60f)
                    }
                    if (local == 1 && turn % 3 == 0) { m.frame(1f / 30f); m.draw(c) }
                    if (local == 2 && turn % 7 == 0) m.frame(0.1f)
                    if (turn % 30 == 0) out.add(m.checksum())
                }
                sums.add(out)
            }
            assertEquals("mode $mode: local 0 vs 1", sums[0], sums[1])
            assertEquals("mode $mode: local 0 vs 2", sums[0], sums[2])
            println("mode $mode deterministic over ${sums[0].size} checkpoints")
        }
    }

    /** Host + client over a real loopback TCP socket, each simulating on its own thread. */
    @Test fun lockstepOverTcp() {
        val host = HostSession(Net.WIFI, "Hôte", 2, 0, 8)
        val lan = Lan.Host(host, 0)
        host.acceptor = lan
        val client = ClientSession(Net.WIFI, Lan.connect("127.0.0.1", lan.boundPort), "Ami", 5, 1)
        val t0 = System.currentTimeMillis()
        while (!client.welcomed && System.currentTimeMillis() - t0 < 5000) Thread.sleep(20)
        assertTrue("client welcomed", client.welcomed)
        while (host.playerCount < 2) Thread.sleep(10)
        host.setLobby(Modes.CTF, -1, true, 1)
        Thread.sleep(200)
        assertEquals(Modes.CTF, client.lobby.mode)

        val st = host.startMatch(0, 20)
        val hashes = ConcurrentHashMap<String, Long>()
        val errors = ConcurrentHashMap<String, Throwable>()
        val turnsDone = ConcurrentHashMap<String, Int>()

        fun run(name: String, cfg: MatchConfig, ses: com.starguys.battle.net.Session, seed: Long) = Thread {
            try {
                val m = Match(app(), cfg); m.start()
                val ls = Lockstep(m, ses)
                ls.onHash = { t, h -> hashes["$name:$t"] = h }
                val r = Random(seed)
                var lastExec = -1
                val end = System.currentTimeMillis() + 60_000
                while (ls.exec < 700 && System.currentTimeMillis() < end) {
                    val inp = randomInput(r, ls.exec)
                    m.hud.input.mx = inp.mx; m.hud.input.my = inp.my; m.hud.input.aiming = inp.aiming; m.hud.input.aim = inp.aim
                    for (a in inp.acts) m.hud.input.add(a[0].toInt(), a[1], a[2])
                    ls.update(1f / 60f)
                    lastExec = ls.exec
                    Thread.sleep(2)
                }
                turnsDone[name] = ls.exec
            } catch (e: Throwable) { errors[name] = e }
        }

        // client gets the START from the network
        var cst: NetStart? = null
        val t1 = System.currentTimeMillis()
        while (cst == null && System.currentTimeMillis() - t1 < 5000) { cst = client.pendingStart; Thread.sleep(10) }
        assertTrue("client received start", cst != null)
        val th = run("host", NetUi.cfgFrom(st, 0), host, 1L)
        val tc = run("client", NetUi.cfgFrom(cst!!, client.myId), client, 2L)
        th.start(); tc.start(); th.join(); tc.join()
        errors.values.firstOrNull()?.let { throw it }
        println("turns: $turnsDone")
        assertTrue("host progressed", (turnsDone["host"] ?: 0) >= 600)
        assertTrue("client progressed", (turnsDone["client"] ?: 0) >= 600)
        var compared = 0
        for ((k, v) in hashes) if (k.startsWith("host:")) {
            val other = hashes["client:" + k.removePrefix("host:")] ?: continue
            assertEquals("checksum at $k", v, other); compared++
        }
        println("compared $compared checkpoints, desync=${host.desync}")
        assertTrue(compared > 10)
        assertFalse(host.desync)
        client.close(); host.close()
    }
}
