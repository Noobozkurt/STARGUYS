package com.starguys.battle.g3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** CPU-side geometry: interleaved position(3) + normal(3), indexed triangles. */
class MeshData {
    val v = ArrayList<Float>()
    val idx = ArrayList<Int>()
    val vertexCount get() = v.size / 6

    fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        val l = sqrt(nx * nx + ny * ny + nz * nz).let { if (it < 1e-6f) 1f else it }
        v.add(x); v.add(y); v.add(z); v.add(nx / l); v.add(ny / l); v.add(nz / l)
        return vertexCount - 1
    }

    fun tri(a: Int, b: Int, c: Int) { idx.add(a); idx.add(b); idx.add(c) }
    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    fun floats(): FloatArray = v.toFloatArray()
    fun shorts(): ShortArray = ShortArray(idx.size) { idx[it].toShort() }
}

/** Unit-sized procedural meshes (centered on the origin, size 1). */
object Meshes {
    const val BOX = 0; const val SPHERE = 1; const val CYL = 2; const val CONE = 3; const val TORUS = 4
    const val HEMI = 5; const val WEDGE = 6; const val QUAD = 7
    const val COUNT = 8

    fun build(id: Int): MeshData = when (id) {
        BOX -> box(0.12f)
        SPHERE -> sphere(18, 12)
        CYL -> cylinder(18)
        CONE -> cone(18)
        TORUS -> torus(20, 10, 0.38f, 0.12f)
        HEMI -> hemisphere(18, 7)
        WEDGE -> wedge()
        else -> quad()
    }

    /** Box with chamfered edges (smooth highlights on edges). */
    fun box(b: Float): MeshData {
        val m = MeshData()
        val h = 0.5f; val i = h - b
        // 6 main faces (inset)
        fun face(nx: Float, ny: Float, nz: Float) {
            // two tangent axes
            val (ux, uy, uz) = if (ny != 0f) Triple(1f, 0f, 0f) else Triple(0f, 1f, 0f)
            val vx = ny * uz - nz * uy; val vy = nz * ux - nx * uz; val vz = nx * uy - ny * ux
            fun p(a: Float, c: Float) = m.vert(nx * h + ux * a + vx * c, ny * h + uy * a + vy * c, nz * h + uz * a + vz * c, nx, ny, nz)
            val p0 = p(-i, -i); val p1 = p(i, -i); val p2 = p(i, i); val p3 = p(-i, i)
            m.quad(p0, p1, p2, p3)
        }
        face(1f, 0f, 0f); face(-1f, 0f, 0f); face(0f, 1f, 0f); face(0f, -1f, 0f); face(0f, 0f, 1f); face(0f, 0f, -1f)
        // 12 edge chamfers + 8 corners: build with averaged normals
        val signs = floatArrayOf(-1f, 1f)
        // edges along X
        for (sy in signs) for (sz in signs) {
            val a = m.vert(-i, sy * h, sz * i, 0f, sy, sz * 0.0001f); val b2 = m.vert(i, sy * h, sz * i, 0f, sy, 0f)
            val c = m.vert(i, sy * i, sz * h, 0f, 0f, sz); val d = m.vert(-i, sy * i, sz * h, 0f, 0f, sz)
            if (sy * sz > 0) m.quad(a, d, c, b2) else m.quad(a, b2, c, d)
        }
        for (sx in signs) for (sz in signs) { // edges along Y
            val a = m.vert(sx * h, -i, sz * i, sx, 0f, 0f); val b2 = m.vert(sx * h, i, sz * i, sx, 0f, 0f)
            val c = m.vert(sx * i, i, sz * h, 0f, 0f, sz); val d = m.vert(sx * i, -i, sz * h, 0f, 0f, sz)
            if (sx * sz > 0) m.quad(a, b2, c, d) else m.quad(a, d, c, b2)
        }
        for (sx in signs) for (sy in signs) { // edges along Z
            val a = m.vert(sx * h, sy * i, -i, sx, 0f, 0f); val b2 = m.vert(sx * h, sy * i, i, sx, 0f, 0f)
            val c = m.vert(sx * i, sy * h, i, 0f, sy, 0f); val d = m.vert(sx * i, sy * h, -i, 0f, sy, 0f)
            if (sx * sy > 0) m.quad(a, d, c, b2) else m.quad(a, b2, c, d)
        }
        for (sx in signs) for (sy in signs) for (sz in signs) {
            val a = m.vert(sx * h, sy * i, sz * i, sx, 0f, 0f); val b2 = m.vert(sx * i, sy * h, sz * i, 0f, sy, 0f); val c = m.vert(sx * i, sy * i, sz * h, 0f, 0f, sz)
            if (sx * sy * sz > 0) m.tri(a, b2, c) else m.tri(a, c, b2)
        }
        fixWinding(m)
        return m
    }

