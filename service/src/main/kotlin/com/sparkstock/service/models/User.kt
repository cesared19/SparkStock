package com.sparkstock.service.models

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/** A copy of a SparkyTC account; SparkyTC owns it, SparkStock refreshes it. */
@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID,

    var username: String,

    @Column(name = "display_name")
    var displayName: String,

    @Enumerated(EnumType.STRING)
    var role: UserRole,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "synced_at")
    var syncedAt: Instant = Instant.now(),
)
