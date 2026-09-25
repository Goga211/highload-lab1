package ru.itmo.carsharing.rentals.service

object FineRules {
    /** УИН постановления: 20 или 25 цифр. */
    const val RESOLUTION_NUMBER_REGEX = "^\\d{20}(\\d{5})?$"
}
