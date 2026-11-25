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
            require(KEY.matches(key)) { Messages.t("field.keyFormat", key) }
            require(seen.add(key)) { Messages.t("field.keyTwice", key) }
            require(field.label.isNotBlank()) { Messages.t("field.needsLabel", key) }
            val options = field.options?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
            if (field.kind == FieldKind.SELECT) require(!options.isNullOrEmpty()) { Messages.t("field.needsOption", field.label) }
            field.copy(key = key, label = field.label.trim(), options = if (field.kind == FieldKind.SELECT) options else null)
        }
    }

    /** Keeps only the type's fields, converted to their kind; empty values are dropped. */
    fun normalize(definitions: List<FieldDefinition>, values: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (field in definitions) {
            val value = convert(field, values[field.key])
            if (value == null) {
                require(!field.required) { Messages.t("field.required", field.label) }
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
            FieldKind.TEXT -> text.also { require(it.length <= 2000) { Messages.t("field.tooLong", field.label) } }
            FieldKind.NUMBER -> (raw as? Number)?.toDouble() ?: text.replace(',', '.').toDoubleOrNull()
                ?: throw IllegalArgumentException(Messages.t("field.number", field.label))
            FieldKind.BOOLEAN -> when (raw) {
                is Boolean -> raw
                else -> when (text.lowercase()) {
                    "true", "yes", "y", "1", "sí", "si", "x" -> true
                    "false", "no", "n", "0" -> false
                    else -> throw IllegalArgumentException(Messages.t("field.boolean", field.label))
                }
            }
            FieldKind.DATE -> try {
                LocalDate.parse(text).toString()
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException(Messages.t("field.date", field.label))
            }
            FieldKind.SELECT -> field.options.orEmpty().firstOrNull { it.equals(text, ignoreCase = true) }
                ?: throw IllegalArgumentException(Messages.t("field.option", field.label, field.options.orEmpty().joinToString()))
        }
    }
}
