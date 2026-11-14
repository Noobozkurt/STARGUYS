package com.starguys.battle.core

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class TouchEvt(val action: Int, val id: Int, val x: Float, val y: Float) {
    companion object { const val DOWN = 0; const val MOVE = 1; const val UP = 2 }
}

/** Base for every full-screen state (menus, match, editor...). */
abstract class Screen(val app: GameApp) {
    val btns = ArrayList<Btn>()
    open fun enter() {}
    open fun leave() {}
    /** Called when the screen size is known / changes. */
    open fun layout() {}
    open fun update(dt: Float) {}
    abstract fun draw(c: Canvas)
    open fun touch(e: TouchEvt) { handleButtons(e) }
    open fun back() {}
    open fun onPause() {}

    fun handleButtons(e: TouchEvt): Boolean {
        var consumed = false
        for (i in btns.indices.reversed()) {
            val b = btns[i]
            if (!b.visible || !b.enabled) continue
            when (e.action) {
                TouchEvt.DOWN -> if (b.hit(e.x, e.y, app.u)) { b.pressed = true; b.pid = e.id; consumed = true; break }
                TouchEvt.MOVE -> if (b.pid == e.id) { b.pressed = b.hit(e.x, e.y, app.u); consumed = true }
                TouchEvt.UP -> if (b.pid == e.id) {
                    val fire = b.pressed && b.hit(e.x, e.y, app.u)
                    b.pressed = false; b.pid = -1; consumed = true
                    if (fire) { Sfx.play(Sfx.CLICK); b.onClick() }
                    break
                }
            }
        }
        return consumed
    }

    fun drawButtons(c: Canvas) { for (b in btns) if (b.visible) b.draw(c, app.u) }
}

/** Chunky cartoon button. Coordinates are in screen pixels. */
class Btn(var label: String, var x: Float, var y: Float, var w: Float, var h: Float, var color: Int,
          val onClick: () -> Unit) {
    var pressed = false
    var pid = -1
    var enabled = true
    var visible = true
    var selected = false
    var sub: String? = null
    var textSize = 0f
    var icon: ((Canvas, Float, Float, Float) -> Unit)? = null

    fun set(nx: Float, ny: Float, nw: Float, nh: Float): Btn { x = nx; y = ny; w = nw; h = nh; return this }
    fun hit(px: Float, py: Float, u: Float): Boolean = px >= x - 6 * u && px <= x + w + 6 * u && py >= y - 6 * u && py <= y + h + 6 * u

    fun draw(c: Canvas, u: Float) {
        val off = if (pressed) 4 * u else 0f
        val lip = 7 * u
        val col = if (enabled) color else 0xFF8A8494.toInt()
        G.ow = 3 * u
        G.rrect(c, x, y + lip, x + w, y + h + lip, 16 * u, G.shade(col, 0.6f))
        G.rrect(c, x, y + off, x + w, y + h + off, 16 * u, col)
        G.rrect(c, x + 6 * u, y + off + 5 * u, x + w - 6 * u, y + off + h * 0.42f, 10 * u, G.alpha(G.WHITE, 0.22f), false)
        if (selected) { G.stroke.strokeWidth = 5 * u; G.rf.set(x - 5 * u, y - 5 * u, x + w + 5 * u, y + h + lip + 5 * u); G.stroke.color = 0xFFFFE14D.toInt(); c.drawRoundRect(G.rf, 20 * u, 20 * u, G.stroke) }
        val ts = if (textSize > 0) textSize * u else minOf(h * 0.42f, 34 * u)
        val ic = icon
        if (ic != null) {
            val isz = if (label.isEmpty()) h * 0.36f else h * 0.28f
            val iy = if (label.isEmpty()) y + h / 2 + off else y + h * 0.38f + off
            ic(c, x + w / 2, iy, isz)
            if (label.isNotEmpty()) G.text(c, label, x + w / 2, y + h * 0.86f + off, ts * 0.75f, G.WHITE)
        } else {
            val sb = sub
            if (sb != null) {
                G.text(c, label, x + w / 2, y + h * 0.48f + off, ts, G.WHITE)
                G.text(c, sb, x + w / 2, y + h * 0.82f + off, ts * 0.55f, 0xFFFFF3B0.toInt())
            } else {
                G.text(c, label, x + w / 2, y + h / 2 + ts * 0.36f + off, ts, G.WHITE)
            }
        }
    }
}

class GameApp(val act: Activity) {
    var w = 1280f; var h = 720f; var u = 1f
    var screen: Screen? = null
    private var pending: Screen? = null
    private var fadeT = 0f
    private val posted = ArrayList<() -> Unit>()
    private var toastMsg = ""
    private var toastT = 0f
    var time = 0f
    var fps = 60f
    private val bgPaint = Paint()

    fun resize(nw: Int, nh: Int) {
        w = nw.toFloat(); h = nh.toFloat(); u = h / 720f
        screen?.layout()
    }

    fun go(s: Screen) { pending = s; if (screen == null) fadeT = 0f }

    fun post(r: () -> Unit) { synchronized(posted) { posted.add(r) } }

    fun toast(msg: String) { toastMsg = msg; toastT = 2.6f }

    fun update(dt: Float) {
        time += dt
        val run: List<() -> Unit>
        synchronized(posted) { run = ArrayList(posted); posted.clear() }
        for (r in run) r()
        val p = pending
        if (p != null) {
            screen?.leave()
            screen = p; pending = null
            p.layout(); p.enter()
            fadeT = 0.25f
        }
        if (fadeT > 0) fadeT -= dt
        if (toastT > 0) toastT -= dt
        screen?.update(dt)
    }

