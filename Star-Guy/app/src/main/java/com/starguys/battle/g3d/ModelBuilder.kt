package com.starguys.battle.g3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Emits mesh instances into a [RenderFrame]. Model space: origin at the feet, +Y up, +X forward, +Z right.
 * World space: GL X = sim x, GL Y = height (sim z), GL Z = sim y.
 */
class MB {
    lateinit var f: RenderFrame
    val st = MatStack(20)
    private val tmp = FloatArray(16)
    private val ident = Mat.identity(FloatArray(16))
    private val segLoc = FloatArray(16)
    /** 0..1 mix toward white (hit flash) */
    var flash = 0f
    /** < 1 turns solid parts into translucent glass (invisible heroes, spawn shield) */
    var alpha = 1f
    /** emissive boost for everything drawn (0 = none) */
    var glowAll = false
    var parts = 0

    fun begin(frame: RenderFrame) { f = frame; st.reset(ident); flash = 0f; alpha = 1f; glowAll = false }

    fun reset() { st.reset(ident); flash = 0f; alpha = 1f; glowAll = false }

    /** Place the model origin at world (x, y, z) facing sim angle [face], uniform scale s. */
    fun at(x: Float, y: Float, z: Float, face: Float, s: Float = 1f) {
        st.reset(ident)
        st.apply(x, z, y, ry = -face, sx = s, sy = s, sz = s)
    }

    fun push() = st.push()
    fun pop() = st.pop()
    fun move(x: Float, y: Float, z: Float, ry: Float = 0f, rz: Float = 0f, rx: Float = 0f, s: Float = 1f) = st.apply(x, y, z, ry, rz, rx, s, s, s)
    fun moveS(x: Float, y: Float, z: Float, ry: Float, rz: Float, rx: Float, sx: Float, sy: Float, sz: Float) = st.apply(x, y, z, ry, rz, rx, sx, sy, sz)

    private fun emit(mesh: Int, cat0: Int, m: FloatArray, col: Int, a0: Float) {
        var r = ((col shr 16) and 255) / 255f; var g = ((col shr 8) and 255) / 255f; var b = (col and 255) / 255f
        if (flash > 0f) { r += (1f - r) * flash; g += (1f - g) * flash; b += (1f - b) * flash }
        var cat = cat0; var a = a0
        if (glowAll && (cat == Cat.SOLID || cat == Cat.FLAT)) a = 2f
        if (alpha < 0.99f) {
            if (cat == Cat.SOLID || cat == Cat.FLAT) { cat = Cat.GLASS; a = alpha }
            else if (cat == Cat.GLASS) a *= alpha
            else if (cat == Cat.GLOW) a *= alpha
        }
        f.batch(cat, mesh).add(m, r, g, b, a)
        parts++
    }

    /** Generic part: unit mesh scaled to (sx, sy, sz) full sizes at (x, y, z) in the current frame. */
    fun part(mesh: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Int,
             ry: Float = 0f, rz: Float = 0f, rx: Float = 0f, cat: Int = Cat.SOLID, a: Float = 1f) {
        st.compose(tmp, x, y, z, ry, rz, rx, sx, sy, sz)
        emit(mesh, cat, tmp, col, if (cat == Cat.GLASS || cat == Cat.GLOW) a else if (a > 1.5f) 2f else 1f)
    }

    fun box(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Int, ry: Float = 0f, rz: Float = 0f, rx: Float = 0f, cat: Int = Cat.SOLID) =
        part(Meshes.BOX, x, y, z, sx, sy, sz, col, ry, rz, rx, cat)
    fun ball(x: Float, y: Float, z: Float, d: Float, col: Int, cat: Int = Cat.SOLID) = part(Meshes.SPHERE, x, y, z, d, d, d, col, cat = cat)
    fun ell(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Int, ry: Float = 0f, rz: Float = 0f, rx: Float = 0f, cat: Int = Cat.SOLID) =
        part(Meshes.SPHERE, x, y, z, sx, sy, sz, col, ry, rz, rx, cat)
    /** vertical cylinder from y0 to y1 */
    fun cyl(x: Float, y0: Float, z: Float, d: Float, y1: Float, col: Int, cat: Int = Cat.SOLID) =
        part(Meshes.CYL, x, (y0 + y1) / 2, z, d, y1 - y0, d, col, cat = cat)
    fun cone(x: Float, y0: Float, z: Float, d: Float, h: Float, col: Int, rz: Float = 0f, rx: Float = 0f, cat: Int = Cat.SOLID) =
        part(Meshes.CONE, x, y0 + h / 2, z, d, h, d, col, rz = rz, rx = rx, cat = cat)
    fun glowBall(x: Float, y: Float, z: Float, d: Float, col: Int, a: Float = 1f) = part(Meshes.SPHERE, x, y, z, d, d, d, col, cat = Cat.GLOW, a = a)
    fun glass(mesh: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Int, a: Float) = part(mesh, x, y, z, sx, sy, sz, col, cat = Cat.GLASS, a = a)
    fun emissive(mesh: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Int) = part(mesh, x, y, z, sx, sy, sz, col, cat = Cat.FLAT, a = 2f)

