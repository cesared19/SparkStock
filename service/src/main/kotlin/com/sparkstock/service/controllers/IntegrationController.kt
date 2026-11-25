package com.sparkstock.service.controllers

import com.sparkstock.service.dto.DataResponse
import com.sparkstock.service.models.Device
import com.sparkstock.service.models.DeviceStatus
import com.sparkstock.service.models.DeviceUsage
import com.sparkstock.service.services.DeviceFilter
import com.sparkstock.service.services.DeviceService
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * A device as other apps of the suite see it. `ref` (the asset tag) is what SparkyTC stores with a result,
 * together with a copy of the name, model and version, so the result still reads well if the device changes later.
 */
data class IntegrationDeviceDTO(
    val ref: String,
    val id: UUID,
    val name: String,
    val manufacturer: String?,
    val model: String,
    val type: String,
    val usage: DeviceUsage,
    val version: String?,
    val status: DeviceStatus,
    val holderUserID: UUID?,
    val holderName: String?,
    val dueAt: Instant?,
) {
    companion object {
        fun of(device: Device) = IntegrationDeviceDTO(
            ref = device.assetTag, id = device.id, name = device.name, manufacturer = device.manufacturer, model = device.model,
            type = device.type.name, usage = device.type.usage, version = device.coverageVersion, status = device.status,
            holderUserID = device.holder?.id, holderName = device.holder?.displayName, dueAt = device.dueAt,
        )
    }
}

/** For SparkyTC (and other apps of the suite), with the shared integration token. Read only. */
@RestController
@RequestMapping("/api/integration/v1")
class IntegrationController(private val deviceService: DeviceService) {

    /**
     * Devices to choose from when recording a result: `holderUserID` (a SparkyTC user ID) lists what that person
     * has; `usage=POOL` keeps test devices only. Retired devices are never listed.
     */
    @GetMapping("/devices")
    fun devices(
        @RequestParam(required = false) holderUserID: UUID?,
        @RequestParam(required = false) usage: DeviceUsage?,
        @RequestParam(required = false) q: String?,
    ): DataResponse<List<IntegrationDeviceDTO>> {
        val devices = deviceService.list(DeviceFilter(query = q, holderId = holderUserID))
            .filter { usage == null || it.type.usage == usage }
        return DataResponse(data = devices.map(IntegrationDeviceDTO::of))
    }

    @GetMapping("/devices/{ref}")
    fun device(@PathVariable ref: String) = DataResponse(data = IntegrationDeviceDTO.of(deviceService.getByTag(ref)))
}
