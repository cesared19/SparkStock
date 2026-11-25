package com.sparkstock.service.services

import com.sparkstock.service.config.SparkStockProperties
import com.sparkstock.service.models.CoverageTarget
import com.sparkstock.service.models.DeviceStatus
import com.sparkstock.service.repositories.CoverageTargetRepository
import com.sparkstock.service.repositories.DeviceRepository
import com.sparkstock.service.sparkytc.SparkyTCClient
import com.sparkstock.service.sparkytc.SparkyTCDeviceResult
import com.sparkstock.service.sparkytc.SparkyTCProject
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CoverageTargetDTO(val id: Long, val projectCode: String, val model: String, val version: String?) {
    companion object {
        fun of(target: CoverageTarget) = CoverageTargetDTO(target.id, target.projectCode, target.model, target.version)
    }
}

data class SaveCoverageTargetDTO(val projectCode: String = "", val model: String = "", val version: String? = null)

enum class CoverageState { PASSING, FAILING, MISSING }

/** One line of the matrix: a target, or a model and version tested without being a target. */
data class CoverageRowDTO(
    val model: String,
    val version: String?,
    val target: CoverageTargetDTO?,
    val state: CoverageState,
    val passed: Int,
    val failed: Int,
    val blocked: Int,
    val skipped: Int,
    // the devices (asset tags) results were recorded on
    val devices: List<String>,
)

data class CoverageMatrixDTO(
    val projectCode: String,
    val build: String?,
    val rows: List<CoverageRowDTO>,
    val covered: Int,
    val targets: Int,
)

/** A model and version SparkStock has, to suggest targets. */
data class KnownModelDTO(val model: String, val version: String?)

/**
 * The coverage matrix: for a SparkyTC project and build, which target models (and versions) results were recorded
 * on, and how they went. Results come from SparkyTC's system API with the suite's integration token.
 */
@Service
class CoverageService(
    private val sparkyTC: SparkyTCClient,
    private val targetRepository: CoverageTargetRepository,
    private val deviceRepository: DeviceRepository,
    private val properties: SparkStockProperties,
) {
    private fun token(): String = properties.integration.token.ifBlank {
        throw NotConfiguredException("The coverage matrix needs STOCK_INTEGRATION_TOKEN, the same secret as in SparkyTC")
    }

    fun projects(): List<SparkyTCProject> = sparkyTC.projects(token())

    fun targets(projectCode: String) =
        targetRepository.findAllByProjectCodeIgnoreCaseOrderByModelAscVersionAsc(projectCode.trim()).map(CoverageTargetDTO::of)

    @Transactional
    fun addTarget(request: SaveCoverageTargetDTO): CoverageTargetDTO {
        val project = request.projectCode.trim().uppercase()
        val model = request.model.trim()
        val version = request.version?.trim()?.takeIf(String::isNotEmpty)
        require(project.isNotEmpty()) { "Choose a project" }
        require(model.isNotEmpty() && model.length <= 200) { "Enter the model, e.g. Focus 3" }
        require(version == null || version.length <= 200) { "The version is too long" }
        val existing = targetRepository.findAllByProjectCodeIgnoreCaseOrderByModelAscVersionAsc(project)
        if (existing.any { it.model.equals(model, true) && (it.version ?: "").equals(version ?: "", true) }) {
            throw ConflictException("$model${version?.let { " $it" } ?: ""} is already a target of $project")
        }
        return CoverageTargetDTO.of(targetRepository.save(CoverageTarget(projectCode = project, model = model, version = version)))
    }

    @Transactional
    fun deleteTarget(id: Long) {
        val target = targetRepository.findById(id).orElseThrow { NotFoundException("Target not found") }
        targetRepository.delete(target)
    }

    /** Models and versions of the devices in stock, to pick targets from. */
    fun knownModels(): List<KnownModelDTO> = deviceRepository.findAllByOrderByAssetTagAsc()
        .filter { it.status != DeviceStatus.RETIRED && it.type.coverageField != null }
        .map { KnownModelDTO(it.model, it.coverageVersion) }
        .distinctBy { it.model.lowercase() to it.version?.lowercase() }
        .sortedWith(compareBy({ it.model.lowercase() }, { it.version }))

    fun matrix(projectCode: String, build: String?): CoverageMatrixDTO {
        val code = projectCode.trim().uppercase()
        val targets = targetRepository.findAllByProjectCodeIgnoreCaseOrderByModelAscVersionAsc(code)
        val results = sparkyTC.deviceResults(token(), code, build?.trim()?.takeIf(String::isNotEmpty))
        val devices = results?.devices.orEmpty()

        fun matches(target: CoverageTarget, device: SparkyTCDeviceResult) =
            device.model.equals(target.model, ignoreCase = true) &&
                (target.version == null || device.version.equals(target.version, ignoreCase = true))

        val targetRows = targets.map { target -> row(target.model, target.version, CoverageTargetDTO.of(target), devices.filter { matches(target, it) }) }
        val untargeted = devices.filter { device -> targets.none { matches(it, device) } }
            .groupBy { (it.model ?: it.name ?: it.ref).lowercase() to it.version?.lowercase() }
            .map { (_, group) -> row(group.first().model ?: group.first().name ?: group.first().ref, group.first().version, null, group) }
            .sortedWith(compareBy({ it.model.lowercase() }, { it.version }))

        return CoverageMatrixDTO(
            projectCode = code,
            build = results?.build,
            rows = targetRows + untargeted,
            covered = targetRows.count { it.state != CoverageState.MISSING },
            targets = targetRows.size,
        )
    }

    private fun row(model: String, version: String?, target: CoverageTargetDTO?, devices: List<SparkyTCDeviceResult>): CoverageRowDTO {
        val failed = devices.sumOf { it.failed }
        val blocked = devices.sumOf { it.blocked }
        val state = when {
            devices.isEmpty() -> CoverageState.MISSING
            failed + blocked > 0 -> CoverageState.FAILING
            else -> CoverageState.PASSING
        }
        return CoverageRowDTO(
            model = model, version = version, target = target, state = state,
            passed = devices.sumOf { it.passed }, failed = failed, blocked = blocked, skipped = devices.sumOf { it.skipped },
            devices = devices.map { it.ref }.distinct().sorted(),
        )
    }
}
