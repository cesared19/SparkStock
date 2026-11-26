package com.sparkstock.scanner.data

import java.net.URI
import java.net.URLDecoder

/**
 * Reads the asset tag out of what a QR label holds: SparkStock's labels hold the device's address,
 * e.g. http://10.0.0.5:3100/t/VR-0001, and a plain tag (VR-0001) works too. Anything else is ignored.
 */
object TagParser {
    private val TAG = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$")

    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            val path = runCatching { URI(text).rawPath }.getOrNull() ?: return null
            val segments = path.split('/').filter { it.isNotEmpty() }
            val index = segments.indexOf("t")
            if (index < 0 || index != segments.size - 2) return null
            val tag = runCatching { URLDecoder.decode(segments.last(), "UTF-8") }.getOrNull() ?: return null
            return tag.takeIf { TAG.matches(it) }?.uppercase()
        }
        return text.takeIf { TAG.matches(it) }?.uppercase()
    }
}
