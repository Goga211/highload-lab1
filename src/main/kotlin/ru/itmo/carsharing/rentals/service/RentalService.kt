package ru.itmo.carsharing.rentals.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.billing.service.BillingOperations
import ru.itmo.carsharing.common.config.CarsharingProperties
import ru.itmo.carsharing.common.error.ApiException
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.error.unprocessable
import ru.itmo.carsharing.fleet.service.FleetOperations
import ru.itmo.carsharing.fleet.service.VehicleSnapshot
import ru.itmo.carsharing.rentals.dto.CreateRentalRequest
import ru.itmo.carsharing.rentals.dto.OptionSelection
import ru.itmo.carsharing.rentals.dto.RentalResponse
import ru.itmo.carsharing.rentals.entity.AppliedRates
import ru.itmo.carsharing.rentals.entity.Rental
import ru.itmo.carsharing.rentals.entity.RentalStatus
import ru.itmo.carsharing.rentals.entity.Tariff
import ru.itmo.carsharing.rentals.entity.TariffStatus
import ru.itmo.carsharing.rentals.mapper.toResponse
import ru.itmo.carsharing.rentals.repository.RentalRepository
import ru.itmo.carsharing.rentals.repository.TariffRepository
import ru.itmo.carsharing.users.service.UserDirectory
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Команды жизненного цикла аренды. Порядок блокировок во всех транзакциях один:
 * аренда, машина, счёт. Бронирование аренды ещё не имеет, поэтому в нём сначала
 * захватывается машина, потом вставляется аренда и только потом блокируется счёт.
 */
