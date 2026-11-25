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

    fun type(id: Long): DeviceType = typeRepository.findById(id).orElseThrow { NotFoundException(Messages.t("type.notFound")) }

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
        require(name.isNotEmpty()) { Messages.t("catalog.enterName") }
        typeRepository.findByNameIgnoreCase(name)?.takeIf { it.id != type.id }?.let {
            throw ConflictException(Messages.t("type.nameUsed", it.name))
        }
        val prefix = request.tagPrefix.trim().uppercase()
        require(prefixPattern.matches(prefix)) { Messages.t("type.prefixFormat") }
        typeRepository.findAll().firstOrNull { it.id != type.id && it.tagPrefix.equals(prefix, ignoreCase = true) }?.let {
            throw ConflictException(Messages.t("type.prefixUsed", it.name, prefix))
        }
        val fields = FieldValues.validateDefinitions(request.fields)
        val coverageField = request.coverageField?.trim()?.takeIf(String::isNotEmpty)
        require(coverageField == null || fields.any { it.key == coverageField }) { Messages.t("type.coverageField") }

        type.name = name
        type.tagPrefix = prefix
        type.usage = request.usage
        type.fields = fields
        type.coverageField = coverageField
        request.isArchived?.let { type.isArchived = it }
    }

    fun locations(): List<Location> = locationRepository.findAllByOrderByNameAsc()

    fun location(id: Long): Location = locationRepository.findById(id).orElseThrow { NotFoundException(Messages.t("location.notFound")) }

    @Transactional
    fun createLocation(request: SaveLocationDTO): Location = locationRepository.save(applyLocation(Location(name = ""), request))

    @Transactional
    fun updateLocation(id: Long, request: SaveLocationDTO): Location = locationRepository.save(applyLocation(location(id), request))

    private fun applyLocation(location: Location, request: SaveLocationDTO): Location {
        val name = request.name.trim()
        require(name.isNotEmpty()) { Messages.t("catalog.enterName") }
        locationRepository.findByNameIgnoreCase(name)?.takeIf { it.id != location.id }?.let {
            throw ConflictException(Messages.t("location.nameUsed", it.name))
        }
        location.name = name
        request.isArchived?.let { location.isArchived = it }
        return location
    }
}
