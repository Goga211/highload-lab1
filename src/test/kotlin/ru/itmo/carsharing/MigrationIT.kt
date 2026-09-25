package ru.itmo.carsharing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.IntegrationTest

class MigrationIT : IntegrationTest() {

    @Test
    fun `all migrations are applied to an empty database`() {
        val tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String::class.java,
        )

        assertThat(tables).contains(
            "app_user", "driver_license", "wallet", "payment", "vehicle_model", "vehicle", "parking_zone",
            "maintenance_task", "spare_part", "maintenance_part", "spare_part_compatibility", "tariff",
            "tariff_vehicle_model", "rental_option", "rental", "rental_option_item", "traffic_fine",
        )
        val applied = jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Int::class.java)
        assertThat(applied).isGreaterThanOrEqualTo(15)
    }

    @Test
    fun `demo data is not loaded in tests`() {
        val demoChangesets = jdbc.queryForObject(
            "SELECT count(*) FROM databasechangelog WHERE id LIKE 'demo-%'",
            Int::class.java,
        )

        assertThat(demoChangesets).isZero()
    }

    @Test
    fun `partial unique indexes protect active rental invariants`() {
        val indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename IN ('rental', 'maintenance_task', 'driver_license')",
            String::class.java,
        )

        assertThat(indexes).contains(
            "ux_rental_active_vehicle",
            "ux_rental_active_user",
            "ux_maintenance_task_open_per_type",
            "ux_driver_license_approved_per_user",
        )
    }
}
