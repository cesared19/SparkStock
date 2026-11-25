package com.sparkstock.service.models

import jakarta.persistence.*
import java.time.Instant

/** A model (and optionally a version) a SparkyTC project has to be tested on. */
@Entity
@Table(name = "coverage_targets")
class CoverageTarget(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "project_code")
    val projectCode: String,

    val model: String,

    val version: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
