package com.starguys.battle.game

import com.starguys.battle.core.TAU
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.roundToInt

/**
 * Everything a human does during one simulation turn. The HUD fills it from touches; the match applies it.
 * In multiplayer it is serialized, sent to the host, and applied on every device at the same tick (lockstep).
 */
class PlayerInput {
    var mx = 0f
    var my = 0f
    var aim = 0f
    var aiming = false
    var hold = false
    var holdDist = 0f
    /** [type, p1, p2] */
    val acts = ArrayList<FloatArray>(4)

    companion object {
        const val ATTACK = 1; const val SUPER = 2; const val JUMP = 3; const val GADGET = 4; const val ITEM = 5
        const val INTERACT = 6; const val PING = 7

        private fun qa(a: Float): Int = ((a / TAU) * 65536f).roundToInt()
        private fun ua(s: Int): Float = s.toShort().toInt() * TAU / 65536f
        private fun qs(v: Float): Int = v.roundToInt().coerceIn(-32768, 32767)

        fun read(i: DataInputStream): PlayerInput {
            val p = PlayerInput()
            p.mx = i.readByte() / 127f
            p.my = i.readByte() / 127f
            val fl = i.readUnsignedByte()
            p.aiming = fl and 1 != 0; p.hold = fl and 2 != 0
            p.aim = ua(i.readShort().toInt())
            p.holdDist = i.readShort().toFloat()
            val n = i.readUnsignedByte()
            for (k in 0 until n) {
                val t = i.readUnsignedByte()
                val a = i.readShort().toInt(); val b = i.readShort().toInt()
                when (t) {
                    ATTACK, SUPER, GADGET -> p.acts.add(floatArrayOf(t.toFloat(), ua(a), b.toFloat()))
                    else -> p.acts.add(floatArrayOf(t.toFloat(), a.toFloat(), b.toFloat()))
                }
            }
            return p
        }
    }

    fun add(type: Int, a: Float = 0f, b: Float = 0f) { if (acts.size < 8) acts.add(floatArrayOf(type.toFloat(), a, b)) }

    fun clearActions() { acts.clear() }

    fun write(o: DataOutputStream) {
        o.writeByte((mx * 127f).roundToInt().coerceIn(-127, 127))
        o.writeByte((my * 127f).roundToInt().coerceIn(-127, 127))
        o.writeByte((if (aiming) 1 else 0) or (if (hold) 2 else 0))
        o.writeShort(qa(aim))
        o.writeShort(qs(holdDist))
        o.writeByte(acts.size)
        for (a in acts) {
            val t = a[0].toInt()
            o.writeByte(t)
            when (t) {
                ATTACK, SUPER, GADGET -> { o.writeShort(qa(a[1])); o.writeShort(qs(a[2])) }
                else -> { o.writeShort(qs(a[1])); o.writeShort(qs(a[2])) }
            }
        }
    }

    /** Copy of the continuous state + the queued actions; the actions are then cleared. */
    fun snapshot(): PlayerInput {
        val p = PlayerInput()
        p.mx = mx; p.my = my; p.aim = aim; p.aiming = aiming; p.hold = hold; p.holdDist = holdDist
        p.acts.addAll(acts); acts.clear()
        return p
    }
}

/** A human taking part in a match (local or remote). id 0 is always the host. */
class NetPlayer(val id: Int, var name: String, var hero: Int, var skin: Int)
