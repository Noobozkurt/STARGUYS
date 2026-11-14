package com.starguys.battle.g3d

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * OpenGL ES 3.0 renderer: shadow map, cel-shaded instanced meshes with ink outlines,
 * world chunks, procedural sky, translucent / additive passes and procedural sprites.
 * All GL calls happen on the GL thread; data arrives through [FrameBus].
 */
class Renderer(private val gl: Gl, private val bus: FrameBus) {
    private class Prog(val id: Int, private val gl: Gl) {
        private val cache = HashMap<String, Int>()
        fun u(name: String): Int = cache.getOrPut(name) { gl.uniformLocation(id, name) }
    }

    private class GpuMesh(val vbo: Int, val ibo: Int, val count: Int)
    private class GpuChunk(val vao: Int, val vbo: Int, val ibo: Int, val count: Int, val c: ChunkMesh)

    private lateinit var meshProg: Prog
    private lateinit var worldProg: Prog
    private lateinit var depthInst: Prog
    private lateinit var depthWorld: Prog
    private lateinit var spriteProg: Prog
    private lateinit var skyProg: Prog

    private val meshes = arrayOfNulls<GpuMesh>(Meshes.COUNT)
    private val instVao = IntArray(Cat.COUNT * Meshes.COUNT)
    private val instVbo = IntArray(Cat.COUNT * Meshes.COUNT)
    private var quadVbo = 0
    private var spriteVaoA = 0; private var spriteVboA = 0
    private var spriteVaoB = 0; private var spriteVboB = 0
    private var skyVao = 0
    private var shadowTex = 0; private var shadowFbo = 0
    private val chunks = HashMap<Int, GpuChunk>()
    private val freeObjs = ArrayList<IntArray>()
    private var chunkGen = -1
    private var fbuf: FloatBuffer = alloc(20 * 2048)
    var width = 1; var height = 1
    var ready = false
        private set
    var lastError = ""
    /** extra detail (ground noise) — can be lowered on weak GPUs */
    var detail = 1f
    var shadowsEnabled = true
    /** framebuffer to draw into (0 = the window) */
    var targetFbo = 0

    private fun alloc(floats: Int): FloatBuffer = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun upload(target: Int, data: FloatArray, n: Int, usage: Int) {
        if (fbuf.capacity() < n) fbuf = alloc(maxOf(n, fbuf.capacity() * 2))
        fbuf.clear(); fbuf.put(data, 0, n); fbuf.flip()
        gl.bufferData(target, n * 4, fbuf, usage)
    }

    private fun compile(type: Int, src: String): Int {
        val s = gl.createShader(type)
        gl.shaderSource(s, src); gl.compileShader(s)
        if (!gl.shaderOk(s)) throw IllegalStateException("shader: " + gl.shaderLog(s) + "\n" + src.lines().take(8).joinToString("\n"))
        return s
    }

    private fun program(vs: String, fs: String): Prog {
        val p = gl.createProgram()
        gl.attachShader(p, compile(C.VERTEX_SHADER, vs)); gl.attachShader(p, compile(C.FRAGMENT_SHADER, fs))
        gl.linkProgram(p)
        if (!gl.programOk(p)) throw IllegalStateException("link: " + gl.programLog(p))
        return Prog(p, gl)
    }

