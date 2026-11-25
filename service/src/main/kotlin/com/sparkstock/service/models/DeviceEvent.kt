package com.sparkstock.service.models

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "device_events")
class DeviceEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "device_id")
    val deviceId: UUID,

    @Enumerated(EnumType.STRING)
    val kind: DeviceEventKind,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id")
    val actor: User? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "holder_id")
    val holder: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    val fromStatus: DeviceStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    val toStatus: DeviceStatus? = null,

    val note: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    val details: Map<String, Any?>? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
