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
class DeviceController(private val deviceService: DeviceService) {

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

    private fun detail(device: Device) =
        DataResponse(data = DeviceDetailDTO(DeviceDTO.of(device), deviceService.events(device).map(DeviceEventDTO::of)))
}

@RestController
@RequestMapping("/api/v1")
class ReminderController(private val reminderService: com.sparkstock.service.services.ReminderService) {

    /** Sends today's reminders now, instead of waiting for the daily run (it never sends one twice). */
    @AdminOnly
    @PostMapping("/reminders/send")
    fun send() = DataResponse(data = reminderService.sendReminders())
}
