package com.starguys.battle.net

import com.starguys.battle.game.Fighter
import com.starguys.battle.game.Match
import com.starguys.battle.game.PlayerInput
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Runs a match in lockstep. One turn = [TPT] simulation ticks (1/60 s each).
 * After executing turn t, the local input for turn t + [DELAY] is sent; turns below DELAY are empty for everyone.
 */
class Lockstep(val m: Match, val s: Session) {
    companion object {
        const val TPT = 2
        const val DELAY = 4
        const val HASH_EVERY = 30
        private const val TICK = 1f / 60f
    }

    var exec = 0
        private set
    private var acc = 0f
    /** seconds spent waiting for other players */
    var stallT = 0f
        private set
    private val byId = HashMap<Int, Fighter>()
    /** test/debug hook */
    var onHash: ((Int, Long) -> Unit)? = null
    private val empty = PlayerInput()

    init {
        for (f in m.fighters) if (f.human && f.netId >= 0) byId[f.netId] = f
    }

    /** Advance as far as received turns allow; returns true if at least one turn ran. */
    fun update(dt: Float): Boolean {
        acc += dt
        val turnDt = TPT * TICK
        var ran = 0
        while (ran < 8) {
            val behind = s.turns.containsKey(exec + 2)
            if (acc < turnDt && !behind) break
            val bundle: ByteArray?
            if (exec < DELAY) bundle = null
            else { bundle = s.turns.remove(exec) ?: break }
            execTurn(bundle)
            sendLocal(exec + DELAY)
            if (exec % HASH_EVERY == 0) { val h = m.checksum(); s.sendHash(exec, h); onHash?.invoke(exec, h) }
            exec++
            acc = maxOf(0f, acc - turnDt)
            ran++
        }
        if (ran == 0) stallT += dt else stallT = 0f
        if (acc > turnDt * 3) acc = turnDt * 3
        s.tick()
        m.frame(dt)
        return ran > 0
    }

    private fun sendLocal(turn: Int) {
        val inp = m.hud.takeInput()
        val bos = ByteArrayOutputStream()
        inp.write(DataOutputStream(bos))
        s.sendInput(turn, bos.toByteArray())
    }

    private fun execTurn(bundle: ByteArray?) {
        val inputs = ArrayList<Pair<Fighter, PlayerInput>>()
        if (bundle != null) {
            val i = Msg.reader(bundle)
            i.readInt()
            val n = i.readUnsignedByte()
            for (k in 0 until n) {
                val id = i.readUnsignedByte(); val flags = i.readUnsignedByte(); val len = i.readUnsignedShort()
                if (flags and 1 != 0) { if (len > 0) i.skipBytes(len); if (byId[id]?.human == true) m.dropPlayer(id); continue }
                val data = ByteArray(len); i.readFully(data)
                val f = byId[id] ?: continue
                if (!f.human) continue
                inputs.add(f to PlayerInput.read(Msg.reader(data)))
            }
        } else {
            for (f in byId.values) if (f.human) inputs.add(f to empty)
        }
        for (tick in 0 until TPT) {
            for ((f, inp) in inputs) m.applyInput(f, inp, tick == 0)
            m.simStep(TICK)
        }
    }
}
