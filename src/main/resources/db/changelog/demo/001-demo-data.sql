--liquibase formatted sql

-- Демонстрационные данные из раздела отчёта "Демонстрационные данные".
-- Идентификаторы фиксированные, чтобы сценарий защиты можно было пройти в Swagger по шагам.

--changeset carsharing:demo-001-users context:demo
INSERT INTO app_user (id, email, phone, full_name, birth_date, role, status, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'client@carsharing.local', '+79990000001',
        'Иван Клиентов', '1995-05-15', 'CLIENT', 'ACTIVE', now(), now()),
       ('10000000-0000-0000-0000-000000000002', 'client2@carsharing.local', '+79990000002',
        'Пётр Второй', '1998-03-10', 'CLIENT', 'ACTIVE', now(), now()),
       ('10000000-0000-0000-0000-000000000003', 'support@carsharing.local', '+79990000003',
        'Ольга Поддержкина', '1990-11-02', 'SUPPORT', 'ACTIVE', now(), now()),
       ('10000000-0000-0000-0000-000000000004', 'mechanic@carsharing.local', '+79990000004',
        'Сергей Механиков', '1987-07-21', 'FLEET_MECHANIC', 'ACTIVE', now(), now()),
       ('10000000-0000-0000-0000-000000000005', 'supervisor@carsharing.local', '+79990000005',
        'Анна Руководова', '1985-01-30', 'SUPERVISOR', 'ACTIVE', now(), now());

INSERT INTO driver_license (id, user_id, number, issued_at, expires_at, first_issued_at, categories,
                            verification_status, verified_by, verified_at, created_at, updated_at)
VALUES ('11000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '7700123456',
        '2019-06-01', '2029-06-01', '2019-06-01', 'B', 'APPROVED',
        '10000000-0000-0000-0000-000000000003', now(), now(), now()),
       ('11000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', '7800654321',
        '2020-01-15', '2030-01-15', '2016-08-20', 'B,BE', 'APPROVED',
        '10000000-0000-0000-0000-000000000003', now(), now(), now());
--rollback DELETE FROM driver_license WHERE id::text LIKE '11000000-%'; DELETE FROM app_user WHERE id::text LIKE '10000000-%';

