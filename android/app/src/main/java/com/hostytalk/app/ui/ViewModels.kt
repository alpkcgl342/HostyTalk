package com.hostytalk.app.ui

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostytalk.app.HostyTalkApp
import com.hostytalk.app.audio.AudioPlayer
import com.hostytalk.app.audio.VoiceRecorder
import com.hostytalk.app.data.ApiException
import com.hostytalk.app.data.Connection
import com.hostytalk.app.data.ConversationClient
import com.hostytalk.app.data.LiveSession
import com.hostytalk.app.data.ServerEvent
import com.hostytalk.app.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Application.app get() = this as HostyTalkApp

/** Ağ/sunucu hatasını ekranda gösterilecek koda indirger. */
internal fun Throwable.code(): String = (this as? ApiException)?.code ?: "network"

/** Giriş yapılmışken gösterilen ekranlar (açık konuşma bunların üstünde gösterilir). */
sealed interface Screen {
    data object Home : Screen
    data object History : Screen
    data class HistoryDetail(val id: Int) : Screen
}

/** Uygulama düzeyi durum: giriş yapıldı mı, hangi ekrandayız, açık bir konuşma var mı. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.app.prefs

    /** null: henüz okunmadı, "": giriş yok. */
    val token: StateFlow<String?> = prefs.token
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _session = MutableStateFlow<LiveSession?>(null)
    val session: StateFlow<LiveSession?> = _session.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun open(session: LiveSession) { _session.value = session }
    fun closeSession() { _session.value = null }
    fun go(screen: Screen) { _screen.value = screen }

    /** Geri tuşu: detaydan listeye, listeden ana sayfaya. */
    fun back() {
        _screen.value = when (_screen.value) {
            is Screen.HistoryDetail -> Screen.History
            else -> Screen.Home
        }
    }

    fun logout() = viewModelScope.launch {
        _session.value = null
        _screen.value = Screen.Home
        prefs.setToken(null)
    }
}

data class AuthState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val api = application.app.api
    private val prefs = application.app.prefs

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url) }

    fun submit(register: Boolean, email: String, password: String, displayName: String) {
        if (_state.value.loading) return
        _state.value = AuthState(loading = true)
        viewModelScope.launch {
            runCatching {
                if (register) api.register(email.trim(), password, displayName.trim())
                else api.login(email.trim(), password)
            }.onSuccess {
                prefs.setToken(it.token)
                _state.value = AuthState()
            }.onFailure {
                _state.value = AuthState(error = it.code())
            }
        }
    }
}

data class HomeState(
    val user: User? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val api = application.app.api
    private val prefs = application.app.prefs

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    val myLang = prefs.myLang.stateIn(viewModelScope, SharingStarted.Eagerly, "tr")
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { api.me() }
            .onSuccess { u -> _state.update { it.copy(user = u, error = null) } }
            .onFailure { e ->
                if ((e as? ApiException)?.status == 401) prefs.setToken(null)
                else _state.update { it.copy(error = e.code()) }
            }
    }

    fun setMyLang(code: String) = viewModelScope.launch { prefs.setMyLang(code) }
    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url); refresh() }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Yeni konuşma başlatır: ekranda kod ve QR çıkar. */
    fun start(onReady: (LiveSession) -> Unit) = openSession(onReady) { api.startSession(myLang.value) }

    /** Karşı tarafın verdiği kodla konuşmaya katılır. */
    fun join(code: String, onReady: (LiveSession) -> Unit) = openSession(onReady) { api.joinSession(code, myLang.value) }

    private fun openSession(onReady: (LiveSession) -> Unit, request: suspend () -> LiveSession) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { request() }
                .onSuccess { _state.update { s -> s.copy(busy = false) }; onReady(it) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.code()) } }
        }
    }
}

data class ChatMessage(
    val id: Int,
    val mine: Boolean,
    val original: String,
    val translated: String,
    val sourceLang: String,
    val targetLang: String,
)

data class ConversationState(
    val isHost: Boolean = true,
    val code: String = "",
    val joinUrl: String = "",
    val joinExpiresAt: Long = 0,
    val myLang: String = "tr",
    val otherName: String = "",
    val otherLang: String = "en",
    val otherConnected: Boolean = false,
    val connection: Connection = Connection.Connecting,
    val messages: List<ChatMessage> = emptyList(),
    /** Çeviri sürerken konuşan taraf: "me" | "other". */
    val processing: String? = null,
    val summary: Map<String, String>? = null,
    val ending: Boolean = false,
    val ended: Boolean = false,
    val endedByOther: Boolean = false,
    val error: String? = null,
    val noSpeech: Boolean = false,
)

