--liquibase formatted sql

--changeset carsharing:users-001-app-user
CREATE TABLE app_user
(
    id         uuid PRIMARY KEY,
    email      varchar(254) NOT NULL,
    phone      varchar(16)  NOT NULL,
    full_name  varchar(200) NOT NULL,
    birth_date date         NOT NULL,
    role       varchar(32)  NOT NULL,
    status     varchar(32)  NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT uq_app_user_phone UNIQUE (phone),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CLIENT', 'SUPPORT', 'FLEET_MECHANIC', 'SUPERVISOR')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);
--rollback DROP TABLE app_user;

--changeset carsharing:users-002-driver-license
CREATE TABLE driver_license
(
    id                  uuid PRIMARY KEY,
    user_id             uuid         NOT NULL,
    number              varchar(10)  NOT NULL,
    issued_at           date         NOT NULL,
    expires_at          date         NOT NULL,
    first_issued_at     date         NOT NULL,
    categories          varchar(64)  NOT NULL,
    verification_status varchar(32)  NOT NULL,
    verified_by         uuid,
    verified_at         timestamptz,
    rejection_reason    varchar(500),
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT fk_driver_license_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_driver_license_verified_by FOREIGN KEY (verified_by) REFERENCES app_user (id),
    CONSTRAINT uq_driver_license_number UNIQUE (number),
    CONSTRAINT ck_driver_license_status
        CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'REPLACED')),
    -- Срок до 13 лет: правам, истекавшим в 2022-2025 годах, действие продлено на три года.
    CONSTRAINT ck_driver_license_validity
        CHECK (expires_at > issued_at AND expires_at <= issued_at + INTERVAL '13 years'),
    CONSTRAINT ck_driver_license_first_issued CHECK (first_issued_at <= issued_at),
    CONSTRAINT ck_driver_license_categories CHECK (categories ~
        '^(A|A1|B|B1|BE|C|C1|CE|C1E|D|D1|DE|D1E|M|TM|TB)(,(A|A1|B|B1|BE|C|C1|CE|C1E|D|D1|DE|D1E|M|TM|TB))*$')
);

-- Инвариант "не больше одного одобренного ВУ на клиента" продублирован в базе.
CREATE UNIQUE INDEX ux_driver_license_approved_per_user
    ON driver_license (user_id) WHERE verification_status = 'APPROVED';
CREATE INDEX ix_driver_license_user_created_at ON driver_license (user_id, created_at);
--rollback DROP TABLE driver_license;
