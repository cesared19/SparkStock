package com.sparkstock.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SparkStockApplication

fun main(args: Array<String>) {
    runApplication<SparkStockApplication>(*args)
}
