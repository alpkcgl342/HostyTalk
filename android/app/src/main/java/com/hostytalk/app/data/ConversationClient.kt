package com.hostytalk.app.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Sunucudan gelen tüm olaylar tek tipte; alanlar olay türüne göre dolar. */
@Serializable
data class ServerEvent(
    val type: String,
    val id: Int? = null,
    val from: String? = null,
    val original: String? = null,
    val translated: String? = null,
    @SerialName("source_lang") val sourceLang: String? = null,
    @SerialName("target_lang") val targetLang: String? = null,
    @SerialName("audio_b64") val audioB64: String? = null,
    @SerialName("merchant_lang") val merchantLang: String? = null,
    @SerialName("guest_lang") val guestLang: String? = null,
    @SerialName("guest_connected") val guestConnected: Boolean? = null,
    @SerialName("join_url") val joinUrl: String? = null,
    @SerialName("join_expires_at") val joinExpiresAt: Long? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val conversions: Map<String, Double>? = null,
    val texts: Map<String, String>? = null,
    val code: String? = null,
    val by: String? = null,
    val status: String? = null,
    @SerialName("share_url") val shareUrl: String? = null,
)

enum class Connection { Connecting, Open, Reconnecting, Closed }

/** Tek bir konuşmanın WebSocket bağlantısı; koparsa kendiliğinden yeniden bağlanır. */
class ConversationClient(private val http: HttpClient, private val scope: CoroutineScope) {
    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events

    private val _connection = MutableStateFlow(Connection.Connecting)
    val connection: StateFlow<Connection> = _connection

    private var socket: DefaultClientWebSocketSession? = null
    private var job: Job? = null
    @Volatile private var closing = false

    fun connect(url: String) {
        job = scope.launch {
            while (isActive && !closing) {
                runCatching {
                    http.webSocket(url) {
                        socket = this
                        _connection.value = Connection.Open
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                runCatching { AppJson.decodeFromString<ServerEvent>(frame.readText()) }
                                    .onSuccess { _events.emit(it) }
                            }
                        }
                        // 44xx: oturum yok / yetki yok — yeniden denemenin anlamı yok.
                        val reason = closeReason.await()
                        if (reason != null && reason.code in 4400..4499) closing = true
                    }
                }
                socket = null
                if (closing) break
                _connection.value = Connection.Reconnecting
                delay(2_000)
            }
            _connection.value = Connection.Closed
        }
    }

    suspend fun sendAudio(wav: ByteArray) {
        socket?.send(Frame.Binary(true, wav))
    }

    suspend fun send(type: String, vararg fields: Pair<String, Any>) {
        val obj = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("type", JsonPrimitive(type))
            for ((k, v) in fields) {
                put(k, when (v) {
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                })
            }
        }
        socket?.send(Frame.Text(JsonObject(obj).toString()))
    }

    fun close() {
        closing = true
        job?.cancel()
    }
}