    /** A cylinder/box spanning two points in the current model frame (rods, ropes, legs). */
    fun seg(mesh: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, thick: Float, col: Int, cat: Int = Cat.SOLID) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-3f) return
        val ux = dx / l; val uy = dy / l; val uz = dz / l
        // any vector not parallel to u
        var px = 0f; var py = 0f; var pz = 1f
        if (abs(uz) > 0.9f) { px = 1f; pz = 0f }
        // a = normalize(p x u), b = u x a
        var ax = py * uz - pz * uy; var ay = pz * ux - px * uz; var az = px * uy - py * ux
        val al = sqrt(ax * ax + ay * ay + az * az); ax /= al; ay /= al; az /= al
        val bx = uy * az - uz * ay; val by = uz * ax - ux * az; val bz = ux * ay - uy * ax
        val loc = segLoc
        loc[0] = ax * thick; loc[1] = ay * thick; loc[2] = az * thick; loc[3] = 0f; loc[7] = 0f; loc[11] = 0f
        loc[4] = dx; loc[5] = dy; loc[6] = dz
        loc[8] = bx * thick; loc[9] = by * thick; loc[10] = bz * thick
        loc[12] = (x0 + x1) / 2; loc[13] = (y0 + y1) / 2; loc[14] = (z0 + z1) / 2; loc[15] = 1f
        Mat.mul(tmp, st.cur, loc)
        emit(mesh, cat, tmp, col, if (cat == Cat.GLOW) 1f else 1f)
    }

    // ------------------------------------------------------------------ shared features
    /** Pair of cartoon eyes on the front of a head. (x, y) centre, sep = half distance between eyes along Z. */
    fun eyes(x: Float, y: Float, sep: Float, d: Float, blink: Boolean, lookY: Float = 0f, angry: Boolean = false, pupil: Int = INK) {
        for (s in intArrayOf(-1, 1)) {
            val ez = s * sep
            if (blink) { box(x + d * 0.2f, y, ez, d * 0.3f, d * 0.14f, d * 0.8f, INK, cat = Cat.FLAT); continue }
            ell(x, y, ez, d * 0.55f, d, d * 0.8f, WHITE, cat = Cat.FLAT)
            ell(x + d * 0.26f, y + lookY * d * 0.15f, ez * 1.04f, d * 0.2f, d * 0.52f, d * 0.42f, pupil, cat = Cat.FLAT)
            ball(x + d * 0.34f, y + d * 0.2f, ez * 1.04f - s * d * 0.08f, d * 0.16f, WHITE, cat = Cat.FLAT)
            if (angry) box(x + d * 0.12f, y + d * 0.62f, ez, d * 0.2f, d * 0.18f, d * 0.95f, INK, rx = -s * 0.45f, cat = Cat.FLAT)
        }
    }

    /** Small smile: a curved line of dark beads on the front. */
    fun smile(x: Float, y: Float, w: Float, sad: Boolean = false) {
        for (k in -2..2) {
            val zz = k * w * 0.25f
            val yy = y + (if (sad) -1f else 1f) * (k * k) * w * 0.045f
            ball(x - abs(k) * w * 0.03f, yy, zz, w * 0.22f, INK, cat = Cat.FLAT)
        }
    }

    companion object {
        const val INK = 0xFF1B1530.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        fun shade(c: Int, k: Float): Int {
            val r = (((c shr 16) and 255) * k).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 255) * k).toInt().coerceIn(0, 255)
            val b = ((c and 255) * k).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        fun light(c: Int, k: Float): Int {
            val r = ((c shr 16) and 255); val g = ((c shr 8) and 255); val b = (c and 255)
            return (0xFF shl 24) or ((r + (255 - r) * k).toInt() shl 16) or ((g + (255 - g) * k).toInt() shl 8) or (b + (255 - b) * k).toInt()
        }
        fun sinF(a: Float) = sin(a)
        fun cosF(a: Float) = cos(a)
    }
}
