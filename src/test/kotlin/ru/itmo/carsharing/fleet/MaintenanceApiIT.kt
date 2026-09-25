package ru.itmo.carsharing.fleet

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.itmo.carsharing.support.ApiResponse
import ru.itmo.carsharing.support.IntegrationTest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MaintenanceApiIT : IntegrationTest() {

    private lateinit var modelId: UUID
    private lateinit var vehicleId: UUID
    private lateinit var mechanicId: UUID

    @BeforeEach
    fun fleet() {
        modelId = fixtures.model()
        vehicleId = fixtures.vehicle(modelId, odometerKm = 5000)
        mechanicId = fixtures.createUser("FLEET_MECHANIC")
    }

    private fun part(
        article: String = "OIL-${fixtures.next()}",
        stock: Int = 10,
        price: Number = 650,
        compatible: Boolean = true,
    ): UUID {
        val id = api.post(
            "/api/v1/spare-parts",
            mapOf("article" to article, "name" to "Масляный фильтр", "stockQuantity" to stock, "price" to price),
        ).id()
        if (compatible) api.put("/api/v1/spare-parts/$id/models", mapOf("modelIds" to listOf(modelId)))
        return id
    }

    private fun openTask(type: String = "REPAIR"): ApiResponse = api.post(
        "/api/v1/maintenance-tasks",
        mapOf(
            "vehicleId" to vehicleId,
            "taskType" to type,
            "description" to "Стук в подвеске",
        ),
    )

    private fun take(taskId: UUID, mechanic: UUID = mechanicId) =
        api.post("/api/v1/maintenance-tasks/$taskId/take", mapOf("mechanicId" to mechanic))

    private fun writeOff(taskId: UUID, partId: UUID, quantity: Int) =
        api.post("/api/v1/maintenance-tasks/$taskId/parts", mapOf("partId" to partId, "quantity" to quantity))

    private fun vehicleStatus() = api.get("/api/v1/vehicles/$vehicleId").string("$.status")

    @Test
    fun `full maintenance cycle returns the vehicle to the fleet`() {
        val partId = part(price = 650)
        val created = openTask()
        val taskId = created.uuid("$.task.id")
        assertThat(created.status).isEqualTo(201)
        assertThat(vehicleStatus()).isEqualTo("SERVICE")

        val taken = take(taskId)
        val withParts = writeOff(taskId, partId, 2)
        writeOff(taskId, partId, 1)
        val closed = api.post("/api/v1/maintenance-tasks/$taskId/close")

        assertThat(taken.string("$.task.status")).isEqualTo("IN_PROGRESS")
        assertThat(taken.uuid("$.task.assignedTo")).isEqualTo(mechanicId)
        assertThat(withParts.decimal("$.partsTotal")).isEqualByComparingTo("1300")
        assertThat(closed.string("$.task.status")).isEqualTo("DONE")
        assertThat(closed.path<Int>("$.parts[0].quantity")).isEqualTo(3)
        assertThat(api.get("/api/v1/spare-parts/$partId").path<Int>("$.stockQuantity")).isEqualTo(7)
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
        assertThat(api.get("/api/v1/maintenance-tasks/$taskId").path<List<Any>>("$.parts")).hasSize(1)
    }

    @Test
    fun `vehicle stays in service until the last open task is closed`() {
        val repair = openTask("REPAIR").uuid("$.task.id")
        val washing = openTask("WASHING").uuid("$.task.id")

        take(repair)
        api.post("/api/v1/maintenance-tasks/$repair/close")
        assertThat(vehicleStatus()).isEqualTo("SERVICE")

        api.post("/api/v1/maintenance-tasks/$washing/cancel")
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
    }

    @Test
    fun `closing scheduled service records the service odometer`() {
        val taskId = openTask("SCHEDULED_SERVICE").uuid("$.task.id")
        fixtures.telemetry(vehicleId, 5100)
        take(taskId)

        api.post("/api/v1/maintenance-tasks/$taskId/close")

        assertThat(api.get("/api/v1/vehicles/$vehicleId").path<Int>("$.lastServiceOdometerKm")).isEqualTo(5100)
    }

    @Test
    fun `closing refueling fills the tank`() {
        val taskId = openTask("REFUELING").uuid("$.task.id")
        take(taskId)

        api.post("/api/v1/maintenance-tasks/$taskId/close")

        assertThat(api.get("/api/v1/vehicles/$vehicleId").path<Int>("$.fuelLevelPercent")).isEqualTo(100)
    }

    @Test
    fun `cancelled task returns written off parts to stock`() {
        val partId = part(stock = 5)
        val taskId = openTask().uuid("$.task.id")
        take(taskId)
        writeOff(taskId, partId, 3)

        val cancelled = api.post("/api/v1/maintenance-tasks/$taskId/cancel")

        assertThat(cancelled.string("$.task.status")).isEqualTo("CANCELLED")
        assertThat(cancelled.path<List<Any>>("$.parts")).isEmpty()
        assertThat(api.get("/api/v1/spare-parts/$partId").path<Int>("$.stockQuantity")).isEqualTo(5)
        assertThat(vehicleStatus()).isEqualTo("AVAILABLE")
    }

    @Test
    fun `second open task of the same type is rejected`() {
        openTask("WASHING")

        val duplicate = openTask("WASHING")

        assertThat(duplicate.status).isEqualTo(409)
    }

    @Test
    fun `only mechanic can take a task and only once`() {
        val taskId = openTask().uuid("$.task.id")
        val client = fixtures.createUser("CLIENT")

        assertThat(take(taskId, client).status).isEqualTo(422)
        assertThat(take(taskId).status).isEqualTo(200)
        assertThat(take(taskId).status).isEqualTo(409)
        assertThat(take(UUID.randomUUID()).status).isEqualTo(404)
    }

    @Test
    fun `parts are written off only into a task in progress and only compatible ones`() {
        val taskId = openTask().uuid("$.task.id")
        val compatible = part()
        val incompatible = part(compatible = false)

        val beforeTake = writeOff(taskId, compatible, 1)
        take(taskId)
        val wrongModel = writeOff(taskId, incompatible, 1)
        val tooMany = writeOff(taskId, compatible, 11)

        assertThat(beforeTake.status).isEqualTo(409)
        assertThat(wrongModel.status).isEqualTo(422)
        assertThat(wrongModel.errorType()).isEqualTo("incompatible-part")
        assertThat(tooMany.status).isEqualTo(409)
        assertThat(tooMany.errorType()).isEqualTo("insufficient-stock")
    }

    @Test
    fun `two mechanics cannot write off the last part twice`() {
        val partId = part(stock = 1)
        val secondVehicle = fixtures.vehicle(modelId)
        val firstTask = openTask().uuid("$.task.id")
        val secondTask = api.post(
            "/api/v1/maintenance-tasks",
            mapOf(
                "vehicleId" to secondVehicle,
                "taskType" to "REPAIR",
            ),
        )
            .uuid("$.task.id")
        take(firstTask)
        take(secondTask, fixtures.createUser("FLEET_MECHANIC"))

        val statuses = runConcurrently(
            { writeOff(firstTask, partId, 1).status },
            { writeOff(secondTask, partId, 1).status },
        )

        assertThat(statuses).containsExactlyInAnyOrder(200, 409)
        assertThat(api.get("/api/v1/spare-parts/$partId").path<Int>("$.stockQuantity")).isZero()
    }

    @Test
    fun `task cannot be opened on a vehicle in rental or on unknown vehicle`() {
        jdbc.update("UPDATE vehicle SET status = 'IN_RENTAL' WHERE id = ?", vehicleId)

        assertThat(openTask().status).isEqualTo(409)
        assertThat(
            api.post(
                "/api/v1/maintenance-tasks",
                mapOf("vehicleId" to UUID.randomUUID(), "taskType" to "REPAIR"),
            ).status,
        ).isEqualTo(404)
    }

    @Test
    fun `task list filters by status and vehicle`() {
        val taskId = openTask().uuid("$.task.id")
        openTask("WASHING")
        take(taskId)

        val inProgress = api.get("/api/v1/maintenance-tasks", "status" to "IN_PROGRESS")
        val byVehicle = api.get("/api/v1/maintenance-tasks", "vehicleId" to vehicleId)

        assertThat(inProgress.path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(byVehicle.path<Int>("$.totalElements")).isEqualTo(2)
    }

    @Test
    fun `vehicle with open tasks cannot be decommissioned`() {
        openTask()

        assertThat(api.delete("/api/v1/vehicles/$vehicleId").status).isEqualTo(409)
    }

    @Test
    fun `spare part CRUD, compatibility and deletion rules`() {
        val partId = part(article = "PADS-1")
        val otherModel = fixtures.model("COMFORT")

        val duplicate = api.post(
            "/api/v1/spare-parts",
            mapOf(
                "article" to "PADS-1",
                "name" to "X",
                "stockQuantity" to 1,
                "price" to 1,
            ),
        )
        val updated = api.put(
            "/api/v1/spare-parts/$partId",
            mapOf(
                "article" to "PADS-1",
                "name" to "Колодки",
                "stockQuantity" to 40,
                "price" to 4100,
            ),
        )
        val models = api.put("/api/v1/spare-parts/$partId/models", mapOf("modelIds" to listOf(modelId, otherModel)))
        val unknownModel = api.put("/api/v1/spare-parts/$partId/models", mapOf("modelIds" to listOf(UUID.randomUUID())))
        val list = api.get("/api/v1/spare-parts")

        assertThat(duplicate.status).isEqualTo(409)
        assertThat(updated.path<Int>("$.stockQuantity")).isEqualTo(40)
        assertThat(models.path<Int>("$.totalElements")).isEqualTo(2)
        assertThat(api.get("/api/v1/spare-parts/$partId/models", "size" to 1).path<Int>("$.totalPages")).isEqualTo(2)
        assertThat(unknownModel.status).isEqualTo(404)
        assertThat(list.path<Int>("$.totalElements")).isEqualTo(1)
        assertThat(api.delete("/api/v1/vehicle-models/$otherModel").status).isEqualTo(409)

        val taskId = openTask().uuid("$.task.id")
        take(taskId)
        writeOff(taskId, partId, 1)
        assertThat(api.delete("/api/v1/spare-parts/$partId").status).isEqualTo(409)
        val unused = part()
        assertThat(api.delete("/api/v1/spare-parts/$unused").status).isEqualTo(204)
    }

    private fun runConcurrently(vararg actions: () -> Int): List<Int> {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(actions.size)
        try {
            val futures = actions.map { action ->
                pool.submit<Int> {
                    start.await()
                    action()
                }
            }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
