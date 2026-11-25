package com.sparkstock.service.models

import jakarta.persistence.*

@Entity
@Table(name = "locations")
class Location(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var name: String,

    @Column(name = "is_archived")
    var isArchived: Boolean = false,
)
