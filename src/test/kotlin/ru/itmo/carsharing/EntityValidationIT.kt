package ru.itmo.carsharing

import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import ru.itmo.carsharing.fleet.entity.FuelType
import ru.itmo.carsharing.fleet.entity.VehicleClass
import ru.itmo.carsharing.fleet.entity.VehicleModel
import ru.itmo.carsharing.fleet.repository.VehicleModelRepository
import ru.itmo.carsharing.support.IntegrationTest
import ru.itmo.carsharing.users.entity.AppUser
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.repository.AppUserRepository
import java.time.LocalDate

/**
 * Валидация на уровне Entity: аннотации Jakarta Validation на полях сущностей проверяет Hibernate
 * перед insert и update, даже если запрос обошёл контроллер. Ниже неё страхует CHECK в базе.
 */
class EntityValidationIT : IntegrationTest() {

    @Autowired
    private lateinit var users: AppUserRepository

    @Autowired
    private lateinit var models: VehicleModelRepository

    @Test
    fun `invalid entity is rejected before insert`() {
        val invalid = AppUser(
            email = "not-an-email",
            phone = "123",
            fullName = "",
            birthDate = LocalDate.of(1990, 1, 1),
            role = UserRole.CLIENT,
        )

        assertThatThrownBy { users.saveAndFlush(invalid) }
            .isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ ex ->
                val fields = (ex as ConstraintViolationException).constraintViolations.map { it.propertyPath.toString() }
                assertThat(fields).contains("email", "phone", "fullName")
            })
        assertThat(users.count()).isZero()
    }

    @Test
    fun `database check constraint guards values that bypass bean validation`() {
        models.saveAndFlush(VehicleModel("Kia", "Rio", VehicleClass.ECONOMY, FuelType.PETROL, 5, 15000))

        assertThatThrownBy {
            jdbc.update("UPDATE vehicle_model SET seats = 42")
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
