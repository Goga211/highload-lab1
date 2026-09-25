package ru.itmo.carsharing.users.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.carsharing.common.web.ApiErrors
import ru.itmo.carsharing.common.web.ApiPaths
import ru.itmo.carsharing.users.dto.ApproveLicenseRequest
import ru.itmo.carsharing.users.dto.LicenseResponse
import ru.itmo.carsharing.users.dto.RejectLicenseRequest
import ru.itmo.carsharing.users.service.DriverLicenseService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/licenses")
@Tag(name = "Пользователи", description = "Клиенты и сотрудники, водительские удостоверения")
class LicenseController(private val licenses: DriverLicenseService) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить ВУ")
    fun get(@PathVariable id: UUID): LicenseResponse = licenses.get(id)

    @PostMapping("/{id}/approve")
    @ApiErrors(409, 422)
    @Operation(summary = "Одобрить ВУ. Прежнее одобренное ВУ клиента переходит в REPLACED")
    fun approve(@PathVariable id: UUID, @Valid @RequestBody request: ApproveLicenseRequest): LicenseResponse =
        licenses.approve(id, request.verifierId)

    @PostMapping("/{id}/reject")
    @ApiErrors(409, 422)
    @Operation(summary = "Отклонить ВУ с причиной")
    fun reject(@PathVariable id: UUID, @Valid @RequestBody request: RejectLicenseRequest): LicenseResponse =
        licenses.reject(id, request.verifierId, request.reason)
}
