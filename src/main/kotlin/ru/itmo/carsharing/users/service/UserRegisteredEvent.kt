package ru.itmo.carsharing.users.service

import ru.itmo.carsharing.users.entity.UserRole
import java.util.UUID

/**
 * Публикуется при создании пользователя внутри той же транзакции.
 * billing слушает его и заводит клиенту счёт, поэтому users не зависит от billing.
 * В ЛР4 это событие уйдёт в Kafka как user.registered.
 */
data class UserRegisteredEvent(val userId: UUID, val role: UserRole)
