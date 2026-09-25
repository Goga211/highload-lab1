package ru.itmo.carsharing.users.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.carsharing.common.error.ErrorCode
import ru.itmo.carsharing.common.error.NotFoundException
import ru.itmo.carsharing.common.error.conflict
import ru.itmo.carsharing.common.web.PageResponse
import ru.itmo.carsharing.common.web.Paging
import ru.itmo.carsharing.users.dto.CreateUserRequest
import ru.itmo.carsharing.users.dto.UpdateUserRequest
import ru.itmo.carsharing.users.dto.UserResponse
import ru.itmo.carsharing.users.entity.AppUser
import ru.itmo.carsharing.users.entity.UserRole
import ru.itmo.carsharing.users.mapper.toResponse
import ru.itmo.carsharing.users.repository.AppUserRepository
import java.util.UUID

@Service
class UserService(private val users: AppUserRepository, private val events: ApplicationEventPublisher) {

    @Transactional
    fun create(request: CreateUserRequest): UserResponse {
        val email = request.email.trim().lowercase()
        if (users.existsByEmail(email)) conflict(ErrorCode.DUPLICATE_RESOURCE, "Пользователь с email $email уже есть")
        if (users.existsByPhone(request.phone)) {
            conflict(ErrorCode.DUPLICATE_RESOURCE, "Пользователь с телефоном ${request.phone} уже есть")
        }
        val user = users.save(
            AppUser(
                email = email,
                phone = request.phone,
                fullName = request.fullName.trim(),
                birthDate = request.birthDate,
                role = request.role,
            ),
        )
        events.publishEvent(UserRegisteredEvent(user.id, user.role))
        return user.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): UserResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(role: UserRole?, page: Int, size: Int): PageResponse<UserResponse> {
        val pageable = Paging.of(page, size, Sort.by("createdAt").descending())
        val result = if (role == null) users.findAll(pageable) else users.findAllByRole(role, pageable)
        return PageResponse.from(result) { it.toResponse() }
    }

    @Transactional
    fun update(id: UUID, request: UpdateUserRequest): UserResponse {
        val user = find(id)
        if (users.existsByPhoneAndIdNot(request.phone, id)) {
            conflict(ErrorCode.DUPLICATE_RESOURCE, "Пользователь с телефоном ${request.phone} уже есть")
        }
        user.updateProfile(request.fullName.trim(), request.phone)
        return user.toResponse()
    }

    @Transactional
    fun block(id: UUID): UserResponse {
        val user = find(id)
        user.block()
        return user.toResponse()
    }

    private fun find(id: UUID): AppUser = users.findByIdOrNull(id) ?: throw NotFoundException("Пользователь", id)
}
