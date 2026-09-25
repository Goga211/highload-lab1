package ru.itmo.carsharing.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Часы, которыми тест управляет вручную: бронь, ожидание и поездка считаются детерминированно. */
class MutableClock(
    @Volatile private var current: Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    fun reset() {
        current = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TestInfrastructure {

    /** Чистый PostgreSQL 16 на каждый прогон, все миграции Liquibase применяются с нуля. */
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:16-alpine")

    @Bean
    @Primary
    fun mutableClock(): MutableClock = MutableClock()
}
