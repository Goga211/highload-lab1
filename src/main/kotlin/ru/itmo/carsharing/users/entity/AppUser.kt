package ru.itmo.carsharing.users.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.users.service.UserRules
import java.time.LocalDate

/** Клиенты и сотрудники в одной таблице: на неё ссылаются verified_by и assigned_to. */
@Entity
@Table(name = "app_user")
class AppUser(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    @Column(name = "email", nullable = false, length = 254)
    var email: String,

    @field:Pattern(regexp = UserRules.PHONE_REGEX)
    @Column(name = "phone", nullable = false, length = 16)
    var phone: String,

    @field:NotBlank
    @field:Size(max = 200)
    @Column(name = "full_name", nullable = false, length = 200)
    var fullName: String,

    @field:Past
    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32, updatable = false)
    var role: UserRole,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    val isActive: Boolean
        get() = status == UserStatus.ACTIVE

    fun updateProfile(fullName: String, phone: String) {
        this.fullName = fullName
        this.phone = phone
    }

    fun block() {
        status = UserStatus.BLOCKED
    }
}
