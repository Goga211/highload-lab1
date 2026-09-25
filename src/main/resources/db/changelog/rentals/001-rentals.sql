--liquibase formatted sql

--changeset carsharing:rentals-001-tariff
CREATE TABLE tariff
(
    id                       uuid PRIMARY KEY,
    name                     varchar(100)   NOT NULL,
    price_per_minute         numeric(12, 2) NOT NULL,
    price_per_km             numeric(12, 2) NOT NULL,
    waiting_price_per_minute numeric(12, 2) NOT NULL,
    free_reservation_minutes integer        NOT NULL,
    deposit_amount           numeric(12, 2) NOT NULL,
    valid_from               timestamptz    NOT NULL,
    valid_to                 timestamptz,
    status                   varchar(32)    NOT NULL,
    created_at               timestamptz    NOT NULL,
    updated_at               timestamptz    NOT NULL,
    CONSTRAINT ck_tariff_prices
        CHECK (price_per_minute > 0 AND price_per_km > 0 AND waiting_price_per_minute > 0 AND deposit_amount > 0),
    CONSTRAINT ck_tariff_free_minutes CHECK (free_reservation_minutes BETWEEN 0 AND 120),
    CONSTRAINT ck_tariff_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_tariff_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE tariff_vehicle_model
(
    tariff_id uuid NOT NULL,
    model_id  uuid NOT NULL,
    CONSTRAINT pk_tariff_vehicle_model PRIMARY KEY (tariff_id, model_id),
    CONSTRAINT fk_tariff_vehicle_model_tariff FOREIGN KEY (tariff_id) REFERENCES tariff (id) ON DELETE CASCADE
);

CREATE INDEX ix_tariff_vehicle_model_model ON tariff_vehicle_model (model_id);
--rollback DROP TABLE tariff_vehicle_model; DROP TABLE tariff;

--changeset carsharing:rentals-002-rental-option
CREATE TABLE rental_option
(
    id         uuid PRIMARY KEY,
    code       varchar(64)    NOT NULL,
    name       varchar(200)   NOT NULL,
    price      numeric(12, 2) NOT NULL,
    price_unit varchar(32)    NOT NULL,
    is_active  boolean        NOT NULL,
    created_at timestamptz    NOT NULL,
    updated_at timestamptz    NOT NULL,
    CONSTRAINT uq_rental_option_code UNIQUE (code),
    CONSTRAINT ck_rental_option_price CHECK (price > 0),
    CONSTRAINT ck_rental_option_unit CHECK (price_unit IN ('PER_RENTAL', 'PER_MINUTE'))
);
--rollback DROP TABLE rental_option;

--changeset carsharing:rentals-003-rental
CREATE TABLE rental
(
    id                               uuid PRIMARY KEY,
    user_id                          uuid           NOT NULL,
    vehicle_id                       uuid           NOT NULL,
    tariff_id                        uuid           NOT NULL,
    status                           varchar(32)    NOT NULL,
    reserved_at                      timestamptz    NOT NULL,
    started_at                       timestamptz,
    finished_at                      timestamptz,
    cancelled_at                     timestamptz,
    cancel_reason                    varchar(500),
    start_zone_id                    uuid,
    finish_zone_id                   uuid,
    start_odometer_km                integer,
    finish_odometer_km               integer,
    applied_price_per_minute         numeric(12, 2) NOT NULL,
    applied_price_per_km             numeric(12, 2) NOT NULL,
    applied_waiting_price_per_minute numeric(12, 2) NOT NULL,
    applied_free_reservation_minutes integer        NOT NULL,
    applied_deposit                  numeric(12, 2) NOT NULL,
    duration_minutes                 integer,
    waiting_minutes                  integer,
    distance_km                      integer,
    options_amount                   numeric(12, 2),
    zone_surcharge                   numeric(12, 2),
    total_amount                     numeric(12, 2),
    created_at                       timestamptz    NOT NULL,
    updated_at                       timestamptz    NOT NULL,
    CONSTRAINT fk_rental_tariff FOREIGN KEY (tariff_id) REFERENCES tariff (id),
    CONSTRAINT ck_rental_status CHECK (status IN ('RESERVED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_rental_started_at CHECK (started_at IS NULL OR started_at >= reserved_at),
    CONSTRAINT ck_rental_finished_at CHECK (finished_at IS NULL OR finished_at >= started_at),
    CONSTRAINT ck_rental_odometer CHECK (finish_odometer_km IS NULL OR finish_odometer_km >= start_odometer_km),
    CONSTRAINT ck_rental_total CHECK (total_amount IS NULL OR total_amount >= 0)
);

-- Инварианты "одна незавершённая аренда на машину и на клиента": обход сервиса не создаст вторую бронь.
CREATE UNIQUE INDEX ux_rental_active_vehicle ON rental (vehicle_id) WHERE status IN ('RESERVED', 'ACTIVE');
CREATE UNIQUE INDEX ux_rental_active_user ON rental (user_id) WHERE status IN ('RESERVED', 'ACTIVE');
CREATE INDEX ix_rental_user_reserved_at ON rental (user_id, reserved_at);
CREATE INDEX ix_rental_vehicle_started_at ON rental (vehicle_id, started_at);
CREATE INDEX ix_rental_reserved_pending ON rental (reserved_at) WHERE status = 'RESERVED';
--rollback DROP TABLE rental;

--changeset carsharing:rentals-004-rental-option-item
CREATE TABLE rental_option_item
(
    id                  uuid PRIMARY KEY,
    rental_id           uuid           NOT NULL,
    option_id           uuid           NOT NULL,
    quantity            integer        NOT NULL,
    unit_price_snapshot numeric(12, 2) NOT NULL,
    price_unit_snapshot varchar(32)    NOT NULL,
    total_amount        numeric(12, 2),
    created_at          timestamptz    NOT NULL,
    updated_at          timestamptz    NOT NULL,
    CONSTRAINT fk_rental_option_item_rental FOREIGN KEY (rental_id) REFERENCES rental (id),
    CONSTRAINT fk_rental_option_item_option FOREIGN KEY (option_id) REFERENCES rental_option (id),
    CONSTRAINT uq_rental_option_item_rental_option UNIQUE (rental_id, option_id),
    CONSTRAINT ck_rental_option_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_rental_option_item_price CHECK (unit_price_snapshot > 0),
    CONSTRAINT ck_rental_option_item_unit CHECK (price_unit_snapshot IN ('PER_RENTAL', 'PER_MINUTE'))
);
--rollback DROP TABLE rental_option_item;

--changeset carsharing:rentals-005-traffic-fine
CREATE TABLE traffic_fine
(
    id                uuid PRIMARY KEY,
    vehicle_id        uuid           NOT NULL,
    rental_id         uuid,
    payment_id        uuid,
    resolution_number varchar(25)    NOT NULL,
    violated_at       timestamptz    NOT NULL,
    amount            numeric(12, 2) NOT NULL,
    status            varchar(32)    NOT NULL,
    dispute_reason    varchar(500),
    created_at        timestamptz    NOT NULL,
    updated_at        timestamptz    NOT NULL,
    CONSTRAINT fk_traffic_fine_rental FOREIGN KEY (rental_id) REFERENCES rental (id),
    CONSTRAINT uq_traffic_fine_resolution_number UNIQUE (resolution_number),
    CONSTRAINT ck_traffic_fine_amount CHECK (amount > 0),
    CONSTRAINT ck_traffic_fine_status
        CHECK (status IN ('RECEIVED', 'REBILLED', 'NO_RENTAL', 'DISPUTED', 'CANCELLED'))
);

CREATE INDEX ix_traffic_fine_status ON traffic_fine (status);
--rollback DROP TABLE traffic_fine;
