package com.sparkstock.service.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sparkstock")
data class SparkStockProperties(
    val sparkytc: SparkyTC = SparkyTC(),
    val integration: Integration = Integration(),
    val loans: Loans = Loans(),
    val warranty: Warranty = Warranty(),
    val reminders: Reminders = Reminders(),
    /** The address people open SparkStock on, for links in reminders (PUBLIC_URL). */
    val publicUrl: String = "",
) {
    data class SparkyTC(
        val baseUrl: String = "http://backend-service:8080",
        val publicUrl: String = "",
        val sessionCacheSeconds: Long = 300,
    )

    data class Integration(val token: String = "")

    data class Loans(val defaultDays: Long = 7, val maxDays: Long = 90)

    data class Warranty(val warningDays: Long = 30)

    /** Daily reminders sent to SparkyTC's bell; `cron` in the server's time zone (TZ). */
    data class Reminders(val enabled: Boolean = true, val cron: String = "0 0 9 * * *")
}