@Service
class RentalService(
    private val rentals: RentalRepository,
    private val tariffs: TariffRepository,
    private val options: RentalOptionService,
    private val users: UserDirectory,
    private val fleet: FleetOperations,
    private val billing: BillingOperations,
    private val properties: CarsharingProperties,
    private val clock: Clock,
) {

    /**
     * Транзакция 1. Бронирование. Между проверкой "машина свободна" и записью брони есть окно,
     * которое закрывают условный UPDATE машины и частичный уникальный индекс аренды.
     * Деньги, бронь и статус машины появляются вместе: нехватка денег откатывает и захват машины.
     */
    @Transactional
    fun book(request: CreateRentalRequest): RentalResponse {
        val now = clock.instant()
        val profile = users.getDriverProfile(request.userId)
        val vehicle = fleet.getVehicle(request.vehicleId)
        DriverEligibilityPolicy.check(profile, vehicle.vehicleClass, LocalDate.ofInstant(now, ZoneOffset.UTC))
            ?.let { throw ApiException(it.code, it.message) }
        if (rentals.existsByUserIdAndStatusIn(request.userId, RentalStatus.OPEN_STATUSES)) {
            conflict(ErrorCode.ACTIVE_RENTAL_EXISTS, "У клиента уже есть незавершённая аренда")
        }
        val tariff = resolveTariff(request.tariffId, vehicle)
        val selectedOptions = request.options.map { resolveOption(it) }

        fleet.reserve(vehicle)
        val rental = Rental(
            userId = request.userId,
            vehicleId = vehicle.id,
            tariff = tariff,
            rates = AppliedRates(
                pricePerMinute = tariff.pricePerMinute,
                pricePerKm = tariff.pricePerKm,
                waitingPricePerMinute = tariff.waitingPricePerMinute,
                freeReservationMinutes = tariff.freeReservationMinutes,
                deposit = tariff.depositAmount,
            ),
            reservedAt = now,
        )
        selectedOptions.forEach { (option, quantity) -> rental.addOption(option, quantity) }
        rentals.saveAndFlush(rental)
        billing.holdDeposit(request.userId, rental.id, tariff.depositAmount)
        return rental.toResponse()
    }

    /** Старт: клиент открывает машину, одометр на старте берётся из телеметрии. */
    @Transactional
    fun start(id: UUID): RentalResponse {
        val rental = lock(id)
        if (rental.status != RentalStatus.RESERVED) {
            conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Стартовать можно только бронь, аренда в статусе ${rental.status}")
        }
        val now = clock.instant()
        if (isOverdue(rental, now)) conflict(ErrorCode.RESERVATION_EXPIRED, "Бронь истекла, забронируйте машину заново")
        val telemetry = fleet.startRental(rental.vehicleId)
        rental.start(now, telemetry.odometerKm, telemetry.zoneId)
        return rental.toResponse()
    }

    /**
     * Транзакция 2. Завершение. Частичный коммит дал бы либо свободную машину без оплаты,
     * либо списание с машиной, навсегда застрявшей в аренде. Повтор на завершённой аренде
     * возвращает прежний результат и не создаёт второй платёж.
     */
    @Transactional
    fun finish(id: UUID): RentalResponse {
        val rental = lock(id)
        if (rental.status == RentalStatus.COMPLETED) return rental.toResponse()
        if (rental.status != RentalStatus.ACTIVE) {
            conflict(ErrorCode.INVALID_STATUS_TRANSITION, "Завершить можно только активную аренду, статус ${rental.status}")
        }
        val telemetry = fleet.readTelemetry(rental.vehicleId)
        val zone = fleet.findZoneAt(telemetry.latitude, telemetry.longitude)
        if (zone == null || !zone.finishAllowed) {
            unprocessable(
                ErrorCode.FINISH_ZONE_FORBIDDEN,
                "Завершить аренду здесь нельзя" + (zone?.let { ": зона \"${it.name}\"" } ?: ": точка вне зон"),
            )
        }
        val now = clock.instant()
        val cost = RentalCostCalculator.calculate(
            CostInput(
                reservedAt = rental.reservedAt,
                startedAt = requireNotNull(rental.startedAt),
                finishedAt = now,
                startOdometerKm = requireNotNull(rental.startOdometerKm),
                finishOdometerKm = telemetry.odometerKm,
                rates = rental.rates,
                options = rental.options.map { OptionCharge(it.unitPriceSnapshot, it.priceUnitSnapshot, it.quantity) },
                zoneSurcharge = zone.finishSurcharge,
            ),
        )
        fleet.completeRental(rental.vehicleId, zone.id)
        billing.settleRental(rental.userId, rental.id, rental.rates.deposit, cost.total)
        rental.complete(now, telemetry.odometerKm, zone.id, cost)
        return rental.toResponse()
    }

    /** Транзакция 3. Снятие брони клиентом или саппортом: машина освобождается, холд возвращается. */
    @Transactional
    fun cancel(id: UUID, reason: String): RentalResponse {
        val rental = lock(id)
        if (rental.status != RentalStatus.RESERVED) {
            conflict(
                ErrorCode.INVALID_STATUS_TRANSITION,
                "Отменить можно только бронь, активную аренду можно только завершить. Статус ${rental.status}",
            )
        }
        rental.cancel(clock.instant(), reason.trim())
        releaseReservation(rental)
        return rental.toResponse()
    }

    /** Снятие просроченной брони планировщиком. Строка аренды уже заблокирована вызывающим. */
    @Transactional
    fun expire(id: UUID) {
        val rental = lock(id)
        if (rental.status != RentalStatus.RESERVED) return
        rental.expire(clock.instant())
        releaseReservation(rental)
    }

    private fun releaseReservation(rental: Rental) {
        fleet.releaseReservation(rental.vehicleId)
        billing.releaseDeposit(rental.userId, rental.id, rental.rates.deposit)
    }

    private fun resolveTariff(tariffId: UUID?, vehicle: VehicleSnapshot): Tariff {
        val now = clock.instant()
        if (tariffId == null) {
            return tariffs.findApplicable(vehicle.modelId, now, TariffStatus.ACTIVE).firstOrNull()
                ?: unprocessable(ErrorCode.TARIFF_NOT_APPLICABLE, "Для модели машины нет действующего тарифа")
        }
        val tariff = tariffs.findWithModelsById(tariffId) ?: throw NotFoundException("Тариф", tariffId)
        if (!tariff.isApplicable(vehicle.modelId, now)) {
            unprocessable(ErrorCode.TARIFF_NOT_APPLICABLE, "Тариф \"${tariff.name}\" не действует для этой машины")
        }
        return tariff
    }

    private fun resolveOption(selection: OptionSelection) =
        options.find(selection.optionId).also {
            if (!it.active) unprocessable(ErrorCode.OPTION_UNAVAILABLE, "Опция ${it.code} недоступна")
        } to selection.quantity

    private fun isOverdue(rental: Rental, now: java.time.Instant): Boolean =
        rental.reservedAt.plus(Duration.ofMinutes(properties.rental.reservationTtlMinutes)).isBefore(now)

    private fun lock(id: UUID): Rental = rentals.findByIdForUpdate(id) ?: throw NotFoundException("Аренда", id)
}