    fun draw(c: Canvas) {
        screen?.draw(c)
        if (toastT > 0) {
            val a = clamp(toastT * 2, 0f, 1f)
            G.fade = a
            val tw = G.textW(toastMsg, 26 * u) + 50 * u
            G.ow = 3 * u
            G.rrect(c, w / 2 - tw / 2, 18 * u, w / 2 + tw / 2, 68 * u, 22 * u, 0xEE2A2140.toInt())
            G.text(c, toastMsg, w / 2, 52 * u, 26 * u, G.WHITE)
            G.fade = 1f
        }
        if (fadeT > 0) {
            G.fill.shader = null
            G.fill.color = G.alpha(0xFF000000.toInt(), fadeT / 0.25f)
            c.drawRect(0f, 0f, w, h, G.fill)
        }
        if (Save.showFps) G.text(c, "${fps.toInt()} FPS", 60 * u, h - 14 * u, 18 * u, 0xFF9CFF9C.toInt())
    }

    fun touch(e: TouchEvt) { if (pending == null) screen?.touch(e) }
    fun back() { screen?.back() }

    /** Animated candy-colored menu background. */
    fun menuBackground(c: Canvas, top: Int = 0xFF5B2BD6.toInt(), bottom: Int = 0xFFFF5FA2.toInt()) {
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, bgPaint)
        // drifting stars & bubbles
        for (i in 0 until 26) {
            val sx = ((i * 197.3f + time * (14f + i % 5 * 6f)) % (w + 120)) - 60
            val sy = ((i * 83.7f) % h + kotlin.math.sin(time * 0.8f + i) * 18 * u)
            val r = (6 + i % 4 * 5) * u
            if (i % 3 == 0) G.star(c, sx, sy, r * 1.4f, r * 0.6f, 5, time * 0.5f + i, G.alpha(G.WHITE, 0.18f), false)
            else G.circle(c, sx, sy, r, G.alpha(G.WHITE, 0.10f), false)
        }
    }

    // ---- Android services (run on UI thread) ----
    fun shareText(subject: String, text: String) {
        act.runOnUiThread {
            try {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, text)
                }
                act.startActivity(Intent.createChooser(i, subject))
            } catch (e: Throwable) { post { toast("Partage impossible") } }
        }
    }

    fun copyText(text: String) {
        act.runOnUiThread {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Star-Guys", text))
        }
    }

    fun readClipboard(cb: (String) -> Unit) {
        act.runOnUiThread {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val t = try { cm?.primaryClip?.getItemAt(0)?.coerceToText(act)?.toString() ?: "" } catch (e: Throwable) { "" }
            post { cb(t) }
        }
    }

    fun quit() { act.runOnUiThread { act.finish() } }
}

/** SurfaceView + dedicated game thread with a fixed 60 Hz simulation step. */
class GameView(val activity: Activity, private val makeFirst: (GameApp) -> Screen) : SurfaceView(activity),
    SurfaceHolder.Callback, Runnable {
    val app = GameApp(activity)
    @Volatile private var running = false
    @Volatile private var hasSurface = false
    private var thread: Thread? = null
    private val pending = ArrayList<TouchEvt>()
    private val work = ArrayList<TouchEvt>()
    @Volatile private var backRequested = false
    private var started = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(h: SurfaceHolder) { hasSurface = true; startThread() }
    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hgt: Int) {
        app.post { app.resize(w, hgt) }
        if (!started) { started = true; app.post { app.resize(w, hgt); app.go(makeFirst(app)) } }
    }
    override fun surfaceDestroyed(h: SurfaceHolder) { hasSurface = false; stopThread() }

    fun onResumeView() { if (hasSurface) startThread() }
    fun onPauseView() { app.post { app.screen?.onPause() }; stopThread() }
    fun requestBack() { backRequested = true }

    private fun startThread() {
        if (running) return
        running = true
        thread = Thread(this, "game").also { it.start() }
    }

    private fun stopThread() {
        running = false
        try { thread?.join(800) } catch (_: InterruptedException) { }
        thread = null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        synchronized(pending) {
            when (act) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex
                    pending.add(TouchEvt(TouchEvt.DOWN, e.getPointerId(i), e.getX(i), e.getY(i)))
                }
                MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount)
                    pending.add(TouchEvt(TouchEvt.MOVE, e.getPointerId(i), e.getX(i), e.getY(i)))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val i = e.actionIndex
                    pending.add(TouchEvt(TouchEvt.UP, e.getPointerId(i), e.getX(i), e.getY(i)))
                }
                MotionEvent.ACTION_CANCEL -> for (i in 0 until e.pointerCount)
                    pending.add(TouchEvt(TouchEvt.UP, e.getPointerId(i), -9999f, -9999f))
            }
        }
        return true
    }

    override fun run() {
        val step = 1f / 60f
        var acc = 0f
        var last = System.nanoTime()
        var fpsAcc = 0f; var fpsN = 0
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1e9f
            last = now
            if (dt > 0.1f) dt = 0.1f
            fpsAcc += dt; fpsN++
            if (fpsAcc > 0.5f) { app.fps = fpsN / fpsAcc; fpsAcc = 0f; fpsN = 0 }
            try {
                synchronized(pending) { work.addAll(pending); pending.clear() }
                for (e in work) app.touch(e)
                work.clear()
                if (backRequested) { backRequested = false; app.back() }
                acc += dt
                var n = 0
                while (acc >= step && n < 4) { app.update(step); acc -= step; n++ }
                if (n == 4) acc = 0f
                val c: Canvas? = try {
                    if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                } catch (e: Throwable) { null }
                if (c != null) {
                    try { app.draw(c) } finally { holder.unlockCanvasAndPost(c) }
                } else Thread.sleep(16)
            } catch (e: Throwable) {
                e.printStackTrace()
                try { Thread.sleep(16) } catch (_: InterruptedException) { }
            }
        }
    }
}
