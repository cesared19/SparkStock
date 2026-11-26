package com.sparkstock.service.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.sparkstock.service.models.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DataResponse<T>(val status: String = "SUCCESS", val data: T)

data class ErrorResponse(val status: String = "ERROR", val error: String, val message: String)

// ---- auth

data class LoginDTO(val username: String = "", val password: String = "")

data class LoginResponseDTO(val token: String, val expiresAt: Instant, val user: UserDTO)

data class UserDTO(
    val userID: UUID,
    val username: String,
    val displayName: String,
    val role: UserRole,
    @JsonProperty("isActive")
    val isActive: Boolean,
) {
    companion object {
        fun of(user: User) = UserDTO(user.id, user.username, user.displayName, user.role, user.isActive)
    }
}

/** A person as shown next to a device. */
data class PersonDTO(val userID: UUID, val displayName: String, val username: String) {
    companion object {
        fun of(user: User?) = user?.let { PersonDTO(it.id, it.displayName, it.username) }
    }
}

// ---- catalog

data class LocationDTO(val id: Long, val name: String, @JsonProperty("isArchived") val isArchived: Boolean) {
    companion object {
        fun of(location: Location) = LocationDTO(location.id, location.name, location.isArchived)
    }
}

data class SaveLocationDTO(val name: String = "", val isArchived: Boolean? = null)

data class DeviceTypeDTO(
    val id: Long,
    val name: String,
    val tagPrefix: String,
    val usage: DeviceUsage,
    val fields: List<FieldDefinition>,
    val coverageField: String?,
    @JsonProperty("isArchived")
    val isArchived: Boolean,
) {
    companion object {
        fun of(type: DeviceType) =
            DeviceTypeDTO(type.id, type.name, type.tagPrefix, type.usage, type.fields, type.coverageField, type.isArchived)
    }
}

data class SaveDeviceTypeDTO(
    val name: String = "",
    val tagPrefix: String = "",
    val usage: DeviceUsage = DeviceUsage.POOL,
    val fields: List<FieldDefinition> = emptyList(),
    val coverageField: String? = null,
    val isArchived: Boolean? = null,
)

// ---- devices

data class DeviceDTO(
    val id: UUID,
    val assetTag: String,
    val type: DeviceTypeDTO,
    val name: String,
    val manufacturer: String?,
    val model: String,
    val serialNumber: String?,
    val status: DeviceStatus,
    val location: LocationDTO?,
    val holder: PersonDTO?,
    val heldSince: Instant?,
    val dueAt: Instant?,
    @JsonProperty("isOverdue")
    val isOverdue: Boolean,
    val purchaseDate: LocalDate?,
    val warrantyEnd: LocalDate?,
    val notes: String?,
    val projectCode: String?,
    val externalId: String?,
    val fields: Map<String, Any?>,
    val coverageVersion: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(device: Device) = DeviceDTO(
            id = device.id,
            assetTag = device.assetTag,
            type = DeviceTypeDTO.of(device.type),
            name = device.name,
            manufacturer = device.manufacturer,
            model = device.model,
            serialNumber = device.serialNumber,
            status = device.status,
            location = device.location?.let(LocationDTO::of),
            holder = PersonDTO.of(device.holder),
            heldSince = device.heldSince,
            dueAt = device.dueAt,
            isOverdue = device.isOverdue,
            purchaseDate = device.purchaseDate,
            warrantyEnd = device.warrantyEnd,
            notes = device.notes,
            projectCode = device.projectCode,
            externalId = device.externalId,
            fields = device.fields,
            coverageVersion = device.coverageVersion,
            createdAt = device.createdAt,
            updatedAt = device.updatedAt,
        )
    }
}

data class DeviceEventDTO(
    val id: Long,
    val kind: DeviceEventKind,
    val actor: PersonDTO?,
    val holder: PersonDTO?,
    val fromStatus: DeviceStatus?,
    val toStatus: DeviceStatus?,
    val note: String?,
    val details: Map<String, Any?>?,
    val createdAt: Instant,
    // photos kept with the event, e.g. how a device came back (GET /api/v1/event-photos/{id})
    val photoIds: List<Long> = emptyList(),
) {
    companion object {
        fun of(event: DeviceEvent, photoIds: List<Long> = emptyList()) = DeviceEventDTO(
            event.id, event.kind, PersonDTO.of(event.actor), PersonDTO.of(event.holder),
            event.fromStatus, event.toStatus, event.note, event.details, event.createdAt, photoIds,
        )
    }
}

data class DeviceDetailDTO(val device: DeviceDTO, val events: List<DeviceEventDTO>)

data class SaveDeviceDTO(
    val typeId: Long? = null,
    val name: String = "",
    val manufacturer: String? = null,
    val model: String = "",
    val serialNumber: String? = null,
    val locationId: Long? = null,
    val purchaseDate: LocalDate? = null,
    val warrantyEnd: LocalDate? = null,
    val notes: String? = null,
    val projectCode: String? = null,
    val fields: Map<String, Any?> = emptyMap(),
    // only when creating: a tag of your own instead of the next <prefix>-NNNN
    val assetTag: String? = null,
)

/** `userId` lends it to someone else (admins only); without it the device goes to whoever asks. */
data class CheckOutDTO(val dueAt: Instant? = null, val note: String? = null, val userId: UUID? = null)

/** Several devices to one person at once, e.g. a headset with its controllers; all or nothing. */
data class BatchCheckOutDTO(
    val deviceIds: List<UUID> = emptyList(),
    val userId: UUID? = null,
    val dueAt: Instant? = null,
    val note: String? = null,
)

/** How a device came back: NEEDS_REPAIR sends it to repair instead of back to stock. */
enum class ReturnCondition { OK, NEEDS_REPAIR }

data class CheckInDTO(val locationId: Long? = null, val note: String? = null, val condition: ReturnCondition? = null)

/** The asset tags scanned in a location, to compare with what SparkStock says is there. */
data class InventoryCheckDTO(val locationId: Long? = null, val assetTags: List<String> = emptyList(), val apply: Boolean = false)

data class MisplacedDeviceDTO(val device: DeviceDTO, val canMove: Boolean)

data class InventoryCheckResultDTO(
    val location: LocationDTO,
    // where SparkStock says they are, and scanned
    val found: List<DeviceDTO>,
    // where SparkStock says they are, but not scanned
    val missing: List<DeviceDTO>,
    // scanned here, but SparkStock has them somewhere else, on loan or lost
    val elsewhere: List<MisplacedDeviceDTO>,
    // scanned tags that aren't in SparkStock
    val unknown: List<String>,
    // devices moved here (with apply), from elsewhere
    val moved: Int,
)

data class AssignDTO(val userId: UUID? = null, val note: String? = null)

data class ChangeStatusDTO(val status: DeviceStatus? = null, val note: String? = null)

// ---- dashboard

data class DashboardDTO(
    val counts: Map<DeviceStatus, Int>,
    val total: Int,
    val myDevices: List<DeviceDTO>,
    val overdue: List<DeviceDTO>,
    val warrantyEnding: List<DeviceDTO>,
    val loanDefaultDays: Long,
)
