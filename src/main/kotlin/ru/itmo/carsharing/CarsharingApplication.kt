package ru.itmo.carsharing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class CarsharingApplication

fun main(args: Array<String>) {
    runApplication<CarsharingApplication>(*args)
}
