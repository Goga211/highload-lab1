package ru.itmo.carsharing.users.entity

enum class UserRole { CLIENT, SUPPORT, FLEET_MECHANIC, SUPERVISOR }

enum class UserStatus { ACTIVE, BLOCKED }

enum class LicenseVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    REPLACED,
    ;

    fun canTransitionTo(target: LicenseVerificationStatus): Boolean = when (this) {
        PENDING -> target == APPROVED || target == REJECTED
        APPROVED -> target == REPLACED
        REJECTED, REPLACED -> false
    }
}

/** Категории ВУ по графе 9. Для аренды легковой машины нужна B. */
enum class LicenseCategory { A, A1, B, B1, BE, C, C1, CE, C1E, D, D1, DE, D1E, M, TM, TB }
