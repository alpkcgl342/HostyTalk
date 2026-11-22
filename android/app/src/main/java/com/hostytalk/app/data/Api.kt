package com.hostytalk.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

@Serializable
data class User(
    val id: Int,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val plan: String,
    @SerialName("used_minutes") val usedMinutes: Double,
    @SerialName("quota_minutes") val quotaMinutes: Int,
)

@Serializable
data class AuthResponse(val token: String, val user: User)

/** Başlatılan ya da katılınan canlı konuşma. role: "host" (başlatan) | "guest" (katılan). */
@Serializable
data class LiveSession(
    val id: String,
    val role: String,
    val code: String,
    @SerialName("join_url") val joinUrl: String,
    @SerialName("join_expires_at") val joinExpiresAt: Long,
    @SerialName("host_name") val hostName: String,
    @SerialName("host_lang") val hostLang: String,
    @SerialName("guest_name") val guestName: String = "",
    @SerialName("guest_lang") val guestLang: String = "en",
) {
    val isHost get() = role == "host"
}

@Serializable
data class HistoryMessage(
    val id: Int,
    val mine: Boolean,
    @SerialName("source_lang") val sourceLang: String,
    @SerialName("target_lang") val targetLang: String,
    val original: String,
    val translated: String,
)

/** Geçmişteki bir konuşma, isteyen kişinin gözünden (kendi dili, karşı tarafın adı ve dili). */
@Serializable
data class Conversation(
    val id: Int,
    @SerialName("my_lang") val myLang: String,
    @SerialName("other_name") val otherName: String,
    @SerialName("other_lang") val otherLang: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long? = null,
    val summary: Map<String, String>? = null,
    val messages: List<HistoryMessage>? = null,
    @SerialName("message_count") val messageCount: Int? = null,
)

@Serializable
private data class RegisterBody(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
private data class LoginBody(val email: String, val password: String)

@Serializable
private data class PasswordBody(@SerialName("new_password") val newPassword: String)

@Serializable
private data class TokenResponse(val token: String)

@Serializable
private data class StartBody(val lang: String)

@Serializable
private data class JoinBody(val code: String, val lang: String)

/** Sunucunun döndürdüğü hata kodu ("email_taken", "session_full"...). */
class ApiException(val code: String, val status: Int) : Exception(code)

class Api(private val prefs: Prefs) {
    val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(AppJson) }
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
        }
        expectSuccess = false
    }

    private suspend fun base() = prefs.currentServerUrl()

    private suspend fun token() = prefs.currentToken() ?: throw ApiException("invalid_token", 401)

    private suspend inline fun <reified T> HttpResponse.parse(): T {
        if (status.isSuccess()) return body()
        val code = runCatching {
            AppJson.parseToJsonElement(body<String>()).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
        throw ApiException(code ?: "http_${status.value}", status.value)
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResponse =
        http.post("${base()}/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterBody(email, password, displayName))
        }.parse()

    suspend fun login(email: String, password: String): AuthResponse =
        http.post("${base()}/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginBody(email, password))
        }.parse()

    suspend fun me(): User = http.get("${base()}/api/me") { bearerAuth(token()) }.parse()

    /** Şifreyi değiştirir; sunucu diğer oturumları kapatır ve bu cihaz için yeni token döner. */
    suspend fun changePassword(newPassword: String): String =
        http.post("${base()}/api/me/password") {
            bearerAuth(token())
            contentType(ContentType.Application.Json)
            setBody(PasswordBody(newPassword))
        }.parse<TokenResponse>().token

    // --- canlı konuşma ---

    suspend fun startSession(lang: String): LiveSession =
        http.post("${base()}/api/sessions") {
            bearerAuth(token())
            contentType(ContentType.Application.Json)
            setBody(StartBody(lang))
        }.parse()

    suspend fun joinSession(code: String, lang: String): LiveSession =
        http.post("${base()}/api/sessions/join") {
            bearerAuth(token())
            contentType(ContentType.Application.Json)
            setBody(JoinBody(code.trim().uppercase(), lang))
        }.parse()

    suspend fun webSocketUrl(session: LiveSession): String {
        val wsBase = base().replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return "$wsBase/ws/${session.id}?role=${session.role}&token=${token()}"
    }

    // --- konuşma geçmişi ---

    suspend fun conversations(): List<Conversation> =
        http.get("${base()}/api/conversations") { bearerAuth(token()) }.parse()

    suspend fun conversation(id: Int): Conversation =
        http.get("${base()}/api/conversations/$id") { bearerAuth(token()) }.parse()

    suspend fun summarize(id: Int): Conversation =
        http.post("${base()}/api/conversations/$id/summary") { bearerAuth(token()) }.parse()

    suspend fun deleteConversation(id: Int) {
        http.delete("${base()}/api/conversations/$id") { bearerAuth(token()) }.parse<Map<String, Boolean>>()
    }
}
