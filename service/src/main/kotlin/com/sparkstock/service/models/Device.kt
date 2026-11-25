package com.sparkstock.service.models

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "devices")
class Device(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "asset_tag")
    var assetTag: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_id")
    var type: DeviceType,

    var name: String,
    var manufacturer: String? = null,
    var model: String,

    @Column(name = "serial_number")
    var serialNumber: String? = null,

    @Enumerated(EnumType.STRING)
    var status: DeviceStatus = DeviceStatus.AVAILABLE,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    var location: Location? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "holder_id")
    var holder: User? = null,

    @Column(name = "held_since")
    var heldSince: Instant? = null,

    @Column(name = "due_at")
    var dueAt: Instant? = null,

    @Column(name = "purchase_date")
    var purchaseDate: LocalDate? = null,

    @Column(name = "warranty_end")
    var warrantyEnd: LocalDate? = null,

    var notes: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    var fields: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),

    @Version
    val version: Long = 0,
) {
    val isOverdue get() = status == DeviceStatus.ON_LOAN && dueAt?.isBefore(Instant.now()) == true

    /** The OS/firmware version used for the coverage matrix, e.g. the VR headset's system version. */
    val coverageVersion get() = type.coverageField?.let { fields[it]?.toString()?.takeIf(String::isNotBlank) }
}
