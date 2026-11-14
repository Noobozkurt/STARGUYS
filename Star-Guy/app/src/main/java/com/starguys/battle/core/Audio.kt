package com.starguys.battle.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * All sounds are synthesized at startup (no audio assets needed): short WAV files are
 * generated into the cache directory and loaded into a SoundPool. Music is a small
 * procedural chiptune sequencer rendered to a looping WAV.
 */
object Sfx {
    const val SHOOT = 0; const val SHOTGUN = 1; const val LASER = 2; const val HIT = 3; const val EXPLODE = 4
    const val PICKUP = 5; const val JUMP = 6; const val SUPER = 7; const val POP = 8; const val CLICK = 9
    const val HEAL = 10; const val COIN = 11; const val BOING = 12; const val SPLASH = 13; const val WHISTLE = 14
    const val ALARM = 15; const val WIN = 16; const val LOSE = 17; const val SWING = 18; const val ZAP = 19
    const val HURT = 20; const val BUBBLE = 21; const val GLASS = 22; const val FLAME = 23; const val METEOR = 24
    const val THUD = 25; const val ENGINE = 26; const val TELEPORT = 27; const val COUNT = 28
    private const val N = 29

    private const val SR = 22050
    private var pool: SoundPool? = null
    private val ids = IntArray(N)
    private val loaded = BooleanArray(N)
    @Volatile private var ready = false
    private var player: MediaPlayer? = null
    private var currentTrack = 0
    private var wantTrack = 0
    private var cacheDir: File? = null
    private val lastPlay = LongArray(N)

    var sfxOn = true
    var musicOn = true
    var vibrator: Vibrator? = null
    var hapticsOn = true

