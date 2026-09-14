package com.jaber.brickdefense.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * پورت کامل sfx.js — همه افکت‌ها به‌صورت رویه‌ای ساخته می‌شوند (بدون فایل صوتی).
 * WebAudio → AudioTrack با PCM تولیدشده در لحظه.
 */
object Sfx {
    var enabled: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val active = AtomicInteger(0)
    private const val SR = 44100
    private const val MAX_ACTIVE = 12
    private const val PEAK_MIN = 0.0001

    private enum class Wave { SINE, SQUARE, TRIANGLE, SAWTOOTH }

    // ---------- هسته سنتز ----------

    /** پورت tone(): اسیلاتور + غلاف نمایی حمله/افت */
    private fun tone(
        wave: Wave = Wave.SINE, f0: Double = 440.0, f1: Double? = null,
        a: Double = 0.005, d: Double = 0.15, peak: Double = 0.2, delayMs: Long = 0
    ) {
        if (!enabled || active.get() >= MAX_ACTIVE) return
        val total = a + d
        val n = max(1, (SR * total).toInt())
        val pcm = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SR.toDouble()
            // غلاف: رشد نمایی تا peak در زمان a، سپس افت نمایی تا 0.0001
            val g = if (t < a) {
                val k = t / a
                exp(lerp(ln(PEAK_MIN), ln(max(peak, PEAK_MIN)), k))
            } else {
                val k = (t - a) / d
                exp(lerp(ln(max(peak, PEAK_MIN)), ln(PEAK_MIN), min(1.0, k)))
            }
            val f: Double = if (f1 != null) {
                // رشد نمایی فرکانس (مثل exponentialRampToValueAtTime)
                exp(lerp(ln(max(1.0, f0)), ln(max(1.0, f1)), min(1.0, t / total)))
            } else f0
            phase += 2.0 * PI * f / SR
            val s: Double = when (wave) {
                Wave.SINE -> sin(phase)
                Wave.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
                Wave.TRIANGLE -> 2.0 / PI * asinK(sin(phase))
                Wave.SAWTOOTH -> 2.0 * (phase / (2.0 * PI) % 1.0) - 1.0
            }
            pcm[i] = (s * g * 32767.0).toInt().toShort()
        }
        playPcm(pcm, delayMs)
    }

    private fun asinK(x: Double): Double = kotlin.math.asin(x.coerceIn(-1.0, 1.0))

    private fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k

    /** پورت noise(): نویز سفید با افت خطی + فیلتر پایین‌گذر */
    private fun noise(d: Double = 0.2, peak: Double = 0.25, filter: Double = 800.0, delayMs: Long = 0) {
        if (!enabled || active.get() >= MAX_ACTIVE) return
        val n = max(1, (SR * d).toInt())
        val pcm = ShortArray(n)
        val alpha = 1.0 - exp(-2.0 * PI * filter / SR)
        var y = 0.0
        for (i in 0 until n) {
            val x = Random.nextDouble() * 2.0 - 1.0
            y += (x - y) * alpha
            val env = 1.0 - i.toDouble() / n
            pcm[i] = (y * env * peak * 32767.0).toInt().toShort()
        }
        playPcm(pcm, delayMs)
    }

    /** پخش PCM با AudioTrack (MODE_STATIC) + مدیریت هم‌زمانی */
    private fun playPcm(pcm: ShortArray, delayMs: Long) {
        val track: AudioTrack
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = max(minBuf, pcm.size * 2)
            track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufSize, AudioTrack.MODE_STATIC, 0
            )
        } catch (e: Exception) {
            return
        }
        track.write(pcm, 0, pcm.size)
        val durMs = (pcm.size * 1000L / SR) + 120
        active.incrementAndGet()
        val play = Runnable {
            try {
                if (track.state != AudioTrack.STATE_UNINITIALIZED) {
                    track.play()
                    track.write(pcm, 0, pcm.size) // در MODE_STATIC نوشتنِ دوباره تضمین پخش است
                }
            } catch (e: Exception) { /* ignore */ }
        }
        val stop = Runnable {
            try {
                track.stop()
            } catch (e: Exception) { /* ignore */ }
            try {
                track.release()
            } catch (e: Exception) { /* ignore */ }
            active.decrementAndGet()
        }
        if (delayMs > 0) handler.postDelayed(play, delayMs) else play.run()
        handler.postDelayed(stop, delayMs + durMs + 100)
    }

    // ---------- افکت‌های بازی (پورت مستقیم sounds) ----------

    fun play(name: String, arg: Int = 3) {
        if (!enabled) return
        try {
            when (name) {
                "shot" -> tone(Wave.SQUARE, 520.0, 180.0, d = 0.08, peak = 0.07)
                "bounce" -> tone(Wave.TRIANGLE, 300.0, 200.0, d = 0.05, peak = 0.05)
                "hit" -> tone(Wave.TRIANGLE, 700.0, 400.0, d = 0.06, peak = 0.06)
                "kill" -> {
                    noise(d = 0.12, peak = 0.11, filter = 1600.0)
                    tone(Wave.SINE, 500.0, 900.0, d = 0.1, peak = 0.06)
                }
                "combo" -> {
                    val base = 550.0 + min(6, max(0, arg - 3)) * 80
                    tone(Wave.SINE, base, base * 1.5, d = 0.14, peak = 0.11)
                    tone(Wave.SINE, base * 1.26, base * 1.9, d = 0.18, peak = 0.07)
                }
                "boom" -> {
                    noise(d = 0.45, peak = 0.32, filter = 900.0)
                    tone(Wave.SINE, 120.0, 40.0, d = 0.4, peak = 0.22)
                }
                "breach" -> {
                    tone(Wave.SAWTOOTH, 200.0, 60.0, d = 0.4, peak = 0.18)
                    noise(d = 0.3, peak = 0.18, filter = 500.0)
                }
                "heal" -> tone(Wave.SINE, 600.0, 1000.0, d = 0.25, peak = 0.06)
                "stage" -> {
                    tone(Wave.SINE, 523.0, d = 0.12, peak = 0.11)
                    tone(Wave.SINE, 659.0, d = 0.12, peak = 0.11, delayMs = 120)
                    tone(Wave.SINE, 784.0, d = 0.2, peak = 0.11, delayMs = 240)
                }
                "gameover" -> tone(Wave.SAWTOOTH, 300.0, 80.0, d = 0.9, peak = 0.18)
                "stageclear" -> {
                    tone(Wave.TRIANGLE, 587.0, d = 0.14, peak = 0.11)
                    tone(Wave.TRIANGLE, 740.0, d = 0.14, peak = 0.11, delayMs = 130)
                    tone(Wave.TRIANGLE, 880.0, d = 0.2, peak = 0.12, delayMs = 260)
                    tone(Wave.SINE, 1174.0, d = 0.32, peak = 0.1, delayMs = 400)
                }
                "sprout" -> tone(Wave.SINE, 900.0, 1400.0, d = 0.1, peak = 0.06)
                "zap" -> {
                    tone(Wave.SAWTOOTH, 1800.0, 300.0, d = 0.12, peak = 0.08)
                    noise(d = 0.1, peak = 0.1, filter = 4000.0)
                }
                "thunder" -> {
                    noise(d = 0.5, peak = 0.3, filter = 2400.0)
                    tone(Wave.SAWTOOTH, 400.0, 60.0, d = 0.5, peak = 0.2)
                    tone(Wave.SQUARE, 1200.0, 200.0, d = 0.3, peak = 0.08)
                }
                "vanish" -> tone(Wave.SINE, 800.0, 120.0, d = 0.3, peak = 0.08)
                "victory" -> {
                    val notes = listOf(
                        523.0 to 0L, 659.0 to 120L, 784.0 to 240L, 1046.0 to 360L, 1318.0 to 560L
                    )
                    for ((f, t) in notes) {
                        tone(Wave.SINE, f, d = 0.5, peak = 0.13, delayMs = t)
                        tone(Wave.TRIANGLE, f * 2, d = 0.35, peak = 0.06, delayMs = t)
                    }
                    noise(d = 0.6, peak = 0.2, filter = 1200.0, delayMs = 560)
                }
                "bosshorn" -> {
                    tone(Wave.SAWTOOTH, 98.0, 92.0, d = 0.55, peak = 0.16)
                    tone(Wave.SAWTOOTH, 147.0, 138.0, d = 0.55, peak = 0.12)
                    tone(Wave.SAWTOOTH, 110.0, 65.0, d = 0.7, peak = 0.18, delayMs = 480)
                }
                "shield" -> tone(Wave.SINE, 1400.0, 700.0, d = 0.12, peak = 0.07)
                "bosskill" -> {
                    tone(Wave.SINE, 523.0, d = 0.14, peak = 0.13)
                    tone(Wave.SINE, 659.0, d = 0.14, peak = 0.13, delayMs = 140)
                    tone(Wave.SINE, 784.0, d = 0.14, peak = 0.13, delayMs = 280)
                    tone(Wave.SINE, 1046.0, d = 0.4, peak = 0.14, delayMs = 420)
                    noise(d = 0.5, peak = 0.25, filter = 900.0, delayMs = 420)
                }
                "up" -> tone(Wave.SINE, 500.0, 900.0, d = 0.18, peak = 0.09)
                "click" -> tone(Wave.SQUARE, 800.0, d = 0.04, peak = 0.04)
            }
        } catch (e: Exception) { /* ignore */ }
    }

    @Suppress("unused")
    private fun unusedAbs(x: Double) = abs(x)
}
