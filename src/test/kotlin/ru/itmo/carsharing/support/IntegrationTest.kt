package ru.itmo.carsharing.support

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.json.JsonMapper

/**
 * База интеграционных тестов: весь контекст Spring, PostgreSQL в Testcontainers, запросы через MockMvc.
 * Перед каждым тестом таблицы очищаются, схема остаётся такой, какой её создал Liquibase.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure::class)
abstract class IntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jdbc: JdbcTemplate

    @Autowired
    protected lateinit var clock: MutableClock

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    protected val api: Api by lazy { Api(mockMvc, jsonMapper) }

    protected val fixtures: Fixtures by lazy { Fixtures(api) }

    @BeforeEach
    fun cleanDatabase() {
        clock.reset()
        jdbc.execute(
            """
            TRUNCATE traffic_fine, payment, wallet, rental_option_item, rental, rental_option,
                     tariff_vehicle_model, tariff, maintenance_part, maintenance_task, spare_part_compatibility,
                     spare_part, vehicle, parking_zone, vehicle_model, driver_license, app_user CASCADE
            """.trimIndent(),
        )
    }
}
