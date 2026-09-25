--liquibase formatted sql

--changeset carsharing:fleet-001-vehicle-model
CREATE TABLE vehicle_model
(
    id                  uuid PRIMARY KEY,
    brand               varchar(64) NOT NULL,
    model               varchar(64) NOT NULL,
    vehicle_class       varchar(32) NOT NULL,
    fuel_type           varchar(32) NOT NULL,
    seats               integer     NOT NULL,
    service_interval_km integer     NOT NULL,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    CONSTRAINT uq_vehicle_model_brand_model UNIQUE (brand, model),
    CONSTRAINT ck_vehicle_model_class CHECK (vehicle_class IN ('ECONOMY', 'COMFORT', 'BUSINESS', 'CARGO')),
    CONSTRAINT ck_vehicle_model_fuel CHECK (fuel_type IN ('PETROL', 'DIESEL', 'ELECTRIC', 'HYBRID')),
    CONSTRAINT ck_vehicle_model_seats CHECK (seats BETWEEN 1 AND 9),
    CONSTRAINT ck_vehicle_model_service_interval CHECK (service_interval_km > 0)
);
--rollback DROP TABLE vehicle_model;

--changeset carsharing:fleet-002-parking-zone
CREATE TABLE parking_zone
(
    id                uuid PRIMARY KEY,
    name              varchar(100)     NOT NULL,
    zone_type         varchar(32)      NOT NULL,
    center_latitude   double precision NOT NULL,
    center_longitude  double precision NOT NULL,
    radius_m          integer          NOT NULL,
    is_finish_allowed boolean          NOT NULL,
    finish_surcharge  numeric(12, 2)   NOT NULL,
    is_active         boolean          NOT NULL,
    created_at        timestamptz      NOT NULL,
    updated_at        timestamptz      NOT NULL,
    CONSTRAINT uq_parking_zone_name UNIQUE (name),
    CONSTRAINT ck_parking_zone_type CHECK (zone_type IN ('HOME', 'BUSINESS', 'AIRPORT', 'RESTRICTED')),
    CONSTRAINT ck_parking_zone_latitude CHECK (center_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_parking_zone_longitude CHECK (center_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_parking_zone_radius CHECK (radius_m > 0),
    CONSTRAINT ck_parking_zone_surcharge CHECK (finish_surcharge >= 0)
);
--rollback DROP TABLE parking_zone;

--changeset carsharing:fleet-003-vehicle
CREATE TABLE vehicle
(
    id                       uuid PRIMARY KEY,
    vin                      varchar(17)      NOT NULL,
    plate_number             varchar(9)       NOT NULL,
    model_id                 uuid             NOT NULL,
    status                   varchar(32)      NOT NULL,
    odometer_km              integer          NOT NULL,
    fuel_level_percent       integer          NOT NULL,
    latitude                 double precision NOT NULL,
    longitude                double precision NOT NULL,
    current_zone_id          uuid,
    last_service_odometer_km integer          NOT NULL,
    telemetry_updated_at     timestamptz      NOT NULL,
    version                  bigint           NOT NULL DEFAULT 0,
    created_at               timestamptz      NOT NULL,
    updated_at               timestamptz      NOT NULL,
    CONSTRAINT fk_vehicle_model FOREIGN KEY (model_id) REFERENCES vehicle_model (id),
    CONSTRAINT fk_vehicle_current_zone FOREIGN KEY (current_zone_id) REFERENCES parking_zone (id),
    CONSTRAINT uq_vehicle_vin UNIQUE (vin),
    CONSTRAINT uq_vehicle_plate_number UNIQUE (plate_number),
    CONSTRAINT ck_vehicle_status
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'IN_RENTAL', 'SERVICE', 'DECOMMISSIONED')),
    CONSTRAINT ck_vehicle_odometer CHECK (odometer_km >= 0),
    CONSTRAINT ck_vehicle_fuel CHECK (fuel_level_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_vehicle_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_vehicle_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_vehicle_last_service CHECK (last_service_odometer_km BETWEEN 0 AND odometer_km)
);

CREATE INDEX ix_vehicle_status ON vehicle (status);
CREATE INDEX ix_vehicle_model ON vehicle (model_id);
-- Поиск рядом идёт только по свободным машинам, поэтому индекс частичный.
CREATE INDEX ix_vehicle_available_location ON vehicle (latitude, longitude) WHERE status = 'AVAILABLE';
--rollback DROP TABLE vehicle;

--changeset carsharing:fleet-004-maintenance-task
CREATE TABLE maintenance_task
(
    id                  uuid PRIMARY KEY,
    vehicle_id          uuid         NOT NULL,
    task_type           varchar(32)  NOT NULL,
    status              varchar(32)  NOT NULL,
    description         varchar(500),
    assigned_to         uuid,
    opened_at           timestamptz  NOT NULL,
    closed_at           timestamptz,
    odometer_at_open_km integer      NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT fk_maintenance_task_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT ck_maintenance_task_type
        CHECK (task_type IN ('SCHEDULED_SERVICE', 'REPAIR', 'WASHING', 'REFUELING')),
    CONSTRAINT ck_maintenance_task_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_maintenance_task_closed_at CHECK (closed_at IS NULL OR closed_at >= opened_at),
    CONSTRAINT ck_maintenance_task_odometer CHECK (odometer_at_open_km >= 0)
);

-- Не больше одного открытого наряда каждого типа на машину: повторный финиш не создаст дубль.
CREATE UNIQUE INDEX ux_maintenance_task_open_per_type
    ON maintenance_task (vehicle_id, task_type) WHERE status IN ('OPEN', 'IN_PROGRESS');
CREATE INDEX ix_maintenance_task_vehicle_status ON maintenance_task (vehicle_id, status);
--rollback DROP TABLE maintenance_task;

--changeset carsharing:fleet-005-spare-part
CREATE TABLE spare_part
(
    id             uuid PRIMARY KEY,
    article        varchar(64)    NOT NULL,
    name           varchar(200)   NOT NULL,
    stock_quantity integer        NOT NULL,
    price          numeric(12, 2) NOT NULL,
    version        bigint         NOT NULL DEFAULT 0,
    created_at     timestamptz    NOT NULL,
    updated_at     timestamptz    NOT NULL,
    CONSTRAINT uq_spare_part_article UNIQUE (article),
    CONSTRAINT ck_spare_part_stock CHECK (stock_quantity >= 0),
    CONSTRAINT ck_spare_part_price CHECK (price > 0)
);

CREATE TABLE maintenance_part
(
    id                  uuid PRIMARY KEY,
    task_id             uuid           NOT NULL,
    part_id             uuid           NOT NULL,
    quantity            integer        NOT NULL,
    unit_price_snapshot numeric(12, 2) NOT NULL,
    created_at          timestamptz    NOT NULL,
    updated_at          timestamptz    NOT NULL,
    CONSTRAINT fk_maintenance_part_task FOREIGN KEY (task_id) REFERENCES maintenance_task (id),
    CONSTRAINT fk_maintenance_part_part FOREIGN KEY (part_id) REFERENCES spare_part (id),
    CONSTRAINT uq_maintenance_part_task_part UNIQUE (task_id, part_id),
    CONSTRAINT ck_maintenance_part_quantity CHECK (quantity > 0),
    CONSTRAINT ck_maintenance_part_price CHECK (unit_price_snapshot >= 0)
);

CREATE TABLE spare_part_compatibility
(
    part_id  uuid NOT NULL,
    model_id uuid NOT NULL,
    CONSTRAINT pk_spare_part_compatibility PRIMARY KEY (part_id, model_id),
    CONSTRAINT fk_spare_part_compatibility_part FOREIGN KEY (part_id) REFERENCES spare_part (id) ON DELETE CASCADE,
    CONSTRAINT fk_spare_part_compatibility_model FOREIGN KEY (model_id) REFERENCES vehicle_model (id)
);

CREATE INDEX ix_spare_part_compatibility_model ON spare_part_compatibility (model_id);
--rollback DROP TABLE spare_part_compatibility; DROP TABLE maintenance_part; DROP TABLE spare_part;
