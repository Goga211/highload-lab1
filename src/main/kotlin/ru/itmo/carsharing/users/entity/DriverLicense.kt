package ru.itmo.carsharing.users.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.carsharing.common.entity.BaseEntity
import ru.itmo.carsharing.common.error.invalidTransition
import ru.itmo.carsharing.users.service.LicenseRules
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Запись ВУ клиента. При замене прав создаётся новая запись, прежняя одобренная уходит в REPLACED,
 * поэтому стаж считается от [firstIssuedAt] (дата получения категории B), а не от даты выдачи карточки.
 */
@Entity
@Table(name = "driver_license")
class DriverLicense(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: AppUser,

    @field:Pattern(regexp = LicenseRules.NUMBER_REGEX)
    @Column(name = "number", nullable = false, length = 10, updatable = false)
    var number: String,

    @Column(name = "issued_at", nullable = false, updatable = false)
    var issuedAt: LocalDate,

    @Column(name = "expires_at", nullable = false, updatable = false)
    var expiresAt: LocalDate,

    @Column(name = "first_issued_at", nullable = false, updatable = false)
    var firstIssuedAt: LocalDate,

    @field:NotEmpty
    @Convert(converter = LicenseCategoriesConverter::class)
    @Column(name = "categories", nullable = false, length = 64, updatable = false)
    var categories: Set<LicenseCategory>,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    var verificationStatus: LicenseVerificationStatus = LicenseVerificationStatus.PENDING
        protected set

    @Column(name = "verified_by")
    var verifiedBy: UUID? = null
        protected set

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null
        protected set

    @field:Size(max = 500)
    @Column(name = "rejection_reason", length = 500)
    var rejectionReason: String? = null
        protected set

    fun isValidOn(date: LocalDate): Boolean = !expiresAt.isBefore(date)

    fun approve(verifierId: UUID, at: Instant) {
        moveTo(LicenseVerificationStatus.APPROVED)
        verifiedBy = verifierId
        verifiedAt = at
    }

    fun reject(verifierId: UUID, reason: String, at: Instant) {
        moveTo(LicenseVerificationStatus.REJECTED)
        verifiedBy = verifierId
        verifiedAt = at
        rejectionReason = reason
    }

    fun markReplaced() {
        moveTo(LicenseVerificationStatus.REPLACED)
    }

    private fun moveTo(target: LicenseVerificationStatus) {
        if (!verificationStatus.canTransitionTo(target)) invalidTransition("ВУ", verificationStatus, target)
        verificationStatus = target
    }
}

/** Категории хранятся строкой "B,BE": перечисления в базе только строками. */
@Converter
class LicenseCategoriesConverter : AttributeConverter<Set<LicenseCategory>, String> {
    override fun convertToDatabaseColumn(attribute: Set<LicenseCategory>?): String? =
        attribute?.sortedBy { it.ordinal }?.joinToString(",") { it.name }

    override fun convertToEntityAttribute(dbData: String?): Set<LicenseCategory>? =
        dbData?.split(",")?.filter { it.isNotBlank() }?.map { LicenseCategory.valueOf(it.trim()) }?.toSet()
}
