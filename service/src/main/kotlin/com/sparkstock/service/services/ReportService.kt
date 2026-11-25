package com.sparkstock.service.services

import com.sparkstock.service.config.SparkStockProperties
import com.sparkstock.service.dto.PersonDTO
import com.sparkstock.service.models.*
import com.sparkstock.service.repositories.DeviceEventRepository
import com.sparkstock.service.repositories.DeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The periods of the reports, like ProDash's: 30 days, 90 days, 6 months, 1 year or everything. */
enum class ReportPeriod(val days: Long?) {
    D30(30), D90(90), D180(180), D365(365), ALL(null);

    companion object {
        fun parse(value: String?): ReportPeriod = when (value?.trim()?.lowercase()) {
            null, "", "30" -> D30
            "90" -> D90
            "180" -> D180
            "365" -> D365
            "all" -> ALL
            else -> throw IllegalArgumentException(Messages.t("report.period"))
        }
    }
}

data class DeviceRefDTO(val id: UUID, val assetTag: String, val name: String, val model: String, val type: String) {
    companion object {
        fun of(device: Device) = DeviceRefDTO(device.id, device.assetTag, device.name, device.model, device.type.name)
    }
}

data class TypeStockDTO(val typeId: Long, val name: String, val usage: DeviceUsage, val total: Int, val byStatus: Map<DeviceStatus, Int>)

data class LocationStockDTO(val name: String?, val count: Int)

data class StockReportDTO(
    // everything but retired devices
    val total: Int,
    val retired: Int,
    val byStatus: Map<DeviceStatus, Int>,
    val byType: List<TypeStockDTO>,
    val byLocation: List<LocationStockDTO>,
)

data class DeviceLoanTimeDTO(val device: DeviceRefDTO, val loans: Int, val hours: Double)

data class PersonLoanTimeDTO(val person: PersonDTO, val loans: Int, val hours: Double, val returnedLate: Int)

data class LongestLoanDTO(val device: DeviceRefDTO, val person: PersonDTO?, val hours: Double, val active: Boolean)

data class LoanReportDTO(
    // loans that started in the period
    val started: Int,
    // loans returned in the period, and how many of them after their return date
    val returned: Int,
    val returnedLate: Int,
    val activeNow: Int,
    // time on loan within the period, adding every device
    val totalHours: Double,
    // how long the loans returned in the period lasted
    val averageHours: Double?,
    val medianHours: Double?,
    // the average loan length people asked for (check-out to return date)
    val averagePlannedHours: Double?,
    val longest: LongestLoanDTO?,
    val byDevice: List<DeviceLoanTimeDTO>,
    val byPerson: List<PersonLoanTimeDTO>,
)

data class DeviceUsageDTO(
    val device: DeviceRefDTO,
    // share of the period the device was on loan, 0 to 1
    val utilization: Double,
    val hours: Double,
    val loans: Int,
    val lastLoanAt: Instant?,
    val unused: Boolean,
    val status: DeviceStatus,
)

data class UsageReportDTO(
    // lent devices (POOL types) that aren't retired
    val devices: List<DeviceUsageDTO>,
    val averageUtilization: Double?,
    val used: Int,
    val unused: Int,
)

enum class AlertLevel { ATTENTION, OVERDUE, VERY_OVERDUE }

data class LoanAlertDTO(
    val device: DeviceRefDTO,
    val holder: PersonDTO?,
    val level: AlertLevel,
    val heldSince: Instant,
    val dueAt: Instant,
    val heldHours: Double,
    val plannedHours: Double,
    // how many times the planned length it has been on loan
    val ratio: Double,
    val overdueHours: Double,
)

data class AlertReportDTO(
    val alerts: List<LoanAlertDTO>,
    val attention: Int,
    val overdue: Int,
    val veryOverdue: Int,
    // overdue loans older than this are also sent to the admins' bell
    val escalateAfterDays: Long,
)

data class ReportDTO(
    val period: ReportPeriod,
    val from: Instant,
    val to: Instant,
    val stock: StockReportDTO,
    val loans: LoanReportDTO,
    val usage: UsageReportDTO,
    val alerts: AlertReportDTO,
)

/** A loan rebuilt from the device's history: from its check-out to its return (or now). */
data class LoanInterval(
    val device: Device,
    val holder: User?,
    val start: Instant,
    val end: Instant?,
    val dueAt: Instant?,
    val returnedLate: Boolean,
) {
    fun endOrNow(now: Instant) = end ?: now

    /** Whether the loan falls in the period at all, even if it just started. */
    fun touches(from: Instant, now: Instant) = !start.isAfter(now) && !endOrNow(now).isBefore(from)
    fun overlapHours(from: Instant, to: Instant, now: Instant): Double {
        val s = maxOf(start, from)
        val e = minOf(endOrNow(now), to)
        return if (e.isAfter(s)) Duration.between(s, e).toMinutes() / 60.0 else 0.0
    }
}

