package ru.itmo.carsharing.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.PathVariable
import ru.itmo.carsharing.common.web.ApiErrors

/**
 * Swagger описывает не только успешный ответ, но и ошибки в формате RFC 9457:
 * 400 есть у всех операций, 404 у операций с идентификатором в пути, остальное задаёт @ApiErrors.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Carsharing API")
                .version("v1")
                .description(
                    "Каршеринг: пользователи и ВУ, парк и обслуживание, тарифы, аренды, штрафы, счета. " +
                        "Лабораторная работа 1 по курсу \"Высокопроизводительные системы\". " +
                        "Ошибки возвращаются в формате RFC 9457 (application/problem+json).",
                ),
        )

    @Bean
    fun problemSchemaCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val components = openApi.components ?: Components().also { openApi.components = it }
        if (components.schemas?.containsKey(PROBLEM_SCHEMA) !=
            true
        ) {
            components.addSchemas(PROBLEM_SCHEMA, problemSchema())
        }
    }

    @Bean
    fun errorResponsesCustomizer(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        val hasPathId = handlerMethod.methodParameters.any { it.hasParameterAnnotation(PathVariable::class.java) }
        val declared = handlerMethod.getMethodAnnotation(ApiErrors::class.java)?.value?.toList().orEmpty()
        val codes = (listOf(BAD_REQUEST) + declared + if (hasPathId) listOf(NOT_FOUND) else emptyList()).toSortedSet()
        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
        codes.forEach { code ->
            responses.putIfAbsent(code.toString(), problemResponse(ERROR_DESCRIPTIONS[code] ?: "Ошибка"))
        }
        operation
    }

    private fun problemResponse(description: String): ApiResponse = ApiResponse()
        .description(description)
        .content(
            Content().addMediaType(
                "application/problem+json",
                MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$PROBLEM_SCHEMA")),
            ),
        )

    private fun problemSchema(): Schema<Any> {
        val violation = ObjectSchema()
            .addProperty("field", StringSchema().example("size"))
            .addProperty("message", StringSchema().example("должно быть не больше 50"))
        @Suppress("UNCHECKED_CAST")
        return ObjectSchema()
            .description("Ошибка по RFC 9457")
            .addProperty("type", StringSchema().example("https://carsharing.local/errors/vehicle-not-available"))
            .addProperty("title", StringSchema().example("Vehicle is not available"))
            .addProperty("status", IntegerSchema().example(409))
            .addProperty("detail", StringSchema().example("Автомобиль А123ВС777 уже забронирован или недоступен"))
            .addProperty("instance", StringSchema().example("/api/v1/rentals"))
            .addProperty("errors", ArraySchema().items(violation)) as Schema<Any>
    }

    private companion object {
        const val PROBLEM_SCHEMA = "Problem"
        const val BAD_REQUEST = 400
        const val NOT_FOUND = 404

        val ERROR_DESCRIPTIONS = mapOf(
            400 to "Поля или параметры не прошли проверку, тело не разобрано",
            404 to "Ресурс не найден",
            409 to "Конфликт состояния: ресурс занят, неверный статус, дубликат, гонка",
            422 to "Нарушено бизнес-правило: нет денег, ВУ, тариф, зона финиша и т. п.",
        )
    }
}
