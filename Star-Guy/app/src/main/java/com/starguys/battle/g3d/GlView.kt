package com.starguys.battle.g3d

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/** Global 3D status: if the GPU cannot run the renderer the game falls back to the 2D view. */
object Gfx3D {
    @Volatile var ok = true
    @Volatile var error = ""
}

/** The OpenGL ES 3.0 surface, placed under the translucent HUD/game canvas. */
class GlView(ctx: Context, private val bus: FrameBus = FrameBus.main) : GLSurfaceView(ctx) {
    private val renderer = Renderer(AndroidGl(), bus)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaChooser())
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                try { renderer.init(); Gfx3D.ok = true } catch (e: Throwable) {
                    Log.e("StarGuys3D", "renderer init failed", e); Gfx3D.error = e.message ?: "init"; Gfx3D.ok = false
                }
            }
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { renderer.resize(width, height) }
            override fun onDrawFrame(gl: GL10?) {
                if (!Gfx3D.ok) return
                try { renderer.draw() } catch (e: Throwable) {
                    Log.e("StarGuys3D", "draw failed", e); Gfx3D.error = e.message ?: "draw"; Gfx3D.ok = false
                }
            }
        })
        renderMode = RENDERMODE_WHEN_DIRTY
        bus.onPublish = { requestRender() }
    }

    private interface Renderer : GLSurfaceView.Renderer

    /** RGB888 + depth 24, 4x MSAA when available. */
    private class MsaaChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val es3 = 0x40 // EGL_OPENGL_ES3_BIT_KHR
            for (samples in intArrayOf(4, 0)) {
                val attrs = if (samples > 0) intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples, EGL10.EGL_NONE)
                else intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE)
                val n = IntArray(1)
                val cfgs = arrayOfNulls<EGLConfig>(1)
                if (egl.eglChooseConfig(display, attrs, cfgs, 1, n) && n[0] > 0 && cfgs[0] != null) return cfgs[0]!!
            }
            throw IllegalArgumentException("no GLES3 config")
        }
    }
}
