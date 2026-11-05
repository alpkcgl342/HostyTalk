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
import com.hostytalk.app.data.Merchant
import com.hostytalk.app.data.NewSession
import com.hostytalk.app.data.Offer
import com.hostytalk.app.data.ServerEvent
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
    data object Phrases : Screen
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

    private val _session = MutableStateFlow<NewSession?>(null)
    val session: StateFlow<NewSession?> = _session.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun open(session: NewSession) { _session.value = session }
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

    fun submit(register: Boolean, email: String, password: String, businessName: String, businessType: String) {
        if (_state.value.loading) return
        _state.value = AuthState(loading = true)
        viewModelScope.launch {
            runCatching {
                if (register) api.register(email.trim(), password, businessName.trim(), businessType)
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
    val merchant: Merchant? = null,
    val starting: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val api = application.app.api
    private val prefs = application.app.prefs

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    val merchantLang = prefs.merchantLang.stateIn(viewModelScope, SharingStarted.Eagerly, "tr")
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { api.me() }
            .onSuccess { m -> _state.update { it.copy(merchant = m, error = null) } }
            .onFailure { e ->
                if ((e as? ApiException)?.status == 401) prefs.setToken(null)
                else _state.update { it.copy(error = e.code()) }
            }
    }

    fun setMerchantLang(code: String) = viewModelScope.launch { prefs.setMerchantLang(code) }
    fun setServerUrl(url: String) = viewModelScope.launch { prefs.setServerUrl(url); refresh() }

    fun setBusinessType(type: String) = viewModelScope.launch {
        runCatching { api.setBusinessType(type) }
            .onSuccess { m -> _state.update { it.copy(merchant = m, error = null) } }
            .onFailure { e -> _state.update { it.copy(error = e.code()) } }
    }

    fun start(onStarted: (NewSession) -> Unit) {
        if (_state.value.starting) return
        _state.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            runCatching { api.createSession(merchantLang.value) }
                .onSuccess { _state.update { s -> s.copy(starting = false) }; onStarted(it) }
                .onFailure { e -> _state.update { it.copy(starting = false, error = e.code()) } }
        }
    }
}

data class ChatMessage(
    val id: Int,
    val fromGuest: Boolean,
    val original: String,
    val translated: String,
    val sourceLang: String,
    val targetLang: String,
)

data class Price(val amount: Double, val currency: String, val conversions: Map<String, Double>)

data class ConversationState(
    val joinUrl: String = "",
    val joinExpiresAt: Long = 0,
    val merchantLang: String = "tr",
    val guestLang: String = "en",
    val guestConnected: Boolean = false,
    val connection: Connection = Connection.Connecting,
    val messages: List<ChatMessage> = emptyList(),
    val processingFrom: String? = null,
    val price: Price? = null,
    val offers: List<Offer> = emptyList(),
    val phrases: List<String> = emptyList(),
    val summary: Map<String, String>? = null,
    val shareUrl: String? = null,
    val ending: Boolean = false,
    val ended: Boolean = false,
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
        _state.update { it.copy(processingFrom = "merchant") }
    }.apply {
        onEnded = { end -> if (end == VoiceRecorder.End.NoSpeech) _state.update { it.copy(noSpeech = true) } }
    }

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    val micOn = recorder.running
    val hearing = recorder.hearing
    val level = recorder.level
    val playing = player.playing

    private var started = false

    fun start(session: NewSession) {
        if (started) return
        started = true
        _state.update {
            it.copy(joinUrl = session.joinUrl, joinExpiresAt = session.joinExpiresAt, merchantLang = session.merchantLang)
        }
        viewModelScope.launch { client.events.collect(::onEvent) }
        viewModelScope.launch { client.connection.collect { c -> _state.update { it.copy(connection = c) } } }
        viewModelScope.launch { client.connect(api.webSocketUrl(session.id)) }
        viewModelScope.launch {
            // Esnafın kendi cümleleri önce, sonra işletme türüne göre hazır set.
            runCatching { api.phrases(session.merchantLang) }
                .onSuccess { p -> _state.update { it.copy(phrases = p.custom.map { c -> c.text } + p.defaults) } }
        }
    }

    private fun onEvent(e: ServerEvent) {
        when (e.type) {
            "state" -> _state.update {
                it.copy(
                    guestConnected = e.guestConnected ?: it.guestConnected,
                    guestLang = e.guestLang ?: it.guestLang,
                    merchantLang = e.merchantLang ?: it.merchantLang,
                    joinUrl = e.joinUrl ?: it.joinUrl,
                    joinExpiresAt = e.joinExpiresAt ?: it.joinExpiresAt,
                )
            }
            "processing" -> _state.update { it.copy(processingFrom = e.from, error = null) }
            "idle" -> _state.update { it.copy(processingFrom = null) }
            "message" -> {
                val msg = ChatMessage(
                    id = e.id ?: 0,
                    fromGuest = e.from == "guest",
                    original = e.original.orEmpty(),
                    translated = e.translated.orEmpty(),
                    sourceLang = e.sourceLang.orEmpty(),
                    targetLang = e.targetLang.orEmpty(),
                )
                _state.update { it.copy(messages = it.messages + msg, processingFrom = null) }
                e.audioB64?.let { player.enqueue(Base64.decode(it, Base64.DEFAULT)) }
            }
            "price" -> _state.update {
                it.copy(price = Price(e.amount ?: 0.0, e.currency ?: "TRY", e.conversions.orEmpty()))
            }
            "offer" -> {
                val offer = Offer(
                    id = e.id ?: return,
                    by = e.by.orEmpty(),
                    amount = e.amount ?: 0.0,
                    currency = e.currency ?: "TRY",
                    conversions = e.conversions.orEmpty(),
                    status = e.status ?: "open",
                )
                _state.update { s ->
                    val others = s.offers.filterNot { it.id == offer.id }
                    s.copy(offers = (others + offer).sortedBy { it.id })
                }
            }
            "summary" -> _state.update { it.copy(summary = e.texts, shareUrl = e.shareUrl ?: it.shareUrl) }
            "error" -> _state.update { it.copy(error = e.code ?: "ai_failed", processingFrom = null) }
            "ended" -> {
                recorder.cancel()
                _state.update { it.copy(ended = true, ending = false, shareUrl = e.shareUrl ?: it.shareUrl) }
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

    fun sayPhrase(text: String) = viewModelScope.launch { client.send("text", "text" to text) }

    fun showPrice(amount: Double, currency: String) =
        viewModelScope.launch { client.send("price", "amount" to amount, "currency" to currency) }

    /** Pazarlık: teklif ya da karşı teklif ver. */
    fun makeOffer(amount: Double, currency: String) =
        viewModelScope.launch { client.send("offer", "amount" to amount, "currency" to currency) }

    fun replyOffer(id: Int, accept: Boolean) =
        viewModelScope.launch { client.send("offer_reply", "offer_id" to id, "accept" to accept) }

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
