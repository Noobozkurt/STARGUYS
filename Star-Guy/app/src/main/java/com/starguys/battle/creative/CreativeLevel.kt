package com.starguys.battle.creative

import android.util.Base64
import com.starguys.battle.core.cosf
import com.starguys.battle.core.sinf
import com.starguys.battle.game.Biome
import com.starguys.battle.game.Breakable
import com.starguys.battle.game.Cannon
import com.starguys.battle.game.Decor
import com.starguys.battle.game.Fan
import com.starguys.battle.game.JumpPad
import com.starguys.battle.game.LootMachine
import com.starguys.battle.game.Match
import com.starguys.battle.game.MouseTrap
import com.starguys.battle.game.Pendulum
import com.starguys.battle.game.Press
import com.starguys.battle.game.SpinBar
import com.starguys.battle.game.T
import com.starguys.battle.game.Teleporter
import com.starguys.battle.game.TileMap
import com.starguys.battle.game.Tl
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** A level built in the creative workshop. Serialized as a short shareable text code. */
class CreativeLevel(var w: Int = 40, var h: Int = 26) {
    var biome = Biome.MEADOW
    var name = "Ma création"
    var type = ByteArray(w * h) { Tl.FLOOR.toByte() }
    var elev = ByteArray(w * h)
    var aux = ByteArray(w * h)
    val props = ArrayList<IntArray>() // [kind, tx, ty, param]

    companion object {
        const val P_SPAWN = 0; const val P_JUMP = 1; const val P_FAN = 2; const val P_BARREL = 3; const val P_CANNON = 4
        const val P_TELE = 5; const val P_PENDULUM = 6; const val P_SPIN = 7; const val P_TRAP = 8; const val P_LOOT = 9
        const val P_PRESS = 10; const val P_SLIME = 11; const val P_HYDRANT = 12; const val P_BOX = 13; const val P_DECOR = 14
        const val P_COUNT = 15
        val PROP_NAMES = arrayOf("Départ", "Tremplin", "Ventilateur", "Baril", "Canon", "Téléporteur", "Hachoir", "Roue", "Tapette", "Distributeur",
            "Presse", "Bonbonne", "Borne", "Caisse butin", "Décor")
        const val PREFIX = "SGB1:"

        fun blank(w: Int = 40, h: Int = 26): CreativeLevel {
            val l = CreativeLevel(w, h)
            for (y in 0 until h) for (x in 0 until w) if (x == 0 || y == 0 || x == w - 1 || y == h - 1) l.type[y * w + x] = Tl.WALL.toByte()
            l.props.add(intArrayOf(P_SPAWN, 3, h / 2, 0))
            return l
        }

        fun decode(code: String): CreativeLevel? {
            return try {
                val raw = code.trim().substringAfter(PREFIX, "")
                if (raw.isEmpty()) return null
                val bytes = Base64.decode(raw, Base64.NO_WRAP or Base64.URL_SAFE)
                val ins = DataInputStream(InflaterInputStream(bytes.inputStream()))
                val ver = ins.readByte().toInt()
                if (ver != 1) return null
                val w = ins.readUnsignedByte(); val h = ins.readUnsignedByte()
                if (w < 8 || h < 8 || w > 80 || h > 60) return null
                val l = CreativeLevel(w, h)
                l.biome = Biome.values()[ins.readUnsignedByte().coerceIn(0, Biome.values().size - 1)]
                l.name = ins.readUTF()
                ins.readFully(l.type); ins.readFully(l.elev); ins.readFully(l.aux)
                for (i in l.type.indices) if (l.type[i] < 0 || l.type[i] >= Tl.COUNT) l.type[i] = Tl.FLOOR.toByte()
                for (i in l.elev.indices) l.elev[i] = l.elev[i].toInt().coerceIn(0, 8).toByte()
                val n = ins.readUnsignedShort()
                for (i in 0 until minOf(n, 600)) {
                    val k = ins.readUnsignedByte(); val x = ins.readUnsignedByte(); val y = ins.readUnsignedByte(); val p = ins.readUnsignedByte()
                    if (k < P_COUNT && x < w && y < h) l.props.add(intArrayOf(k, x, y, p))
                }
                l
            } catch (e: Throwable) { null }
        }
    }

    fun encode(): String {
        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(DeflaterOutputStream(bos, Deflater(9)))
        out.writeByte(1); out.writeByte(w); out.writeByte(h)
        out.writeByte(biome.ordinal)
        out.writeUTF(name.take(40))
        out.write(type); out.write(elev); out.write(aux)
        out.writeShort(props.size)
        for (p in props) { out.writeByte(p[0]); out.writeByte(p[1]); out.writeByte(p[2]); out.writeByte(p[3]) }
        out.close()
        return PREFIX + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun spawns(): List<IntArray> = props.filter { it[0] == P_SPAWN }

    /** Builds the playable map + props into a match. */
    fun build(m: Match): TileMap {
        val mp = TileMap(w, h, biome)
        m.map = mp
        System.arraycopy(type, 0, mp.type, 0, type.size)
        System.arraycopy(elev, 0, mp.elev, 0, elev.size)
        System.arraycopy(aux, 0, mp.aux, 0, aux.size)
        for (i in mp.hp.indices) mp.hp[i] = Tl.maxHp(mp.type[i].toInt())
        var lastTele: Teleporter? = null
        for (p in props) {
            val x = p[1] * T + T / 2; val y = p[2] * T + T / 2
            when (p[0]) {
                P_JUMP -> m.addProp(JumpPad(m, x, y))
                P_FAN -> m.addProp(Fan(m, x, y, p[3] * 1.5708f))
                P_BARREL -> m.addProp(Breakable(m, x, y, 0))
                P_CANNON -> { val a = p[3] * 1.5708f; m.addProp(Cannon(m, x, y, (x + cosf(a) * 9f * T).coerceIn(T, mp.pxW - T), (y + sinf(a) * 9f * T).coerceIn(T, mp.pxH - T))) }
                P_TELE -> { val t = Teleporter(m, x, y, if (lastTele == null) 0xFF7CF0FF.toInt() else 0xFF7CF0FF.toInt()); val lt = lastTele; if (lt == null) lastTele = t else { lt.partner = t; t.partner = lt; lastTele = null }; m.addProp(t) }
                P_PENDULUM -> m.addProp(Pendulum(m, x, y, if (p[3] % 2 == 0) 1.5708f else 0f, 2.2f * T, 1.9f))
                P_SPIN -> m.addProp(SpinBar(m, x, y, 2.4f * T, if (p[3] % 2 == 0) 1.5f else -1.5f))
                P_TRAP -> m.addProp(MouseTrap(m, x, y))
                P_LOOT -> m.addProp(LootMachine(m, x, y))
                P_PRESS -> m.addProp(Press(m, x, y, 3f, (p[1] + p[2]) * 0.4f))
                P_SLIME -> m.addProp(Breakable(m, x, y, 1))
                P_HYDRANT -> m.addProp(Breakable(m, x, y, 2))
                P_BOX -> m.addProp(Breakable(m, x, y, 3))
                P_DECOR -> m.addProp(Decor(m, x, y, p[3].coerceIn(0, Decor.COUNT - 1)))
            }
        }
        return mp
    }
}
