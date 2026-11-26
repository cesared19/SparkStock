package com.sparkstock.scanner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** SparkStock answered with an error; `message` is its explanation, in the phone's language. */
class ApiException(message: String, val status: Int) : Exception(message)

/** The session expired or was never valid: sign in again. */
class SessionExpiredException : Exception()

/** The server couldn't be reached (wrong address, no Wi-Fi, server down). */
class OfflineException(cause: Throwable) : Exception(cause)

/**
 * SparkStock's API, through its web address (http://<server>:3100/api/v1/...), with the SparkyTC session token the
 * sign-in returned. Every call runs off the main thread.
 */
class Api(
    private val serverUrl: String,
    private val token: String?,
    private val language: String,
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(username: String, password: String): Login =
        post("auth/login", buildJsonObject { put("username", username); put("password", password) }, Login.serializer())

    suspend fun me(): User = get("auth/me", User.serializer())

    suspend fun logout() {
        runCatching { post<Unit>("auth/logout", buildJsonObject {}, null) }
    }

    /** Every SparkyTC account, refreshed from SparkyTC (admins only). */
    suspend fun users(): List<User> = get("users", ListSerializer(User.serializer()))

    suspend fun locations(): List<Location> = get("locations", ListSerializer(Location.serializer()))

    suspend fun device(tag: String): DeviceDetail = get("devices/by-tag/" + encode(tag), DeviceDetail.serializer())

    /** Lends several devices to a person at once; SparkStock lends all or none. `dueAt` in seconds. */
    suspend fun lend(deviceIds: List<String>, userId: String, dueAt: Long, note: String?): List<Device> =
        post("devices/check-out", buildJsonObject {
            putJsonArray("deviceIds") { deviceIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("userId", userId)
            put("dueAt", dueAt)
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
        }, ListSerializer(Device.serializer()))

    suspend fun giveBack(deviceId: String, condition: Condition, note: String?): DeviceDetail =
        post("devices/$deviceId/check-in", buildJsonObject {
            put("condition", condition.name)
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
        }, DeviceDetail.serializer())

    suspend fun addPhoto(deviceId: String, eventId: Long, jpeg: ByteArray): PhotoAdded {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "return.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        return send(request("devices/$deviceId/events/$eventId/photos").post(body), PhotoAdded.serializer())
    }

    suspend fun inventoryCheck(locationId: Long, tags: List<String>, apply: Boolean): InventoryCheck =
        post("inventory-checks", buildJsonObject {
            put("locationId", locationId)
            putJsonArray("assetTags") { tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            put("apply", apply)
        }, InventoryCheck.serializer())

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): T = send(request(path).get(), serializer)

    private suspend fun <T> post(path: String, body: JsonObject, serializer: KSerializer<T>?): T =
        send(request(path).post(body.toString().toRequestBody(jsonType)), serializer)

    private fun request(path: String): Request.Builder {
        val builder = Request.Builder().url(url(path))
            .header("Accept", "application/json")
            .header("Accept-Language", language)
        token?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private fun url(path: String): HttpUrl {
        val base = normalizeServer(serverUrl) ?: throw ApiException("Invalid server address", 0)
        return (base.trimEnd('/') + "/api/v1/" + path).toHttpUrlOrNull() ?: throw ApiException("Invalid server address", 0)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> send(builder: Request.Builder, serializer: KSerializer<T>?): T = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw OfflineException(e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (it.code == 401 && token != null) throw SessionExpiredException()
            if (!it.isSuccessful || root?.get("status")?.jsonPrimitive?.content != "SUCCESS") {
                val message = root?.get("message")?.jsonPrimitive?.content ?: "HTTP ${it.code}"
                throw ApiException(message, it.code)
            }
            if (serializer == null) return@withContext Unit as T
            json.decodeFromJsonElement(serializer, root["data"] as JsonElement)
        }
    }

    private fun encode(segment: String) = java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /** "10.0.0.5:3100" → "http://10.0.0.5:3100"; null when it isn't an address. */
        fun normalizeServer(input: String): String? {
            val text = input.trim().trimEnd('/')
            if (text.isEmpty()) return null
            val withScheme = if (text.contains("://")) text else "http://$text"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            if (url.scheme != "http" && url.scheme != "https") return null
            return url.toString().trimEnd('/')
        }
    }
}
