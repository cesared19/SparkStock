package com.sparkstock.service.services

import com.sparkstock.service.dto.SaveDeviceTypeDTO
import com.sparkstock.service.dto.SaveLocationDTO
import com.sparkstock.service.models.DeviceType
import com.sparkstock.service.models.Location
import com.sparkstock.service.repositories.DeviceTypeRepository
import com.sparkstock.service.repositories.LocationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Device types (with their extra fields) and locations, managed by admins. */
@Service
class CatalogService(
    private val typeRepository: DeviceTypeRepository,
    private val locationRepository: LocationRepository,
) {
    private val prefixPattern = Regex("^[A-Z][A-Z0-9]{0,7}$")

    fun types(): List<DeviceType> = typeRepository.findAllByOrderByNameAsc()

    fun type(id: Long): DeviceType = typeRepository.findById(id).orElseThrow { NotFoundException("Device type not found") }

    @Transactional
    fun createType(request: SaveDeviceTypeDTO): DeviceType {
        val type = DeviceType(name = "", tagPrefix = "", usage = request.usage)
        apply(type, request)
        return typeRepository.save(type)
    }

    @Transactional
    fun updateType(id: Long, request: SaveDeviceTypeDTO): DeviceType {
        val type = type(id)
        apply(type, request)
        return typeRepository.save(type)
    }

    private fun apply(type: DeviceType, request: SaveDeviceTypeDTO) {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Enter a name" }
        typeRepository.findByNameIgnoreCase(name)?.takeIf { it.id != type.id }?.let {
            throw ConflictException("There is already a device type called ${it.name}")
        }
        val prefix = request.tagPrefix.trim().uppercase()
        require(prefixPattern.matches(prefix)) { "The tag prefix must be 1 to 8 letters or digits, starting with a letter" }
        typeRepository.findAll().firstOrNull { it.id != type.id && it.tagPrefix.equals(prefix, ignoreCase = true) }?.let {
            throw ConflictException("${it.name} already uses the prefix $prefix")
        }
        val fields = FieldValues.validateDefinitions(request.fields)
        val coverageField = request.coverageField?.trim()?.takeIf(String::isNotEmpty)
        require(coverageField == null || fields.any { it.key == coverageField }) { "The coverage field must be one of the type's fields" }

        type.name = name
        type.tagPrefix = prefix
        type.usage = request.usage
        type.fields = fields
        type.coverageField = coverageField
        request.isArchived?.let { type.isArchived = it }
    }

    fun locations(): List<Location> = locationRepository.findAllByOrderByNameAsc()

    fun location(id: Long): Location = locationRepository.findById(id).orElseThrow { NotFoundException("Location not found") }

    @Transactional
    fun createLocation(request: SaveLocationDTO): Location = locationRepository.save(applyLocation(Location(name = ""), request))

    @Transactional
    fun updateLocation(id: Long, request: SaveLocationDTO): Location = locationRepository.save(applyLocation(location(id), request))

    private fun applyLocation(location: Location, request: SaveLocationDTO): Location {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Enter a name" }
        locationRepository.findByNameIgnoreCase(name)?.takeIf { it.id != location.id }?.let {
            throw ConflictException("There is already a location called ${it.name}")
        }
        location.name = name
        request.isArchived?.let { location.isArchived = it }
        return location
    }
}
