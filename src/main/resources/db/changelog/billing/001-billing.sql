--liquibase formatted sql

--changeset carsharing:billing-001-wallet
CREATE TABLE wallet
(
    id          uuid PRIMARY KEY,
    user_id     uuid           NOT NULL,
    balance     numeric(12, 2) NOT NULL,
    held_amount numeric(12, 2) NOT NULL,
    created_at  timestamptz    NOT NULL,
    updated_at  timestamptz    NOT NULL,
    CONSTRAINT uq_wallet_user UNIQUE (user_id),
    -- Баланс может быть отрицательным: это долг клиента, с ним нельзя бронировать.
    CONSTRAINT ck_wallet_held_amount CHECK (held_amount >= 0)
);
--rollback DROP TABLE wallet;

--changeset carsharing:billing-002-payment
CREATE TABLE payment
(
    id              uuid PRIMARY KEY,
    user_id         uuid           NOT NULL,
    rental_id       uuid,
    payment_type    varchar(32)    NOT NULL,
    amount          numeric(12, 2) NOT NULL,
    status          varchar(32)    NOT NULL,
    idempotency_key varchar(200)   NOT NULL,
    created_at      timestamptz    NOT NULL,
    updated_at      timestamptz    NOT NULL,
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_type CHECK (payment_type IN
        ('TOP_UP', 'DEPOSIT_HOLD', 'DEPOSIT_RELEASE', 'RENTAL_CHARGE', 'FINE_CHARGE', 'REFUND')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payment_rental_link CHECK ((payment_type = 'TOP_UP') = (rental_id IS NULL))
);

-- Повторная операция с тем же ключом не создаст второй платёж.
CREATE UNIQUE INDEX ux_payment_idempotency_key ON payment (idempotency_key);
CREATE INDEX ix_payment_user_created_at ON payment (user_id, created_at);
--rollback DROP TABLE payment;
