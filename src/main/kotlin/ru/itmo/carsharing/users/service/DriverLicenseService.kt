package ru.itmo.carsharing.users.service

import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.users.dto.AddLicenseRequest
import ru.itmo.carsharing.users.dto.LicenseResponse
import ru.itmo.carsharing.users.entity.DriverLicense
import ru.itmo.carsharing.users.entity.LicenseVerificationStatus
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.mapper.toResponse
import ru.itmo.carsharing.users.repository.AppUserRepository
import ru.itmo.carsharing.users.repository.DriverLicenseRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class DriverLicenseService(
    private val licenses: DriverLicenseRepository,
    private val users: AppUserRepository,
    private val directory: UserDirectory,
    private val clock: Clock,
) {

    @Transactional
    fun add(userId: UUID, request: AddLicenseRequest): LicenseResponse {
        val user = users.findByIdOrNull(userId) ?: throw NotFoundException("Пользователь", userId)
        if (user.role != UserRole.CLIENT) {
            unprocessable(ErrorCode.WRONG_USER_ROLE, "ВУ добавляется только клиенту, у пользователя роль ${user.role}")
        }
        LicenseRules.checkAgainstBirthDate(request.firstIssuedAt, user.birthDate)?.let {
            unprocessable(ErrorCode.LICENSE_INVALID, it.message)
        }
        val number = LicenseRules.normalizeNumber(request.number)
        if (licenses.existsByNumber(number)) conflict(ErrorCode.DUPLICATE_RESOURCE, "ВУ $number уже зарегистрировано")
        val license = DriverLicense(
            user = user,
            number = number,
            issuedAt = request.issuedAt,
            expiresAt = request.expiresAt,
            firstIssuedAt = request.firstIssuedAt,
            categories = request.categories,
        )
        return licenses.save(license).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(licenseId: UUID): LicenseResponse =
        (licenses.findByIdOrNull(licenseId) ?: throw NotFoundException("ВУ", licenseId)).toResponse()

    @Transactional(readOnly = true)
    fun listForUser(userId: UUID, page: Int, size: Int): PageResponse<LicenseResponse> {
        if (!users.existsById(userId)) throw NotFoundException("Пользователь", userId)
        val pageable = Paging.of(page, size, Sort.by("createdAt").descending())
        return PageResponse.from(licenses.findAllByUserId(userId, pageable)) { it.toResponse() }
    }

    /**
     * Одобрение: прежнее одобренное ВУ клиента уходит в REPLACED в той же транзакции.
     * Порядок важен, частичный уникальный индекс не даст двум одобренным ВУ существовать одновременно.
     */
    @Transactional
    fun approve(licenseId: UUID, verifierId: UUID): LicenseResponse {
        val license = licenses.findByIdForUpdate(licenseId) ?: throw NotFoundException("ВУ", licenseId)
        directory.requireActiveWithRole(verifierId, UserRole.SUPPORT)
        if (license.verificationStatus != LicenseVerificationStatus.PENDING) {
            conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Одобрить можно только ВУ на проверке")
        }
        if (!license.isValidOn(LocalDate.now(clock))) {
            unprocessable(ErrorCode.LICENSE_INVALID, "Срок действия ВУ истёк ${license.expiresAt}")
        }
        licenses.findFirstByUserIdAndVerificationStatus(license.user.id, LicenseVerificationStatus.APPROVED)
            ?.let { previous ->
                previous.markReplaced()
                licenses.saveAndFlush(previous)
            }
        license.approve(verifierId, clock.instant())
        return licenses.saveAndFlush(license).toResponse()
    }

    @Transactional
    fun reject(licenseId: UUID, verifierId: UUID, reason: String): LicenseResponse {
        val license = licenses.findByIdForUpdate(licenseId) ?: throw NotFoundException("ВУ", licenseId)
        directory.requireActiveWithRole(verifierId, UserRole.SUPPORT)
        if (license.verificationStatus != LicenseVerificationStatus.PENDING) {
            conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Отклонить можно только ВУ на проверке")
        }
        license.reject(verifierId, reason.trim(), clock.instant())
        return license.toResponse()
    }
}