    /** Make every triangle face outward (normal direction agrees with the geometric normal). */
    private fun fixWinding(m: MeshData) {
        val v = m.v
        var t = 0
        while (t < m.idx.size) {
            val a = m.idx[t]; val b = m.idx[t + 1]; val c = m.idx[t + 2]
            val ax = v[a * 6]; val ay = v[a * 6 + 1]; val az = v[a * 6 + 2]
            val e1x = v[b * 6] - ax; val e1y = v[b * 6 + 1] - ay; val e1z = v[b * 6 + 2] - az
            val e2x = v[c * 6] - ax; val e2y = v[c * 6 + 1] - ay; val e2z = v[c * 6 + 2] - az
            val nx = e1y * e2z - e1z * e2y; val ny = e1z * e2x - e1x * e2z; val nz = e1x * e2y - e1y * e2x
            // compare with the vertex normal / centroid direction
            val cx = (ax + v[b * 6] + v[c * 6]); val cy = (ay + v[b * 6 + 1] + v[c * 6 + 1]); val cz = (az + v[b * 6 + 2] + v[c * 6 + 2])
            if (nx * cx + ny * cy + nz * cz < 0f) { m.idx[t + 1] = c; m.idx[t + 2] = b }
            t += 3
        }
    }

    fun sphere(seg: Int, rings: Int): MeshData {
        val m = MeshData()
        for (r in 0..rings) {
            val phi = PI * r / rings
            val y = cos(phi).toFloat(); val rr = sin(phi).toFloat()
            for (s in 0..seg) {
                val th = 2 * PI * s / seg
                val x = (rr * cos(th)).toFloat(); val z = (rr * sin(th)).toFloat()
                m.vert(x * 0.5f, y * 0.5f, z * 0.5f, x, y, z)
            }
        }
        for (r in 0 until rings) for (s in 0 until seg) {
            val a = r * (seg + 1) + s; val b = a + seg + 1
            m.tri(a, a + 1, b); m.tri(a + 1, b + 1, b)
        }
        fixWinding(m)
        return m
    }

    fun hemisphere(seg: Int, rings: Int): MeshData {
        val m = MeshData()
        for (r in 0..rings) {
            val phi = (PI / 2) * r / rings
            val y = cos(phi).toFloat(); val rr = sin(phi).toFloat()
            for (s in 0..seg) {
                val th = 2 * PI * s / seg
                val x = (rr * cos(th)).toFloat(); val z = (rr * sin(th)).toFloat()
                m.vert(x * 0.5f, y * 0.5f, z * 0.5f, x, y, z)
            }
        }
        for (r in 0 until rings) for (s in 0 until seg) {
            val a = r * (seg + 1) + s; val b = a + seg + 1
            m.tri(a, a + 1, b); m.tri(a + 1, b + 1, b)
        }
        // flat bottom
        val c = m.vert(0f, 0f, 0f, 0f, -1f, 0f)
        val base = m.vertexCount
        for (s in 0..seg) { val th = 2 * PI * s / seg; m.vert((0.5 * cos(th)).toFloat(), 0f, (0.5 * sin(th)).toFloat(), 0f, -1f, 0f) }
        for (s in 0 until seg) m.tri(c, base + s + 1, base + s)
        fixWindingBottom(m, c)
        return m
    }

    private fun fixWindingBottom(m: MeshData, center: Int) {
        // hemisphere: dome triangles use centroid test, bottom cap must face down
        var t = 0
        while (t < m.idx.size) {
            val a = m.idx[t]; val b = m.idx[t + 1]; val c = m.idx[t + 2]
            val v = m.v
            val e1x = v[b * 6] - v[a * 6]; val e1y = v[b * 6 + 1] - v[a * 6 + 1]; val e1z = v[b * 6 + 2] - v[a * 6 + 2]
            val e2x = v[c * 6] - v[a * 6]; val e2y = v[c * 6 + 1] - v[a * 6 + 1]; val e2z = v[c * 6 + 2] - v[a * 6 + 2]
            val nx = e1y * e2z - e1z * e2y; val ny = e1z * e2x - e1x * e2z; val nz = e1x * e2y - e1y * e2x
            val vnx = v[a * 6 + 3] + v[b * 6 + 3] + v[c * 6 + 3]; val vny = v[a * 6 + 4] + v[b * 6 + 4] + v[c * 6 + 4]; val vnz = v[a * 6 + 5] + v[b * 6 + 5] + v[c * 6 + 5]
            if (nx * vnx + ny * vny + nz * vnz < 0f) { m.idx[t + 1] = c; m.idx[t + 2] = b }
            t += 3
        }
        if (center < 0) Unit
    }

