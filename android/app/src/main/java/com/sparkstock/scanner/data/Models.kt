package com.sparkstock.scanner.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What SparkStock's API returns (only the fields the app uses). Dates are seconds since the epoch.

@Serializable
data class User(
    val userID: String,
    val username: String,
    val displayName: String,
    val role: String,
    @SerialName("isActive") val isActive: Boolean = true,
) {
    val isAdmin get() = role == "ADMIN"
}

@Serializable
data class Login(val token: String, val user: User)

@Serializable
data class Person(val userID: String, val displayName: String, val username: String)

@Serializable
data class Location(val id: Long, val name: String, @SerialName("isArchived") val isArchived: Boolean = false)

@Serializable
data class DeviceType(val id: Long, val name: String, val usage: String)

@Serializable
data class Device(
    val id: String,
    val assetTag: String,
    val name: String,
    val model: String,
    val type: DeviceType,
    val status: String,
    val holder: Person? = null,
    val dueAt: Double? = null,
    @SerialName("isOverdue") val isOverdue: Boolean = false,
    val location: Location? = null,
    val coverageVersion: String? = null,
) {
    val isLendable get() = type.usage == "POOL" && status == "AVAILABLE"
}

@Serializable
data class DeviceEvent(val id: Long, val kind: String)

@Serializable
data class DeviceDetail(val device: Device, val events: List<DeviceEvent>)

@Serializable
data class MisplacedDevice(val device: Device, val canMove: Boolean)

@Serializable
data class InventoryCheck(
    val location: Location,
    val found: List<Device>,
    val missing: List<Device>,
    val elsewhere: List<MisplacedDevice>,
    val unknown: List<String>,
    val moved: Int,
)

@Serializable
data class PhotoAdded(val photoId: Long)

enum class Condition { OK, NEEDS_REPAIR }
