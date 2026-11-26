package com.sparkstock.service.models

import jakarta.persistence.*
import java.time.Instant

/** A photo kept with an event of a device's history, e.g. the state it came back in. */
@Entity
@Table(name = "event_photos")
class EventPhoto(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "event_id")
    val eventId: Long,

    @Column(name = "content_type")
    val contentType: String,

    @Basic(fetch = FetchType.LAZY)
    val data: ByteArray,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
