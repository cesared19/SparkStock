package com.sparkstock.service.services

import com.sparkstock.service.dto.SaveDeviceDTO
import com.sparkstock.service.models.*
import com.sparkstock.service.repositories.DeviceRepository
import com.sparkstock.service.repositories.DeviceTypeRepository
import com.sparkstock.service.repositories.LocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/** One row of a spreadsheet (e.g. the Confluence database exported as CSV), already mapped to SparkStock's fields. */
data class ImportRowDTO(
    val line: Int = 0,
    val externalId: String? = null,
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val typeName: String? = null,
    val version: String? = null,
    val accessories: String? = null,
    val projectCode: String? = null,
    val location: String? = null,
    val notes: String? = null,
)

data class ImportRequestDTO(val rows: List<ImportRowDTO> = emptyList(), val dryRun: Boolean = true)

enum class ImportAction { CREATE, UPDATE, UNCHANGED, ERROR }

data class ImportRowResultDTO(
    val line: Int,
    val action: ImportAction,
    val assetTag: String? = null,
    val name: String? = null,
    val typeName: String? = null,
    val message: String? = null,
)

data class ImportResultDTO(
    val dryRun: Boolean,
    // nothing is saved when any row has an error
    val applied: Boolean,
    val rows: List<ImportRowResultDTO>,
    val newTypes: List<String>,
    val newLocations: List<String>,
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val errors: Int,
)

/**
 * Imports devices from a spreadsheet, all or nothing. A preview (`dryRun`) runs exactly the same import and rolls it
 * back. Rows are matched to existing devices by external ID, then by serial number, so importing the same file again
 * updates instead of duplicating. Unknown types are created as lent (POOL) types with a version and an accessories
 * field; unknown locations are created too. Status and holder are never changed by an import.
 */
