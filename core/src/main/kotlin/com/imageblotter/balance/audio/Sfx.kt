package com.imageblotter.balance.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Sound effects synthesised at start-up, so the app ships without audio files. */
class Sfx : Disposable {
    var enabled = true

    private val sounds = HashMap<Effect, Sound>()

    enum class Effect { DROP, FALL, ROLL, WIN, CLICK }

    init {
        try {
            for (effect in Effect.entries) {
                val file = Gdx.files.local("sfx/${effect.name.lowercase()}.wav")
                file.writeBytes(wav(synth(effect)), false)
                sounds[effect] = Gdx.audio.newSound(file)
            }
        } catch (e: Throwable) {
            Gdx.app.log("Sfx", "Sound unavailable: ${e.message}")
            sounds.clear()
        }
    }

    fun play(effect: Effect, volume: Float = 1f) {
        if (enabled) sounds[effect]?.play(volume)
    }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        sounds.clear()
    }

    private companion object {
        const val RATE = 22050

        fun synth(effect: Effect): ShortArray = when (effect) {
            Effect.DROP -> thud()
            Effect.FALL -> slide(700f, 180f, 0.55f)
            Effect.ROLL -> rattle()
            Effect.WIN -> arpeggio(floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f))
            Effect.CLICK -> click(0.05f)
        }

        fun buffer(seconds: Float) = ShortArray((seconds * RATE).toInt())

        fun thud(): ShortArray {
            val out = buffer(0.22f)
            val rng = Random(1)
            for (i in out.indices) {
                val t = i.toFloat() / RATE
                val v = (sin(2 * PI * 140 * t) * 0.8 + (rng.nextFloat() - 0.5f) * 0.6) * exp(-t * 28.0)
                out[i] = (v * 26000).toInt().toShort()
            }
            return out
        }

        fun slide(from: Float, to: Float, seconds: Float): ShortArray {
            val out = buffer(seconds)
            var phase = 0.0
            for (i in out.indices) {
                val u = i.toFloat() / out.size
                phase += 2 * PI * (from + (to - from) * u) / RATE
                out[i] = (sin(phase) * exp(-u * 3.0) * 22000).toInt().toShort()
            }
            return out
        }

        fun click(seconds: Float): ShortArray {
            val out = buffer(seconds)
            for (i in out.indices) {
                val t = i.toFloat() / RATE
                out[i] = (sin(2 * PI * 1200 * t) * exp(-t * 90.0) * 16000).toInt().toShort()
            }
            return out
        }

        fun rattle(): ShortArray {
            val out = buffer(1.3f)
            val rng = Random(7)
            var at = 0f
            var gap = 0.05f
            while (at < 1.2f) {
                val start = (at * RATE).toInt()
                val len = (0.04f * RATE).toInt()
                val freq = 500f + rng.nextFloat() * 900f
                for (i in 0 until len) {
                    if (start + i >= out.size) break
                    val t = i.toFloat() / RATE
                    val v = (sin(2 * PI * freq * t) * 0.6 + (rng.nextFloat() - 0.5f) * 0.8) * exp(-t * 120.0) * 20000
                    out[start + i] = (out[start + i] + v.toInt()).coerceIn(-32000, 32000).toShort()
                }
                gap += 0.012f
                at += gap
            }
            return out
        }

        fun arpeggio(notes: FloatArray): ShortArray {
            val step = 0.14f
            val out = buffer(step * notes.size + 0.5f)
            for ((n, freq) in notes.withIndex()) {
                val start = (n * step * RATE).toInt()
                for (i in 0 until (0.6f * RATE).toInt()) {
                    if (start + i >= out.size) break
                    val t = i.toFloat() / RATE
                    val v = (sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t)) * exp(-t * 6.0) * 9000
                    out[start + i] = (out[start + i] + v.toInt()).coerceIn(-32000, 32000).toShort()
                }
            }
            return out
        }

        fun wav(samples: ShortArray): ByteArray {
            val data = samples.size * 2
            val out = ByteArrayOutputStream(44 + data)
            fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xff) }
            fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xff) }
            out.write("RIFF".toByteArray()); int(36 + data)
            out.write("WAVEfmt ".toByteArray()); int(16); short(1); short(1); int(RATE); int(RATE * 2); short(2); short(16)
            out.write("data".toByteArray()); int(data)
            for (s in samples) short(s.toInt())
            return out.toByteArray()
        }
    }
}
