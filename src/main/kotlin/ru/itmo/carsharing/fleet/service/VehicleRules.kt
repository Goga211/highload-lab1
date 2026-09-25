package ru.itmo.carsharing.fleet.service

object VehicleRules {
    /** 17 символов, без I, O и Q, как в стандарте VIN. */
    const val VIN_REGEX = "^[A-HJ-NPR-Z0-9]{17}$"

    /** Госномер: буква, три цифры, две буквы, регион из 2-3 цифр. Только буквы, похожие на латиницу. */
    const val PLATE_REGEX = "^[АВЕКМНОРСТУХ]\\d{3}[АВЕКМНОРСТУХ]{2}\\d{2,3}$"

    const val MAX_SEARCH_RADIUS_M: Long = 50_000
}
