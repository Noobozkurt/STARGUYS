package com.starguys.battle.core

import android.content.Context
import android.content.SharedPreferences

/** Player profile + settings, persisted in SharedPreferences. */
object Save {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("starguys", Context.MODE_PRIVATE)
        Sfx.sfxOn = sfx; Sfx.musicOn = music; Sfx.hapticsOn = haptics
    }

    private fun i(k: String, d: Int) = sp?.getInt(k, d) ?: d
    private fun b(k: String, d: Boolean) = sp?.getBoolean(k, d) ?: d
    private fun s(k: String, d: String) = sp?.getString(k, d) ?: d
    private fun put(k: String, v: Int) { sp?.edit()?.putInt(k, v)?.apply() }
    private fun put(k: String, v: Boolean) { sp?.edit()?.putBoolean(k, v)?.apply() }
    private fun put(k: String, v: String) { sp?.edit()?.putString(k, v)?.apply() }

    var playerName: String get() = s("name", "Moi"); set(v) = put("name", v)
    var coins: Int get() = i("coins", 150); set(v) = put("coins", v)
    var trophies: Int get() = i("trophies", 0); set(v) = put("trophies", v)
    var xp: Int get() = i("xp", 0); set(v) = put("xp", v)
    var wins: Int get() = i("wins", 0); set(v) = put("wins", v)
    var matches: Int get() = i("matches", 0); set(v) = put("matches", v)
    var kills: Int get() = i("kills", 0); set(v) = put("kills", v)
    var hero: Int get() = i("hero", 0); set(v) = put("hero", v)
    var lastMode: Int get() = i("lastMode", 0); set(v) = put("lastMode", v)
    var biome: Int get() = i("biome", -1); set(v) = put("biome", v)

    var sfx: Boolean get() = b("sfx", true); set(v) { put("sfx", v); Sfx.sfxOn = v }
    var music: Boolean get() = b("music", true); set(v) { put("music", v); Sfx.musicOn = v }
    var haptics: Boolean get() = b("haptics", true); set(v) { put("haptics", v); Sfx.hapticsOn = v }
    /** 0 easy, 1 normal, 2 hard */
    var difficulty: Int get() = i("diff", 1); set(v) = put("diff", v)
    var royaleSize: Int get() = i("brsize", 25); set(v) = put("brsize", v)
    var aimAssist: Boolean get() = b("aimassist", true); set(v) = put("aimassist", v)
    var showFps: Boolean get() = b("fps", false); set(v) = put("fps", v)
    var stickSize: Int get() = i("stick", 1); set(v) = put("stick", v)
    var damageNumbers: Boolean get() = b("dmgnum", true); set(v) = put("dmgnum", v)

    fun heroTrophies(h: Int) = i("ht_$h", 0)
    fun addHeroTrophies(h: Int, d: Int) = put("ht_$h", maxOf(0, heroTrophies(h) + d))
    fun skinOwned(h: Int, s: Int) = s == 0 || b("skin_${h}_$s", false)
    fun buySkin(h: Int, s: Int) = put("skin_${h}_$s", true)
    fun skin(h: Int) = i("skinsel_$h", 0)
    fun setSkin(h: Int, s: Int) = put("skinsel_$h", s)

    fun level(): Int = 1 + xp / 500
    fun levelProgress(): Float = (xp % 500) / 500f

    // Creative workshop slots
    const val SLOTS = 6
    fun creative(slot: Int): String = s("creative_$slot", "")
    fun setCreative(slot: Int, v: String) = put("creative_$slot", v)
    fun creativeName(slot: Int): String = s("creative_name_$slot", "Création ${slot + 1}")
    fun setCreativeName(slot: Int, v: String) = put("creative_name_$slot", v)
}
