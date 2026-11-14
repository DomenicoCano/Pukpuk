package com.example.pukpuk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.tanh

/** Streams the engine's output to an AudioTrack from a dedicated thread. */
class AudioEngine(context: Context) {
    val info: CreatureInfo
    @Volatile var outRms = 0.0
        private set
    @Volatile var activeCall = -1
        private set
    /** smoothed parameter values as the engine currently has them (def order) */
    @Volatile var params: DoubleArray = DoubleArray(0)
        private set

    /** output gain; the model's own level is quiet (RMS about -28 dBFS), peaks stay below 1 */
    @Volatile var gain = 2.0f

    private val rate = 48000
    private val block = 512
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focus: AudioFocusRequest? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    init { info = CreatureInfo(Chimera.nativeInit(rate.toDouble())) }

    private fun buildTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, block * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }

    /** gain with a soft knee above 0.9, so a boosted peak never clips hard */
    private fun shape(x: Float): Float {
        val y = x * gain
        val a = abs(y)
        if (a <= 0.9f) return y
        val s = 0.9f + 0.1f * tanh((a - 0.9f) / 0.1f)
        return if (y < 0) -s else s
    }

    fun start() {
        if (running) return
        running = true
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }
            .build()
            .also { audioManager.requestAudioFocus(it) }
        val tele = DoubleArray(1024)
        worker = thread(name = "chimera-audio", priority = Thread.MAX_PRIORITY) {
            var track: AudioTrack = runCatching { buildTrack() }.getOrNull() ?: run { running = false; return@thread }
            val buf = FloatArray(block)
            var n = 0
            while (running) {
                Chimera.nativeRender(buf)
                for (i in buf.indices) buf[i] = shape(buf[i])
                val w = track.write(buf, 0, block, AudioTrack.WRITE_BLOCKING)
                if (w < 0) {
                    // the output died (device switched, audio server restarted): build a new one
                    runCatching { track.release() }
                    val fresh = runCatching { buildTrack() }.getOrNull()
                    if (fresh == null) { Thread.sleep(200); continue }
                    track = fresh
                } else if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    runCatching { track.play() }
                }
                if (++n % 4 == 0) {
                    val ok = Chimera.nativeTelemetry(tele)
                    if (ok) {
                        outRms = tele[1]
                        activeCall = tele[3].toInt()
                        params = tele.copyOfRange(4, 4 + info.params.size)
                    }
                }
            }
            runCatching { track.stop() }
            track.release()
        }
    }

    fun stop() {
        setGate(0.0)
        stopCall()
        running = false
        worker?.join()
        worker = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun setParam(i: Int, v: Double) = Chimera.nativeSetParam(i, v)
    fun playCall(i: Int) = Chimera.nativePlayCall(i)
    fun stopCall() = Chimera.nativeStopCall()
    fun setGate(level: Double) = Chimera.nativeSetGate(level)

    /** Hold pressed: open the gate and draw a fresh pocket of gas, so a quick re-press still sounds. */
    fun hold(down: Boolean) {
        if (down) {
            Chimera.nativeSetGate(1.0)
            Chimera.nativeFill()
        } else {
            Chimera.nativeSetGate(0.0)
        }
    }
}
