package com.sparkstock.service.controllers

import com.sparkstock.service.auth.AdminOnly
import com.sparkstock.service.dto.*
import com.sparkstock.service.services.CatalogService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class CatalogController(private val catalogService: CatalogService) {

    @GetMapping("/device-types")
    fun types() = DataResponse(data = catalogService.types().map(DeviceTypeDTO::of))

    @AdminOnly
    @PostMapping("/device-types")
    fun createType(@RequestBody request: SaveDeviceTypeDTO) = DataResponse(data = DeviceTypeDTO.of(catalogService.createType(request)))

    @AdminOnly
    @PutMapping("/device-types/{id}")
    fun updateType(@PathVariable id: Long, @RequestBody request: SaveDeviceTypeDTO) =
        DataResponse(data = DeviceTypeDTO.of(catalogService.updateType(id, request)))

    @GetMapping("/locations")
    fun locations() = DataResponse(data = catalogService.locations().map(LocationDTO::of))

    @AdminOnly
    @PostMapping("/locations")
    fun createLocation(@RequestBody request: SaveLocationDTO) = DataResponse(data = LocationDTO.of(catalogService.createLocation(request)))

    @AdminOnly
    @PutMapping("/locations/{id}")
    fun updateLocation(@PathVariable id: Long, @RequestBody request: SaveLocationDTO) =
        DataResponse(data = LocationDTO.of(catalogService.updateLocation(id, request)))
}

@RestController
@RequestMapping("/api/v1")
class ImportController(private val importService: com.sparkstock.service.services.ImportService) {

    /** Previews (`dryRun: true`, the default) or applies an import of devices from a spreadsheet. */
    @AdminOnly
    @PostMapping("/import/devices")
    fun importDevices(@RequestBody request: com.sparkstock.service.services.ImportRequestDTO) =
        DataResponse(data = importService.import(request, com.sparkstock.service.auth.CurrentUser.get()))
}