    fun cylinder(seg: Int): MeshData {
        val m = MeshData()
        for (s in 0..seg) {
            val th = 2 * PI * s / seg
            val x = cos(th).toFloat(); val z = sin(th).toFloat()
            m.vert(x * 0.5f, -0.5f, z * 0.5f, x, 0f, z); m.vert(x * 0.5f, 0.5f, z * 0.5f, x, 0f, z)
        }
        for (s in 0 until seg) { val a = s * 2; m.quad(a, a + 2, a + 3, a + 1) }
        for (cap in intArrayOf(-1, 1)) {
            val c = m.vert(0f, cap * 0.5f, 0f, 0f, cap.toFloat(), 0f)
            val base = m.vertexCount
            for (s in 0..seg) { val th = 2 * PI * s / seg; m.vert((0.5 * cos(th)).toFloat(), cap * 0.5f, (0.5 * sin(th)).toFloat(), 0f, cap.toFloat(), 0f) }
            for (s in 0 until seg) m.tri(c, base + s, base + s + 1)
        }
        fixWindingBottom(m, -1)
        return m
    }

    fun cone(seg: Int): MeshData {
        val m = MeshData()
        val slope = 0.5f // radius 0.5 over height 1
        for (s in 0..seg) {
            val th = 2 * PI * s / seg
            val x = cos(th).toFloat(); val z = sin(th).toFloat()
            m.vert(x * 0.5f, -0.5f, z * 0.5f, x, slope, z)
            m.vert(0f, 0.5f, 0f, x, slope, z)
        }
        for (s in 0 until seg) { val a = s * 2; m.tri(a, a + 2, a + 1) }
        val c = m.vert(0f, -0.5f, 0f, 0f, -1f, 0f)
        val base = m.vertexCount
        for (s in 0..seg) { val th = 2 * PI * s / seg; m.vert((0.5 * cos(th)).toFloat(), -0.5f, (0.5 * sin(th)).toFloat(), 0f, -1f, 0f) }
        for (s in 0 until seg) m.tri(c, base + s, base + s + 1)
        fixWindingBottom(m, -1)
        return m
    }

    fun torus(seg: Int, side: Int, major: Float, minor: Float): MeshData {
        val m = MeshData()
        for (i in 0..seg) {
            val u = 2 * PI * i / seg
            val cu = cos(u).toFloat(); val su = sin(u).toFloat()
            for (j in 0..side) {
                val v = 2 * PI * j / side
                val cv = cos(v).toFloat(); val sv = sin(v).toFloat()
                val x = (major + minor * cv) * cu; val z = (major + minor * cv) * su; val y = minor * sv
                m.vert(x, y, z, cv * cu, sv, cv * su)
            }
        }
        for (i in 0 until seg) for (j in 0 until side) {
            val a = i * (side + 1) + j; val b = a + side + 1
            m.tri(a, b, a + 1); m.tri(a + 1, b, b + 1)
        }
        fixWindingBottom(m, -1)
        return m
    }

    /** Triangular prism (roof / ramp / tent), ridge along Z at the top. */
    fun wedge(): MeshData {
        val m = MeshData()
        val h = 0.5f
        val l1 = m.vert(-h, -h, -h, -1f, 1f, 0f); val l2 = m.vert(-h, -h, h, -1f, 1f, 0f); val l3 = m.vert(0f, h, h, -1f, 1f, 0f); val l4 = m.vert(0f, h, -h, -1f, 1f, 0f)
        m.quad(l1, l2, l3, l4)
        val r1 = m.vert(h, -h, -h, 1f, 1f, 0f); val r2 = m.vert(h, -h, h, 1f, 1f, 0f); val r3 = m.vert(0f, h, h, 1f, 1f, 0f); val r4 = m.vert(0f, h, -h, 1f, 1f, 0f)
        m.quad(r1, r4, r3, r2)
        val b1 = m.vert(-h, -h, -h, 0f, -1f, 0f); val b2 = m.vert(h, -h, -h, 0f, -1f, 0f); val b3 = m.vert(h, -h, h, 0f, -1f, 0f); val b4 = m.vert(-h, -h, h, 0f, -1f, 0f)
        m.quad(b1, b2, b3, b4)
        val f1 = m.vert(-h, -h, h, 0f, 0f, 1f); val f2 = m.vert(h, -h, h, 0f, 0f, 1f); val f3 = m.vert(0f, h, h, 0f, 0f, 1f); m.tri(f1, f2, f3)
        val k1 = m.vert(-h, -h, -h, 0f, 0f, -1f); val k2 = m.vert(h, -h, -h, 0f, 0f, -1f); val k3 = m.vert(0f, h, -h, 0f, 0f, -1f); m.tri(k1, k3, k2)
        fixWindingBottom(m, -1)
        return m
    }

    fun quad(): MeshData {
        val m = MeshData()
        val a = m.vert(-0.5f, 0f, -0.5f, 0f, 1f, 0f); val b = m.vert(0.5f, 0f, -0.5f, 0f, 1f, 0f)
        val c = m.vert(0.5f, 0f, 0.5f, 0f, 1f, 0f); val d = m.vert(-0.5f, 0f, 0.5f, 0f, 1f, 0f)
        m.quad(a, d, c, b)
        fixWindingBottom(m, -1)
        return m
    }
}
