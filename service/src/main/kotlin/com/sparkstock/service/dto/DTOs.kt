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
) {
    companion object {
        fun of(event: DeviceEvent) = DeviceEventDTO(
            event.id, event.kind, PersonDTO.of(event.actor), PersonDTO.of(event.holder),
            event.fromStatus, event.toStatus, event.note, event.details, event.createdAt,
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
    val fields: Map<String, Any?> = emptyMap(),
    // only when creating: a tag of your own instead of the next <prefix>-NNNN
    val assetTag: String? = null,
)

data class CheckOutDTO(val dueAt: Instant? = null, val note: String? = null)

data class CheckInDTO(val locationId: Long? = null, val note: String? = null)

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
