package com.hostytalk.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Gelen çeviri seslerini (mp3) sırayla çalar. */
class AudioPlayer(private val context: Context, scope: CoroutineScope) {
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    init {
        scope.launch(Dispatchers.Main) {
            for (bytes in queue) {
                _playing.value = true
                runCatching { playOne(bytes) }
                if (pending.decrementAndGet() == 0) {
                    delay(300) // hoparlör yankısı bitene kadar mikrofon beklesin
                    if (pending.get() == 0) _playing.value = false
                }
            }
        }
    }

    fun enqueue(mp3: ByteArray) {
        if (mp3.isEmpty()) return
        pending.incrementAndGet()
        queue.trySend(mp3)
    }

    private suspend fun playOne(bytes: ByteArray) = suspendCancellableCoroutine { cont ->
        val file = File.createTempFile("tts", ".mp3", context.cacheDir).apply { writeBytes(bytes) }
        val mp = MediaPlayer()
        fun done() {
            mp.release(); file.delete()
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnCompletionListener { done() }
        mp.setOnErrorListener { _, _, _ -> done(); true }
        cont.invokeOnCancellation { mp.release(); file.delete() }
        runCatching {
            mp.setDataSource(file.path)
            mp.prepare()
            mp.start()
        }.onFailure { done() }
    }

    fun close() {
        queue.close()
    }
}
