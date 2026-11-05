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
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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
data class Merchant(
    val id: Int,
    val email: String,
    @SerialName("business_name") val businessName: String,
    @SerialName("business_type") val businessType: String = "other",
    val plan: String,
    @SerialName("used_minutes") val usedMinutes: Double,
    @SerialName("quota_minutes") val quotaMinutes: Int,
)

@Serializable
data class AuthResponse(val token: String, val merchant: Merchant)

@Serializable
data class NewSession(
    val id: String,
    @SerialName("join_url") val joinUrl: String,
    @SerialName("join_expires_at") val joinExpiresAt: Long,
    @SerialName("merchant_lang") val merchantLang: String,
)

@Serializable
data class CustomPhrase(val id: Int, val text: String)

@Serializable
data class PhraseList(
    @SerialName("business_type") val businessType: String,
    val custom: List<CustomPhrase>,
    val defaults: List<String>,
)

/** Pazarlık teklifi; status: open | accepted | rejected | countered. */
@Serializable
data class Offer(
    val id: Int,
    val by: String,
    val amount: Double,
    val currency: String,
    val conversions: Map<String, Double> = emptyMap(),
    val status: String,
)

@Serializable
data class HistoryMessage(
    val id: Int,
    val from: String,
    @SerialName("source_lang") val sourceLang: String,
    @SerialName("target_lang") val targetLang: String,
    val original: String,
    val translated: String,
)

@Serializable
data class Conversation(
    val id: Int,
    @SerialName("merchant_lang") val merchantLang: String,
    @SerialName("guest_lang") val guestLang: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long? = null,
    val summary: Map<String, String>? = null,
    val offers: List<Offer> = emptyList(),
    val messages: List<HistoryMessage>? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("share_url") val shareUrl: String,
) {
    val agreed: Offer? get() = offers.lastOrNull { it.status == "accepted" }
}

@Serializable
private data class RegisterBody(
    val email: String,
    val password: String,
    @SerialName("business_name") val businessName: String,
    @SerialName("business_type") val businessType: String,
)

@Serializable
private data class LoginBody(val email: String, val password: String)

@Serializable
private data class MeBody(@SerialName("business_type") val businessType: String)

@Serializable
private data class SessionBody(@SerialName("merchant_lang") val merchantLang: String)

@Serializable
private data class PhraseBody(val text: String, val lang: String)

/** Sunucunun döndürdüğü hata kodu ("email_taken", "invalid_credentials"...). */
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

    suspend fun register(email: String, password: String, businessName: String, businessType: String): AuthResponse =
        http.post("${base()}/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterBody(email, password, businessName, businessType))
        }.parse()

    suspend fun login(email: String, password: String): AuthResponse =
        http.post("${base()}/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginBody(email, password))
        }.parse()

    suspend fun me(): Merchant = http.get("${base()}/api/me") { bearerAuth(token()) }.parse()

    suspend fun setBusinessType(type: String): Merchant =
        http.patch("${base()}/api/me") {
            bearerAuth(token())
            contentType(ContentType.Application.Json)
            setBody(MeBody(type))
        }.parse()

    suspend fun createSession(merchantLang: String): NewSession =
        http.post("${base()}/api/sessions") {
            bearerAuth(token())
            contentType(ContentType.Application.Json)
            setBody(SessionBody(merchantLang))
        }.parse()

    suspend fun webSocketUrl(sessionId: String): String {
        val wsBase = base().replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return "$wsBase/ws/$sessionId?role=merchant&token=${token()}"
    }

    // --- hazır cümleler ---

    suspend fun phrases(lang: String): PhraseList =
        http.get("${base()}/api/phrases") { bearerAuth(token()); parameter("lang", lang) }.parse()

    suspend fun addPhrase(text: String, lang: String): CustomPhrase =
        http.post("${base()}/api/phrases") {
            bearerAuth(token())
            contentType(ContentType.Application.Json)
            setBody(PhraseBody(text, lang))
        }.parse()

    suspend fun deletePhrase(id: Int) {
        http.delete("${base()}/api/phrases/$id") { bearerAuth(token()) }.parse<Map<String, Boolean>>()
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