    fun init(ctx: Context) {
        cacheDir = ctx.cacheDir
        @Suppress("DEPRECATION")
        vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        Thread {
            try {
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                val p = SoundPool.Builder().setMaxStreams(14).setAudioAttributes(attrs).build()
                p.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) for (i in 0 until N) if (ids[i] == sampleId) loaded[i] = true
                }
                pool = p
                for (i in 0 until N) {
                    val data = synth(i) ?: continue
                    val f = File(ctx.cacheDir, "sfx_$i.wav")
                    writeWav(f, data)
                    ids[i] = p.load(f.absolutePath, 1)
                }
                ready = true
                // pre-render music
                for (t in 1..2) {
                    val f = File(ctx.cacheDir, "music_$t.wav")
                    if (!f.exists() || f.length() < 1000) writeWav(f, Music.render(t))
                }
                synchronized(this) { if (wantTrack != 0) startTrack(wantTrack) }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }.start()
    }

    fun play(s: Int, vol: Float = 1f, rate: Float = 1f) {
        if (!sfxOn || !ready || vol <= 0.02f) return
        val p = pool ?: return
        if (s < 0 || s >= N || !loaded[s]) return
        val now = System.nanoTime()
        if (now - lastPlay[s] < 35_000_000L) return // rate-limit identical sounds
        lastPlay[s] = now
        val v = clamp(vol, 0f, 1f)
        p.play(ids[s], v, v, 1, 0, clamp(rate, 0.5f, 2f))
    }

    fun buzz(ms: Long) {
        if (!hapticsOn) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (_: Throwable) { }
    }

    /** 0 = silence, 1 = menu, 2 = battle */
    fun music(track: Int) {
        synchronized(this) {
            wantTrack = track
            if (!musicOn) { stopMusic(); return }
            if (track == currentTrack && player != null) return
            if (!ready) return
            startTrack(track)
        }
    }

    fun refreshMusic() { val t = wantTrack; currentTrack = -1; stopMusic(); music(t) }

    private fun stopMusic() {
        try { player?.stop(); player?.release() } catch (_: Throwable) { }
        player = null; currentTrack = 0
    }

    private fun startTrack(track: Int) {
        stopMusic()
        if (track == 0 || !musicOn) return
        val f = File(cacheDir ?: return, "music_$track.wav")
        if (!f.exists()) return
        try {
            val mp = MediaPlayer()
            mp.setDataSource(f.absolutePath)
            mp.isLooping = true
            mp.setVolume(0.45f, 0.45f)
            mp.prepare()
            mp.start()
            player = mp; currentTrack = track
        } catch (e: Throwable) { e.printStackTrace() }
    }

    fun pauseAll() { try { player?.pause() } catch (_: Throwable) { }; pool?.autoPause() }
    fun resumeAll() { try { if (musicOn) player?.start() } catch (_: Throwable) { }; pool?.autoResume() }

    // ------------------------------------------------------------------ synthesis
    private fun writeWav(f: File, s: FloatArray) {
        val bb = ByteBuffer.allocate(44 + s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + s.size * 2); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(SR); bb.putInt(SR * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(s.size * 2)
        for (v in s) bb.putShort((clamp(v, -1f, 1f) * 32000).toInt().toShort())
        FileOutputStream(f).use { it.write(bb.array()) }
    }

    private fun buf(sec: Float) = FloatArray((sec * SR).toInt())

    /** wave: 0 sine 1 square 2 saw 3 triangle 4 noise */
    private fun tone(out: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, wave: Int, vol: Float,
                     attack: Float = 0.005f, decay: Float = 6f, duty: Float = 0.5f) {
        val s0 = (start * SR).toInt(); val n = (dur * SR).toInt()
        var ph = 0.0
        var noise = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx < 0 || idx >= out.size) break
            val t = i.toFloat() / SR
            val k = i.toFloat() / n
            val f = f0 * (f1 / f0).toDouble().pow(k.toDouble()).toFloat()
            ph += f / SR
            val p = (ph % 1.0).toFloat()
            val v = when (wave) {
                0 -> sin(2 * PI * ph).toFloat()
                1 -> if (p < duty) 1f else -1f
                2 -> 2f * p - 1f
                3 -> if (p < 0.5f) 4f * p - 1f else 3f - 4f * p
                else -> { if (i % maxOf(1, (SR / maxOf(f, 1f)).toInt()) == 0) noise = rf(-1f, 1f); noise }
            }
            val env = (if (t < attack) t / attack else 1f) * exp(-decay * t).toFloat() * (1f - k * 0.2f)
            out[idx] += v * vol * env
        }
    }

    private fun synth(id: Int): FloatArray? {
        when (id) {
            SHOOT -> { val b = buf(0.12f); tone(b, 0f, 0.12f, 900f, 260f, 1, 0.35f, decay = 22f, duty = 0.3f); return b }
            SHOTGUN -> { val b = buf(0.3f); tone(b, 0f, 0.3f, 4000f, 800f, 4, 0.6f, decay = 14f); tone(b, 0f, 0.1f, 180f, 60f, 0, 0.6f, decay = 20f); return b }
            LASER -> { val b = buf(0.35f); tone(b, 0f, 0.35f, 300f, 2400f, 2, 0.3f, decay = 8f); tone(b, 0f, 0.35f, 2000f, 2000f, 4, 0.12f, decay = 10f); return b }
            HIT -> { val b = buf(0.12f); tone(b, 0f, 0.12f, 220f, 70f, 0, 0.7f, decay = 25f); tone(b, 0f, 0.06f, 3000f, 1000f, 4, 0.25f, decay = 40f); return b }
            EXPLODE -> { val b = buf(0.9f); tone(b, 0f, 0.9f, 1200f, 90f, 4, 0.8f, decay = 4.5f); tone(b, 0f, 0.5f, 90f, 30f, 0, 0.8f, decay = 6f); return b }
            PICKUP -> { val b = buf(0.3f); tone(b, 0f, 0.08f, 660f, 660f, 3, 0.4f, decay = 8f); tone(b, 0.08f, 0.08f, 880f, 880f, 3, 0.4f, decay = 8f); tone(b, 0.16f, 0.14f, 1320f, 1320f, 3, 0.4f, decay = 10f); return b }
            JUMP -> { val b = buf(0.18f); tone(b, 0f, 0.18f, 280f, 700f, 1, 0.22f, decay = 10f, duty = 0.25f); return b }
            SUPER -> { val b = buf(0.6f); tone(b, 0f, 0.6f, 220f, 880f, 2, 0.25f, decay = 3f); tone(b, 0f, 0.6f, 330f, 1320f, 1, 0.15f, decay = 3f); return b }
            POP -> { val b = buf(0.5f); tone(b, 0f, 0.05f, 900f, 200f, 0, 0.6f, decay = 30f); for (k in 0..5) tone(b, 0.05f + k * 0.05f, 0.08f, 1500f + k * 300f, 1800f + k * 300f, 0, 0.18f, decay = 20f); return b }
            CLICK -> { val b = buf(0.06f); tone(b, 0f, 0.06f, 1200f, 900f, 3, 0.35f, decay = 40f); return b }
            HEAL -> { val b = buf(0.45f); for (k in 0..3) tone(b, k * 0.08f, 0.15f, 520f * (1 + k * 0.25f), 540f * (1 + k * 0.25f), 0, 0.25f, decay = 10f); return b }
            COIN -> { val b = buf(0.3f); tone(b, 0f, 0.07f, 988f, 988f, 1, 0.25f, decay = 6f); tone(b, 0.07f, 0.2f, 1319f, 1319f, 1, 0.25f, decay = 7f); return b }
            BOING -> {
                val b = buf(0.4f)
                for (i in b.indices) { val t = i.toFloat() / SR; val f = 180f + 120f * t + 40f * sin(t * 60f); b[i] = (sin(2 * PI * f * t).toFloat()) * 0.45f * exp(-6f * t) }
                return b
            }
            SPLASH -> { val b = buf(0.5f); tone(b, 0f, 0.5f, 3000f, 400f, 4, 0.4f, decay = 6f); return b }
            WHISTLE -> { val b = buf(0.6f); for (i in b.indices) { val t = i.toFloat() / SR; b[i] = sin(2 * PI * (2400f + 120f * sin(t * 90f)) * t).toFloat() * 0.3f * (if (t > 0.5f) (0.6f - t) * 10f else 1f) }; return b }
            ALARM -> { val b = buf(0.8f); for (k in 0..3) tone(b, k * 0.2f, 0.18f, if (k % 2 == 0) 880f else 660f, if (k % 2 == 0) 880f else 660f, 1, 0.22f, decay = 1f); return b }
            WIN -> { val b = buf(1.4f); val notes = floatArrayOf(523f, 659f, 784f, 1047f, 784f, 1047f); for (k in notes.indices) tone(b, k * 0.14f, 0.4f, notes[k], notes[k], 1, 0.22f, decay = 4f, duty = 0.25f); tone(b, 0.84f, 0.55f, 1047f, 1047f, 3, 0.35f, decay = 3f); return b }
            LOSE -> { val b = buf(1.2f); val notes = floatArrayOf(392f, 370f, 349f, 330f); for (k in notes.indices) tone(b, k * 0.22f, 0.4f, notes[k], notes[k] * 0.97f, 2, 0.2f, decay = 4f); return b }
            SWING -> { val b = buf(0.2f); tone(b, 0f, 0.2f, 600f, 2500f, 4, 0.25f, decay = 12f); return b }
            ZAP -> { val b = buf(0.25f); tone(b, 0f, 0.25f, 1800f, 300f, 1, 0.22f, decay = 10f, duty = 0.15f); tone(b, 0f, 0.25f, 5000f, 3000f, 4, 0.1f, decay = 12f); return b }
            HURT -> { val b = buf(0.2f); tone(b, 0f, 0.2f, 400f, 150f, 1, 0.25f, decay = 12f, duty = 0.4f); return b }
            BUBBLE -> { val b = buf(0.2f); tone(b, 0f, 0.2f, 300f, 1200f, 0, 0.4f, decay = 14f); return b }
            GLASS -> { val b = buf(0.5f); tone(b, 0f, 0.5f, 6000f, 3000f, 4, 0.3f, decay = 9f); for (k in 0..3) tone(b, k * 0.04f, 0.2f, 2500f + k * 700f, 2400f + k * 700f, 0, 0.12f, decay = 18f); return b }
            FLAME -> { val b = buf(0.25f); tone(b, 0f, 0.25f, 900f, 500f, 4, 0.25f, decay = 6f); return b }
            METEOR -> { val b = buf(0.8f); tone(b, 0f, 0.8f, 2200f, 300f, 0, 0.2f, attack = 0.3f, decay = 1f); return b }
            THUD -> { val b = buf(0.25f); tone(b, 0f, 0.25f, 120f, 40f, 0, 0.8f, decay = 14f); return b }
            ENGINE -> { val b = buf(0.3f); tone(b, 0f, 0.3f, 70f, 90f, 2, 0.3f, decay = 3f); return b }
            TELEPORT -> { val b = buf(0.4f); tone(b, 0f, 0.4f, 200f, 3000f, 0, 0.3f, decay = 5f); tone(b, 0.1f, 0.3f, 3000f, 200f, 0, 0.2f, decay = 5f); return b }
            COUNT -> { val b = buf(0.25f); tone(b, 0f, 0.25f, 880f, 880f, 1, 0.25f, decay = 8f, duty = 0.3f); return b }
        }
        return null
    }

    /** Tiny chiptune sequencer. */
    private object Music {
        private fun midi(n: Int): Float = 440f * 2f.pow((n - 69) / 12f)

        fun render(track: Int): FloatArray {
            val bpm = if (track == 1) 104f else 140f
            val beat = 60f / bpm
            val bars = 8
            val total = bars * 4 * beat
            val out = FloatArray((total * SR).toInt() + 10)
            val rnd = java.util.Random(if (track == 1) 7L else 42L)
            // C - G - Am - F progression, twice (second half uses Dm - G variation)
            val roots = if (track == 1) intArrayOf(48, 43, 45, 41, 48, 43, 41, 43) else intArrayOf(45, 41, 48, 43, 45, 41, 43, 44)
            val scale = intArrayOf(0, 2, 4, 7, 9)
            for (bar in 0 until bars) {
                val root = roots[bar]
                val t0 = bar * 4 * beat
                val minor = (track == 2 && (root == 45 || root == 44)) || (track == 1 && root == 45)
                val third = if (minor) 3 else 4
                // bass: 8th notes
                for (k in 0 until 8) {
                    val n = if (k % 4 == 3) root + 7 else root
                    tone(out, t0 + k * beat / 2, beat / 2 * 0.9f, midi(n - 12), midi(n - 12), 3, 0.32f, decay = 3f)
                }
                // arpeggio pad
                val chord = intArrayOf(root + 12, root + 12 + third, root + 19, root + 24)
                for (k in 0 until 16) {
                    val n = chord[k % 4]
                    tone(out, t0 + k * beat / 4, beat / 4 * 0.8f, midi(n), midi(n), 1, if (track == 1) 0.05f else 0.06f, decay = 9f, duty = 0.25f)
                }
                // lead melody
                var k = 0
                while (k < 8) {
                    val len = if (rnd.nextFloat() < 0.3f) 2 else 1
                    if (rnd.nextFloat() < 0.8f) {
                        val deg = scale[rnd.nextInt(scale.size)]
                        val n = root + 24 + deg + (if (rnd.nextFloat() < 0.2f) 12 else 0)
                        tone(out, t0 + k * beat / 2, beat / 2 * len * 0.9f, midi(n), midi(n), 1, if (track == 1) 0.07f else 0.09f, decay = 2.5f, duty = 0.5f)
                    }
                    k += len
                }
                // drums
                for (b in 0 until 4) {
                    val tb = t0 + b * beat
                    tone(out, tb, 0.18f, 160f, 45f, 0, if (track == 1) 0.35f else 0.5f, decay = 16f)
                    if (b % 2 == 1) tone(out, tb, 0.15f, 5000f, 2000f, 4, if (track == 1) 0.12f else 0.2f, decay = 18f)
                    tone(out, tb + beat / 2, 0.04f, 9000f, 8000f, 4, 0.05f, decay = 50f)
                    if (track == 2) tone(out, tb + beat / 4 * 3, 0.03f, 9000f, 8000f, 4, 0.04f, decay = 60f)
                }
            }
            // soft limiter
            for (i in out.indices) { val v = out[i]; out[i] = v / (1f + kotlin.math.abs(v)) * 1.2f }
            return out
        }
    }
}
