package com.starguys.battle.net

import com.starguys.battle.game.NetPlayer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Local multiplayer (Wi-Fi LAN or Bluetooth) using deterministic lockstep:
 * every device runs the same simulation; only player inputs travel.
 * Clients send their input for a future turn to the host; the host bundles everybody's input
 * for that turn and broadcasts it; nobody simulates a turn before having its bundle.
 */
object Msg {
    const val HELLO = 1; const val WELCOME = 2; const val LOBBY = 3; const val HERO = 4; const val START = 5
    const val INPUT = 6; const val TURN = 7; const val HASH = 8; const val DESYNC = 9; const val BYE = 10
    const val FULL = 11
    const val VERSION = 3

    fun build(f: (DataOutputStream) -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        val o = DataOutputStream(bos)
        f(o); o.flush()
        return bos.toByteArray()
    }

    fun reader(b: ByteArray) = DataInputStream(ByteArrayInputStream(b))
}

/** A bidirectional, framed message pipe (TCP socket or Bluetooth RFCOMM socket). */
open class Link(val label: String, private val ins: InputStream, private val outs: OutputStream, private val closer: () -> Unit) {
    @Volatile var open = true
        private set
    private val outQ = LinkedBlockingQueue<ByteArray>()
    var id = -1
    @Volatile var lastHeard = System.currentTimeMillis()

    fun start(onMsg: (Link, Int, ByteArray) -> Unit, onClose: (Link) -> Unit) {
        Thread({
            try {
                val i = DataInputStream(ins)
                while (open) {
                    val len = i.readInt()
                    if (len < 1 || len > 2_000_000) break
                    val type = i.readUnsignedByte()
                    val p = ByteArray(len - 1)
                    i.readFully(p)
                    lastHeard = System.currentTimeMillis()
                    onMsg(this, type, p)
                }
            } catch (_: Throwable) { }
            close()
            onClose(this)
        }, "net-read-$label").apply { isDaemon = true }.start()
        Thread({
            try {
                val o = DataOutputStream(outs)
                while (open) {
                    val b = outQ.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    o.write(b)
                    if (outQ.isEmpty()) o.flush()
                }
            } catch (_: Throwable) { close() }
        }, "net-write-$label").apply { isDaemon = true }.start()
    }

    fun send(type: Int, payload: ByteArray = ByteArray(0)) {
        if (!open) return
        val b = ByteArray(5 + payload.size)
        val len = payload.size + 1
        b[0] = (len ushr 24).toByte(); b[1] = (len ushr 16).toByte(); b[2] = (len ushr 8).toByte(); b[3] = len.toByte()
        b[4] = type.toByte()
        System.arraycopy(payload, 0, b, 5, payload.size)
        outQ.offer(b)
    }

    fun close() {
        if (!open) return
        open = false
        try { closer() } catch (_: Throwable) { }
    }
}

class LobbyState {
    @Volatile var mode = 0
    @Volatile var biome = -1
    @Volatile var versus = false
    @Volatile var difficulty = 1
    val players = ArrayList<NetPlayer>()
}

/** Parameters of a networked match, identical on every device. */
class NetStart(val seed: Long, val mode: Int, val biome: Int, val role: Int, val difficulty: Int, val brSize: Int,
               val versus: Boolean, val players: List<NetPlayer>) {
    fun write(o: DataOutputStream) {
        o.writeLong(seed); o.writeByte(mode); o.writeByte(biome); o.writeByte(role); o.writeByte(difficulty); o.writeByte(brSize)
        o.writeBoolean(versus); o.writeByte(players.size)
        for (p in players) { o.writeByte(p.id); o.writeUTF(p.name); o.writeByte(p.hero); o.writeByte(p.skin) }
    }
    companion object {
        fun read(i: DataInputStream): NetStart {
            val seed = i.readLong(); val mode = i.readByte().toInt(); val biome = i.readByte().toInt(); val role = i.readByte().toInt()
            val diff = i.readByte().toInt(); val br = i.readUnsignedByte(); val vs = i.readBoolean()
            val n = i.readUnsignedByte()
            val ps = ArrayList<NetPlayer>()
            for (k in 0 until n) ps.add(NetPlayer(i.readUnsignedByte(), i.readUTF(), i.readUnsignedByte(), i.readUnsignedByte()))
            return NetStart(seed, mode, biome, role, diff, br, vs, ps)
        }
    }
}

