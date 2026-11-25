package com.sparkstock.service.sparkytc

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.sparkstock.service.config.SparkStockProperties
import com.sparkstock.service.models.UserRole
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import com.sparkstock.service.services.Lang
import com.sparkstock.service.services.Messages
import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.Date
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class SparkyTCUser(
    val userID: UUID,
    val username: String,
    val displayName: String,
    val role: UserRole,
    @JsonProperty("isActive")
    val isActive: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
// SparkyTC sends dates as milliseconds since the epoch (java.util.Date)
data class SparkyTCLogin(val token: String, val expiresAt: Date, val user: SparkyTCUser)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SparkyTCEnvelope<T>(val data: T? = null, val message: String? = null)

/** A notification for a SparkyTC user's bell; the same sourceRef is only shown once. */
data class SparkyTCNotification(
    val userID: UUID,
    val title: String,
    val message: String,
    val link: String?,
    val sourceRef: String,
    val app: String = "SparkStock",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SparkyTCProject(val projectShortCode: String, val title: String, val deviceMode: String? = null, val builds: List<String> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class SparkyTCDeviceResult(
    val ref: String,
    val name: String? = null,
    val model: String? = null,
    val version: String? = null,
    val passed: Int = 0,
    val failed: Int = 0,
    val blocked: Int = 0,
    val skipped: Int = 0,
    val testCases: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SparkyTCBuildDeviceResults(val projectShortCode: String, val build: String, val devices: List<SparkyTCDeviceResult> = emptyList())

/** SparkyTC rejected the credentials or the session. */
class SparkyTCUnauthorizedException(message: String) : RuntimeException(message)

/** SparkyTC couldn't be reached or answered with an unexpected error. */
class SparkyTCUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Talks to SparkyTC's own API with the signed-in user's SparkyTC session, so SparkyTC decides who
 * can sign in and what role they have. SparkStock never sees or stores passwords beyond forwarding them once.
 */
@Component
class SparkyTCClient(builder: RestClient.Builder, properties: SparkStockProperties) {
    private val errorReader = com.fasterxml.jackson.databind.ObjectMapper()
    // the JDK HttpClient keeps the body of a 401 (HttpURLConnection drops it for POSTs), which carries SparkyTC's message
    private val client = builder
        .baseUrl(properties.sparkytc.baseUrl.trimEnd('/'))
        .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()).apply {
            setReadTimeout(Duration.ofSeconds(15))
        })
        .build()

    fun login(username: String, password: String): SparkyTCLogin = call {
        client.post().uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("username" to username, "password" to password))
            .retrieve()
            .body(LoginEnvelope::class.java)?.data
    }

    fun me(token: String): SparkyTCUser = call {
        client.get().uri("/api/v1/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .body(UserEnvelope::class.java)?.data
    }

    fun logout(token: String) {
        try {
            client.post().uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .retrieve()
                .toBodilessEntity()
        } catch (_: Exception) {
            // the session ends on SparkStock's side anyway
        }
    }

    /** Every SparkyTC account; needs an admin's session. */
    fun users(token: String): List<SparkyTCUser> = call {
        client.get().uri("/api/v1/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .body(UserListEnvelope::class.java)?.data
    }

    /**
     * Puts a notification in a SparkyTC user's bell (POST /api/system/v1/notifications, with the suite's shared
     * integration token). Returns whether a new one was created: false when the same sourceRef was already sent, or
     * the user turned these off.
     */
    fun notify(integrationToken: String, notification: SparkyTCNotification): Boolean {
        val body = call {
            client.post().uri("/api/system/v1/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $integrationToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification)
                .retrieve()
                .body(String::class.java)
        }
        return errorReader.readTree(body).path("data").path("created").asBoolean(false)
    }

    /** Every project with its builds, newest first (system API, with the suite's integration token). */
    fun projects(integrationToken: String): List<SparkyTCProject> = call {
        client.get().uri("/api/system/v1/projects")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $integrationToken")
            .retrieve()
            .body(ProjectListEnvelope::class.java)?.data
    }

    /** How the test cases went on each device in a build (the current one when null); null when there's no such build. */
    fun deviceResults(integrationToken: String, projectShortCode: String, build: String?): SparkyTCBuildDeviceResults? {
        return try {
            call {
                client.get().uri { builder ->
                    builder.path("/api/system/v1/projects/{code}/device-results")
                    build?.let { builder.queryParam("build", it) }
                    builder.build(projectShortCode)
                }
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $integrationToken")
                    .retrieve()
                    .body(DeviceResultsEnvelope::class.java)?.data
            }
        } catch (ex: SparkyTCUnavailableException) {
            if ((ex.cause as? RestClientResponseException)?.statusCode?.value() == 404) null else throw ex
        }
    }

    private fun <T> call(request: () -> T?): T {
        try {
            return request() ?: throw SparkyTCUnavailableException("SparkyTC sent an empty answer")
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode == HttpStatusCode.valueOf(401) || ex.statusCode == HttpStatusCode.valueOf(403)) {
                val message = runCatching {
                    errorReader.readTree(ex.responseBodyAsString).path("message").asText(null)
                }.getOrNull()
                // SparkyTC answers in English; its usual "wrong password" is translated, anything else is said generically
                val shown = when {
                    message == null -> Messages.t("auth.rejected")
                    Messages.lang() == Lang.EN -> message
                    message.startsWith("Invalid username or password") -> Messages.t("auth.invalid")
                    else -> Messages.t("auth.rejected")
                }
                throw SparkyTCUnauthorizedException(shown)
            }
            throw SparkyTCUnavailableException("SparkyTC answered ${ex.statusCode.value()}", ex)
        } catch (ex: ResourceAccessException) {
            throw SparkyTCUnavailableException("SparkyTC can't be reached", ex)
        }
    }

    class LoginEnvelope(val data: SparkyTCLogin? = null)
    class UserEnvelope(val data: SparkyTCUser? = null)
    class UserListEnvelope(val data: List<SparkyTCUser>? = null)
    class ProjectListEnvelope(val data: List<SparkyTCProject>? = null)
    class DeviceResultsEnvelope(val data: SparkyTCBuildDeviceResults? = null)
}
