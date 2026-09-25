package ru.itmo.carsharing.fleet.entity

enum class VehicleClass { ECONOMY, COMFORT, BUSINESS, CARGO }

enum class FuelType { PETROL, DIESEL, ELECTRIC, HYBRID }

/** Статус машины меняют только аренда и наряды, вручную его не ставят. */
enum class VehicleStatus { AVAILABLE, RESERVED, IN_RENTAL, SERVICE, DECOMMISSIONED }

enum class ZoneType { HOME, BUSINESS, AIRPORT, RESTRICTED }

enum class MaintenanceType { SCHEDULED_SERVICE, REPAIR, WASHING, REFUELING }

enum class MaintenanceStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED,
    ;

    fun canTransitionTo(target: MaintenanceStatus): Boolean = when (this) {
        OPEN -> target == IN_PROGRESS || target == CANCELLED
        IN_PROGRESS -> target == DONE || target == CANCELLED
        DONE, CANCELLED -> false
    }

    companion object {
        val OPEN_STATUSES: Set<MaintenanceStatus> = setOf(OPEN, IN_PROGRESS)
    }
}
