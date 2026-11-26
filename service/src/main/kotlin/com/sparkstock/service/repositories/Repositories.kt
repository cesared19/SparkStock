package com.sparkstock.service.repositories

import com.sparkstock.service.models.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findAllByOrderByDisplayNameAsc(): List<User>
}

interface LocationRepository : JpaRepository<Location, Long> {
    fun findAllByOrderByNameAsc(): List<Location>
    fun findByNameIgnoreCase(name: String): Location?
}

interface DeviceTypeRepository : JpaRepository<DeviceType, Long> {
    fun findAllByOrderByNameAsc(): List<DeviceType>
    fun findByNameIgnoreCase(name: String): DeviceType?
    fun existsByTagPrefixIgnoreCase(tagPrefix: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DeviceType t where t.id = :id")
    fun findByIdForUpdate(id: Long): DeviceType?
}

interface DeviceRepository : JpaRepository<Device, UUID> {
    fun findByAssetTagIgnoreCase(assetTag: String): Device?
    fun findBySerialNumberIgnoreCase(serialNumber: String): Device?
    fun findByExternalId(externalId: String): Device?
    fun findAllByHolderIdOrderByAssetTagAsc(holderId: UUID): List<Device>
    fun findAllByOrderByAssetTagAsc(): List<Device>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    fun findByIdForUpdate(id: UUID): Optional<Device>

    @Query("select d from Device d where d.status = com.sparkstock.service.models.DeviceStatus.ON_LOAN and d.dueAt < :now order by d.dueAt")
    fun findOverdue(now: Instant): List<Device>

    @Query("""select d from Device d where d.warrantyEnd is not null and d.warrantyEnd <= :until
        and d.status <> com.sparkstock.service.models.DeviceStatus.RETIRED order by d.warrantyEnd""")
    fun findWarrantyEndingBefore(until: LocalDate): List<Device>
}

interface EventPhotoRepository : JpaRepository<EventPhoto, Long> {
    @Query("select p.id, p.eventId from EventPhoto p where p.eventId in :eventIds order by p.id")
    fun findIdsByEventIds(eventIds: Collection<Long>): List<Array<Any>>
    fun countByEventId(eventId: Long): Long
}

interface DeviceEventRepository : JpaRepository<DeviceEvent, Long> {
    fun findAllByDeviceIdOrderByCreatedAtDescIdDesc(deviceId: UUID): List<DeviceEvent>
}

interface CoverageTargetRepository : JpaRepository<CoverageTarget, Long> {
    fun findAllByProjectCodeIgnoreCaseOrderByModelAscVersionAsc(projectCode: String): List<CoverageTarget>
}
