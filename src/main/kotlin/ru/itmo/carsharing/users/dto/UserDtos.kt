package ru.itmo.carsharing.users.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.validation.MinAge
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.entity.UserStatus
import ru.itmo.carsharing.users.service.UserRules
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateUserRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String,

    @field:Pattern(regexp = UserRules.PHONE_REGEX, message = "телефон в формате +79991234567")
    val phone: String,

    @field:NotBlank
    @field:Size(max = 200)
    val fullName: String,

    @field:Past
    @field:MinAge(UserRules.MIN_USER_AGE)
    val birthDate: LocalDate,

    val role: UserRole,
)

data class UpdateUserRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val fullName: String,

    @field:Pattern(regexp = UserRules.PHONE_REGEX, message = "телефон в формате +79991234567")
    val phone: String,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val phone: String,
    val fullName: String,
    val birthDate: LocalDate,
    val role: UserRole,
    val status: UserStatus,
    val createdAt: Instant,
)
