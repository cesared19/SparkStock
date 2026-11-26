package com.sparkstock.scanner.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * What the app remembers on the phone: SparkStock's address, the SparkyTC session (7 days, like the website) and
 * the people lent to last, to show them first. Private to the app.
 */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("sparkstock", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var serverUrl: String?
        get() = prefs.getString("server", null)
        set(value) = prefs.edit().putString("server", value).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var user: User?
        get() = prefs.getString("user", null)?.let { runCatching { json.decodeFromString(User.serializer(), it) }.getOrNull() }
        set(value) = prefs.edit().putString("user", value?.let { json.encodeToString(it) }).apply()

    /** The people lent to most recently, newest first. */
    var recentPeople: List<String>
        get() = prefs.getString("recent", "")!!.split(',').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("recent", value.take(8).joinToString(",")).apply()

    fun remember(personId: String) {
        recentPeople = listOf(personId) + recentPeople.filter { it != personId }
    }

    fun signOut() {
        prefs.edit().remove("token").remove("user").apply()
    }

    /** The API for the signed-in person, in the phone's language (the server answers in English or Spanish). */
    fun api(): Api = Api(serverUrl.orEmpty(), token, Locale.getDefault().toLanguageTag())
}
