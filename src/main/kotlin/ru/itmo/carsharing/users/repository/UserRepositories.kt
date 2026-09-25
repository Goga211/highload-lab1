package ru.itmo.carsharing.users.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.itmo.carsharing.users.entity.AppUser
import ru.itmo.carsharing.users.entity.DriverLicense
import ru.itmo.carsharing.users.entity.LicenseVerificationStatus
import ru.itmo.carsharing.users.entity.UserRole
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun existsByEmail(email: String): Boolean

    fun existsByPhone(phone: String): Boolean

    fun existsByPhoneAndIdNot(phone: String, id: UUID): Boolean

    fun findAllByRole(role: UserRole, pageable: Pageable): Page<AppUser>
}

interface DriverLicenseRepository : JpaRepository<DriverLicense, UUID> {
    fun existsByNumber(number: String): Boolean

    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<DriverLicense>

    fun findFirstByUserIdAndVerificationStatus(userId: UUID, status: LicenseVerificationStatus): DriverLicense?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from DriverLicense l join fetch l.user where l.id = :id")
    fun findByIdForUpdate(id: UUID): DriverLicense?
}