    /** (Re)creates every GL object. Call from onSurfaceCreated (the context may be new). */
    fun init() {
        ready = false
        meshProg = program(Shaders.meshVS, Shaders.meshFS)
        worldProg = program(Shaders.worldVS, Shaders.worldFS)
        depthInst = program(Shaders.depthInstVS, Shaders.depthFS)
        depthWorld = program(Shaders.depthWorldVS, Shaders.depthFS)
        spriteProg = program(Shaders.spriteVS, Shaders.spriteFS)
        skyProg = program(Shaders.skyVS, Shaders.skyFS)

        for (i in 0 until Meshes.COUNT) {
            val md = Meshes.build(i)
            val vbo = gl.genBuffer(); gl.bindBuffer(C.ARRAY_BUFFER, vbo)
            val v = md.floats(); upload(C.ARRAY_BUFFER, v, v.size, C.STATIC_DRAW)
            val ibo = gl.genBuffer(); gl.bindBuffer(C.ELEMENT_ARRAY_BUFFER, ibo)
            val s = md.shorts()
            val sb = ByteBuffer.allocateDirect(s.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer(); sb.put(s); sb.flip()
            gl.bufferData(C.ELEMENT_ARRAY_BUFFER, s.size * 2, sb, C.STATIC_DRAW)
            meshes[i] = GpuMesh(vbo, ibo, s.size)
        }
        for (cat in 0 until Cat.COUNT) for (mi in 0 until Meshes.COUNT) {
            val k = cat * Meshes.COUNT + mi
            val mesh = meshes[mi]!!
            val vao = gl.genVertexArray(); gl.bindVertexArray(vao)
            gl.bindBuffer(C.ARRAY_BUFFER, mesh.vbo)
            gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, C.FLOAT, false, 24, 0)
            gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 3, C.FLOAT, false, 24, 12)
            val ivbo = gl.genBuffer(); gl.bindBuffer(C.ARRAY_BUFFER, ivbo)
            gl.bufferData(C.ARRAY_BUFFER, 80, null, C.STREAM_DRAW)
            for (a in 0 until 5) {
                gl.enableVertexAttribArray(2 + a)
                gl.vertexAttribPointer(2 + a, 4, C.FLOAT, false, 80, a * 16)
                gl.vertexAttribDivisor(2 + a, 1)
            }
            gl.bindBuffer(C.ELEMENT_ARRAY_BUFFER, mesh.ibo)
            gl.bindVertexArray(0)
            instVao[k] = vao; instVbo[k] = ivbo
        }
        // unit quad (triangle strip corners)
        quadVbo = gl.genBuffer(); gl.bindBuffer(C.ARRAY_BUFFER, quadVbo)
        val q = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        upload(C.ARRAY_BUFFER, q, q.size, C.STATIC_DRAW)
        fun spriteVao(): IntArray {
            val vao = gl.genVertexArray(); gl.bindVertexArray(vao)
            gl.bindBuffer(C.ARRAY_BUFFER, quadVbo)
            gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, C.FLOAT, false, 8, 0)
            val vbo = gl.genBuffer(); gl.bindBuffer(C.ARRAY_BUFFER, vbo)
            gl.bufferData(C.ARRAY_BUFFER, 48, null, C.STREAM_DRAW)
            for (a in 0 until 3) {
                gl.enableVertexAttribArray(1 + a); gl.vertexAttribPointer(1 + a, 4, C.FLOAT, false, 48, a * 16); gl.vertexAttribDivisor(1 + a, 1)
            }
            gl.bindVertexArray(0)
            return intArrayOf(vao, vbo)
        }
        spriteVao().let { spriteVaoA = it[0]; spriteVboA = it[1] }
        spriteVao().let { spriteVaoB = it[0]; spriteVboB = it[1] }
        skyVao = gl.genVertexArray(); gl.bindVertexArray(skyVao)
        gl.bindBuffer(C.ARRAY_BUFFER, quadVbo)
        gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, C.FLOAT, false, 8, 0)
        gl.bindVertexArray(0)

        // shadow map
        shadowTex = gl.genTexture()
        gl.bindTexture(C.TEXTURE_2D, shadowTex)
        gl.texImage2D(C.TEXTURE_2D, 0, C.DEPTH_COMPONENT24, Shaders.SHADOW_SIZE, Shaders.SHADOW_SIZE, 0, C.DEPTH_COMPONENT, C.UNSIGNED_INT, null)
        gl.texParameteri(C.TEXTURE_2D, C.TEXTURE_MIN_FILTER, C.LINEAR)
        gl.texParameteri(C.TEXTURE_2D, C.TEXTURE_MAG_FILTER, C.LINEAR)
        gl.texParameteri(C.TEXTURE_2D, C.TEXTURE_WRAP_S, C.CLAMP_TO_EDGE)
        gl.texParameteri(C.TEXTURE_2D, C.TEXTURE_WRAP_T, C.CLAMP_TO_EDGE)
        gl.texParameteri(C.TEXTURE_2D, C.TEXTURE_COMPARE_MODE, C.COMPARE_REF_TO_TEXTURE)
        gl.texParameteri(C.TEXTURE_2D, C.TEXTURE_COMPARE_FUNC, C.LEQUAL)
        shadowFbo = gl.genFramebuffer()
        gl.bindFramebuffer(C.FRAMEBUFFER, shadowFbo)
        gl.framebufferTexture2D(C.FRAMEBUFFER, C.DEPTH_ATTACHMENT, C.TEXTURE_2D, shadowTex, 0)
        shadowsEnabled = gl.checkFramebufferStatus(C.FRAMEBUFFER) == C.FRAMEBUFFER_COMPLETE
        gl.bindFramebuffer(C.FRAMEBUFFER, targetFbo)
        chunks.clear(); freeObjs.clear(); chunkGen = -1
        bus.wantFullWorld = true
        ready = true
    }

    fun resize(w: Int, h: Int) { width = maxOf(1, w); height = maxOf(1, h) }

    private fun takeChunks() {
        while (true) {
            val c = bus.chunks.poll() ?: break
            if (c.gen != chunkGen) {
                // a new world: drop the old chunks (GPU memory is reclaimed with the context or reused below)
                for (o in chunks.values) freeObjs.add(intArrayOf(o.vao, o.vbo, o.ibo))
                chunks.clear(); chunkGen = c.gen
            }
            val old = chunks[c.id]
            val recycled = if (old == null && freeObjs.isNotEmpty()) freeObjs.removeAt(freeObjs.size - 1) else null
            val vao = old?.vao ?: recycled?.get(0) ?: gl.genVertexArray()
            val vbo = old?.vbo ?: recycled?.get(1) ?: gl.genBuffer()
            val ibo = old?.ibo ?: recycled?.get(2) ?: gl.genBuffer()
            gl.bindVertexArray(vao)
            gl.bindBuffer(C.ARRAY_BUFFER, vbo)
            upload(C.ARRAY_BUFFER, c.verts, c.verts.size, C.STATIC_DRAW)
            gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, C.FLOAT, false, 40, 0)
            gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 3, C.FLOAT, false, 40, 12)
            gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 4, C.FLOAT, false, 40, 24)
            gl.bindBuffer(C.ELEMENT_ARRAY_BUFFER, ibo)
            val sb = ByteBuffer.allocateDirect(maxOf(2, c.idx.size * 2)).order(ByteOrder.nativeOrder()).asShortBuffer(); sb.put(c.idx); sb.flip()
            gl.bufferData(C.ELEMENT_ARRAY_BUFFER, c.idx.size * 2, sb, C.STATIC_DRAW)
            gl.bindVertexArray(0)
            chunks[c.id] = GpuChunk(vao, vbo, ibo, c.idx.size, c)
        }
    }

    private fun chunkVisible(c: ChunkMesh, vp: FloatArray): Boolean {
        // conservative frustum test of the chunk's bounding box corners in clip space
        var outL = 0; var outR = 0; var outB = 0; var outT = 0; var outN = 0
        for (i in 0 until 8) {
            val x = if (i and 1 == 0) c.minX else c.maxX
            val y = if (i and 2 == 0) c.minY else c.maxY
            val z = if (i and 4 == 0) c.minZ else c.maxZ
            val cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12]
            val cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13]
            val cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15]
            if (cx < -cw) outL++; if (cx > cw) outR++; if (cy < -cw) outB++; if (cy > cw) outT++; if (cw < 0f) outN++
        }
        return !(outL == 8 || outR == 8 || outB == 8 || outT == 8 || outN == 8)
    }

    private fun lightUniforms(p: Prog, f: RenderFrame) {
        gl.uniform3f(p.u("uSun"), f.sunDir[0], f.sunDir[1], f.sunDir[2])
        gl.uniform3f(p.u("uSunCol"), f.sunCol[0], f.sunCol[1], f.sunCol[2])
        gl.uniform3f(p.u("uSkyAmb"), f.skyAmb[0], f.skyAmb[1], f.skyAmb[2])
        gl.uniform3f(p.u("uGndAmb"), f.gndAmb[0], f.gndAmb[1], f.gndAmb[2])
        gl.uniform3f(p.u("uCam"), f.camX, f.camY, f.camZ)
        gl.uniform3f(p.u("uFogCol"), f.horizon[0], f.horizon[1], f.horizon[2])
        gl.uniform2f(p.u("uFog"), f.fogStart, f.fogEnd)
        gl.uniform1f(p.u("uTime"), f.time)
        gl.uniform1f(p.u("uShadowOn"), if (f.shadows && shadowsEnabled) 1f else 0f)
        gl.uniformMatrix4fv(p.u("uVP"), f.viewProj)
        gl.uniformMatrix4fv(p.u("uLightVP"), f.lightVP)
        gl.uniform1i(p.u("uShadow"), 0)
    }

    private fun uploadInstances(f: RenderFrame) {
        for (k in 0 until Cat.COUNT * Meshes.COUNT) {
            val b = f.batches[k]
            if (b.n == 0) continue
            gl.bindBuffer(C.ARRAY_BUFFER, instVbo[k])
            upload(C.ARRAY_BUFFER, b.data, b.n * 20, C.STREAM_DRAW)
        }
    }

    private fun drawBatches(f: RenderFrame, cat: Int) {
        for (mi in 0 until Meshes.COUNT) {
            val k = cat * Meshes.COUNT + mi
            val b = f.batches[k]
            if (b.n == 0) continue
            gl.bindVertexArray(instVao[k])
            gl.drawElementsInstanced(C.TRIANGLES, meshes[mi]!!.count, C.UNSIGNED_SHORT, 0, b.n)
        }
    }

    private fun drawSprites(s: SpriteBatch, vao: Int, vbo: Int) {
        if (s.n == 0) return
        gl.bindBuffer(C.ARRAY_BUFFER, vbo)
        upload(C.ARRAY_BUFFER, s.data, s.n * 12, C.STREAM_DRAW)
        gl.bindVertexArray(vao)
        gl.drawArraysInstanced(C.TRIANGLE_STRIP, 0, 4, s.n)
    }

    /** Draws the latest frame. Returns false when nothing could be drawn. */
    fun draw(): Boolean {
        if (!ready) return false
        takeChunks()
        val f = bus.acquire()
        if (!f.valid) {
            gl.bindFramebuffer(C.FRAMEBUFFER, targetFbo)
            gl.viewport(0, 0, width, height)
            gl.clearColor(0.05f, 0.04f, 0.12f, 1f); gl.clear(C.COLOR_BUFFER_BIT or C.DEPTH_BUFFER_BIT)
            return false
        }
        uploadInstances(f)
        val world = f.drawWorld && chunks.isNotEmpty() && chunkGen == f.worldGen
        gl.enable(C.DEPTH_TEST); gl.depthFunc(C.LEQUAL); gl.depthMask(true)
        gl.enable(C.CULL_FACE); gl.cullFace(C.BACK)
        gl.disable(C.BLEND)

        // ------------------------------------------------ shadow pass
        val doShadow = f.shadows && shadowsEnabled
        if (doShadow) {
            gl.bindFramebuffer(C.FRAMEBUFFER, shadowFbo)
            gl.viewport(0, 0, Shaders.SHADOW_SIZE, Shaders.SHADOW_SIZE)
            gl.colorMask(false, false, false, false)
            gl.clear(C.DEPTH_BUFFER_BIT)
            gl.enable(C.POLYGON_OFFSET_FILL); gl.polygonOffset(1.6f, 3f)
            if (world) {
                gl.useProgram(depthWorld.id)
                gl.uniformMatrix4fv(depthWorld.u("uLightVP"), f.lightVP)
                for (c in chunks.values) { gl.bindVertexArray(c.vao); gl.drawElements(C.TRIANGLES, c.count, C.UNSIGNED_SHORT, 0) }
            }
            gl.useProgram(depthInst.id)
            gl.uniformMatrix4fv(depthInst.u("uLightVP"), f.lightVP)
            gl.disable(C.CULL_FACE)
            drawBatches(f, Cat.SOLID); drawBatches(f, Cat.FLAT)
            gl.enable(C.CULL_FACE)
            gl.disable(C.POLYGON_OFFSET_FILL)
            gl.colorMask(true, true, true, true)
        }

        // ------------------------------------------------ main pass
        gl.bindFramebuffer(C.FRAMEBUFFER, targetFbo)
        gl.viewport(0, 0, width, height)
        gl.clearColor(f.horizon[0], f.horizon[1], f.horizon[2], 1f)
        gl.clear(C.COLOR_BUFFER_BIT or C.DEPTH_BUFFER_BIT)
        gl.activeTexture(C.TEXTURE0); gl.bindTexture(C.TEXTURE_2D, shadowTex)

        if (world) {
            gl.useProgram(worldProg.id)
            lightUniforms(worldProg, f)
            gl.uniform1f(worldProg.u("uDetail"), detail)
            for (c in chunks.values) {
                if (!chunkVisible(c.c, f.viewProj)) continue
                gl.bindVertexArray(c.vao); gl.drawElements(C.TRIANGLES, c.count, C.UNSIGNED_SHORT, 0)
            }
        }
        gl.useProgram(meshProg.id)
        lightUniforms(meshProg, f)
        gl.uniform1f(meshProg.u("uOutline"), 0f)
        gl.uniform1f(meshProg.u("uMode"), 0f)
        drawBatches(f, Cat.SOLID); drawBatches(f, Cat.FLAT)
        // ink outlines (inverted hull)
        gl.cullFace(C.FRONT)
        gl.uniform1f(meshProg.u("uOutline"), 0.0026f * 1080f / height.coerceAtLeast(400))
        gl.uniform1f(meshProg.u("uMode"), 3f)
        drawBatches(f, Cat.SOLID)
        gl.cullFace(C.BACK)
        gl.uniform1f(meshProg.u("uOutline"), 0f)

        // sky fills what is left
        gl.depthMask(false)
        gl.disable(C.CULL_FACE)
        gl.useProgram(skyProg.id)
        gl.uniformMatrix4fv(skyProg.u("uInvVP"), f.invViewProj)
        gl.uniform3f(skyProg.u("uCam"), f.camX, f.camY, f.camZ)
        gl.uniform3f(skyProg.u("uZenith"), f.zenith[0], f.zenith[1], f.zenith[2])
        gl.uniform3f(skyProg.u("uHorizon"), f.horizon[0], f.horizon[1], f.horizon[2])
        gl.uniform3f(skyProg.u("uAbyss"), f.abyss[0], f.abyss[1], f.abyss[2])
        gl.uniform3f(skyProg.u("uSun"), f.sunDir[0], f.sunDir[1], f.sunDir[2])
        gl.uniform1f(skyProg.u("uTime"), f.time)
        gl.uniform1f(skyProg.u("uStars"), f.stars)
        gl.uniform1f(skyProg.u("uClouds"), f.clouds)
        gl.bindVertexArray(skyVao)
        gl.drawArrays(C.TRIANGLE_STRIP, 0, 4)

        // ------------------------------------------------ transparent passes
        gl.enable(C.BLEND)
        gl.blendFunc(C.SRC_ALPHA, C.ONE_MINUS_SRC_ALPHA)
        gl.useProgram(spriteProg.id)
        gl.uniformMatrix4fv(spriteProg.u("uVP"), f.viewProj)
        gl.uniform3f(spriteProg.u("uCam"), f.camX, f.camY, f.camZ)
        gl.uniform3f(spriteProg.u("uRight"), f.rightX, f.rightY, f.rightZ)
        gl.uniform3f(spriteProg.u("uUp"), f.upX, f.upY, f.upZ)
        gl.uniform3f(spriteProg.u("uFogCol"), f.horizon[0], f.horizon[1], f.horizon[2])
        gl.uniform2f(spriteProg.u("uFog"), f.fogStart, f.fogEnd)
        gl.uniform1f(spriteProg.u("uAdd"), 0f)
        drawSprites(f.spritesAlpha, spriteVaoA, spriteVboA)

        gl.useProgram(meshProg.id)
        gl.uniform1f(meshProg.u("uMode"), 1f)
        drawBatches(f, Cat.GLASS)

        gl.blendFunc(C.ONE, C.ONE)
        gl.uniform1f(meshProg.u("uMode"), 2f)
        drawBatches(f, Cat.GLOW)
        gl.useProgram(spriteProg.id)
        gl.uniform1f(spriteProg.u("uAdd"), 1f)
        drawSprites(f.spritesAdd, spriteVaoB, spriteVboB)

        gl.depthMask(true)
        gl.disable(C.BLEND)
        gl.bindVertexArray(0)
        val e = gl.getError()
        if (e != 0) lastError = "GL error 0x" + Integer.toHexString(e)
        return true
    }
}

@Suppress("unused") private fun keepShort(s: ShortBuffer?) = Unit