class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val api = application.app.api
    private val client = ConversationClient(api.http, viewModelScope)
    private val player = AudioPlayer(application, viewModelScope)
    private val recorder = VoiceRecorder(viewModelScope) { wav ->
        client.sendAudio(wav)
        // Sunucudan "processing" gelmeden de çeviri bekleniyor görünsün.
        _state.update { it.copy(processing = "me") }
    }.apply {
        onEnded = { end -> if (end == VoiceRecorder.End.NoSpeech) _state.update { it.copy(noSpeech = true) } }
    }

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    val micOn = recorder.running
    val hearing = recorder.hearing
    val level = recorder.level
    val playing = player.playing

    private var role = "host"
    private var started = false
    private val isHost get() = role == "host"

    fun start(session: LiveSession) {
        if (started) return
        started = true
        role = session.role
        _state.update {
            it.copy(
                isHost = session.isHost,
                code = session.code,
                joinUrl = session.joinUrl,
                joinExpiresAt = session.joinExpiresAt,
                myLang = if (session.isHost) session.hostLang else session.guestLang,
                otherName = if (session.isHost) session.guestName else session.hostName,
                otherLang = if (session.isHost) session.guestLang else session.hostLang,
            )
        }
        viewModelScope.launch { client.events.collect(::onEvent) }
        viewModelScope.launch { client.connection.collect { c -> _state.update { it.copy(connection = c) } } }
        viewModelScope.launch { client.connect(api.webSocketUrl(session)) }
    }

    private fun onEvent(e: ServerEvent) {
        when (e.type) {
            "state" -> _state.update {
                it.copy(
                    myLang = (if (isHost) e.hostLang else e.guestLang) ?: it.myLang,
                    otherLang = (if (isHost) e.guestLang else e.hostLang) ?: it.otherLang,
                    otherName = (if (isHost) e.guestName else e.hostName)?.takeIf { n -> n.isNotEmpty() } ?: it.otherName,
                    otherConnected = (if (isHost) e.guestConnected else e.hostConnected) ?: it.otherConnected,
                    code = e.code ?: it.code,
                    joinUrl = e.joinUrl ?: it.joinUrl,
                    joinExpiresAt = e.joinExpiresAt ?: it.joinExpiresAt,
                )
            }
            "processing" -> _state.update {
                it.copy(processing = if (e.from == role) "me" else "other", error = null)
            }
            "idle" -> _state.update { it.copy(processing = null) }
            "message" -> {
                val msg = ChatMessage(
                    id = e.id ?: 0,
                    mine = e.from == role,
                    original = e.original.orEmpty(),
                    translated = e.translated.orEmpty(),
                    sourceLang = e.sourceLang.orEmpty(),
                    targetLang = e.targetLang.orEmpty(),
                )
                _state.update { it.copy(messages = it.messages + msg, processing = null) }
                e.audioB64?.let { player.enqueue(Base64.decode(it, Base64.DEFAULT)) }
            }
            "summary" -> _state.update { it.copy(summary = e.texts) }
            "error" -> _state.update { it.copy(error = e.code ?: "ai_failed", processing = null) }
            "ended" -> {
                recorder.cancel()
                _state.update { it.copy(ended = true, ending = false, endedByOther = e.by != null && e.by != role) }
                client.close()
            }
        }
    }

    /** Sesli asistan gibi: kapalıyken dokununca bir konuşmalık açılır, açıkken dokununca hemen gönderir. */
    fun tapMic() {
        if (recorder.running.value) {
            recorder.finish()
        } else {
            _state.update { it.copy(noSpeech = false, error = null) }
            recorder.start()
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { client.send("text", "text" to text.trim()) }
    }

    fun end(withSummary: Boolean) {
        recorder.cancel()
        if (_state.value.connection != Connection.Open) {
            // Sunucuya ulaşılamıyorsa en azından bu taraf kapansın.
            client.close()
            _state.update { it.copy(ended = true) }
            return
        }
        _state.update { it.copy(ending = true) }
        viewModelScope.launch { client.send("end", "summary" to withSummary) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        recorder.cancel()
        player.close()
        client.close()
    }
}
