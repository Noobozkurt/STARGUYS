package com.starguys.battle.g3d

import android.opengl.GLES30
import java.nio.Buffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * The small subset of OpenGL ES 3.0 the 3D renderer uses. On Android it maps to GLES30;
 * the headless test harness provides a desktop implementation so the renderer can be verified off-device.
 */
interface Gl {
    fun createShader(type: Int): Int
    fun shaderSource(s: Int, src: String)
    fun compileShader(s: Int)
    fun shaderOk(s: Int): Boolean
    fun shaderLog(s: Int): String
    fun createProgram(): Int
    fun attachShader(p: Int, s: Int)
    fun linkProgram(p: Int)
    fun programOk(p: Int): Boolean
    fun programLog(p: Int): String
    fun useProgram(p: Int)
    fun uniformLocation(p: Int, name: String): Int
    fun uniformMatrix4fv(loc: Int, v: FloatArray)
    fun uniform1f(loc: Int, v: Float)
    fun uniform2f(loc: Int, a: Float, b: Float)
    fun uniform3f(loc: Int, a: Float, b: Float, c: Float)
    fun uniform4f(loc: Int, a: Float, b: Float, c: Float, d: Float)
    fun uniform1i(loc: Int, v: Int)
    fun genBuffer(): Int
    fun bindBuffer(target: Int, b: Int)
    fun bufferData(target: Int, size: Int, data: Buffer?, usage: Int)
    fun bufferSubData(target: Int, offset: Int, size: Int, data: Buffer)
    fun genVertexArray(): Int
    fun bindVertexArray(v: Int)
    fun enableVertexAttribArray(i: Int)
    fun vertexAttribPointer(i: Int, size: Int, type: Int, norm: Boolean, stride: Int, offset: Int)
    fun vertexAttribDivisor(i: Int, d: Int)
    fun vertexAttrib4f(i: Int, a: Float, b: Float, c: Float, d: Float)
    fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instances: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offset: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int)
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun cullFace(mode: Int)
    fun depthMask(b: Boolean)
    fun depthFunc(f: Int)
    fun blendFunc(s: Int, d: Int)
    fun viewport(x: Int, y: Int, w: Int, h: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)
    fun genTexture(): Int
    fun bindTexture(target: Int, t: Int)
    fun texImage2D(target: Int, level: Int, internal: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: Buffer?)
    fun texParameteri(target: Int, p: Int, v: Int)
    fun genFramebuffer(): Int
    fun bindFramebuffer(target: Int, f: Int)
    fun framebufferTexture2D(target: Int, attach: Int, texTarget: Int, tex: Int, level: Int)
    fun checkFramebufferStatus(target: Int): Int
    fun activeTexture(unit: Int)
    fun polygonOffset(f: Float, u: Float)
    fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean)
    fun getError(): Int
}

/** GL constants (identical values on every GLES 3.0 implementation). */
object C {
    const val VERTEX_SHADER = 0x8B31; const val FRAGMENT_SHADER = 0x8B30
    const val ARRAY_BUFFER = 0x8892; const val ELEMENT_ARRAY_BUFFER = 0x8893
    const val STATIC_DRAW = 0x88E4; const val DYNAMIC_DRAW = 0x88E8; const val STREAM_DRAW = 0x88E0
    const val FLOAT = 0x1406; const val UNSIGNED_SHORT = 0x1403; const val UNSIGNED_INT = 0x1405; const val UNSIGNED_BYTE = 0x1401
    const val TRIANGLES = 0x0004; const val TRIANGLE_STRIP = 0x0005
    const val DEPTH_TEST = 0x0B71; const val CULL_FACE = 0x0B44; const val BLEND = 0x0BE2; const val POLYGON_OFFSET_FILL = 0x8037
    const val BACK = 0x0405; const val FRONT = 0x0404
    const val LESS = 0x0201; const val LEQUAL = 0x0203
    const val SRC_ALPHA = 0x0302; const val ONE_MINUS_SRC_ALPHA = 0x0303; const val ONE = 1
    const val COLOR_BUFFER_BIT = 0x4000; const val DEPTH_BUFFER_BIT = 0x0100
    const val TEXTURE_2D = 0x0DE1; const val TEXTURE0 = 0x84C0; const val TEXTURE1 = 0x84C1
    const val DEPTH_COMPONENT = 0x1902; const val DEPTH_COMPONENT24 = 0x81A6; const val DEPTH_COMPONENT16 = 0x81A5
    const val TEXTURE_MIN_FILTER = 0x2801; const val TEXTURE_MAG_FILTER = 0x2800; const val LINEAR = 0x2601; const val NEAREST = 0x2600
    const val TEXTURE_WRAP_S = 0x2802; const val TEXTURE_WRAP_T = 0x2803; const val CLAMP_TO_EDGE = 0x812F
    const val TEXTURE_COMPARE_MODE = 0x884C; const val TEXTURE_COMPARE_FUNC = 0x884D; const val COMPARE_REF_TO_TEXTURE = 0x884E
    const val FRAMEBUFFER = 0x8D40; const val DEPTH_ATTACHMENT = 0x8D00; const val FRAMEBUFFER_COMPLETE = 0x8CD5
    const val RGBA = 0x1908
}

