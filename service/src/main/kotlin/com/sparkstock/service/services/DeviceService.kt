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

    fun get(id: UUID): Device = deviceRepository.findById(id).orElseThrow { NotFoundException(Messages.t("device.notFound")) }

    fun getByTag(tag: String): Device = deviceRepository.findByAssetTagIgnoreCase(tag.trim())
        ?: throw NotFoundException(Messages.t("device.noTag", tag))

    fun events(device: Device): List<DeviceEvent> = eventRepository.findAllByDeviceIdOrderByCreatedAtDescIdDesc(device.id)

    @Transactional
    fun create(request: SaveDeviceDTO, actor: User?, kind: DeviceEventKind = DeviceEventKind.CREATED): Device {
        val typeId = request.typeId ?: throw IllegalArgumentException(Messages.t("device.chooseType"))
        val type = typeRepository.findByIdForUpdate(typeId) ?: throw NotFoundException(Messages.t("type.notFound"))
        require(!type.isArchived) { Messages.t("type.archived", type.name) }

        val assetTag = request.assetTag?.trim()?.uppercase()?.takeIf(String::isNotEmpty)?.also { tag ->
            require(tag.length <= 40 && tag.matches(Regex("^[A-Z0-9][A-Z0-9._-]*$"))) { Messages.t("device.tagFormat") }
            if (deviceRepository.findByAssetTagIgnoreCase(tag) != null) throw ConflictException(Messages.t("device.tagUsed", tag))
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
        require(request.typeId == null || request.typeId == device.type.id) { Messages.t("device.typeFixed") }
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
        require(model.isNotEmpty()) { Messages.t("device.enterModel") }
        val serial = request.serialNumber?.trim()?.takeIf(String::isNotEmpty)
        if (serial != null) {
            deviceRepository.findBySerialNumberIgnoreCase(serial)?.takeIf { it.id != device.id }?.let {
                throw ConflictException(Messages.t("device.serialUsed", it.assetTag, serial))
            }
        }
        if (request.purchaseDate != null && request.warrantyEnd != null) {
            require(!request.warrantyEnd.isBefore(request.purchaseDate)) { Messages.t("device.warrantyBeforePurchase") }
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
            require(it.length <= 20) { Messages.t("device.projectTooLong") }
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
        // an admin (e.g. the IT manager scanning the label) can lend it to someone else
        val borrower = request.userId?.takeIf { it != actor.id }?.let { userId ->
            if (actor.role != UserRole.ADMIN) throw ForbiddenException(Messages.t("loan.othersAdmin"))
            val person = userRepository.findById(userId).orElseThrow { NotFoundException(Messages.t("user.notFound")) }
            require(person.isActive) { Messages.t("assign.disabled", person.displayName) }
            person
        } ?: actor
        val device = locked(id)
        if (device.type.usage != DeviceUsage.POOL) throw ConflictException(Messages.t("loan.assignedType", device.type.name))
        if (device.status != DeviceStatus.AVAILABLE) throw ConflictException(unavailableMessage(device))

        val now = Instant.now()
        val dueAt = request.dueAt ?: now.plus(Duration.ofDays(properties.loans.defaultDays))
        require(dueAt.isAfter(now)) { Messages.t("loan.dueInPast") }
        require(!dueAt.isAfter(now.plus(Duration.ofDays(properties.loans.maxDays)))) {
            Messages.t("loan.tooLong", properties.loans.maxDays)
        }
        return hold(device, DeviceStatus.ON_LOAN, borrower, actor, dueAt, DeviceEventKind.CHECKED_OUT, request.note)
    }

    /** Several devices to one person in one go (a headset, its controllers, the charger): all are lent or none. */
    @Transactional
    fun checkOutMany(request: BatchCheckOutDTO, actor: User): List<Device> {
        val ids = request.deviceIds.distinct()
        require(ids.isNotEmpty()) { Messages.t("loan.noDevices") }
        require(ids.size <= 50) { Messages.t("loan.tooMany", 50) }
        val single = CheckOutDTO(dueAt = request.dueAt, note = request.note, userId = request.userId)
        // locking in a fixed order keeps two batches with the same devices from waiting on each other
        return ids.sorted().map { checkOut(it, single, actor) }.sortedBy { it.assetTag }
    }

    /** The borrower or an admin returns a device on loan. */
    @Transactional
    fun checkIn(id: UUID, request: CheckInDTO, actor: User): Device {
        val device = locked(id)
        if (device.status != DeviceStatus.ON_LOAN) throw ConflictException(Messages.t("loan.notOnLoan", device.assetTag))
        if (device.holder?.id != actor.id && actor.role != UserRole.ADMIN) {
            throw ForbiddenException(Messages.t("loan.onlyBorrower", device.holder?.displayName ?: Messages.t("loan.theBorrower"), device.assetTag))
        }
        request.locationId?.let { device.location = catalogService.location(it) }
        // one that came back broken goes to repair instead of back to stock
        val status = if (request.condition == ReturnCondition.NEEDS_REPAIR) DeviceStatus.IN_REPAIR else DeviceStatus.AVAILABLE
        return release(device, status, actor, DeviceEventKind.CHECKED_IN, request.note,
            request.condition?.let { mapOf("condition" to it.name) })
    }

    /** An admin gives an ASSIGNED device to a person, or moves it from one person to another. */
    @Transactional
    fun assign(id: UUID, request: AssignDTO, actor: User): Device {
        val device = locked(id)
        if (device.type.usage != DeviceUsage.ASSIGNED) throw ConflictException(Messages.t("assign.lentType", device.type.name))
        if (device.status != DeviceStatus.AVAILABLE && device.status != DeviceStatus.ASSIGNED) throw ConflictException(unavailableMessage(device))
        val userId = request.userId ?: throw IllegalArgumentException(Messages.t("assign.choosePerson"))
        val person = userRepository.findById(userId).orElseThrow { NotFoundException(Messages.t("user.notFound")) }
        require(person.isActive) { Messages.t("assign.disabled", person.displayName) }
        if (device.holder?.id == person.id) throw ConflictException(Messages.t("assign.already", device.assetTag, person.displayName))
        if (device.status == DeviceStatus.ASSIGNED) release(device, DeviceStatus.AVAILABLE, actor, DeviceEventKind.UNASSIGNED, request.note)
        return hold(device, DeviceStatus.ASSIGNED, person, actor, null, DeviceEventKind.ASSIGNED, request.note)
    }

    @Transactional
    fun unassign(id: UUID, request: CheckInDTO, actor: User): Device {
        val device = locked(id)
        if (device.status != DeviceStatus.ASSIGNED) throw ConflictException(Messages.t("assign.notAssigned", device.assetTag))
        request.locationId?.let { device.location = catalogService.location(it) }
        return release(device, DeviceStatus.AVAILABLE, actor, DeviceEventKind.UNASSIGNED, request.note)
    }

    /** Admins move a device to repair, lost, retired or back to available; whoever had it no longer does. */
    @Transactional
    fun changeStatus(id: UUID, request: ChangeStatusDTO, actor: User): Device {
        val device = locked(id)
        val status = request.status ?: throw IllegalArgumentException(Messages.t("status.choose"))
        if (status.isHeld) throw IllegalArgumentException(Messages.t("status.useFlows"))
        if (status == device.status) throw ConflictException(Messages.t("status.already", device.assetTag, Messages.status(status)))
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

    private fun release(
        device: Device, status: DeviceStatus, actor: User, kind: DeviceEventKind, note: String?, extra: Map<String, Any?>? = null,
    ): Device {
        val from = device.status
        val holder = device.holder
        val wasOverdue = device.isOverdue
        device.status = status
        device.holder = null
        device.heldSince = null
        device.dueAt = null
        device.updatedAt = Instant.now()
        record(device, kind, actor, holder = holder, fromStatus = from, toStatus = status, note = note,
            details = ((if (wasOverdue) mapOf("overdue" to true) else emptyMap()) + extra.orEmpty()).takeIf { it.isNotEmpty() })
        return device
    }

    private fun unavailableMessage(device: Device): String = when (device.status) {
        DeviceStatus.ON_LOAN -> Messages.t("unavailable.onLoan", device.assetTag, device.holder?.displayName) +
            (device.dueAt?.let { Messages.t("unavailable.until", LocalDate.ofInstant(it, java.time.ZoneId.systemDefault())) } ?: "")
        DeviceStatus.ASSIGNED -> Messages.t("unavailable.assigned", device.assetTag, device.holder?.displayName)
        else -> Messages.t("unavailable.other", device.assetTag, Messages.status(device.status))
    }

    private fun locked(id: UUID): Device = deviceRepository.findByIdForUpdate(id).orElseThrow { NotFoundException(Messages.t("device.notFound")) }

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
