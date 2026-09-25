package ru.itmo.carsharing.billing.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.itmo.carsharing.billing.entity.Payment
import ru.itmo.carsharing.billing.entity.Wallet
import java.util.UUID

interface WalletRepository : JpaRepository<Wallet, UUID> {
    fun findByUserId(userId: UUID): Wallet?

    fun existsByUserId(userId: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    fun findByUserIdForUpdate(userId: UUID): Wallet?
}

interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByIdempotencyKey(key: String): Payment?

    fun existsByIdempotencyKey(key: String): Boolean

    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Payment>
}
