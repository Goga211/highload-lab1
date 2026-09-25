package ru.itmo.carsharing.common

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import ru.itmo.carsharing.support.IntegrationTest

/** Swagger совпадает с кодом, а ошибки уровня Spring MVC приходят в том же формате, что и наши. */
class ApiDocsAndErrorsIT : IntegrationTest() {

    private val docs: String by lazy {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/v3/api-docs"),
        ).andReturn().response.getContentAsString(Charsets.UTF_8)
    }

    private fun codes(path: String, method: String): Set<String> =
        JsonPath.read<Map<String, Any>>(docs, "$.paths['$path'].$method.responses").keys

    @Test
    fun `openapi documents success codes of the real handlers`() {
        assertThat(codes("/api/v1/users", "post")).contains("201").doesNotContain("200")
        assertThat(codes("/api/v1/vehicle-models/{id}", "delete")).contains("204").doesNotContain("200")
        assertThat(codes("/api/v1/vehicles", "get")).contains("200")
    }

    @Test
    fun `openapi documents error responses in problem details format`() {
        assertThat(codes("/api/v1/rentals", "post")).contains("201", "400", "404", "409", "422")
        assertThat(codes("/api/v1/rentals/{id}/finish", "post")).contains("400", "404", "409", "422")
        assertThat(codes("/api/v1/vehicles", "get")).contains("400").doesNotContain("404", "409")
        assertThat(codes("/api/v1/users/{id}", "get")).contains("404")
        val schema = JsonPath.read<String>(
            docs,
            "$.paths['/api/v1/rentals'].post.responses['409'].content['application/problem+json'].schema['\$ref']",
        )
        assertThat(schema).isEqualTo("#/components/schemas/Problem")
        assertThat(JsonPath.read<Map<String, Any>>(docs, "$.components.schemas.Problem.properties").keys)
            .contains("type", "title", "status", "detail", "instance", "errors")
    }

    @Test
    fun `missing parameter and bad enum are described in russian with field errors`() {
        val missing = api.get("/api/v1/rentals/history")
        val badEnum = api.get("/api/v1/vehicles", "status" to "FLYING")

        assertThat(missing.status).isEqualTo(400)
        assertThat(missing.errorType()).isEqualTo("bad-request")
        assertThat(missing.string("$.detail")).contains("userId")
        assertThat(missing.path<List<String>>("$.errors[*].field")).containsExactly("userId")
        assertThat(badEnum.status).isEqualTo(400)
        assertThat(badEnum.path<List<String>>("$.errors[*].field")).containsExactly("status")
    }

    @Test
    fun `unknown path, wrong method and media type keep the error format`() {
        val unknown = api.get("/api/v1/nope")
        val wrongMethod = api.delete("/api/v1/rentals")
        val wrongMedia = mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/users").contentType(MediaType.TEXT_PLAIN).content("hi"),
        ).andReturn().response

        assertThat(unknown.status).isEqualTo(404)
        assertThat(unknown.errorType()).isEqualTo("endpoint-not-found")
        assertThat(unknown.string("$.detail")).contains("не найден")
        assertThat(wrongMethod.status).isEqualTo(405)
        assertThat(wrongMethod.errorType()).isEqualTo("method-not-allowed")
        assertThat(wrongMethod.headers.getFirst("Allow")).contains("GET")
        assertThat(wrongMedia.status).isEqualTo(415)
        assertThat(wrongMedia.getContentAsString(Charsets.UTF_8)).contains("unsupported-media-type", "JSON")
    }
}
