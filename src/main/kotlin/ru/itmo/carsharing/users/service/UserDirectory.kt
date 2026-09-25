package ru.itmo.carsharing.users.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.users.entity.AppUser
import ru.itmo.carsharing.users.entity.LicenseCategory
import ru.itmo.carsharing.users.entity.LicenseVerificationStatus
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.entity.UserStatus
import ru.itmo.carsharing.users.repository.AppUserRepository
import ru.itmo.carsharing.users.repository.DriverLicenseRepository
import java.time.LocalDate
import java.util.UUID

data class LicenseSnapshot(
    val licenseId: UUID,
    val expiresAt: LocalDate,
    val firstIssuedAt: LocalDate,
    val categories: Set<LicenseCategory>,
)

/** Всё, что аренде нужно знать о водителе для допуска к поездке. */
data class DriverProfile(
    val userId: UUID,
    val role: UserRole,
    val status: UserStatus,
    val birthDate: LocalDate,
    val approvedLicense: LicenseSnapshot?,
)

/**
 * Точка входа в домен users для других доменов. Другие пакеты не трогают репозитории users,
 * в ЛР2 этот интерфейс станет Feign-клиентом user-service.
 */
@Service
class UserDirectory(private val users: AppUserRepository, private val licenses: DriverLicenseRepository) {

    @Transactional(readOnly = true)
    fun getDriverProfile(userId: UUID): DriverProfile {
        val user = find(userId)
        val license = licenses.findFirstByUserIdAndVerificationStatus(userId, LicenseVerificationStatus.APPROVED)
        return DriverProfile(
            userId = user.id,
            role = user.role,
            status = user.status,
            birthDate = user.birthDate,
            approvedLicense = license?.let {
                LicenseSnapshot(it.id, it.expiresAt, it.firstIssuedAt, it.categories)
            },
        )
    }

    @Transactional(readOnly = true)
    fun ensureExists(userId: UUID) {
        if (!users.existsById(userId)) throw NotFoundException("Пользователь", userId)
    }

    /** Исполнитель действия должен существовать, быть активным и иметь нужную роль. */
    @Transactional(readOnly = true)
    fun requireActiveWithRole(userId: UUID, role: UserRole) {
        val user = find(userId)
        if (!user.isActive) unprocessable(ErrorCode.USER_BLOCKED, "Пользователь ${user.id} заблокирован")
        if (user.role != role) {
            unprocessable(ErrorCode.WRONG_USER_ROLE, "Нужна роль $role, у пользователя ${user.id} роль ${user.role}")
        }
    }

    private fun find(userId: UUID): AppUser =
        users.findByIdOrNull(userId) ?: throw NotFoundException("Пользователь", userId)
}
