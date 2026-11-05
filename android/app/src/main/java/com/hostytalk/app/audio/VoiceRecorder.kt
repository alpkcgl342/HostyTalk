package com.hostytalk.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Sesli asistan tarzı tek konuşmalık kayıt: [start] ile mikrofon açılır, konuşma bitince
 * (kısa sessizlik) kendiliğinden kapanır ve cümle WAV olarak [onUtterance]'a verilir.
 * Hiç konuşulmazsa bir süre sonra kendiliğinden kapanır. Turist sayfasıyla (guest.js) aynı eşikler.
 */
class VoiceRecorder(
    private val scope: CoroutineScope,
    private val onUtterance: suspend (ByteArray) -> Unit,
) {
    enum class End { Sent, NoSpeech, Cancelled }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _hearing = MutableStateFlow(false)
    val hearing: StateFlow<Boolean> = _hearing
    /** 0..1 arası anlık ses şiddeti (mikrofon animasyonu için). */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    /** Tur bittiğinde nasıl bittiğini bildirir. */
    var onEnded: (End) -> Unit = {}

    private var job: Job? = null
    @Volatile private var finishNow = false

    @SuppressLint("MissingPermission") // izin, başlatmadan önce ekranda istenir
    fun start() {
        if (job?.isActive == true) return
        finishNow = false
        _running.value = true
        job = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FRAME_SAMPLES * 8),
            )
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true

            val frame = ShortArray(FRAME_SAMPLES)
            val vad = Vad()
            val startedAt = System.currentTimeMillis()
            var end = End.Cancelled
            try {
                rec.startRecording()
                while (isActive) {
                    val n = rec.read(frame, 0, frame.size)
                    if (n <= 0) continue
                    var utterance = vad.feed(frame.copyOf(n))
                    _hearing.value = vad.inSpeech
                    _level.value = (vad.lastRms / 0.15).toFloat().coerceIn(0f, 1f)
                    if (utterance == null && finishNow) utterance = vad.flush() ?: break
                    if (utterance != null) {
                        onUtterance(toWav(utterance))
                        end = End.Sent
                        break
                    }
                    if (!vad.inSpeech && System.currentTimeMillis() - startedAt > NO_SPEECH_TIMEOUT_MS) {
                        end = End.NoSpeech
                        break
                    }
                }
            } finally {
                _hearing.value = false
                _level.value = 0f
                runCatching { rec.stop() }
                rec.release()
                _running.value = false
                onEnded(end)
            }
        }
    }

    /** Açıkken dokunulunca: konuşmayı beklemeden bitir ve gönder. */
    fun finish() {
        finishNow = true
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private class Vad {
        private var noiseFloor = 0.004
        var lastRms = 0.0
            private set
        var inSpeech = false
            private set
        private var speechMs = 0
        private var silenceMs = 0
        private var loudFrames = 0
        private val preRoll = ArrayDeque<ShortArray>()
        private val chunks = mutableListOf<ShortArray>()

        fun reset() {
            inSpeech = false; speechMs = 0; silenceMs = 0; loudFrames = 0
            preRoll.clear(); chunks.clear()
        }

        /** Bir cümle bittiğinde PCM örneklerini döndürür, aksi halde null. */
        fun feed(frame: ShortArray): ShortArray? {
            var sum = 0.0
            for (s in frame) { val v = s / 32768.0; sum += v * v }
            val rms = sqrt(sum / frame.size)
            lastRms = rms
            val loud = rms > max(0.01, noiseFloor * 3)

            if (!inSpeech) {
                noiseFloor = noiseFloor * 0.95 + rms * 0.05
                preRoll.addLast(frame)
                if (preRoll.size > 10) preRoll.removeFirst()
                loudFrames = if (loud) loudFrames + 1 else 0
                if (loudFrames >= 3) {
                    inSpeech = true
                    chunks.addAll(preRoll); preRoll.clear()
                    speechMs = chunks.size * FRAME_MS; silenceMs = 0
                }
                return null
            }
            chunks.add(frame)
            speechMs += FRAME_MS
            silenceMs = if (loud) 0 else silenceMs + FRAME_MS
            if (silenceMs < SILENCE_END_MS && speechMs < MAX_SPEECH_MS) return null

            return flush()
        }

        /** Yakalanan konuşmayı hemen döndürür (yeterince uzun değilse null). */
        fun flush(): ShortArray? {
            val voiced = speechMs - silenceMs
            val ok = inSpeech && voiced >= MIN_SPEECH_MS
            val pcm = ShortArray(chunks.sumOf { it.size })
            var o = 0
            for (c in chunks) { c.copyInto(pcm, o); o += c.size }
            reset()
            return if (ok) pcm else null
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME_MS = 30
        private const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        private const val SILENCE_END_MS = 1000
        private const val MIN_SPEECH_MS = 300
        private const val MAX_SPEECH_MS = 30_000
        private const val NO_SPEECH_TIMEOUT_MS = 8_000

        fun toWav(pcm: ShortArray): ByteArray {
            val dataLen = pcm.size * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(dataLen)
            }
            val body = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) body.putShort(s)
            return ByteArrayOutputStream(44 + dataLen).apply {
                write(header.array()); write(body.array())
            }.toByteArray()
        }
    }
}
