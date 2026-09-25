package ru.itmo.carsharing.users.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.carsharing.common.web.ApiPaths
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.common.web.created
import ru.itmo.carsharing.users.dto.AddLicenseRequest
import ru.itmo.carsharing.users.dto.CreateUserRequest
import ru.itmo.carsharing.users.dto.LicenseResponse
import ru.itmo.carsharing.users.dto.UpdateUserRequest
import ru.itmo.carsharing.users.dto.UserResponse
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.service.DriverLicenseService
import ru.itmo.carsharing.users.service.UserService
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.BASE}/users")
@Tag(name = "Пользователи", description = "Клиенты и сотрудники, водительские удостоверения")
class UserController(
    private val users: UserService,
    private val licenses: DriverLicenseService,
) {

    @PostMapping
    @Operation(summary = "Создать пользователя. Клиенту автоматически открывается счёт")
    fun create(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = users.create(request)
        return created("${ApiPaths.BASE}/users/${user.id}", user)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя")
    fun get(@PathVariable id: UUID): UserResponse = users.get(id)

    @GetMapping
    @Operation(summary = "Список пользователей, фильтр по роли")
    fun list(
        @RequestParam(required = false) role: UserRole?,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<UserResponse> = users.list(role, page, size)

    @PutMapping("/{id}")
    @Operation(summary = "Изменить профиль")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdateUserRequest): UserResponse =
        users.update(id, request)

    @PostMapping("/{id}/block")
    @Operation(summary = "Заблокировать пользователя")
    fun block(@PathVariable id: UUID): UserResponse = users.block(id)

    @PostMapping("/{id}/licenses")
    @Operation(summary = "Добавить ВУ клиенту, запись уходит на проверку саппорту")
    fun addLicense(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddLicenseRequest,
    ): ResponseEntity<LicenseResponse> {
        val license = licenses.add(id, request)
        return created("${ApiPaths.BASE}/licenses/${license.id}", license)
    }

    @GetMapping("/{id}/licenses")
    @Operation(summary = "История ВУ клиента")
    fun licenses(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @Min(0) page: Int,
        @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_PAGE_SIZE) size: Int,
    ): PageResponse<LicenseResponse> = licenses.listForUser(id, page, size)
}
