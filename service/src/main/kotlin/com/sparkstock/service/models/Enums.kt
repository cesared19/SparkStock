package com.sparkstock.service.models

enum class UserRole { ADMIN, TESTER }

/** POOL devices are lent to anyone for a while, ASSIGNED ones are given to a person by an admin. */
enum class DeviceUsage { POOL, ASSIGNED }

enum class DeviceStatus {
    AVAILABLE,
    ON_LOAN,
    ASSIGNED,
    IN_REPAIR,
    LOST,
    RETIRED;

    /** A device in these states is with a person. */
    val isHeld get() = this == ON_LOAN || this == ASSIGNED
}

enum class FieldKind { TEXT, NUMBER, DATE, BOOLEAN, SELECT }

enum class DeviceEventKind { CREATED, UPDATED, CHECKED_OUT, CHECKED_IN, ASSIGNED, UNASSIGNED, STATUS_CHANGED, IMPORTED }
