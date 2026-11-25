package com.sparkstock.service.services

import com.sparkstock.service.config.SparkStockProperties
import com.sparkstock.service.dto.*
import com.sparkstock.service.models.*
import com.sparkstock.service.repositories.DeviceEventRepository
import com.sparkstock.service.repositories.DeviceRepository
import com.sparkstock.service.repositories.DeviceTypeRepository
import com.sparkstock.service.repositories.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DeviceFilter(
    val query: String? = null,
    val typeId: Long? = null,
    val status: DeviceStatus? = null,
    val holderId: UUID? = null,
    val overdueOnly: Boolean = false,
    val includeRetired: Boolean = false,
)

/**
 * Devices and their life: created and edited by admins, lent (POOL types) to anyone who takes them,
 * assigned (ASSIGNED types) to a person by an admin, and moved between repair, lost and retired.
 * Every change is written to device_events.
 */
@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val eventRepository: DeviceEventRepository,
    private val typeRepository: DeviceTypeRepository,
    private val userRepository: UserRepository,
    private val catalogService: CatalogService,
    private val properties: SparkStockProperties,
) {
    fun list(filter: DeviceFilter): List<Device> {
        val words = filter.query?.trim()?.lowercase()?.split(Regex("\\s+"))?.filter(String::isNotEmpty).orEmpty()
        return deviceRepository.findAllByOrderByAssetTagAsc().filter { device ->
            (filter.includeRetired || filter.status == DeviceStatus.RETIRED || device.status != DeviceStatus.RETIRED) &&
                (filter.typeId == null || device.type.id == filter.typeId) &&
                (filter.status == null || device.status == filter.status) &&
                (filter.holderId == null || device.holder?.id == filter.holderId) &&
                (!filter.overdueOnly || device.isOverdue) &&
                words.all { word -> searchText(device).contains(word) }
        }
    }

    private fun searchText(device: Device) = listOfNotNull(
        device.assetTag, device.name, device.manufacturer, device.model, device.serialNumber, device.type.name,
        device.location?.name, device.holder?.displayName, device.holder?.username, device.notes, device.projectCode, device.externalId,
    ).plus(device.fields.values.mapNotNull { it?.toString() }).joinToString(" ").lowercase()

    fun get(id: UUID): Device = deviceRepository.findById(id).orElseThrow { NotFoundException("Device not found") }

    fun getByTag(tag: String): Device = deviceRepository.findByAssetTagIgnoreCase(tag.trim())
        ?: throw NotFoundException("No device has the tag $tag")

    fun events(device: Device): List<DeviceEvent> = eventRepository.findAllByDeviceIdOrderByCreatedAtDescIdDesc(device.id)

    @Transactional
    fun create(request: SaveDeviceDTO, actor: User?, kind: DeviceEventKind = DeviceEventKind.CREATED): Device {
        val typeId = request.typeId ?: throw IllegalArgumentException("Choose a device type")
        val type = typeRepository.findByIdForUpdate(typeId) ?: throw NotFoundException("Device type not found")
        require(!type.isArchived) { "${type.name} is archived, new devices can't use it" }

        val assetTag = request.assetTag?.trim()?.uppercase()?.takeIf(String::isNotEmpty)?.also { tag ->
            require(tag.length <= 40 && tag.matches(Regex("^[A-Z0-9][A-Z0-9._-]*$"))) { "Asset tags use letters, digits, '.', '_' and '-'" }
            if (deviceRepository.findByAssetTagIgnoreCase(tag) != null) throw ConflictException("The tag $tag is already used")
        } ?: nextTag(type)

        val draft = Device(assetTag = assetTag, type = type, name = "", model = "")
        applyDetails(draft, request)
        // the ID is assigned here, so save() merges and returns the managed copy: keep working on that one
        val device = deviceRepository.save(draft)
        record(device, kind, actor, toStatus = device.status)
        return device
    }

    internal fun nextTag(type: DeviceType): String {
        while (true) {
            val tag = "%s-%04d".format(type.tagPrefix, type.nextNumber)
            type.nextNumber += 1
            if (deviceRepository.findByAssetTagIgnoreCase(tag) == null) return tag
        }
    }

    @Transactional
    fun update(id: UUID, request: SaveDeviceDTO, actor: User): Device {
        val device = locked(id)
        require(request.typeId == null || request.typeId == device.type.id) { "A device's type can't be changed" }
        val before = snapshot(device)
        applyDetails(device, request)
        val changes = snapshot(device).filter { (key, value) -> before[key] != value }.keys
        if (changes.isNotEmpty()) {
            device.updatedAt = Instant.now()
            record(device, DeviceEventKind.UPDATED, actor, details = mapOf("changed" to changes.sorted()))
        }
        return device
    }

    internal fun applyDetails(device: Device, request: SaveDeviceDTO) {
        val model = request.model.trim()
        require(model.isNotEmpty()) { "Enter the model" }
        val serial = request.serialNumber?.trim()?.takeIf(String::isNotEmpty)
        if (serial != null) {
            deviceRepository.findBySerialNumberIgnoreCase(serial)?.takeIf { it.id != device.id }?.let {
                throw ConflictException("${it.assetTag} already has the serial number $serial")
            }
        }
        if (request.purchaseDate != null && request.warrantyEnd != null) {
            require(!request.warrantyEnd.isBefore(request.purchaseDate)) { "The warranty can't end before the purchase date" }
        }
        device.name = request.name.trim().ifEmpty { model }
        device.manufacturer = request.manufacturer?.trim()?.takeIf(String::isNotEmpty)
        device.model = model
        device.serialNumber = serial
        device.location = request.locationId?.let { catalogService.location(it) }
        device.purchaseDate = request.purchaseDate
        device.warrantyEnd = request.warrantyEnd
        device.notes = request.notes?.trim()?.takeIf(String::isNotEmpty)
        device.projectCode = request.projectCode?.trim()?.uppercase()?.takeIf(String::isNotEmpty)?.also {
            require(it.length <= 20) { "The project code is too long" }
        }
        device.fields = FieldValues.normalize(device.type.fields, request.fields)
    }

    internal fun snapshot(device: Device): Map<String, Any?> = mapOf(
        "name" to device.name, "manufacturer" to device.manufacturer, "model" to device.model,
        "serialNumber" to device.serialNumber, "location" to device.location?.id, "purchaseDate" to device.purchaseDate,
        "warrantyEnd" to device.warrantyEnd, "notes" to device.notes, "project" to device.projectCode,
    ) + device.type.fields.associate { "fields.${it.key}" to device.fields[it.key] }

    /** Anyone takes an available POOL device for themselves, until `dueAt` (by default in LOAN_DEFAULT_DAYS days). */
    @Transactional
    fun checkOut(id: UUID, request: CheckOutDTO, actor: User): Device {
        val device = locked(id)
        if (device.type.usage != DeviceUsage.POOL) throw ConflictException("${device.type.name} devices are assigned by an admin, not lent")
        if (device.status != DeviceStatus.AVAILABLE) throw ConflictException(unavailableMessage(device))

        val now = Instant.now()
        val dueAt = request.dueAt ?: now.plus(Duration.ofDays(properties.loans.defaultDays))
        require(dueAt.isAfter(now)) { "The return date must be in the future" }
        require(!dueAt.isAfter(now.plus(Duration.ofDays(properties.loans.maxDays)))) {
            "A loan can last ${properties.loans.maxDays} days at most"
        }
        return hold(device, DeviceStatus.ON_LOAN, actor, actor, dueAt, DeviceEventKind.CHECKED_OUT, request.note)
    }

    /** The borrower or an admin returns a device on loan. */
    @Transactional
    fun checkIn(id: UUID, request: CheckInDTO, actor: User): Device {
        val device = locked(id)
        if (device.status != DeviceStatus.ON_LOAN) throw ConflictException("${device.assetTag} isn't on loan")
        if (device.holder?.id != actor.id && actor.role != UserRole.ADMIN) {
            throw ForbiddenException("Only ${device.holder?.displayName ?: "the borrower"} or an admin can return ${device.assetTag}")
        }
        request.locationId?.let { device.location = catalogService.location(it) }
        return release(device, DeviceStatus.AVAILABLE, actor, DeviceEventKind.CHECKED_IN, request.note)
    }

    /** An admin gives an ASSIGNED device to a person, or moves it from one person to another. */
    @Transactional
    fun assign(id: UUID, request: AssignDTO, actor: User): Device {
        val device = locked(id)
        if (device.type.usage != DeviceUsage.ASSIGNED) throw ConflictException("${device.type.name} devices are lent, not assigned")
        if (device.status != DeviceStatus.AVAILABLE && device.status != DeviceStatus.ASSIGNED) throw ConflictException(unavailableMessage(device))
        val userId = request.userId ?: throw IllegalArgumentException("Choose who gets the device")
        val person = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        require(person.isActive) { "${person.displayName}'s account is disabled" }
        if (device.holder?.id == person.id) throw ConflictException("${device.assetTag} is already assigned to ${person.displayName}")
        if (device.status == DeviceStatus.ASSIGNED) release(device, DeviceStatus.AVAILABLE, actor, DeviceEventKind.UNASSIGNED, request.note)
        return hold(device, DeviceStatus.ASSIGNED, person, actor, null, DeviceEventKind.ASSIGNED, request.note)
    }

    @Transactional
    fun unassign(id: UUID, request: CheckInDTO, actor: User): Device {
        val device = locked(id)
        if (device.status != DeviceStatus.ASSIGNED) throw ConflictException("${device.assetTag} isn't assigned")
        request.locationId?.let { device.location = catalogService.location(it) }
        return release(device, DeviceStatus.AVAILABLE, actor, DeviceEventKind.UNASSIGNED, request.note)
    }

    /** Admins move a device to repair, lost, retired or back to available; whoever had it no longer does. */
    @Transactional
    fun changeStatus(id: UUID, request: ChangeStatusDTO, actor: User): Device {
        val device = locked(id)
        val status = request.status ?: throw IllegalArgumentException("Choose a status")
        if (status.isHeld) throw IllegalArgumentException("Use check-out or assign to give a device to someone")
        if (status == device.status) throw ConflictException("${device.assetTag} is already ${status.name.lowercase().replace('_', ' ')}")
        if (device.status.isHeld) {
            // e.g. a loaned headset that broke: the loan ends and the event remembers who had it
            return release(device, status, actor, DeviceEventKind.STATUS_CHANGED, request.note)
        }
        val from = device.status
        device.status = status
        device.updatedAt = Instant.now()
        record(device, DeviceEventKind.STATUS_CHANGED, actor, fromStatus = from, toStatus = status, note = request.note)
        return device
    }

    private fun hold(device: Device, status: DeviceStatus, holder: User, actor: User, dueAt: Instant?, kind: DeviceEventKind, note: String?): Device {
        val from = device.status
        device.status = status
        device.holder = holder
        device.heldSince = Instant.now()
        device.dueAt = dueAt
        device.updatedAt = Instant.now()
        record(device, kind, actor, holder = holder, fromStatus = from, toStatus = status, note = note,
            details = dueAt?.let { mapOf("dueAt" to it.toString()) })
        return device
    }

    private fun release(device: Device, status: DeviceStatus, actor: User, kind: DeviceEventKind, note: String?): Device {
        val from = device.status
        val holder = device.holder
        val wasOverdue = device.isOverdue
        device.status = status
        device.holder = null
        device.heldSince = null
        device.dueAt = null
        device.updatedAt = Instant.now()
        record(device, kind, actor, holder = holder, fromStatus = from, toStatus = status, note = note,
            details = if (wasOverdue) mapOf("overdue" to true) else null)
        return device
    }

    private fun unavailableMessage(device: Device): String = when (device.status) {
        DeviceStatus.ON_LOAN -> "${device.assetTag} is on loan to ${device.holder?.displayName}" +
            (device.dueAt?.let { " until ${LocalDate.ofInstant(it, java.time.ZoneOffset.UTC)}" } ?: "")
        DeviceStatus.ASSIGNED -> "${device.assetTag} is assigned to ${device.holder?.displayName}"
        else -> "${device.assetTag} is ${device.status.name.lowercase().replace('_', ' ')}"
    }

    private fun locked(id: UUID): Device = deviceRepository.findByIdForUpdate(id).orElseThrow { NotFoundException("Device not found") }

    internal fun record(
        device: Device, kind: DeviceEventKind, actor: User?, holder: User? = null, fromStatus: DeviceStatus? = null,
        toStatus: DeviceStatus? = null, note: String? = null, details: Map<String, Any?>? = null,
    ) {
        eventRepository.save(DeviceEvent(
            deviceId = device.id, kind = kind, actor = actor, holder = holder, fromStatus = fromStatus, toStatus = toStatus,
            note = note?.trim()?.takeIf(String::isNotEmpty)?.take(2000), details = details,
        ))
    }

    fun dashboard(user: User): DashboardDTO {
        val devices = deviceRepository.findAllByOrderByAssetTagAsc()
        val active = devices.filter { it.status != DeviceStatus.RETIRED }
        val warrantyUntil = LocalDate.now().plusDays(properties.warranty.warningDays)
        return DashboardDTO(
            counts = DeviceStatus.entries.associateWith { status -> devices.count { it.status == status } },
            total = active.size,
            myDevices = devices.filter { it.holder?.id == user.id }.map(DeviceDTO::of),
            overdue = devices.filter { it.isOverdue }.sortedBy { it.dueAt }.map(DeviceDTO::of),
            warrantyEnding = active.filter { it.warrantyEnd != null && !it.warrantyEnd!!.isAfter(warrantyUntil) }
                .sortedBy { it.warrantyEnd }.map(DeviceDTO::of),
            loanDefaultDays = properties.loans.defaultDays,
        )
    }
}
