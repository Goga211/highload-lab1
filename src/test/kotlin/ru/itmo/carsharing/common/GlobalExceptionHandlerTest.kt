package ru.itmo.carsharing.common

import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.TransactionSystemException
import ru.itmo.carsharing.common.error.GlobalExceptionHandler
import java.sql.SQLException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest("POST", "/api/v1/rentals")

    @Test
    fun `unique index violation from a race is translated to a domain conflict`() {
        val cause = org.hibernate.exception.ConstraintViolationException(
            "duplicate key",
            SQLException("duplicate key value"),
            "ux_rental_active_vehicle",
        )

        val response = handler.handleDataIntegrity(DataIntegrityViolationException("insert", cause), request)

        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat(response.body?.type.toString()).endsWith("/vehicle-not-available")
        assertThat(response.body?.instance.toString()).isEqualTo("/api/v1/rentals")
    }

    @Test
    fun `constraint name is also found in the driver message`() {
        val cause = RuntimeException("ERROR: duplicate key value violates unique constraint \"ux_rental_active_user\"")

        val response = handler.handleDataIntegrity(DataIntegrityViolationException("insert", cause), request)

        assertThat(response.body?.type.toString()).endsWith("/active-rental-exists")
    }

    @Test
    fun `unknown integrity violation is a generic conflict`() {
        val response = handler.handleDataIntegrity(DataIntegrityViolationException("fk"), request)

        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat(response.body?.type.toString()).endsWith("/data-conflict")
    }

    @Test
    fun `optimistic locking failure asks to retry`() {
        val response = handler.handleLockingFailure(OptimisticLockingFailureException("version"), request)

        assertThat(response.statusCode.value()).isEqualTo(409)
        assertThat(response.body?.type.toString()).endsWith("/concurrent-modification")
    }

    @Test
    fun `entity validation at commit is a 400, other commit failures are 500`() {
        val invalid = TransactionSystemException("commit").apply { initCause(ConstraintViolationException(emptySet())) }

        assertThat(handler.handleTransactionSystem(invalid, request).statusCode.value()).isEqualTo(400)
        assertThat(handler.handleTransactionSystem(TransactionSystemException("commit"), request).statusCode.value())
            .isEqualTo(500)
    }

    @Test
    fun `unexpected error does not leak details`() {
        val response = handler.handleUnexpected(IllegalStateException("secret internals"), request)

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.body?.detail).doesNotContain("secret")
        assertThat(response.body?.properties?.get("errors")).isEqualTo(emptyList<Any>())
    }
}
