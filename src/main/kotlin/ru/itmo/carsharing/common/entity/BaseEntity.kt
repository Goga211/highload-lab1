package ru.itmo.carsharing.common.entity

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Transient
import org.hibernate.Hibernate
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * Общая часть сущностей: uuid, который генерирует приложение, и отметки времени.
 * Реализует [Persistable], чтобы save() для новой записи делал INSERT без лишнего SELECT.
 */
@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private var id: UUID = UUID.randomUUID()

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
        protected set

    @Transient
    private var isNewEntity: Boolean = true

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

    @PrePersist
    protected fun onPrePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onPreUpdate() {
        updatedAt = Instant.now()
    }

    @PostPersist
    @PostLoad
    protected fun markNotNew() {
        isNewEntity = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        return id == (other as BaseEntity).id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "${Hibernate.getClass(this).simpleName}(id=$id)"
}