object Net {
    @Volatile var session: Session? = null
    const val WIFI = 0
    const val BT = 1

    fun leave() { session?.close(); session = null }
}

abstract class Session(val kind: Int) {
    abstract val isHost: Boolean
    @Volatile var myId = 0
    val lobby = LobbyState()
    /** turn bundles ready to simulate (turn -> bundle bytes) */
    val turns = ConcurrentHashMap<Int, ByteArray>()
    /** UI events: "start", "closed", "desync" */
    val events = ConcurrentLinkedQueue<Any>()
    @Volatile var desync = false
    @Volatile var closed = false
    @Volatile var status = ""
    @Volatile var inMatch = false

    abstract fun sendInput(turn: Int, data: ByteArray)
    abstract fun sendHash(turn: Int, h: Long)
    abstract fun close()
    open fun tick() {}
    open fun setMyHero(hero: Int, skin: Int) {}

    fun playersSnapshot(): List<NetPlayer> = synchronized(lobby) { lobby.players.map { NetPlayer(it.id, it.name, it.hero, it.skin) } }

    protected fun lobbyBytes(): ByteArray = Msg.build { o ->
        synchronized(lobby) {
            o.writeByte(lobby.mode); o.writeByte(lobby.biome); o.writeBoolean(lobby.versus); o.writeByte(lobby.difficulty)
            o.writeByte(lobby.players.size)
            for (p in lobby.players) { o.writeByte(p.id); o.writeUTF(p.name); o.writeByte(p.hero); o.writeByte(p.skin) }
        }
    }

    protected fun readLobby(b: ByteArray) {
        val i = Msg.reader(b)
        synchronized(lobby) {
            lobby.mode = i.readByte().toInt(); lobby.biome = i.readByte().toInt(); lobby.versus = i.readBoolean(); lobby.difficulty = i.readByte().toInt()
            val n = i.readUnsignedByte()
            lobby.players.clear()
            for (k in 0 until n) lobby.players.add(NetPlayer(i.readUnsignedByte(), i.readUTF(), i.readUnsignedByte(), i.readUnsignedByte()))
        }
    }
}

/** The host: accepts players, relays lockstep turns, detects desyncs. Player id 0. */
class HostSession(kind: Int, name: String, hero: Int, skin: Int, val maxPlayers: Int) : Session(kind) {
    override val isHost = true
    private val links = ConcurrentHashMap<Int, Link>()
    private var nextId = 1
    private val inputs = HashMap<Int, HashMap<Int, ByteArray>>()
    private val dropped = HashSet<Int>()
    private var nextBroadcast = 0
    private var active = ArrayList<Int>()
    private val hashes = HashMap<Int, Long>()
    private val clientHashes = HashMap<Int, HashMap<Int, Long>>()
    private var waitSince = 0L
    var acceptor: AutoCloseable? = null

    init {
        myId = 0
        lobby.players.add(NetPlayer(0, name, hero, skin))
    }

    val playerCount get() = synchronized(lobby) { lobby.players.size }

    /** A transport accepted a new connection. */
    fun onLink(l: Link) {
        l.start({ link, type, p -> onMsg(link, type, p) }, { link -> onClosed(link) })
    }

