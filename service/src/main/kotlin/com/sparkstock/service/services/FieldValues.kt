package com.sparkstock.service.services

import com.sparkstock.service.models.FieldDefinition
import com.sparkstock.service.models.FieldKind
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Checks and normalizes the extra field values of a device against its type's definitions. */
object FieldValues {
    private val KEY = Regex("^[a-z][a-z0-9_]{0,39}$")

    fun validateDefinitions(fields: List<FieldDefinition>): List<FieldDefinition> {
        val seen = mutableSetOf<String>()
        return fields.map { field ->
            val key = field.key.trim()
            require(KEY.matches(key)) { "Field key \"$key\" must be lowercase letters, digits or _ and start with a letter" }
            require(seen.add(key)) { "Field key \"$key\" is used twice" }
            require(field.label.isNotBlank()) { "Field \"$key\" needs a label" }
            val options = field.options?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
            if (field.kind == FieldKind.SELECT) require(!options.isNullOrEmpty()) { "Field \"${field.label}\" needs at least one option" }
            field.copy(key = key, label = field.label.trim(), options = if (field.kind == FieldKind.SELECT) options else null)
        }
    }

    /** Keeps only the type's fields, converted to their kind; empty values are dropped. */
    fun normalize(definitions: List<FieldDefinition>, values: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (field in definitions) {
            val value = convert(field, values[field.key])
            if (value == null) {
                require(!field.required) { "${field.label} is required" }
                continue
            }
            result[field.key] = value
        }
        return result
    }

    private fun convert(field: FieldDefinition, raw: Any?): Any? {
        if (raw == null) return null
        val text = raw.toString().trim()
        if (text.isEmpty()) return null
        return when (field.kind) {
            FieldKind.TEXT -> text.also { require(it.length <= 2000) { "${field.label} is too long" } }
            FieldKind.NUMBER -> (raw as? Number)?.toDouble() ?: text.replace(',', '.').toDoubleOrNull()
                ?: throw IllegalArgumentException("${field.label} must be a number")
            FieldKind.BOOLEAN -> when (raw) {
                is Boolean -> raw
                else -> when (text.lowercase()) {
                    "true", "yes", "y", "1", "sí", "si", "x" -> true
                    "false", "no", "n", "0" -> false
                    else -> throw IllegalArgumentException("${field.label} must be yes or no")
                }
            }
            FieldKind.DATE -> try {
                LocalDate.parse(text).toString()
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException("${field.label} must be a date (YYYY-MM-DD)")
            }
            FieldKind.SELECT -> field.options.orEmpty().firstOrNull { it.equals(text, ignoreCase = true) }
                ?: throw IllegalArgumentException("${field.label} must be one of: ${field.options.orEmpty().joinToString()}")
        }
    }
}
