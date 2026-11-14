package com.starguys.battle.g3d

import java.util.concurrent.ConcurrentLinkedQueue

/** Render categories (material classes) for instanced meshes. */
object Cat {
    /** lit, cel-shaded, receives an ink outline */
    const val SOLID = 0
    /** lit, no outline (small details: eyes, buttons...) */
    const val FLAT = 1
    /** lit, translucent (glass, bubbles, ghosts) */
    const val GLASS = 2
    /** unlit additive glow (projectiles, flames, energy) */
    const val GLOW = 3
    const val COUNT = 4
}

/** Growable float array of per-instance data: mat4 (16) + rgba (4). */
class InstBatch {
    var data = FloatArray(20 * 64)
    var n = 0

    fun clear() { n = 0 }

    fun add(m: FloatArray, r: Float, g: Float, b: Float, a: Float) {
        if ((n + 1) * 20 > data.size) data = data.copyOf(data.size * 2)
        val o = n * 20
        System.arraycopy(m, 0, data, o, 16)
        data[o + 16] = r; data[o + 17] = g; data[o + 18] = b; data[o + 19] = a
        n++
    }
}

/** Camera-facing or ground-aligned quads: a(xyz,kind) b(4) color(4) */
class SpriteBatch {
    var data = FloatArray(12 * 128)
    var n = 0
    fun clear() { n = 0 }
    fun add(x: Float, y: Float, z: Float, kind: Float, b0: Float, b1: Float, b2: Float, b3: Float, r: Float, g: Float, b: Float, a: Float) {
        if (a <= 0.002f) return
        if ((n + 1) * 12 > data.size) data = data.copyOf(data.size * 2)
        val o = n * 12
        data[o] = x; data[o + 1] = y; data[o + 2] = z; data[o + 3] = kind
        data[o + 4] = b0; data[o + 5] = b1; data[o + 6] = b2; data[o + 7] = b3
        data[o + 8] = r; data[o + 9] = g; data[o + 10] = b; data[o + 11] = a
        n++
    }
}

/** Sprite shapes (fragment-shader procedural) and placement modes. */
object Spr {
    const val GLOW = 0; const val DISC = 1; const val RING = 2; const val STAR = 3; const val SQUARE = 4; const val SECTOR = 5
    const val BAR = 6; const val SMOKE = 7; const val CHEVRON = 8; const val BEAM = 9; const val BLOB = 10; const val STREAK = 11
    const val BILL = 0; const val DECAL = 16; const val LINE = 32
}

/** Everything the GL thread needs to draw one frame. Built on the game thread. */
class RenderFrame {
    val batches = Array(Cat.COUNT * Meshes.COUNT) { InstBatch() }
    val spritesAlpha = SpriteBatch()
    val spritesAdd = SpriteBatch()
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    val invViewProj = FloatArray(16)
    val lightVP = FloatArray(16)
    var camX = 0f; var camY = 0f; var camZ = 0f
    var rightX = 1f; var rightY = 0f; var rightZ = 0f
    var upX = 0f; var upY = 1f; var upZ = 0f
    /** sky & lighting (linear-ish rgb 0..1) */
    val zenith = floatArrayOf(0.25f, 0.55f, 0.95f)
    val horizon = floatArrayOf(0.75f, 0.88f, 1f)
    val abyss = floatArrayOf(0.15f, 0.2f, 0.4f)
    val sunDir = floatArrayOf(-0.42f, 0.78f, -0.46f)
    val sunCol = floatArrayOf(0.78f, 0.74f, 0.66f)
    val skyAmb = floatArrayOf(0.52f, 0.56f, 0.66f)
    val gndAmb = floatArrayOf(0.36f, 0.33f, 0.36f)
    var fogStart = 1400f; var fogEnd = 3600f
    var stars = 0f
    var clouds = 1f
    var time = 0f
    var shadows = true
    var drawWorld = true
    var worldGen = 0
    /** 0 = in-game perspective, 1 = menu showcase (no world) */
    var showcase = false
    var valid = false

    fun batch(cat: Int, mesh: Int) = batches[cat * Meshes.COUNT + mesh]

    fun clear() {
        for (b in batches) b.clear()
        spritesAlpha.clear(); spritesAdd.clear()
        valid = false
    }
}

/** A finished world chunk mesh: interleaved pos(3) nrm(3) col(4); short indices. */
class ChunkMesh(val gen: Int, val id: Int, val verts: FloatArray, val idx: ShortArray,
                val minX: Float, val minY: Float, val minZ: Float, val maxX: Float, val maxY: Float, val maxZ: Float)

/** Triple buffer between the game thread (producer) and the GL thread (consumer). */
class FrameBus {
    private val frames = arrayOf(RenderFrame(), RenderFrame(), RenderFrame())
    private var back = 0; private var ready = 1; private var front = 2
    private var fresh = false
    private val lock = Any()
    val chunks = ConcurrentLinkedQueue<ChunkMesh>()
    @Volatile var wantFullWorld = true
    /** notified after every publish (GLSurfaceView.requestRender) */
    var onPublish: (() -> Unit)? = null

    fun back(): RenderFrame = frames[back]

    fun publish() {
        synchronized(lock) { val t = back; back = ready; ready = t; fresh = true }
        onPublish?.invoke()
    }

    fun acquire(): RenderFrame {
        synchronized(lock) { if (fresh) { val t = front; front = ready; ready = t; fresh = false } }
        return frames[front]
    }

    companion object {
        /** the one bus shared by the activity's GL view and the game */
        val main = FrameBus()
    }
}
