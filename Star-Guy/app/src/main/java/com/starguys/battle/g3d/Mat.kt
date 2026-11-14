package com.starguys.battle.g3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Column-major 4x4 matrices stored in FloatArray(16) (OpenGL convention). Allocation-free. */
object Mat {
    fun identity(m: FloatArray): FloatArray {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        return m
    }

    /** out = a * b (out may not alias a or b) */
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) {
            val b0 = b[c * 4]; val b1 = b[c * 4 + 1]; val b2 = b[c * 4 + 2]; val b3 = b[c * 4 + 3]
            out[c * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            out[c * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            out[c * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[c * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
    }

    fun perspective(m: FloatArray, fovyDeg: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / tan(Math.toRadians(fovyDeg / 2.0)).toFloat()
        for (i in 0 until 16) m[i] = 0f
        m[0] = f / aspect; m[5] = f
        m[10] = (far + near) / (near - far); m[11] = -1f
        m[14] = 2f * far * near / (near - far)
    }

    fun ortho(m: FloatArray, l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 2f / (r - l); m[5] = 2f / (t - b); m[10] = -2f / (f - n)
        m[12] = -(r + l) / (r - l); m[13] = -(t + b) / (t - b); m[14] = -(f + n) / (f - n); m[15] = 1f
    }

    fun lookAt(m: FloatArray, ex: Float, ey: Float, ez: Float, cx: Float, cy: Float, cz: Float, ux: Float, uy: Float, uz: Float) {
        var fx = cx - ex; var fy = cy - ey; var fz = cz - ez
        var l = sqrt(fx * fx + fy * fy + fz * fz); fx /= l; fy /= l; fz /= l
        var sx = fy * uz - fz * uy; var sy = fz * ux - fx * uz; var sz = fx * uy - fy * ux
        l = sqrt(sx * sx + sy * sy + sz * sz); sx /= l; sy /= l; sz /= l
        val ux2 = sy * fz - sz * fy; val uy2 = sz * fx - sx * fz; val uz2 = sx * fy - sy * fx
        m[0] = sx; m[1] = ux2; m[2] = -fx; m[3] = 0f
        m[4] = sy; m[5] = uy2; m[6] = -fy; m[7] = 0f
        m[8] = sz; m[9] = uz2; m[10] = -fz; m[11] = 0f
        m[12] = -(sx * ex + sy * ey + sz * ez); m[13] = -(ux2 * ex + uy2 * ey + uz2 * ez); m[14] = fx * ex + fy * ey + fz * ez; m[15] = 1f
    }

    /** m = T * Ry * Rz * Rx * S */
    fun trs(m: FloatArray, tx: Float, ty: Float, tz: Float, ry: Float, rz: Float, rx: Float, sx: Float, sy: Float, sz: Float) {
        val cy = cos(ry); val sy0 = sin(ry); val cz = cos(rz); val sz0 = sin(rz); val cx = cos(rx); val sx0 = sin(rx)
        // R = Ry * Rz * Rx
        val r00 = cy * cz; val r01 = -cy * sz0 * cx + sy0 * sx0; val r02 = cy * sz0 * sx0 + sy0 * cx
        val r10 = sz0; val r11 = cz * cx; val r12 = -cz * sx0
        val r20 = -sy0 * cz; val r21 = sy0 * sz0 * cx + cy * sx0; val r22 = -sy0 * sz0 * sx0 + cy * cx
        m[0] = r00 * sx; m[1] = r10 * sx; m[2] = r20 * sx; m[3] = 0f
        m[4] = r01 * sy; m[5] = r11 * sy; m[6] = r21 * sy; m[7] = 0f
        m[8] = r02 * sz; m[9] = r12 * sz; m[10] = r22 * sz; m[11] = 0f
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
    }

    /** Projects a world point; returns false if behind the camera. out = [ndcX, ndcY, depth] */
    fun project(vp: FloatArray, x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12]
        val cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13]
        val cz = vp[2] * x + vp[6] * y + vp[10] * z + vp[14]
        val cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15]
        if (cw <= 1f) return false
        out[0] = cx / cw; out[1] = cy / cw; out[2] = cz / cw
        return true
    }

    /** Inverts a general 4x4 matrix; returns false if singular. */
    fun invert(out: FloatArray, m: FloatArray): Boolean {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return false
        det = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * det
        return true
    }
}

/** Matrix stack used while composing models. */
class MatStack(depth: Int = 12) {
    private val st = Array(depth) { Mat.identity(FloatArray(16)) }
    private val tmp = FloatArray(16)
    private val loc = FloatArray(16)
    var top = 0
        private set
    val cur: FloatArray get() = st[top]

    fun reset(m: FloatArray) { top = 0; System.arraycopy(m, 0, st[0], 0, 16) }
    fun push() { System.arraycopy(st[top], 0, st[top + 1], 0, 16); top++ }
    fun pop() { if (top > 0) top-- }

    /** cur = cur * TRS */
    fun apply(tx: Float, ty: Float, tz: Float, ry: Float = 0f, rz: Float = 0f, rx: Float = 0f, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f) {
        Mat.trs(loc, tx, ty, tz, ry, rz, rx, sx, sy, sz)
        Mat.mul(tmp, st[top], loc)
        System.arraycopy(tmp, 0, st[top], 0, 16)
    }

    /** out = cur * TRS (does not modify the stack) */
    fun compose(out: FloatArray, tx: Float, ty: Float, tz: Float, ry: Float, rz: Float, rx: Float, sx: Float, sy: Float, sz: Float) {
        Mat.trs(loc, tx, ty, tz, ry, rz, rx, sx, sy, sz)
        Mat.mul(out, st[top], loc)
    }
}
