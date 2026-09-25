package ru.itmo.carsharing.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import jakarta.validation.ClockProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    /** Все бизнес-отметки времени берутся из Clock, чтобы тесты могли управлять временем. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** @Past, @Future и @MinAge сверяются с теми же часами, что и бизнес-логика. */
    @Bean
    fun validationClock(clock: Clock): ValidationConfigurationCustomizer =
        ValidationConfigurationCustomizer { configuration -> configuration.clockProvider(ClockProvider { clock }) }

    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Carsharing API")
            .version("v1")
            .description(
                "Каршеринг: пользователи и ВУ, парк и обслуживание, тарифы, аренды, штрафы, счета. " +
                    "Лабораторная работа 1 по курсу \"Высокопроизводительные системы\".",
            ),
    )
}

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "carsharing.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig
