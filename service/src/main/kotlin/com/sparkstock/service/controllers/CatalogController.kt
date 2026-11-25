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
