package ru.itmo.carsharing.rentals.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.users.entity.LicenseCategory
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.entity.UserStatus
import ru.itmo.carsharing.users.service.DriverProfile
import ru.itmo.carsharing.users.service.LicenseSnapshot
import java.time.LocalDate
import java.util.UUID

class DriverEligibilityPolicyTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun profile(
        birthDate: LocalDate = LocalDate.of(1995, 5, 15),
        firstIssuedAt: LocalDate = LocalDate.of(2019, 6, 1),
        expiresAt: LocalDate = LocalDate.of(2029, 6, 1),
        categories: Set<LicenseCategory> = setOf(LicenseCategory.B),
        status: UserStatus = UserStatus.ACTIVE,
        role: UserRole = UserRole.CLIENT,
        withLicense: Boolean = true,
    ) = DriverProfile(
        userId = UUID.randomUUID(),
        role = role,
        status = status,
        birthDate = birthDate,
        approvedLicense = if (withLicense) LicenseSnapshot(UUID.randomUUID(), expiresAt, firstIssuedAt, categories) else null,
    )

    private fun check(profile: DriverProfile, vehicleClass: VehicleClass = VehicleClass.ECONOMY) =
        DriverEligibilityPolicy.check(profile, vehicleClass, today)

    @Test
    fun `experienced adult client may drive any class`() {
        VehicleClass.entries.forEach { assertThat(check(profile(), it)).isNull() }
    }

    @Test
    fun `blocked user is rejected`() {
        assertThat(check(profile(status = UserStatus.BLOCKED))?.code).isEqualTo(ErrorCode.USER_BLOCKED)
    }

    @Test
    fun `only clients may book`() {
        assertThat(check(profile(role = UserRole.SUPPORT))?.code).isEqualTo(ErrorCode.WRONG_USER_ROLE)
    }

    @Test
    fun `client without approved license is rejected`() {
        assertThat(check(profile(withLicense = false))?.code).isEqualTo(ErrorCode.LICENSE_INVALID)
    }

    @Test
    fun `expired license does not allow booking`() {
        val violation = check(profile(expiresAt = today.minusDays(1)))

        assertThat(violation?.code).isEqualTo(ErrorCode.LICENSE_INVALID)
        assertThat(violation?.message).contains("истёк")
    }

    @Test
    fun `license valid until today still allows booking`() {
        assertThat(check(profile(expiresAt = today))).isNull()
    }

    @Test
    fun `license without category B is rejected`() {
        assertThat(check(profile(categories = setOf(LicenseCategory.C)))?.code).isEqualTo(ErrorCode.LICENSE_INVALID)
    }

    @Test
    fun `driver younger than 18 is rejected`() {
        assertThat(check(profile(birthDate = today.minusYears(17)))?.code).isEqualTo(ErrorCode.DRIVER_NOT_ELIGIBLE)
    }

    @Test
    fun `business class rejects drivers younger than 21`() {
        val young = profile(birthDate = today.minusYears(20), firstIssuedAt = today.minusYears(2))

        assertThat(check(young, VehicleClass.ECONOMY)).isNull()
        assertThat(check(young, VehicleClass.BUSINESS)?.code).isEqualTo(ErrorCode.DRIVER_NOT_ELIGIBLE)
    }

    @Test
    fun `business class rejects experience below two years`() {
        val novice = profile(firstIssuedAt = today.minusYears(1))

        assertThat(check(novice, VehicleClass.COMFORT)).isNull()
        val violation = check(novice, VehicleClass.BUSINESS)
        assertThat(violation?.code).isEqualTo(ErrorCode.DRIVER_NOT_ELIGIBLE)
        assertThat(violation?.message).contains("стаж")
    }
}
