package com.starguys.battle.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small math + random helpers used everywhere. */
val RNG = Random()

const val PIF = PI.toFloat()
const val TAU = (PI * 2).toFloat()

fun rf(): Float = RNG.nextFloat()
fun rf(a: Float, b: Float): Float = a + (b - a) * RNG.nextFloat()
fun ri(n: Int): Int = if (n <= 0) 0 else RNG.nextInt(n)
fun ri(a: Int, b: Int): Int = a + ri(b - a + 1)
fun chance(p: Float): Boolean = RNG.nextFloat() < p
fun <T> List<T>.pick(): T = this[ri(size)]
fun <T> Array<T>.pick(): T = this[ri(size)]
fun IntArray.pick(): Int = this[ri(size)]

fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
fun clampi(v: Int, a: Int, b: Int): Int = if (v < a) a else if (v > b) b else v
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
fun approach(v: Float, target: Float, step: Float): Float =
    if (v < target) minOf(v + step, target) else maxOf(v - step, target)

fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1; val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}

fun dist2(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1; val dy = y2 - y1
    return dx * dx + dy * dy
}

fun ang(x1: Float, y1: Float, x2: Float, y2: Float): Float = atan2(y2 - y1, x2 - x1)
fun cosf(a: Float): Float = cos(a)
fun sinf(a: Float): Float = sin(a)
fun len(x: Float, y: Float): Float = sqrt(x * x + y * y)

fun wrapAngle(a: Float): Float {
    var r = a
    while (r > PIF) r -= TAU
    while (r < -PIF) r += TAU
    return r
}

fun angleDiff(a: Float, b: Float): Float = wrapAngle(b - a)

fun smooth(t: Float): Float { val c = clamp(t, 0f, 1f); return c * c * (3 - 2 * c) }

/** Ease-out-back used for popping UI. */
fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    val x = clamp(t, 0f, 1f) - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

fun Float.floorInt(): Int = kotlin.math.floor(this.toDouble()).toInt()
