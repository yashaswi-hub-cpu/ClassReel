package com.classreel.player.captions

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Sits inside the player's audio pipeline, passes the sound through untouched, and
 * hands a 16 kHz mono copy to the caption engine together with the video time it belongs to.
 */
@UnstableApi
class AudioTap(
    private val onChunk: (pcm16k: ByteArray, startMs: Long, endMs: Long) -> Unit,
    private val onDiscontinuity: () -> Unit
) : BaseAudioProcessor() {

    /** Video position the next audio (after a seek / start) belongs to. Set BEFORE calling seekTo. */
    @Volatile
    var pendingBaseMs: Long = 0L

    @Volatile
    var enabled: Boolean = false

    private var baseMs = 0L
    private var frames = 0L
    private var channels = 2
    private var rate = 44100
    private var resampler = Resampler(44100)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount < 1 ||
            inputAudioFormat.sampleRate <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET // inactive: audio plays normally, no captions
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        resampler = Resampler(rate)
        return inputAudioFormat
    }

    override fun onFlush() {
        baseMs = pendingBaseMs
        frames = 0
        resampler = Resampler(rate)
        onDiscontinuity()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        val frameBytes = 2 * channels
        val nFrames = size / frameBytes
        val startMs = baseMs + frames * 1000 / rate

        if (enabled && nFrames > 0) {
            val bytes = ByteArray(size)
            inputBuffer.duplicate().get(bytes)
            val mono = ShortArray(nFrames)
            var p = 0
            for (f in 0 until nFrames) {
                var sum = 0
                for (c in 0 until channels) {
                    val lo = bytes[p].toInt() and 0xFF
                    val hi = bytes[p + 1].toInt()
                    sum += (hi shl 8) or lo
                    p += 2
                }
                mono[f] = (sum / channels).toShort()
            }
            val endMs = baseMs + (frames + nFrames) * 1000 / rate
            val out16 = resampler.process(mono)
            if (out16.isNotEmpty()) onChunk(out16, startMs, endMs)
        }
        frames += nFrames

        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }
}

/** Averages input samples down to 16 kHz (box filter, so no aliasing). Output is little-endian 16-bit PCM. */
class Resampler(inRate: Int) {
    private val ratio = inRate / 16000.0
    private var sum = 0.0
    private var windowLeft = ratio

    fun process(mono: ShortArray): ByteArray {
        val cap = (mono.size / ratio).toInt() + 4
        val out = ByteArray(cap * 2)
        var o = 0
        for (s in mono) {
            var w = 1.0
            while (w > 1e-9) {
                val take = if (w < windowLeft) w else windowLeft
                sum += s * take
                windowLeft -= take
                w -= take
                if (windowLeft <= 1e-9) {
                    val v = (sum / ratio).toInt().coerceIn(-32768, 32767)
                    if (o + 2 <= out.size) {
                        out[o++] = (v and 0xFF).toByte()
                        out[o++] = ((v shr 8) and 0xFF).toByte()
                    }
                    sum = 0.0
                    windowLeft = ratio
                }
            }
        }
        return out.copyOf(o)
    }
}
