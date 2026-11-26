package com.sparkstock.service.services

import com.sparkstock.service.dto.*
import com.sparkstock.service.models.*
import com.sparkstock.service.repositories.DeviceEventRepository
import com.sparkstock.service.repositories.DeviceRepository
import com.sparkstock.service.repositories.EventPhotoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

/** What the IT manager's scanner app needs besides lending and returning: photos of returns and inventory checks. */
@Service
class ScannerService(
    private val deviceRepository: DeviceRepository,
    private val eventRepository: DeviceEventRepository,
    private val photoRepository: EventPhotoRepository,
    private val catalogService: CatalogService,
    private val deviceService: DeviceService,
) {
    /** A photo of how a device came back, kept with that event; whoever recorded the event or an admin adds it. */
    @Transactional
    fun addPhoto(deviceId: UUID, eventId: Long, file: MultipartFile, actor: User): Long {
        val event = eventRepository.findById(eventId).orElse(null)?.takeIf { it.deviceId == deviceId }
            ?: throw NotFoundException(Messages.t("photo.eventNotFound"))
        if (event.actor?.id != actor.id && actor.role != UserRole.ADMIN) throw ForbiddenException(Messages.t("photo.notYours"))
        val type = file.contentType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        require(type in PHOTO_TYPES) { Messages.t("photo.type") }
        require(file.size in 1..MAX_PHOTO_BYTES) { Messages.t("photo.size", MAX_PHOTO_BYTES / 1024 / 1024) }
        require(photoRepository.countByEventId(eventId) < MAX_PHOTOS) { Messages.t("photo.tooMany", MAX_PHOTOS) }
        val bytes = file.bytes
        // checks the file really is a JPEG, PNG or WebP, not just what it says it is
        require(looksLikeImage(bytes)) { Messages.t("photo.type") }
        return photoRepository.save(EventPhoto(eventId = eventId, contentType = type, data = bytes)).id
    }

    @Transactional(readOnly = true)
    fun photo(id: Long): EventPhoto = photoRepository.findById(id).orElseThrow { NotFoundException(Messages.t("photo.notFound")) }

    fun photoIds(events: List<DeviceEvent>): Map<Long, List<Long>> {
        if (events.isEmpty()) return emptyMap()
        return photoRepository.findIdsByEventIds(events.map { it.id })
            .groupBy({ (it[1] as Number).toLong() }, { (it[0] as Number).toLong() })
    }

    /**
     * Compares what was scanned in a location with what SparkStock says is there: found, missing, somewhere else
     * (another location, on loan, lost) and unknown tags. With `apply`, the devices found here that SparkStock had
     * elsewhere move here; lost ones come back as available. Devices on loan or assigned are only reported.
     */
    @Transactional
    fun inventoryCheck(request: InventoryCheckDTO, actor: User): InventoryCheckResultDTO {
        val locationId = request.locationId ?: throw IllegalArgumentException(Messages.t("inventory.chooseLocation"))
        val location = catalogService.location(locationId)
        val tags = request.assetTags.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        require(tags.size <= 1000) { Messages.t("inventory.tooMany", 1000) }

        val all = deviceRepository.findAllByOrderByAssetTagAsc()
        val byTag = all.associateBy { it.assetTag.uppercase() }
        val scanned = tags.mapNotNull { byTag[it] }
        val unknown = tags.filter { it !in byTag }
        val scannedIds = scanned.map { it.id }.toSet()

        fun isHere(device: Device) = device.location?.id == location.id && device.status in SHELF_STATUSES
        val expected = all.filter { isHere(it) }
        val found = scanned.filter { isHere(it) }
        val missing = expected.filter { it.id !in scannedIds }
        val elsewhere = scanned.filter { !isHere(it) }

        var moved = 0
        if (request.apply) {
            for (device in elsewhere.filter { canMove(it) }) {
                val locked = deviceRepository.findByIdForUpdate(device.id).orElseThrow()
                val from = locked.status
                val previousLocation = locked.location?.name
                locked.location = location
                if (locked.status == DeviceStatus.LOST) locked.status = DeviceStatus.AVAILABLE
                locked.updatedAt = Instant.now()
                deviceService.record(locked, DeviceEventKind.UPDATED, actor,
                    fromStatus = from.takeIf { it != locked.status }, toStatus = locked.status.takeIf { it != from },
                    details = mapOf("inventoryCheck" to location.name, "from" to previousLocation, "changed" to listOf("location")))
                moved++
            }
        }

        return InventoryCheckResultDTO(
            location = LocationDTO.of(location),
            found = found.map(DeviceDTO::of),
            missing = missing.map(DeviceDTO::of),
            elsewhere = elsewhere.map { MisplacedDeviceDTO(DeviceDTO.of(it), canMove(it)) },
            unknown = unknown,
            moved = moved,
        )
    }

    // on loan or assigned devices stay with their person; the check only reports them
    private fun canMove(device: Device) = device.status == DeviceStatus.AVAILABLE || device.status == DeviceStatus.IN_REPAIR ||
        device.status == DeviceStatus.LOST

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        fun starts(vararg values: Int) = bytes.size >= values.size && values.indices.all { (bytes[it].toInt() and 0xFF) == values[it] }
        val webp = bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        return starts(0xFF, 0xD8, 0xFF) || starts(0x89, 0x50, 0x4E, 0x47) || webp
    }

    companion object {
        val PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
        const val MAX_PHOTOS = 5
        // the devices that should be on the shelf of their location
        val SHELF_STATUSES = setOf(DeviceStatus.AVAILABLE, DeviceStatus.IN_REPAIR)
    }
}