--changeset carsharing:demo-002-wallets context:demo
INSERT INTO wallet (id, user_id, balance, held_amount, created_at, updated_at)
VALUES ('12000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 5000.00, 0, now(), now()),
       ('12000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 5000.00, 0, now(), now());

INSERT INTO payment (id, user_id, rental_id, payment_type, amount, status, idempotency_key, created_at, updated_at)
VALUES ('13000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', NULL, 'TOP_UP',
        5000.00, 'SUCCEEDED', 'demo:top-up:client-1', now(), now()),
       ('13000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', NULL, 'TOP_UP',
        5000.00, 'SUCCEEDED', 'demo:top-up:client-2', now(), now());
--rollback DELETE FROM payment WHERE id::text LIKE '13000000-%'; DELETE FROM wallet WHERE id::text LIKE '12000000-%';

--changeset carsharing:demo-003-fleet context:demo
INSERT INTO vehicle_model (id, brand, model, vehicle_class, fuel_type, seats, service_interval_km, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000001', 'Kia', 'Rio', 'ECONOMY', 'PETROL', 5, 15000, now(), now()),
       ('20000000-0000-0000-0000-000000000002', 'Skoda', 'Octavia', 'COMFORT', 'PETROL', 5, 15000, now(), now()),
       ('20000000-0000-0000-0000-000000000003', 'BMW', '320i', 'BUSINESS', 'PETROL', 5, 10000, now(), now()),
       ('20000000-0000-0000-0000-000000000004', 'Lada', 'Largus', 'CARGO', 'PETROL', 2, 15000, now(), now());

INSERT INTO parking_zone (id, name, zone_type, center_latitude, center_longitude, radius_m, is_finish_allowed,
                          finish_surcharge, is_active, created_at, updated_at)
VALUES ('21000000-0000-0000-0000-000000000001', 'Город', 'HOME', 59.9386, 30.3141, 15000, true, 0, true, now(), now()),
       ('21000000-0000-0000-0000-000000000002', 'Аэропорт', 'AIRPORT', 59.8003, 30.2625, 2000, true, 500, true, now(), now()),
       ('21000000-0000-0000-0000-000000000003', 'Промзона', 'RESTRICTED', 59.8700, 30.3600, 1500, false, 0, true, now(), now());

-- Первая Kia Rio почти на пороге ТО: после поездки на 12 км автоматически получит наряд.
INSERT INTO vehicle (id, vin, plate_number, model_id, status, odometer_km, fuel_level_percent, latitude, longitude,
                     current_zone_id, last_service_odometer_km, telemetry_updated_at, version, created_at, updated_at)
VALUES ('22000000-0000-0000-0000-000000000001', 'Z94K241BAMR000001', 'А001АА178', '20000000-0000-0000-0000-000000000001',
        'AVAILABLE', 14990, 80, 59.9343, 30.3351, '21000000-0000-0000-0000-000000000001', 0, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000002', 'Z94K241BAMR000002', 'А002АА178', '20000000-0000-0000-0000-000000000001',
        'AVAILABLE', 5230, 64, 59.9398, 30.3146, '21000000-0000-0000-0000-000000000001', 0, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000003', 'Z94K241BAMR000003', 'В003ВВ178', '20000000-0000-0000-0000-000000000001',
        'AVAILABLE', 22100, 55, 59.9311, 30.3609, '21000000-0000-0000-0000-000000000001', 15000, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000004', 'TMBAG7NE1K0000004', 'Е004ЕЕ178', '20000000-0000-0000-0000-000000000002',
        'AVAILABLE', 10300, 90, 59.9500, 30.3160, '21000000-0000-0000-0000-000000000001', 0, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000005', 'TMBAG7NE1K0000005', 'К005КК178', '20000000-0000-0000-0000-000000000002',
        'AVAILABLE', 3000, 70, 59.9270, 30.3200, '21000000-0000-0000-0000-000000000001', 0, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000006', 'WBA5R1C05LA000006', 'М006ММ178', '20000000-0000-0000-0000-000000000003',
        'AVAILABLE', 8000, 95, 59.9420, 30.2950, '21000000-0000-0000-0000-000000000001', 0, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000007', 'XTAKS0Y5LK0000007', 'Н007НН178', '20000000-0000-0000-0000-000000000004',
        'AVAILABLE', 40100, 60, 59.9150, 30.3150, '21000000-0000-0000-0000-000000000001', 30000, now(), 0, now(), now()),
       ('22000000-0000-0000-0000-000000000008', 'XTAKS0Y5LK0000008', 'Р008РР178', '20000000-0000-0000-0000-000000000004',
        'AVAILABLE', 1200, 18, 59.9560, 30.3350, '21000000-0000-0000-0000-000000000001', 0, now(), 0, now(), now());

INSERT INTO spare_part (id, article, name, stock_quantity, price, version, created_at, updated_at)
VALUES ('23000000-0000-0000-0000-000000000001', 'OIL-FILTER-RIO', 'Масляный фильтр Kia Rio', 20, 650.00, 0, now(), now()),
       ('23000000-0000-0000-0000-000000000002', 'ENGINE-OIL-5W30-4L', 'Моторное масло 5W-30, 4 л', 30, 3200.00, 0, now(), now()),
       ('23000000-0000-0000-0000-000000000003', 'BRAKE-PADS-OCTAVIA', 'Тормозные колодки Skoda Octavia', 8, 4100.00, 0, now(), now());

INSERT INTO spare_part_compatibility (part_id, model_id)
VALUES ('23000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001'),
       ('23000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001'),
       ('23000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002'),
       ('23000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000003'),
       ('23000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000004'),
       ('23000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000002');
--rollback DELETE FROM spare_part_compatibility WHERE part_id::text LIKE '23000000-%'; DELETE FROM spare_part WHERE id::text LIKE '23000000-%'; DELETE FROM vehicle WHERE id::text LIKE '22000000-%'; DELETE FROM parking_zone WHERE id::text LIKE '21000000-%'; DELETE FROM vehicle_model WHERE id::text LIKE '20000000-%';

--changeset carsharing:demo-004-tariffs context:demo
INSERT INTO tariff (id, name, price_per_minute, price_per_km, waiting_price_per_minute, free_reservation_minutes,
                    deposit_amount, valid_from, valid_to, status, created_at, updated_at)
VALUES ('30000000-0000-0000-0000-000000000001', 'Базовый', 8.00, 3.00, 2.50, 20, 3000.00, '2026-01-01T00:00:00Z',
        NULL, 'ACTIVE', now(), now()),
       ('30000000-0000-0000-0000-000000000002', 'Бизнес', 15.00, 5.00, 4.00, 15, 5000.00, '2026-01-01T00:00:00Z',
        NULL, 'ACTIVE', now(), now());

INSERT INTO tariff_vehicle_model (tariff_id, model_id)
VALUES ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001'),
       ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002'),
       ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000004'),
       ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000003');

INSERT INTO rental_option (id, code, name, price, price_unit, is_active, created_at, updated_at)
VALUES ('31000000-0000-0000-0000-000000000001', 'CHILD_SEAT', 'Детское кресло', 150.00, 'PER_RENTAL', true, now(), now()),
       ('31000000-0000-0000-0000-000000000002', 'FRANCHISE_REDUCTION', 'Снижение франшизы', 2.00, 'PER_MINUTE', true, now(), now());
--rollback DELETE FROM rental_option WHERE id::text LIKE '31000000-%'; DELETE FROM tariff_vehicle_model WHERE tariff_id::text LIKE '30000000-%'; DELETE FROM tariff WHERE id::text LIKE '30000000-%';