class AndroidGl : Gl {
    private val iv = IntArray(1)
    override fun createShader(type: Int) = GLES30.glCreateShader(type)
    override fun shaderSource(s: Int, src: String) = GLES30.glShaderSource(s, src)
    override fun compileShader(s: Int) = GLES30.glCompileShader(s)
    override fun shaderOk(s: Int): Boolean { GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, iv, 0); return iv[0] != 0 }
    override fun shaderLog(s: Int): String = GLES30.glGetShaderInfoLog(s)
    override fun createProgram() = GLES30.glCreateProgram()
    override fun attachShader(p: Int, s: Int) = GLES30.glAttachShader(p, s)
    override fun linkProgram(p: Int) = GLES30.glLinkProgram(p)
    override fun programOk(p: Int): Boolean { GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, iv, 0); return iv[0] != 0 }
    override fun programLog(p: Int): String = GLES30.glGetProgramInfoLog(p)
    override fun useProgram(p: Int) = GLES30.glUseProgram(p)
    override fun uniformLocation(p: Int, name: String) = GLES30.glGetUniformLocation(p, name)
    override fun uniformMatrix4fv(loc: Int, v: FloatArray) = GLES30.glUniformMatrix4fv(loc, 1, false, v, 0)
    override fun uniform1f(loc: Int, v: Float) = GLES30.glUniform1f(loc, v)
    override fun uniform2f(loc: Int, a: Float, b: Float) = GLES30.glUniform2f(loc, a, b)
    override fun uniform3f(loc: Int, a: Float, b: Float, c: Float) = GLES30.glUniform3f(loc, a, b, c)
    override fun uniform4f(loc: Int, a: Float, b: Float, c: Float, d: Float) = GLES30.glUniform4f(loc, a, b, c, d)
    override fun uniform1i(loc: Int, v: Int) = GLES30.glUniform1i(loc, v)
    override fun genBuffer(): Int { GLES30.glGenBuffers(1, iv, 0); return iv[0] }
    override fun bindBuffer(target: Int, b: Int) = GLES30.glBindBuffer(target, b)
    override fun bufferData(target: Int, size: Int, data: Buffer?, usage: Int) = GLES30.glBufferData(target, size, data, usage)
    override fun bufferSubData(target: Int, offset: Int, size: Int, data: Buffer) = GLES30.glBufferSubData(target, offset, size, data)
    override fun genVertexArray(): Int { GLES30.glGenVertexArrays(1, iv, 0); return iv[0] }
    override fun bindVertexArray(v: Int) = GLES30.glBindVertexArray(v)
    override fun enableVertexAttribArray(i: Int) = GLES30.glEnableVertexAttribArray(i)
    override fun vertexAttribPointer(i: Int, size: Int, type: Int, norm: Boolean, stride: Int, offset: Int) = GLES30.glVertexAttribPointer(i, size, type, norm, stride, offset)
    override fun vertexAttribDivisor(i: Int, d: Int) = GLES30.glVertexAttribDivisor(i, d)
    override fun vertexAttrib4f(i: Int, a: Float, b: Float, c: Float, d: Float) = GLES30.glVertexAttrib4f(i, a, b, c, d)
    override fun drawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instances: Int) = GLES30.glDrawElementsInstanced(mode, count, type, offset, instances)
    override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) = GLES30.glDrawElements(mode, count, type, offset)
    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
    override fun drawArraysInstanced(mode: Int, first: Int, count: Int, instances: Int) = GLES30.glDrawArraysInstanced(mode, first, count, instances)
    override fun enable(cap: Int) = GLES30.glEnable(cap)
    override fun disable(cap: Int) = GLES30.glDisable(cap)
    override fun cullFace(mode: Int) = GLES30.glCullFace(mode)
    override fun depthMask(b: Boolean) = GLES30.glDepthMask(b)
    override fun depthFunc(f: Int) = GLES30.glDepthFunc(f)
    override fun blendFunc(s: Int, d: Int) = GLES30.glBlendFunc(s, d)
    override fun viewport(x: Int, y: Int, w: Int, h: Int) = GLES30.glViewport(x, y, w, h)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES30.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES30.glClear(mask)
    override fun genTexture(): Int { GLES30.glGenTextures(1, iv, 0); return iv[0] }
    override fun bindTexture(target: Int, t: Int) = GLES30.glBindTexture(target, t)
    override fun texImage2D(target: Int, level: Int, internal: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: Buffer?) = GLES30.glTexImage2D(target, level, internal, w, h, border, format, type, data)
    override fun texParameteri(target: Int, p: Int, v: Int) = GLES30.glTexParameteri(target, p, v)
    override fun genFramebuffer(): Int { GLES30.glGenFramebuffers(1, iv, 0); return iv[0] }
    override fun bindFramebuffer(target: Int, f: Int) = GLES30.glBindFramebuffer(target, f)
    override fun framebufferTexture2D(target: Int, attach: Int, texTarget: Int, tex: Int, level: Int) = GLES30.glFramebufferTexture2D(target, attach, texTarget, tex, level)
    override fun checkFramebufferStatus(target: Int) = GLES30.glCheckFramebufferStatus(target)
    override fun activeTexture(unit: Int) = GLES30.glActiveTexture(unit)
    override fun polygonOffset(f: Float, u: Float) = GLES30.glPolygonOffset(f, u)
    override fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GLES30.glColorMask(r, g, b, a)
    override fun getError() = GLES30.glGetError()
}

@Suppress("unused") private fun keep(f: FloatBuffer?, i: IntBuffer?) = Unit
