--liquibase formatted sql

--changeset carsharing:cross-domain-001-foreign-keys
ALTER TABLE maintenance_task
    ADD CONSTRAINT fk_maintenance_task_assigned_to FOREIGN KEY (assigned_to) REFERENCES app_user (id);

ALTER TABLE tariff_vehicle_model
    ADD CONSTRAINT fk_tariff_vehicle_model_model FOREIGN KEY (model_id) REFERENCES vehicle_model (id);

ALTER TABLE rental
    ADD CONSTRAINT fk_rental_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    ADD CONSTRAINT fk_rental_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    ADD CONSTRAINT fk_rental_start_zone FOREIGN KEY (start_zone_id) REFERENCES parking_zone (id),
    ADD CONSTRAINT fk_rental_finish_zone FOREIGN KEY (finish_zone_id) REFERENCES parking_zone (id);

ALTER TABLE traffic_fine
    ADD CONSTRAINT fk_traffic_fine_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    ADD CONSTRAINT fk_traffic_fine_payment FOREIGN KEY (payment_id) REFERENCES payment (id);

ALTER TABLE wallet
    ADD CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES app_user (id);

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    ADD CONSTRAINT fk_payment_rental FOREIGN KEY (rental_id) REFERENCES rental (id);
--rollback ALTER TABLE payment DROP CONSTRAINT fk_payment_rental, DROP CONSTRAINT fk_payment_user;
--rollback ALTER TABLE wallet DROP CONSTRAINT fk_wallet_user;
--rollback ALTER TABLE traffic_fine DROP CONSTRAINT fk_traffic_fine_payment, DROP CONSTRAINT fk_traffic_fine_vehicle;
--rollback ALTER TABLE rental DROP CONSTRAINT fk_rental_finish_zone, DROP CONSTRAINT fk_rental_start_zone, DROP CONSTRAINT fk_rental_vehicle, DROP CONSTRAINT fk_rental_user;
--rollback ALTER TABLE tariff_vehicle_model DROP CONSTRAINT fk_tariff_vehicle_model_model;
--rollback ALTER TABLE maintenance_task DROP CONSTRAINT fk_maintenance_task_assigned_to;
