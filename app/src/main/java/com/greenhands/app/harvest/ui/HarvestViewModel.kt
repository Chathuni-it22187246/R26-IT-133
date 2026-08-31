package com.greenhands.app.harvest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greenhands.app.environment.GreenhouseEnvironmentRepository
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.UnconnectedGreenhouseEnvironmentRepository
import com.greenhands.app.harvest.data.HarvestRecordFactory
import com.greenhands.app.harvest.data.HarvestReferenceRepository
import com.greenhands.app.harvest.data.InMemoryHarvestReferenceRepository
import com.greenhands.app.harvest.data.InMemoryScanHistoryRepository
import com.greenhands.app.harvest.data.ScanHistoryRepository
import com.greenhands.app.harvest.domain.CropScanGate
import com.greenhands.app.harvest.domain.HarvestEnvironmentContext
import com.greenhands.app.harvest.domain.HarvestSensorUiState
import com.greenhands.app.harvest.domain.MaturityAssessment
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.PlantingDates
import com.greenhands.app.harvest.model.DiseaseReference
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.HarvestSaveStatus
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.VarietyReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

data class HarvestUiState(
    val environment: GreenhouseEnvironmentSnapshot,
    val recentScans: List<ScanRecord> = emptyList(),
    val plantingDateUtcMillis: Long? = null,
    val daysSincePlanting: Int? = null,
    val cropType: String = MaturityCalculator.TOMATO_CROP,
    val tomatoVarieties: List<VarietyReference> = emptyList(),
    val selectedVariety: VarietyReference? = null,
    val tomatoDiseases: List<DiseaseReference> = emptyList(),
    val maturity: MaturityAssessment = MaturityCalculator.assessTomato(null, null),
    val referenceLoadFailed: Boolean = false,
    val historyLoadFailed: Boolean = false
) {
    val environmentContext: HarvestEnvironmentContext
        get() = HarvestEnvironmentContext.from(environment)

    val sensorUi: HarvestSensorUiState
        get() = HarvestSensorUiState.from(environment)

    val canScanCrop: Boolean
        get() = CropScanGate.allowScan(plantingDateUtcMillis)
}

private data class HarvestSessionSlice(
    val plantingDateUtcMillis: Long?,
    val selectedVarietyName: String?,
    val tomatoVarieties: List<VarietyReference>,
    val tomatoDiseases: List<DiseaseReference>
)

/**
 * Hub-facing state for Harvesting Decision Optimization.
 * Persistent scan history is stored separately from current session fields.
 */
