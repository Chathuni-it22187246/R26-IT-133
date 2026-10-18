package com.greenhands.app

import com.greenhands.app.heat.data.CropRowCount
import com.greenhands.app.heat.data.HeatSessionCodec
import com.greenhands.app.heat.data.HeatSessionPolicy
import com.greenhands.app.heat.data.HeatSessionRecord
import com.greenhands.app.heat.data.InMemoryHeatConfigRepository
import com.greenhands.app.heat.data.InMemoryHeatSessionRepository
import com.greenhands.app.heat.data.UserHeatSession
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.ui.HeatConfigViewModel
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeatSessionRecordsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun tomatoFlower() = CropProfileRegistry.stageProfile(Crop.TOMATO, "flowering").stage
    private fun cucumberVeg() = CropProfileRegistry.stageProfile(Crop.SALAD_CUCUMBER, "vegetative").stage

    private fun record(
        id: String,
        created: Long,
        cropId: String = "tomato",
        stageId: String = "flowering",
        rows: Int = CropRowCount.DEFAULT
    ) = HeatSessionRecord(
        id = id,
        createdAtMillis = created,
        cropId = cropId,
        cropDisplayName = cropId,
        stageId = stageId,
        stageDisplayName = stageId,
        cropRowCount = rows
    )

    private fun customDims() = GreenhouseDimensions(
        lengthM = 8.0,
        widthM = 4.0,
        heightM = 3.0,
        provenance = ProvenanceClass.MEASURED_INPUT,
        provenanceNote = "test"
    )

    private fun boundVm(
        userId: String,
        heatRepo: InMemoryHeatConfigRepository = InMemoryHeatConfigRepository(),
        sessionRepo: InMemoryHeatSessionRepository = InMemoryHeatSessionRepository()
    ): Pair<HeatConfigViewModel, InMemoryHeatSessionRepository> {
        val vm = HeatConfigViewModel(heatRepo, sessionRepo)
        vm.bindUserSession(userId)
        return vm to sessionRepo
    }

    @Test
    fun newUserStartsWithEmptyActiveAndRecent() {
        val (vm, _) = boundVm("new@greenhands.app")
        assertNull(vm.state.value.activeSession)
        assertTrue(vm.state.value.recentSessions.isEmpty())
        vm.selectCrop(Crop.TOMATO)
        assertTrue(vm.onStageClicked(tomatoFlower()))
        assertNull(vm.state.value.activeSession)
        assertTrue(vm.state.value.recentSessions.isEmpty())
    }

    @Test
    fun startingASimulationCreatesOneActiveConfiguration() {
        val (vm, repo) = boundVm("grower@greenhands.app")
        vm.selectCrop(Crop.TOMATO)
        assertTrue(vm.onStageClicked(tomatoFlower()))
        vm.recordStartedSimulation()
        val active = vm.state.value.activeSession
        assertEquals("tomato", active?.cropId)
        assertEquals("flowering", active?.stageId)
        assertEquals(1, listOfNotNull(active).size)
        assertTrue(vm.state.value.recentSessions.isEmpty())
        assertEquals("tomato", repo.snapshot("grower@greenhands.app").active?.cropId)
    }

    @Test
    fun returningDuringTheSameSessionPreservesActiveConfiguration() {
        val (vm, _) = boundVm("grower@greenhands.app")
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        vm.recordStartedSimulation()
        val firstId = vm.state.value.activeSession!!.id
        vm.bindUserSession("grower@greenhands.app")
        assertEquals(firstId, vm.state.value.activeSession?.id)
        assertTrue(vm.state.value.recentSessions.isEmpty())
    }

    @Test
    fun startingAnotherConfigurationArchivesThePreviousActive() {
        val (vm, _) = boundVm("grower@greenhands.app")
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        vm.recordStartedSimulation()
        val firstId = vm.state.value.activeSession!!.id
        vm.selectCrop(Crop.SALAD_CUCUMBER)
        vm.onStageClicked(cucumberVeg())
        vm.recordStartedSimulation()
        assertEquals("salad_cucumber", vm.state.value.activeSession?.cropId)
        assertEquals(1, vm.state.value.recentSessions.size)
        assertEquals(firstId, vm.state.value.recentSessions.single().id)
        assertEquals("tomato", vm.state.value.recentSessions.single().cropId)
    }

    @Test
    fun newLoginOrColdSessionArchivesPreviousActive() {
        val sessionRepo = InMemoryHeatSessionRepository()
        val first = HeatConfigViewModel(InMemoryHeatConfigRepository(), sessionRepo)
        first.bindUserSession("grower@greenhands.app")
        first.selectCrop(Crop.TOMATO)
        first.onStageClicked(tomatoFlower())
        first.recordStartedSimulation()
        val archivedId = first.state.value.activeSession!!.id
        first.unbindUserSession()

        val second = HeatConfigViewModel(InMemoryHeatConfigRepository(), sessionRepo)
        second.bindUserSession("grower@greenhands.app")
        assertNull(second.state.value.activeSession)
        assertEquals(1, second.state.value.recentSessions.size)
        assertEquals(archivedId, second.state.value.recentSessions.single().id)
    }

    @Test
    fun recentConfigurationsAreIsolatedByUser() {
        val sessionRepo = InMemoryHeatSessionRepository()
        val alice = HeatConfigViewModel(InMemoryHeatConfigRepository(), sessionRepo)
        alice.bindUserSession("alice@greenhands.app")
        alice.selectCrop(Crop.TOMATO)
        alice.onStageClicked(tomatoFlower())
        alice.recordStartedSimulation()
        alice.unbindUserSession()
        alice.bindUserSession("alice@greenhands.app")

        val bob = HeatConfigViewModel(InMemoryHeatConfigRepository(), sessionRepo)
        bob.bindUserSession("bob@greenhands.app")
        assertNull(bob.state.value.activeSession)
        assertTrue(bob.state.value.recentSessions.isEmpty())
        assertEquals("tomato", sessionRepo.snapshot("alice@greenhands.app").recent.single().cropId)
        assertTrue(sessionRepo.snapshot("bob@greenhands.app").recent.isEmpty())
        assertNull(sessionRepo.snapshot("bob@greenhands.app").active)
    }

    @Test
    fun deletingOneRecentDoesNotDeleteOthersOrActive() {
        val stored = UserHeatSession(
            userId = "grower@greenhands.app",
            active = record("active", 30L, cropId = "lettuce", stageId = "harvest"),
            recent = listOf(
                record("recent-new", 20L, cropId = "tomato"),
                record("recent-old", 10L, cropId = "chilli", stageId = "vegetative")
            )
        )
        val after = HeatSessionPolicy.deleteRecent(stored, "recent-new")
        assertEquals("active", after.active?.id)
        assertEquals(listOf("recent-old"), after.recent.map { it.id })

        val blocked = HeatSessionPolicy.deleteRecent(stored, "active")
        assertEquals("active", blocked.active?.id)
        assertEquals(2, blocked.recent.size)

        val sessionRepo = InMemoryHeatSessionRepository()
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository(), sessionRepo)
        vm.bindUserSession("grower@greenhands.app")
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        vm.recordStartedSimulation()
        vm.selectCrop(Crop.SALAD_CUCUMBER)
        vm.onStageClicked(cucumberVeg())
        vm.recordStartedPreview(PreviewMode.DEMO, DemoGreenhouseProfile.dimensions(), 6)
        val activeId = vm.state.value.activeSession!!.id
        val recentId = vm.state.value.recentSessions.single().id
        vm.deleteRecent(recentId)
        assertEquals(activeId, vm.state.value.activeSession?.id)
        assertTrue(vm.state.value.recentSessions.isEmpty())
        assertEquals(activeId, sessionRepo.snapshot("grower@greenhands.app").active?.id)
    }

    @Test
    fun oldRecordsWithoutRowCountDefaultToThree() {
        val encoded = "id=legacy|created=1|crop=tomato|cropName=Tomato|stage=flowering|stageName=Flowering|dayT=25.5|nightT=19|dayRh=75|nightRh=75|mode=DEMO|length=10.0|width=5.0|height=3.0"
        val decoded = HeatSessionCodec.decode(encoded)!!
        assertEquals(3, decoded.resolvedCropRowCount)
        assertEquals(3, decoded.cropRowCount)
        val roundTrip = HeatSessionCodec.decode(HeatSessionCodec.encode(decoded.copy(cropRowCount = 6)))!!
        assertEquals(6, roundTrip.cropRowCount)
    }

    @Test
    fun beginSessionArchivesActiveWithoutChangingOtherUsers() {
        val stored = UserHeatSession(
            userId = "a@x.com",
            active = record("a1", 5L),
            recent = listOf(record("r1", 1L, cropId = "chilli"))
        )
        val started = HeatSessionPolicy.beginSession(stored)
        assertNull(started.active)
        assertEquals(listOf("a1", "r1"), started.recent.map { it.id })
    }

    @Test
    fun previewStartUsesDemoRowCountEvenIfCustomValuePassed() {
        val (vm, _) = boundVm("grower@greenhands.app")
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        vm.recordStartedPreview(PreviewMode.DEMO, DemoGreenhouseProfile.dimensions(), 20)
        assertEquals(DemoGreenhouseProfile.CROP_ROW_COUNT, vm.state.value.activeSession?.cropRowCount)
        vm.recordStartedPreview(PreviewMode.MY_GREENHOUSE, customDims(), 6)
        assertEquals(PreviewMode.MY_GREENHOUSE.name, vm.state.value.activeSession?.previewMode)
        assertEquals(6, vm.state.value.activeSession?.cropRowCount)
        assertEquals(1, vm.state.value.recentSessions.size)
    }
}
