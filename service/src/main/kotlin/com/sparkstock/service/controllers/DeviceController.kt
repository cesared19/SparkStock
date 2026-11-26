package com.sparkstock.service.controllers

import com.sparkstock.service.auth.AdminOnly
import com.sparkstock.service.auth.CurrentUser
import com.sparkstock.service.dto.*
import com.sparkstock.service.models.Device
import com.sparkstock.service.models.DeviceStatus
import com.sparkstock.service.services.DeviceFilter
import com.sparkstock.service.services.DeviceService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class DeviceController(
    private val deviceService: DeviceService,
    private val scannerService: com.sparkstock.service.services.ScannerService,
) {

    @GetMapping("/dashboard")
    fun dashboard() = DataResponse(data = deviceService.dashboard(CurrentUser.get()))

    /** `holder=me` lists the signed-in user's devices. */
    @GetMapping("/devices")
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) typeId: Long?,
        @RequestParam(required = false) status: DeviceStatus?,
        @RequestParam(required = false) holder: String?,
        @RequestParam(defaultValue = "false") overdue: Boolean,
        @RequestParam(defaultValue = "false") includeRetired: Boolean,
    ): DataResponse<List<DeviceDTO>> {
        val holderId = when {
            holder.isNullOrBlank() -> null
            holder == "me" -> CurrentUser.get().id
            else -> UUID.fromString(holder)
        }
        val filter = DeviceFilter(q, typeId, status, holderId, overdue, includeRetired)
        return DataResponse(data = deviceService.list(filter).map(DeviceDTO::of))
    }

    @GetMapping("/devices/{id}")
    fun get(@PathVariable id: UUID) = detail(deviceService.get(id))

    /** What a scanned QR label opens. */
    @GetMapping("/devices/by-tag/{tag}")
    fun byTag(@PathVariable tag: String) = detail(deviceService.getByTag(tag))

    @AdminOnly
    @PostMapping("/devices")
    fun create(@RequestBody request: SaveDeviceDTO) = detail(deviceService.create(request, CurrentUser.get()))

    @AdminOnly
    @PutMapping("/devices/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: SaveDeviceDTO) = detail(deviceService.update(id, request, CurrentUser.get()))

    @PostMapping("/devices/{id}/check-out")
    fun checkOut(@PathVariable id: UUID, @RequestBody(required = false) request: CheckOutDTO?) =
        detail(deviceService.checkOut(id, request ?: CheckOutDTO(), CurrentUser.get()))

    /** Several devices to one person at once (the scanner app); all or nothing. */
    @PostMapping("/devices/check-out")
    fun checkOutMany(@RequestBody request: BatchCheckOutDTO) =
        DataResponse(data = deviceService.checkOutMany(request, CurrentUser.get()).map(DeviceDTO::of))

    @PostMapping("/devices/{id}/check-in")
    fun checkIn(@PathVariable id: UUID, @RequestBody(required = false) request: CheckInDTO?) =
        detail(deviceService.checkIn(id, request ?: CheckInDTO(), CurrentUser.get()))

    @AdminOnly
    @PostMapping("/devices/{id}/assign")
    fun assign(@PathVariable id: UUID, @RequestBody request: AssignDTO) = detail(deviceService.assign(id, request, CurrentUser.get()))

    @AdminOnly
    @PostMapping("/devices/{id}/unassign")
    fun unassign(@PathVariable id: UUID, @RequestBody(required = false) request: CheckInDTO?) =
        detail(deviceService.unassign(id, request ?: CheckInDTO(), CurrentUser.get()))

    @AdminOnly
    @PostMapping("/devices/{id}/status")
    fun changeStatus(@PathVariable id: UUID, @RequestBody request: ChangeStatusDTO) =
        detail(deviceService.changeStatus(id, request, CurrentUser.get()))

    /** A photo of how a device came back, added to that event of its history (multipart `file`). */
    @PostMapping("/devices/{id}/events/{eventId}/photos", consumes = [org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addPhoto(@PathVariable id: UUID, @PathVariable eventId: Long, @RequestParam("file") file: org.springframework.web.multipart.MultipartFile) =
        DataResponse(data = mapOf("photoId" to scannerService.addPhoto(id, eventId, file, CurrentUser.get())))

    @GetMapping("/event-photos/{id}")
    fun photo(@PathVariable id: Long): org.springframework.http.ResponseEntity<ByteArray> {
        val photo = scannerService.photo(id)
        return org.springframework.http.ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(photo.contentType))
            .header("X-Content-Type-Options", "nosniff")
            .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
            .body(photo.data)
    }

    /** Compares what was scanned in a location with SparkStock, and with `apply` moves what was found there. */
    @AdminOnly
    @PostMapping("/inventory-checks")
    fun inventoryCheck(@RequestBody request: InventoryCheckDTO) =
        DataResponse(data = scannerService.inventoryCheck(request, CurrentUser.get()))

    private fun detail(device: Device): DataResponse<DeviceDetailDTO> {
        val events = deviceService.events(device)
        val photos = scannerService.photoIds(events)
        return DataResponse(data = DeviceDetailDTO(DeviceDTO.of(device), events.map { DeviceEventDTO.of(it, photos[it.id].orEmpty()) }))
    }
}

@RestController
@RequestMapping("/api/v1")
class ReminderController(private val reminderService: com.sparkstock.service.services.ReminderService) {

    /** Sends today's reminders now, instead of waiting for the daily run (it never sends one twice). */
    @AdminOnly
    @PostMapping("/reminders/send")
    fun send() = DataResponse(data = reminderService.sendReminders())
}

@RestController
@RequestMapping("/api/v1")
class ReportController(private val reportService: com.sparkstock.service.services.ReportService) {

    /** Stock, loan time, use and disuse and non-return alerts; `period` is 30, 90, 180, 365 or all. */
    @GetMapping("/reports")
    fun report(@RequestParam(required = false) period: String?) =
        DataResponse(data = reportService.report(com.sparkstock.service.services.ReportPeriod.parse(period)))
}