class HarvestViewModel(
    private val scanHistoryRepository: ScanHistoryRepository = InMemoryScanHistoryRepository(),
    private val referenceRepository: HarvestReferenceRepository = InMemoryHarvestReferenceRepository(),
    private val environmentRepository: GreenhouseEnvironmentRepository =
        UnconnectedGreenhouseEnvironmentRepository(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newRecordId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val _environment = MutableStateFlow(environmentRepository.snapshot)
    private val _plantingDateUtcMillis = MutableStateFlow<Long?>(null)
    private val _elapsedRefresh = MutableStateFlow(0)
    private val _selectedVarietyName = MutableStateFlow<String?>(null)
    private val _tomatoVarieties = MutableStateFlow<List<VarietyReference>>(emptyList())
    private val _tomatoDiseases = MutableStateFlow<List<DiseaseReference>>(emptyList())
    private val _fruitSaveStatus = MutableStateFlow(HarvestSaveStatus.IDLE)
    private val _leafSaveStatus = MutableStateFlow(HarvestSaveStatus.IDLE)
    private val _referenceLoadFailed = MutableStateFlow(false)
    private val _historyLoadFailed = MutableStateFlow(false)
    private val fruitSaveMutex = Mutex()
    private val leafSaveMutex = Mutex()
    private var lastSavedFruitMeasurement: FruitColorMeasurement? = null
    private var lastSavedLeafMeasurement: LeafColorMeasurement? = null

    private val recordsSafe = scanHistoryRepository.records
        .onEach { _historyLoadFailed.value = false }
        .catch {
            _historyLoadFailed.value = true
            emit(emptyList())
        }

    private val sessionSlice = combine(
        _plantingDateUtcMillis.asStateFlow(),
        _elapsedRefresh.asStateFlow(),
        _selectedVarietyName.asStateFlow(),
        _tomatoVarieties.asStateFlow(),
        _tomatoDiseases.asStateFlow()
    ) { plantingDateUtcMillis, _, selectedVarietyName, tomatoVarieties, tomatoDiseases ->
        HarvestSessionSlice(
            plantingDateUtcMillis = plantingDateUtcMillis,
            selectedVarietyName = selectedVarietyName,
            tomatoVarieties = tomatoVarieties,
            tomatoDiseases = tomatoDiseases
        )
    }

    val state: StateFlow<HarvestUiState> = combine(
        _environment.asStateFlow(),
        recordsSafe,
        sessionSlice,
        _referenceLoadFailed.asStateFlow(),
        _historyLoadFailed.asStateFlow()
    ) { environment, records, session, referenceLoadFailed, historyLoadFailed ->
        val daysSincePlanting = session.plantingDateUtcMillis?.let {
            PlantingDates.daysSinceTransplant(it)
        }
        val selectedVariety = session.tomatoVarieties.firstOrNull {
            it.variety == session.selectedVarietyName
        }
        HarvestUiState(
            environment = environment,
            recentScans = records,
            plantingDateUtcMillis = session.plantingDateUtcMillis,
            daysSincePlanting = daysSincePlanting,
            cropType = MaturityCalculator.TOMATO_CROP,
            tomatoVarieties = session.tomatoVarieties,
            selectedVariety = selectedVariety,
            tomatoDiseases = session.tomatoDiseases,
            maturity = MaturityCalculator.assessTomato(daysSincePlanting, selectedVariety),
            referenceLoadFailed = referenceLoadFailed,
            historyLoadFailed = historyLoadFailed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HarvestUiState(
            environment = environmentRepository.snapshot,
            maturity = MaturityCalculator.assessTomato(null, null)
        )
    )

    val fruitSaveStatus: StateFlow<HarvestSaveStatus> = _fruitSaveStatus.asStateFlow()
    val leafSaveStatus: StateFlow<HarvestSaveStatus> = _leafSaveStatus.asStateFlow()

    init {
        viewModelScope.launch {
            environmentRepository.snapshots.collect { snapshot ->
                _environment.value = snapshot
            }
        }
        viewModelScope.launch {
            try {
                val data = referenceRepository.load()
                _tomatoVarieties.value = data.varieties.filter {
                    it.cropType.equals(MaturityCalculator.TOMATO_CROP, ignoreCase = true)
                }
                _tomatoDiseases.value = data.diseases.filter {
                    it.cropType.equals(MaturityCalculator.TOMATO_CROP, ignoreCase = true)
                }
                _referenceLoadFailed.value = false
            } catch (_: Exception) {
                _tomatoVarieties.value = emptyList()
                _tomatoDiseases.value = emptyList()
                _referenceLoadFailed.value = true
            }
        }
    }

    fun setPlantingDateUtcMillis(utcMidnightMillis: Long) {
        if (!PlantingDates.isNotAfterToday(utcMidnightMillis)) return
        _plantingDateUtcMillis.value = utcMidnightMillis
    }

    fun clearPlantingDate() {
        _plantingDateUtcMillis.value = null
    }

    fun selectVariety(varietyName: String) {
        if (_tomatoVarieties.value.none { it.variety == varietyName }) return
        _selectedVarietyName.value = varietyName
    }

    fun clearVariety() {
        _selectedVarietyName.value = null
    }

    fun refreshDaysSincePlanting() {
        _elapsedRefresh.update { it + 1 }
    }

    fun prepareFruitSave(measurement: FruitColorMeasurement?) {
        if (measurement !== lastSavedFruitMeasurement) {
            _fruitSaveStatus.value = HarvestSaveStatus.IDLE
        }
    }

    fun prepareLeafSave(measurement: LeafColorMeasurement?) {
        if (measurement !== lastSavedLeafMeasurement) {
            _leafSaveStatus.value = HarvestSaveStatus.IDLE
        }
    }

    fun saveFruitScan(decision: HarvestDecisionResult) {
        viewModelScope.launch {
            if (!fruitSaveMutex.tryLock()) return@launch
            try {
                if (_fruitSaveStatus.value == HarvestSaveStatus.SAVING) return@launch
                val session = state.value
                val record = HarvestRecordFactory.fruit(
                    decision = decision,
                    cropType = session.cropType,
                    variety = session.selectedVariety?.variety,
                    transplantDateUtcMillis = session.plantingDateUtcMillis,
                    daysSinceTransplant = session.daysSincePlanting,
                    environment = session.environment,
                    id = newRecordId(),
                    scannedAtEpochMillis = clock()
                )
                if (record == null) {
                    _fruitSaveStatus.value = HarvestSaveStatus.NO_VALID_SCAN
                    return@launch
                }
                val measurement = decision.fruitMeasurement
                if (measurement != null && measurement === lastSavedFruitMeasurement) {
                    _fruitSaveStatus.value = HarvestSaveStatus.ALREADY_SAVED
                    return@launch
                }
                _fruitSaveStatus.value = HarvestSaveStatus.SAVING
                try {
                    scanHistoryRepository.add(record)
                    lastSavedFruitMeasurement = measurement
                    _fruitSaveStatus.value = HarvestSaveStatus.SAVED
                } catch (_: Exception) {
                    _fruitSaveStatus.value = HarvestSaveStatus.FAILED
                }
            } finally {
                fruitSaveMutex.unlock()
            }
        }
    }

    fun saveLeafScan(assessment: PlantHealthAssessment) {
        viewModelScope.launch {
            if (!leafSaveMutex.tryLock()) return@launch
            try {
                if (_leafSaveStatus.value == HarvestSaveStatus.SAVING) return@launch
                val session = state.value
                val record = HarvestRecordFactory.leaf(
                    assessment = assessment,
                    cropType = session.cropType,
                    variety = session.selectedVariety?.variety,
                    transplantDateUtcMillis = session.plantingDateUtcMillis,
                    environment = session.environment,
                    id = newRecordId(),
                    scannedAtEpochMillis = clock(),
                    maturity = session.maturity,
                    daysSinceTransplant = session.daysSincePlanting
                )
                if (record == null) {
                    _leafSaveStatus.value = HarvestSaveStatus.NO_VALID_SCAN
                    return@launch
                }
                val measurement = assessment.leafMeasurement
                if (measurement != null && measurement === lastSavedLeafMeasurement) {
                    _leafSaveStatus.value = HarvestSaveStatus.ALREADY_SAVED
                    return@launch
                }
                _leafSaveStatus.value = HarvestSaveStatus.SAVING
                try {
                    scanHistoryRepository.add(record)
                    lastSavedLeafMeasurement = measurement
                    _leafSaveStatus.value = HarvestSaveStatus.SAVED
                } catch (_: Exception) {
                    _leafSaveStatus.value = HarvestSaveStatus.FAILED
                }
            } finally {
                leafSaveMutex.unlock()
            }
        }
    }

    fun deleteScan(id: String) {
        viewModelScope.launch {
            try {
                scanHistoryRepository.delete(id)
            } catch (_: Exception) {
                _historyLoadFailed.value = true
            }
        }
    }

    fun recordById(id: String): ScanRecord? =
        state.value.recentScans.firstOrNull { it.id == id }
}

class HarvestViewModelFactory(
    private val scanHistoryRepository: ScanHistoryRepository = InMemoryScanHistoryRepository(),
    private val referenceRepository: HarvestReferenceRepository = InMemoryHarvestReferenceRepository(),
    private val environmentRepository: GreenhouseEnvironmentRepository =
        UnconnectedGreenhouseEnvironmentRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HarvestViewModel::class.java)) {
            return HarvestViewModel(
                scanHistoryRepository,
                referenceRepository,
                environmentRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
