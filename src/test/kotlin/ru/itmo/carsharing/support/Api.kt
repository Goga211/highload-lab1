package ru.itmo.carsharing.support

import com.jayway.jsonpath.JsonPath
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.UUID

data class ApiResponse(val status: Int, val body: String, val headers: HttpHeaders) {
    fun <T> path(expression: String): T = JsonPath.read(body, expression)

    fun id(): UUID = UUID.fromString(path("$.id"))

    fun uuid(expression: String): UUID = UUID.fromString(path(expression))

    fun decimal(expression: String): BigDecimal = BigDecimal(path<Any>(expression).toString())

    fun string(expression: String): String = path(expression)

    fun errorType(): String = path<String>("$.type").substringAfterLast('/')

    override fun toString(): String = "HTTP $status $body"
}

/** Тонкая обёртка над MockMvc: запросы идут через весь стек, как у реального клиента. */
class Api(private val mockMvc: MockMvc, private val json: JsonMapper) {

    fun get(path: String, vararg params: Pair<String, Any?>): ApiResponse =
        perform(MockMvcRequestBuilders.get(path).withParams(params))

    fun post(path: String, body: Any? = null, headers: Map<String, String> = emptyMap()): ApiResponse =
        perform(MockMvcRequestBuilders.post(path).withBody(body).withHeaders(headers))

    fun put(path: String, body: Any?): ApiResponse = perform(MockMvcRequestBuilders.put(path).withBody(body))

    fun delete(path: String): ApiResponse = perform(MockMvcRequestBuilders.delete(path))

    fun postRaw(path: String, rawJson: String): ApiResponse =
        perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(rawJson))

    private fun MockHttpServletRequestBuilder.withBody(body: Any?): MockHttpServletRequestBuilder =
        if (body == null) this else contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))

    private fun MockHttpServletRequestBuilder.withHeaders(headers: Map<String, String>): MockHttpServletRequestBuilder =
        apply { headers.forEach { (name, value) -> header(name, value) } }

    private fun MockHttpServletRequestBuilder.withParams(params: Array<out Pair<String, Any?>>): MockHttpServletRequestBuilder =
        apply { params.forEach { (name, value) -> if (value != null) param(name, value.toString()) } }

    private fun perform(request: MockHttpServletRequestBuilder): ApiResponse {
        val response = mockMvc.perform(request.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn().response
        val headers = HttpHeaders()
        response.headerNames.forEach { name -> response.getHeaders(name).forEach { headers.add(name, it) } }
        return ApiResponse(response.status, response.getContentAsString(Charsets.UTF_8), headers)
    }
}