    private fun onMsg(l: Link, type: Int, p: ByteArray) {
        when (type) {
            Msg.HELLO -> {
                val i = Msg.reader(p)
                val ver = i.readInt(); val name = i.readUTF(); val hero = i.readUnsignedByte(); val skin = i.readUnsignedByte()
                if (ver != Msg.VERSION || inMatch || playerCount >= maxPlayers) { l.send(Msg.FULL); l.close(); return }
                val id: Int
                synchronized(lobby) { id = nextId++; lobby.players.add(NetPlayer(id, name.take(16), hero.coerceIn(0, 11), skin.coerceIn(0, 2))) }
                l.id = id
                links[id] = l
                l.send(Msg.WELCOME, Msg.build { it.writeByte(id) })
                broadcastLobby()
            }
            Msg.HERO -> {
                val i = Msg.reader(p)
                val hero = i.readUnsignedByte(); val skin = i.readUnsignedByte()
                synchronized(lobby) { lobby.players.firstOrNull { it.id == l.id }?.let { it.hero = hero.coerceIn(0, 11); it.skin = skin.coerceIn(0, 2) } }
                broadcastLobby()
            }
            Msg.INPUT -> {
                val i = Msg.reader(p)
                val turn = i.readInt()
                val data = ByteArray(p.size - 4); i.readFully(data)
                synchronized(this) {
                    if (!inMatch || turn < nextBroadcast) return
                    inputs.getOrPut(turn) { HashMap() }[l.id] = data
                    tryBroadcast()
                }
            }
            Msg.HASH -> {
                val i = Msg.reader(p)
                val turn = i.readInt(); val h = i.readLong()
                synchronized(this) {
                    clientHashes.getOrPut(turn) { HashMap() }[l.id] = h
                    checkHash(turn)
                }
            }
            Msg.BYE -> l.close()
        }
    }

    private fun onClosed(l: Link) {
        if (l.id < 0) return
        links.remove(l.id)
        synchronized(this) {
            if (inMatch) { dropped.add(l.id); tryBroadcast() }
        }
        if (!inMatch) { synchronized(lobby) { lobby.players.removeAll { it.id == l.id } }; broadcastLobby() }
    }

    fun broadcastLobby() {
        val b = lobbyBytes()
        for (l in links.values) l.send(Msg.LOBBY, b)
    }

    fun setLobby(mode: Int, biome: Int, versus: Boolean, difficulty: Int) {
        lobby.mode = mode; lobby.biome = biome; lobby.versus = versus; lobby.difficulty = difficulty
        broadcastLobby()
    }

    override fun setMyHero(hero: Int, skin: Int) {
        synchronized(lobby) { lobby.players.firstOrNull { it.id == 0 }?.let { it.hero = hero; it.skin = skin } }
        broadcastLobby()
    }

    /** Starts a match for everybody; returns the start parameters for the host's own match. */
    fun startMatch(role: Int, brSize: Int): NetStart {
        val st = NetStart(System.nanoTime() xor 0x5DEECE66DL, lobby.mode, lobby.biome, role, lobby.difficulty, brSize, lobby.versus, playersSnapshot())
        synchronized(this) {
            inputs.clear(); dropped.clear(); hashes.clear(); clientHashes.clear(); turns.clear()
            nextBroadcast = Lockstep.DELAY
            active = ArrayList(st.players.map { it.id })
            inMatch = true; desync = false
            waitSince = System.currentTimeMillis()
        }
        val b = Msg.build { st.write(it) }
        for (l in links.values) l.send(Msg.START, b)
        return st
    }

    fun endMatch() {
        synchronized(this) { inMatch = false; inputs.clear(); turns.clear() }
        synchronized(lobby) { lobby.players.removeAll { it.id != 0 && !links.containsKey(it.id) } }
        broadcastLobby()
    }

    override fun sendInput(turn: Int, data: ByteArray) {
        synchronized(this) {
            if (!inMatch || turn < nextBroadcast) return
            inputs.getOrPut(turn) { HashMap() }[0] = data
            tryBroadcast()
        }
    }

