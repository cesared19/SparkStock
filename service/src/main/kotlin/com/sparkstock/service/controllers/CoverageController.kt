package com.sparkstock.service.controllers

import com.sparkstock.service.auth.AdminOnly
import com.sparkstock.service.dto.DataResponse
import com.sparkstock.service.services.CoverageService
import com.sparkstock.service.services.SaveCoverageTargetDTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/coverage")
class CoverageController(private val coverageService: CoverageService) {

    /** SparkyTC's projects with their builds, and the device models in stock to pick targets from. */
    @GetMapping("/projects")
    fun projects() = DataResponse(data = mapOf(
        "projects" to coverageService.projects(),
        "knownModels" to coverageService.knownModels(),
    ))

    @GetMapping
    fun matrix(@RequestParam project: String, @RequestParam(required = false) build: String?) =
        DataResponse(data = coverageService.matrix(project, build))

    @AdminOnly
    @PostMapping("/targets")
    fun addTarget(@RequestBody request: SaveCoverageTargetDTO) = DataResponse(data = coverageService.addTarget(request))

    @AdminOnly
    @DeleteMapping("/targets/{id}")
    fun deleteTarget(@PathVariable id: Long): DataResponse<Boolean> {
        coverageService.deleteTarget(id)
        return DataResponse(data = true)
    }
}
