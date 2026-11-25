package com.sparkstock.service.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sparkstock")
data class SparkStockProperties(
    val sparkytc: SparkyTC = SparkyTC(),
    val integration: Integration = Integration(),
    val loans: Loans = Loans(),
    val warranty: Warranty = Warranty(),
) {
    data class SparkyTC(
        val baseUrl: String = "http://backend-service:8080",
        val publicUrl: String = "",
        val sessionCacheSeconds: Long = 300,
    )

    data class Integration(val token: String = "")

    data class Loans(val defaultDays: Long = 7, val maxDays: Long = 90)

    data class Warranty(val warningDays: Long = 30)
}