@Service
class ImportService(
    private val deviceService: DeviceService,
    private val deviceRepository: DeviceRepository,
    private val typeRepository: DeviceTypeRepository,
    private val locationRepository: LocationRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transaction = TransactionTemplate(transactionManager)

    // "N/A" and friends mean the cell is empty
    private val emptyValues = setOf("", "n/a", "na", "-", "--", "none", "null", "—")
    private val versionKeys = listOf("system_version", "version", "firmware", "os")

    fun import(request: ImportRequestDTO, actor: User): ImportResultDTO {
        require(request.rows.isNotEmpty()) { Messages.t("import.noRows") }
        require(request.rows.size <= MAX_ROWS) { Messages.t("import.tooMany", MAX_ROWS) }
        return transaction.execute { status ->
            val newTypes = mutableListOf<String>()
            val newLocations = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            val results = request.rows.map { row ->
                try {
                    importRow(clean(row), actor, newTypes, newLocations, seen)
                } catch (ex: IllegalArgumentException) {
                    error(row, ex.message)
                } catch (ex: ConflictException) {
                    error(row, ex.message)
                } catch (ex: NotFoundException) {
                    error(row, ex.message)
                }
            }
            val errors = results.count { it.action == ImportAction.ERROR }
            if (request.dryRun || errors > 0) status.setRollbackOnly()
            ImportResultDTO(
                dryRun = request.dryRun,
                applied = !request.dryRun && errors == 0,
                rows = results,
                newTypes = newTypes,
                newLocations = newLocations,
                created = results.count { it.action == ImportAction.CREATE },
                updated = results.count { it.action == ImportAction.UPDATE },
                unchanged = results.count { it.action == ImportAction.UNCHANGED },
                errors = errors,
            )
        }!!
    }

    private fun error(row: ImportRowDTO, message: String?) =
        ImportRowResultDTO(row.line, ImportAction.ERROR, name = row.name, typeName = row.typeName, message = message ?: Messages.t("import.invalidRow"))

    private fun value(text: String?): String? = text?.trim()?.takeIf { it.lowercase() !in emptyValues }

    private fun clean(row: ImportRowDTO) = row.copy(
        externalId = value(row.externalId), name = value(row.name), manufacturer = value(row.manufacturer),
        model = value(row.model), serialNumber = value(row.serialNumber), typeName = value(row.typeName),
        version = value(row.version), accessories = value(row.accessories), projectCode = value(row.projectCode),
        location = value(row.location), notes = value(row.notes),
    )

    private fun importRow(
        row: ImportRowDTO, actor: User, newTypes: MutableList<String>, newLocations: MutableList<String>, seen: MutableSet<String>,
    ): ImportRowResultDTO {
        val key = row.externalId?.let { "id:$it" } ?: row.serialNumber?.let { "sn:${it.uppercase()}" }
        if (key != null && !seen.add(key)) throw IllegalArgumentException(Messages.t(if (row.externalId != null) "import.sameId" else "import.sameSerial"))
        val model = row.model ?: row.name ?: throw IllegalArgumentException(Messages.t("import.noModel"))

        val existing = row.externalId?.let { deviceRepository.findByExternalId(it) }
            ?: row.serialNumber?.let { deviceRepository.findBySerialNumberIgnoreCase(it) }

        val type = existing?.type ?: resolveType(row.typeName, newTypes)
        if (existing != null && row.typeName != null && !sameType(existing.type, row.typeName)) {
            throw IllegalArgumentException(Messages.t("import.otherType", existing.assetTag, existing.type.name, row.typeName))
        }
        val location = row.location?.let { resolveLocation(it, newLocations) }

        // the version goes to the type's version field, accessories to its accessories field, otherwise to the notes
        val fields = existing?.fields?.toMutableMap() ?: mutableMapOf()
        val extraNotes = mutableListOf<String>()
        row.version?.let { version ->
            val key = type.coverageField ?: versionKeys.firstOrNull { k -> type.fields.any { it.key == k } }
            if (key != null) fields[key] = version else extraNotes += Messages.t("import.versionNote", version)
        }
        row.accessories?.let { accessories ->
            if (type.fields.any { it.key == "accessories" }) fields["accessories"] = accessories else extraNotes += Messages.t("import.accessoriesNote", accessories)
        }
        val notes = listOfNotNull(row.notes ?: existing?.notes).plus(extraNotes.filter { existing?.notes?.contains(it) != true })
            .joinToString("\n").ifBlank { null }

        val request = SaveDeviceDTO(
            typeId = type.id,
            name = row.name ?: existing?.name ?: model,
            manufacturer = row.manufacturer ?: existing?.manufacturer,
            model = model,
            serialNumber = row.serialNumber ?: existing?.serialNumber,
            locationId = location?.id ?: existing?.location?.id,
            purchaseDate = existing?.purchaseDate,
            warrantyEnd = existing?.warrantyEnd,
            notes = notes,
            projectCode = row.projectCode ?: existing?.projectCode,
            fields = fields,
        )

        if (existing == null) {
            val device = deviceService.create(request, actor, DeviceEventKind.IMPORTED)
            device.externalId = row.externalId
            return ImportRowResultDTO(row.line, ImportAction.CREATE, device.assetTag, device.name, type.name)
        }

        val before = deviceService.snapshot(existing) + ("externalId" to existing.externalId)
        deviceService.applyDetails(existing, request)
        if (row.externalId != null) existing.externalId = row.externalId
        val changed = (deviceService.snapshot(existing) + ("externalId" to existing.externalId)).filter { (k, v) -> before[k] != v }.keys
        if (changed.isEmpty()) return ImportRowResultDTO(row.line, ImportAction.UNCHANGED, existing.assetTag, existing.name, type.name)
        existing.updatedAt = Instant.now()
        deviceService.record(existing, DeviceEventKind.IMPORTED, actor, details = mapOf("changed" to changed.sorted()))
        return ImportRowResultDTO(row.line, ImportAction.UPDATE, existing.assetTag, existing.name, type.name, Messages.t("import.changes", changed.sorted().joinToString()))
    }

    private fun normalized(name: String) = name.trim().lowercase().removeSuffix("s")

    private fun sameType(type: DeviceType, name: String) = normalized(type.name) == normalized(name)

    private fun resolveType(name: String?, newTypes: MutableList<String>): DeviceType {
        val typeName = name ?: throw IllegalArgumentException(Messages.t("import.noType"))
        typeRepository.findAll().firstOrNull { sameType(it, typeName) }?.let { return it }
        val created = typeRepository.save(DeviceType(
            name = typeName.trim(),
            tagPrefix = uniquePrefix(typeName),
            usage = DeviceUsage.POOL,
            fields = listOf(
                FieldDefinition("version", "Firmware / OS"),
                FieldDefinition("accessories", "Accessories"),
            ),
            coverageField = "version",
        ))
        newTypes += created.name
        return created
    }

    /** "Network Adapter" → NA, "PC" → PC, "Controller" → CTRL-like first letters; made unique with a digit. */
    private fun uniquePrefix(name: String): String {
        val words = name.uppercase().split(Regex("[^A-Z0-9]+")).filter(String::isNotEmpty)
        val base = when {
            words.isEmpty() -> "DEV"
            words.size == 1 -> words[0].take(4)
            else -> words.joinToString("") { it.take(1) }.take(6)
        }.let { if (it.first().isLetter()) it else "D$it".take(8) }
        if (!typeRepository.existsByTagPrefixIgnoreCase(base)) return base
        return (2..99).map { "$base$it" }.first { !typeRepository.existsByTagPrefixIgnoreCase(it) }
    }

    private fun resolveLocation(name: String, newLocations: MutableList<String>): Location =
        locationRepository.findByNameIgnoreCase(name) ?: locationRepository.save(Location(name = name)).also { newLocations += it.name }

    companion object {
        const val MAX_ROWS = 2000
    }
}
