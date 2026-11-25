package com.sparkstock.service.models

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** An extra field of a device type, e.g. "System version" for VR headsets. */
data class FieldDefinition(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val options: List<String>? = null,
    val required: Boolean = false,
)

@Entity
@Table(name = "device_types")
class DeviceType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var name: String,

    @Column(name = "tag_prefix")
    var tagPrefix: String,

    @Enumerated(EnumType.STRING)
    var usage: DeviceUsage,

    @JdbcTypeCode(SqlTypes.JSON)
    var fields: List<FieldDefinition> = emptyList(),

    @Column(name = "coverage_field")
    var coverageField: String? = null,

    @Column(name = "next_number")
    var nextNumber: Int = 1,

    @Column(name = "is_archived")
    var isArchived: Boolean = false,
)
