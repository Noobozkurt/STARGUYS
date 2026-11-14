package com.starguys.battle

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.starguys.battle.core.GameView
import com.starguys.battle.core.Save
import com.starguys.battle.core.Sfx
import com.starguys.battle.ui.MainMenu

class MainActivity : Activity() {
    private lateinit var view: GameView
    private var glView: com.starguys.battle.g3d.GlView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        Save.init(this)
        Sfx.init(this)
        view = GameView(this) { app -> MainMenu(app) }
        view.app.onFrame = { app -> com.starguys.battle.ui.NetUi.pump(app) }
        glView = try { com.starguys.battle.g3d.GlView(this) } catch (e: Throwable) { com.starguys.battle.g3d.Gfx3D.ok = false; null }
        val root = android.widget.FrameLayout(this)
        glView?.let { root.addView(it) }
        root.addView(view)
        setContentView(root)
        immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        glView?.onResume()
        view.onResumeView()
        Sfx.resumeAll()
    }

    override fun onPause() {
        view.onPauseView()
        glView?.onPause()
        Sfx.pauseAll()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        view.app.onPermissionResult(grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED })
    }

    override fun onDestroy() {
        com.starguys.battle.net.Net.leave()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        view.requestBack()
    }
}