    private fun tryBroadcast() {
        while (true) {
            val got = inputs[nextBroadcast] ?: HashMap()
            for (id in active) if (id !in dropped && !got.containsKey(id)) return
            val t = nextBroadcast
            val bundle = Msg.build { o ->
                o.writeInt(t)
                o.writeByte(active.size)
                for (id in active) {
                    o.writeByte(id)
                    val d = got[id]
                    if (id in dropped || d == null) { o.writeByte(1); o.writeShort(0) }
                    else { o.writeByte(0); o.writeShort(d.size); o.write(d) }
                }
            }
            inputs.remove(t)
            turns[t] = bundle
            for (l in links.values) l.send(Msg.TURN, bundle)
            nextBroadcast++
            waitSince = System.currentTimeMillis()
        }
    }

    override fun sendHash(turn: Int, h: Long) {
        synchronized(this) { hashes[turn] = h; checkHash(turn); hashes.keys.removeAll { it < turn - 600 } }
    }

    private fun checkHash(turn: Int) {
        val mine = hashes[turn] ?: return
        val others = clientHashes[turn] ?: return
        for ((_, h) in others) if (h != mine && !desync) {
            desync = true
            for (l in links.values) l.send(Msg.DESYNC, Msg.build { it.writeInt(turn) })
        }
        clientHashes.keys.removeAll { it < turn - 600 }
    }

    /** Called every frame: drops players who stopped sending inputs. */
    override fun tick() {
        if (!inMatch) return
        val now = System.currentTimeMillis()
        val stuck = synchronized(this) {
            if (now - waitSince < 8000) emptyList() else {
                val got = inputs[nextBroadcast] ?: HashMap()
                active.filter { it != 0 && it !in dropped && !got.containsKey(it) }
            }
        }
        for (id in stuck) links[id]?.close()
    }

    override fun close() {
        if (closed) return
        closed = true
        for (l in links.values) { l.send(Msg.BYE); }
        Thread { try { Thread.sleep(200) } catch (_: Throwable) { }; for (l in links.values) l.close() }.start()
        try { acceptor?.close() } catch (_: Throwable) { }
    }
}

/** A client connected to a host. */
class ClientSession(kind: Int, val link: Link, name: String, hero: Int, skin: Int) : Session(kind) {
    override val isHost = false
    @Volatile var welcomed = false
    @Volatile var pendingStart: NetStart? = null

    init {
        link.start({ _, type, p -> onMsg(type, p) }, { if (!closed) { closed = true; events.add("closed") } })
        link.send(Msg.HELLO, Msg.build { it.writeInt(Msg.VERSION); it.writeUTF(name); it.writeByte(hero); it.writeByte(skin) })
    }

    private fun onMsg(type: Int, p: ByteArray) {
        when (type) {
            Msg.WELCOME -> { myId = Msg.reader(p).readUnsignedByte(); welcomed = true }
            Msg.LOBBY -> readLobby(p)
            Msg.START -> {
                val st = NetStart.read(Msg.reader(p))
                turns.clear(); desync = false; inMatch = true
                pendingStart = st
                events.add(st)
            }
            Msg.TURN -> { val t = Msg.reader(p).readInt(); turns[t] = p }
            Msg.DESYNC -> desync = true
            Msg.FULL -> { status = "Partie pleine, déjà commencée ou version différente"; events.add("full") }
            Msg.BYE -> { closed = true; events.add("closed"); link.close() }
        }
    }

    override fun setMyHero(hero: Int, skin: Int) { link.send(Msg.HERO, Msg.build { it.writeByte(hero); it.writeByte(skin) }) }
    override fun sendInput(turn: Int, data: ByteArray) { link.send(Msg.INPUT, Msg.build { it.writeInt(turn); it.write(data) }) }
    override fun sendHash(turn: Int, h: Long) { link.send(Msg.HASH, Msg.build { it.writeInt(turn); it.writeLong(h) }) }

    override fun close() {
        if (closed) return
        closed = true
        link.send(Msg.BYE)
        Thread { try { Thread.sleep(150) } catch (_: Throwable) { }; link.close() }.start()
    }
}
