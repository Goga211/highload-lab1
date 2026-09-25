package ru.itmo.carsharing.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.itmo.carsharing.common.error.ApiException
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.fleet.entity.MaintenanceStatus
import ru.itmo.carsharing.rentals.entity.FineStatus
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.entity.TrafficFine
import ru.itmo.carsharing.users.entity.LicenseVerificationStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class StatusTransitionsTest {

    @ParameterizedTest
    @CsvSource(
        "RESERVED, ACTIVE, true",
        "RESERVED, CANCELLED, true",
        "RESERVED, EXPIRED, true",
        "RESERVED, COMPLETED, false",
        "ACTIVE, COMPLETED, true",
        "ACTIVE, CANCELLED, false",
        "COMPLETED, ACTIVE, false",
        "CANCELLED, RESERVED, false",
        "EXPIRED, ACTIVE, false",
    )
    fun `rental transitions follow the state diagram`(from: RentalStatus, to: RentalStatus, allowed: Boolean) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed)
    }

    @ParameterizedTest
    @CsvSource(
        "OPEN, IN_PROGRESS, true",
        "OPEN, CANCELLED, true",
        "OPEN, DONE, false",
        "IN_PROGRESS, DONE, true",
        "IN_PROGRESS, CANCELLED, true",
        "DONE, OPEN, false",
        "CANCELLED, IN_PROGRESS, false",
    )
    fun `maintenance transitions`(from: MaintenanceStatus, to: MaintenanceStatus, allowed: Boolean) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed)
    }

    @ParameterizedTest
    @CsvSource(
        "RECEIVED, REBILLED, true",
        "RECEIVED, NO_RENTAL, true",
        "RECEIVED, DISPUTED, true",
        "REBILLED, DISPUTED, true",
        "NO_RENTAL, DISPUTED, true",
        "DISPUTED, CANCELLED, true",
        "REBILLED, CANCELLED, false",
        "CANCELLED, DISPUTED, false",
    )
    fun `fine transitions`(from: FineStatus, to: FineStatus, allowed: Boolean) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed)
    }

    @ParameterizedTest
    @CsvSource(
        "PENDING, APPROVED, true",
        "PENDING, REJECTED, true",
        "APPROVED, REPLACED, true",
        "APPROVED, REJECTED, false",
        "REJECTED, APPROVED, false",
        "REPLACED, APPROVED, false",
    )
    fun `license transitions`(from: LicenseVerificationStatus, to: LicenseVerificationStatus, allowed: Boolean) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed)
    }

    @Test
    fun `open statuses are reserved and active`() {
        assertThat(RentalStatus.OPEN_STATUSES).containsExactlyInAnyOrder(RentalStatus.RESERVED, RentalStatus.ACTIVE)
        assertThat(MaintenanceStatus.OPEN_STATUSES)
            .containsExactlyInAnyOrder(MaintenanceStatus.OPEN, MaintenanceStatus.IN_PROGRESS)
    }

    @Test
    fun `entity rejects transition outside the diagram with 409`() {
        val fine = TrafficFine(UUID.randomUUID(), "18810177260920000001", Instant.now(), BigDecimal("500.00"))
        fine.markNoRental()

        assertThatThrownBy { fine.cancel() }
            .isInstanceOf(ApiException::class.java)
            .extracting { (it as ApiException).code }
            .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION)
    }
}
