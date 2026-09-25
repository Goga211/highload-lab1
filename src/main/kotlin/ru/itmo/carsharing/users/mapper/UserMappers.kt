package ru.itmo.carsharing.users.mapper

import ru.itmo.carsharing.users.dto.LicenseResponse
import ru.itmo.carsharing.users.dto.UserResponse
import ru.itmo.carsharing.users.entity.AppUser
import ru.itmo.carsharing.users.entity.DriverLicense

fun AppUser.toResponse(): UserResponse = UserResponse(
    id = id,
    email = email,
    phone = phone,
    fullName = fullName,
    birthDate = birthDate,
    role = role,
    status = status,
    createdAt = createdAt,
)

fun DriverLicense.toResponse(): LicenseResponse = LicenseResponse(
    id = id,
    userId = user.id,
    number = number,
    issuedAt = issuedAt,
    expiresAt = expiresAt,
    firstIssuedAt = firstIssuedAt,
    categories = categories,
    verificationStatus = verificationStatus,
    verifiedBy = verifiedBy,
    verifiedAt = verifiedAt,
    rejectionReason = rejectionReason,
    createdAt = createdAt,
)
