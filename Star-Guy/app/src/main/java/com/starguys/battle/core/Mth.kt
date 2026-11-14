package com.starguys.battle.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Small math + random helpers used everywhere.
 *
 * Multiplayer is lockstep: every device runs the exact same simulation, so
 *  - the simulation RNG is seeded per match (and is thread-local so tests can run two matches at once),
 *  - cosmetic randomness (particles, camera shake, menus) uses a separate RNG,
 *  - trigonometry uses the deterministic float implementations below instead of the platform libm.
 */
private val simRng = object : ThreadLocal<Random>() { override fun initialValue() = Random() }
val RNG: Random get() = simRng.get()!!
fun seedSim(seed: Long) { simRng.get()!!.setSeed(seed) }

/** cosmetic-only random (never used by the simulation) */
val FXR = Random()
fun frf(): Float = FXR.nextFloat()
fun frf(a: Float, b: Float): Float = a + (b - a) * FXR.nextFloat()

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
/** deterministic (simulation RNG) random element, null if empty */
fun <T> List<T>.pickOrNull(): T? = if (isEmpty()) null else this[ri(size)]

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
fun cosf(a: Float): Float = dsin(a + HALF_PI)
fun sinf(a: Float): Float = dsin(a)

const val HALF_PI = (PI / 2).toFloat()

/** Deterministic sine (only +,-,*,/ so results are bit-identical on every device). */
fun dsin(x: Float): Float {
    var a = x % TAU
    if (a > PIF) a -= TAU
    if (a < -PIF) a += TAU
    if (a > HALF_PI) a = PIF - a else if (a < -HALF_PI) a = -PIF - a
    val a2 = a * a
    return a * (1f + a2 * (-1f / 6f + a2 * (1f / 120f + a2 * (-1f / 5040f + a2 * (1f / 362880f - a2 / 39916800f)))))
}

private fun atanPoly(z: Float): Float {
    val z2 = z * z
    return z * (1f + z2 * (-1f / 3f + z2 * (1f / 5f + z2 * (-1f / 7f + z2 * (1f / 9f + z2 * (-1f / 11f + z2 / 13f))))))
}

/** Deterministic atan2. */
fun atan2(y: Float, x: Float): Float {
    if (x == 0f && y == 0f) return 0f
    val ax = if (x < 0f) -x else x
    val ay = if (y < 0f) -y else y
    val swap = ay > ax
    val t = if (swap) ax / ay else ay / ax
    var r = if (t > 0.41421356f) PIF / 4f + atanPoly((t - 1f) / (t + 1f)) else atanPoly(t)
    if (swap) r = HALF_PI - r
    if (x < 0f) r = PIF - r
    return if (y < 0f) -r else r
}
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