/**
 * The reports: stock now, and for a period the time on loan, the use and disuse of each lent device and the loans
 * not returned on time. Loans are rebuilt from device_events (check-out to check-in, or to the status change that
 * ended the loan), so they also cover the time before the reports existed.
 */
@Service
class ReportService(
    private val deviceRepository: DeviceRepository,
    private val eventRepository: DeviceEventRepository,
    private val properties: SparkStockProperties,
) {
    @Transactional(readOnly = true)
    fun report(period: ReportPeriod, now: Instant = Instant.now()): ReportDTO {
        val devices = deviceRepository.findAllByOrderByAssetTagAsc()
        val loans = loans(devices)
        val from = period.days?.let { now.minus(Duration.ofDays(it)) }
            ?: (devices.map { it.createdAt } + loans.map { it.start }).minOrNull()?.coerceAtMost(now) ?: now
        return ReportDTO(
            period = period, from = from, to = now,
            stock = stock(devices),
            loans = loanReport(loans, from, now),
            usage = usage(devices, loans, from, now),
            alerts = alerts(devices, now),
        )
    }

    fun loans(devices: List<Device>): List<LoanInterval> {
        val byId = devices.associateBy { it.id }
        val result = mutableListOf<LoanInterval>()
        eventRepository.findAll()
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .groupBy { it.deviceId }
            .forEach { (deviceId, events) ->
                val device = byId[deviceId] ?: return@forEach
                var open: DeviceEvent? = null
                for (event in events) {
                    val endsLoan = event.kind == DeviceEventKind.CHECKED_IN ||
                        (event.kind == DeviceEventKind.STATUS_CHANGED && event.fromStatus == DeviceStatus.ON_LOAN)
                    if (event.kind == DeviceEventKind.CHECKED_OUT) {
                        open = event
                    } else if (endsLoan && open != null) {
                        result += interval(device, open, event)
                        open = null
                    }
                }
                open?.let { result += interval(device, it, null) }
            }
        return result
    }

    private fun interval(device: Device, start: DeviceEvent, end: DeviceEvent?) = LoanInterval(
        device = device,
        holder = start.holder,
        start = start.createdAt,
        end = end?.createdAt,
        dueAt = (start.details?.get("dueAt") as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() },
        returnedLate = end?.details?.get("overdue") == true,
    )

    private fun stock(devices: List<Device>): StockReportDTO {
        val active = devices.filter { it.status != DeviceStatus.RETIRED }
        return StockReportDTO(
            total = active.size,
            retired = devices.size - active.size,
            byStatus = DeviceStatus.entries.filter { it != DeviceStatus.RETIRED }.associateWith { s -> active.count { it.status == s } },
            byType = active.groupBy { it.type.id }.values.map { group ->
                val type = group.first().type
                TypeStockDTO(type.id, type.name, type.usage, group.size,
                    DeviceStatus.entries.filter { it != DeviceStatus.RETIRED }.associateWith { s -> group.count { it.status == s } })
            }.sortedByDescending { it.total },
            byLocation = active.groupBy { it.location?.name }.map { (name, group) -> LocationStockDTO(name, group.size) }
                .sortedByDescending { it.count },
        )
    }

    private fun loanReport(loans: List<LoanInterval>, from: Instant, now: Instant): LoanReportDTO {
        val inPeriod = loans.filter { it.touches(from, now) }
        val returned = loans.filter { it.end != null && it.end >= from }
        val durations = returned.map { Duration.between(it.start, it.end).toMinutes() / 60.0 }.sorted()
        val planned = loans.filter { it.start >= from && it.dueAt != null }.map { Duration.between(it.start, it.dueAt).toMinutes() / 60.0 }
        val longest = inPeriod.maxByOrNull { Duration.between(it.start, it.endOrNow(now)).toMinutes() }

        return LoanReportDTO(
            started = loans.count { it.start >= from },
            returned = returned.size,
            returnedLate = returned.count { it.returnedLate },
            activeNow = loans.count { it.end == null },
            totalHours = round(inPeriod.sumOf { it.overlapHours(from, now, now) }),
            averageHours = durations.takeIf { it.isNotEmpty() }?.average()?.let(::round),
            medianHours = durations.takeIf { it.isNotEmpty() }?.let { round(if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2) },
            averagePlannedHours = planned.takeIf { it.isNotEmpty() }?.average()?.let(::round),
            longest = longest?.let {
                LongestLoanDTO(DeviceRefDTO.of(it.device), PersonDTO.of(it.holder), round(Duration.between(it.start, it.endOrNow(now)).toMinutes() / 60.0), it.end == null)
            },
            byDevice = inPeriod.groupBy { it.device.id }.values.map { group ->
                DeviceLoanTimeDTO(DeviceRefDTO.of(group.first().device), group.size, round(group.sumOf { it.overlapHours(from, now, now) }))
            }.sortedByDescending { it.hours },
            byPerson = inPeriod.filter { it.holder != null }.groupBy { it.holder!!.id }.values.map { group ->
                PersonLoanTimeDTO(PersonDTO.of(group.first().holder)!!, group.size, round(group.sumOf { it.overlapHours(from, now, now) }), group.count { it.returnedLate })
            }.sortedByDescending { it.hours },
        )
    }

    private fun usage(devices: List<Device>, loans: List<LoanInterval>, from: Instant, now: Instant): UsageReportDTO {
        val lent = devices.filter { it.type.usage == DeviceUsage.POOL && it.status != DeviceStatus.RETIRED }
        val byDevice = loans.groupBy { it.device.id }
        val rows = lent.map { device ->
            val deviceLoans = byDevice[device.id].orEmpty()
            // a device added during the period is measured from the day it arrived
            val start = maxOf(from, device.createdAt)
            val available = Duration.between(start, now).toMinutes() / 60.0
            val hours = deviceLoans.sumOf { it.overlapHours(from, now, now) }
            val loansInPeriod = deviceLoans.count { it.touches(from, now) }
            DeviceUsageDTO(
                device = DeviceRefDTO.of(device),
                utilization = if (available > 0) round(minOf(1.0, hours / available), 3) else 0.0,
                hours = round(hours),
                loans = loansInPeriod,
                lastLoanAt = deviceLoans.maxOfOrNull { it.start },
                unused = loansInPeriod == 0,
                status = device.status,
            )
        }.sortedWith(compareByDescending<DeviceUsageDTO> { it.utilization }.thenBy { it.device.assetTag })
        return UsageReportDTO(
            devices = rows,
            averageUtilization = rows.takeIf { it.isNotEmpty() }?.map { it.utilization }?.average()?.let { round(it, 3) },
            used = rows.count { !it.unused },
            unused = rows.count { it.unused },
        )
    }

    /**
     * Loans to keep an eye on: due today or tomorrow (attention), past their return date (overdue), and on loan more
     * than twice as long as asked for (very overdue), worst first.
     */
    fun alerts(devices: List<Device>, now: Instant): AlertReportDTO {
        val alerts = devices.filter { it.status == DeviceStatus.ON_LOAN && it.dueAt != null && it.heldSince != null }.mapNotNull { device ->
            val due = device.dueAt!!
            val since = device.heldSince!!
            val held = Duration.between(since, now).toMinutes() / 60.0
            val planned = maxOf(Duration.between(since, due).toMinutes() / 60.0, 1.0)
            val level = when {
                due.isBefore(now) && held > planned * 2 -> AlertLevel.VERY_OVERDUE
                due.isBefore(now) -> AlertLevel.OVERDUE
                due.isBefore(now.plus(Duration.ofDays(1))) -> AlertLevel.ATTENTION
                else -> return@mapNotNull null
            }
            LoanAlertDTO(
                device = DeviceRefDTO.of(device), holder = PersonDTO.of(device.holder), level = level,
                heldSince = since, dueAt = due, heldHours = round(held), plannedHours = round(planned),
                ratio = round(held / planned, 2),
                overdueHours = round(maxOf(0.0, Duration.between(due, now).toMinutes() / 60.0)),
            )
        }.sortedWith(compareByDescending<LoanAlertDTO> { it.level.ordinal }.thenByDescending { it.overdueHours }.thenBy { it.dueAt })
        return AlertReportDTO(
            alerts = alerts,
            attention = alerts.count { it.level == AlertLevel.ATTENTION },
            overdue = alerts.count { it.level == AlertLevel.OVERDUE },
            veryOverdue = alerts.count { it.level == AlertLevel.VERY_OVERDUE },
            escalateAfterDays = properties.loans.escalateAfterDays,
        )
    }

    private fun round(value: Double, decimals: Int = 1): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return Math.round(value * factor) / factor
    }
}
