package com.sparkstock.service.services

import com.sparkstock.service.config.SparkStockProperties
import com.sparkstock.service.models.Device
import com.sparkstock.service.models.DeviceStatus
import com.sparkstock.service.models.UserRole
import com.sparkstock.service.repositories.DeviceRepository
import com.sparkstock.service.repositories.UserRepository
import com.sparkstock.service.sparkytc.SparkyTCClient
import com.sparkstock.service.sparkytc.SparkyTCNotification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReminderRunDTO(val sent: Int, val alreadySent: Int, val failed: Int, val skipped: String? = null)

/**
 * Reminders in SparkyTC's bell, once a day (`sparkstock.reminders.cron`, 9:00 by default):
 * - the borrower of a loan due within a day, once per loan;
 * - the borrower of an overdue loan, every day until it's returned;
 * - every admin, once per loan overdue for LOAN_ESCALATE_AFTER_DAYS or more;
 * - every admin, once per device whose warranty ends within WARRANTY_WARNING_DAYS.
 * Each comes in the language the person last used SparkStock in.
 * Each has a sourceRef, so sending again (a restart, the button for admins) never duplicates them.
 * Off while STOCK_INTEGRATION_TOKEN is empty.
 */
@Service
class ReminderService(
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
    private val sparkyTC: SparkyTCClient,
    private val properties: SparkStockProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${sparkstock.reminders.cron:0 0 9 * * *}")
    fun scheduled() {
        if (!properties.reminders.enabled) return
        val result = sendReminders()
        log.info("Reminders: {} sent, {} already sent, {} failed{}", result.sent, result.alreadySent, result.failed,
            result.skipped?.let { " ($it)" } ?: "")
    }

    fun sendReminders(now: Instant = Instant.now()): ReminderRunDTO {
        val token = properties.integration.token
        if (token.isBlank()) return ReminderRunDTO(0, 0, 0, skipped = Messages.t("reminder.noToken"))

        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(now, zone)
        fun day(lang: Lang, date: LocalDate) = DateTimeFormatter.ofPattern(Messages.t(lang, "date.pattern"), lang.locale).format(date)
        val devices = deviceRepository.findAllByOrderByAssetTagAsc()
        val admins = userRepository.findAll().filter { it.role == UserRole.ADMIN && it.isActive }
        val notifications = mutableListOf<SparkyTCNotification>()

        for (device in devices.filter { it.status == DeviceStatus.ON_LOAN && it.holder?.isActive == true && it.dueAt != null }) {
            val holder = device.holder!!
            val lang = holder.language
            val due = device.dueAt!!
            val dueDate = LocalDate.ofInstant(due, zone)
            val loan = "${device.assetTag}:${device.heldSince?.epochSecond ?: 0}"
            if (due.isBefore(now)) {
                notifications += SparkyTCNotification(
                    userID = holder.id,
                    title = Messages.t(lang, "reminder.overdue.title", device.assetTag, day(lang, dueDate)),
                    message = Messages.t(lang, "reminder.overdue.message", device.name),
                    link = link(device),
                    sourceRef = "sparkstock:overdue:$loan:$today",
                )
                // long overdue: the admins find out too, once per loan
                val daysLate = Duration.between(due, now).toDays()
                if (daysLate >= properties.loans.escalateAfterDays) {
                    for (admin in admins.filter { it.id != holder.id }) {
                        val adminLang = admin.language
                        notifications += SparkyTCNotification(
                            userID = admin.id,
                            title = Messages.t(adminLang, "reminder.escalation.title", device.assetTag, daysLate),
                            message = Messages.t(adminLang, "reminder.escalation.message", holder.displayName, device.name,
                                day(adminLang, LocalDate.ofInstant(device.heldSince ?: due, zone)), day(adminLang, dueDate)),
                            link = link(device),
                            sourceRef = "sparkstock:escalation:$loan",
                        )
                    }
                }
            } else if (due.isBefore(now.plus(Duration.ofDays(1)))) {
                notifications += SparkyTCNotification(
                    userID = holder.id,
                    title = Messages.t(lang, "reminder.dueSoon.title", device.assetTag, day(lang, dueDate)),
                    message = Messages.t(lang, "reminder.dueSoon.message", device.name),
                    link = link(device),
                    sourceRef = "sparkstock:due-soon:$loan",
                )
            }
        }

        val warrantyUntil = today.plusDays(properties.warranty.warningDays)
        for (device in devices.filter { it.status != DeviceStatus.RETIRED && it.warrantyEnd != null && !it.warrantyEnd!!.isAfter(warrantyUntil) && !it.warrantyEnd!!.isBefore(today) }) {
            val end = device.warrantyEnd!!
            for (admin in admins) {
                val lang = admin.language
                notifications += SparkyTCNotification(
                    userID = admin.id,
                    title = Messages.t(lang, "reminder.warranty.title", device.assetTag, day(lang, end)),
                    message = Messages.t(lang, "reminder.warranty.message", device.name + (device.serialNumber?.let { " (SN $it)" } ?: "")),
                    link = link(device),
                    sourceRef = "sparkstock:warranty:${device.assetTag}:$end",
                )
            }
        }

        var sent = 0
        var alreadySent = 0
        var failed = 0
        for (notification in notifications) {
            try {
                if (sparkyTC.notify(token, notification)) sent++ else alreadySent++
            } catch (ex: Exception) {
                failed++
                log.warn("Couldn't send the reminder {} to SparkyTC: {}", notification.sourceRef, ex.message)
            }
        }
        return ReminderRunDTO(sent, alreadySent, failed)
    }

    private fun link(device: Device): String? =
        properties.publicUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/t/${device.assetTag}" }
}
