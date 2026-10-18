package com.greenhands.app.heat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greenhands.app.heat.data.HeatConfigCodec
import com.greenhands.app.heat.data.HeatConfigRepository
import com.greenhands.app.heat.data.HeatWorkspace
import com.greenhands.app.heat.domain.CropDefaults
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.domain.HeatStageChange
import com.greenhands.app.heat.domain.HeatValidation
import com.greenhands.app.heat.domain.ProfileMigration212
import com.greenhands.app.heat.domain.SuggestedComparison
import com.greenhands.app.heat.domain.TargetParser
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ClimateRecommendation
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.model.toClimateRangeFor
import com.greenhands.app.heat.profile.CropProfileRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HeatUiState(
    val workspace: HeatWorkspace = HeatWorkspace(),
    val config: HeatConfiguration = HeatConfiguration(),
    val loaded: Boolean = false,
    val climateEditing: Boolean = false,
    val schedulePeriod: SchedulePeriod = SchedulePeriod.DAY,
    val dayTempInput: String = "",
    val nightTempInput: String = "",
    val dayRhInput: String = "",
    val nightRhInput: String = "",
    val dayTempError: String? = null,
    val nightTempError: String? = null,
    val dayRhError: String? = null,
    val nightRhError: String? = null,
    val dayTempWarning: String? = null,
    val nightTempWarning: String? = null,
    val dayRhWarning: String? = null,
    val nightRhWarning: String? = null,
    val cspInput: String = "",
    val cdpInput: String = "",
    val conInput: String = "",
    val circulationError: String? = null,
    val circulationWarning: String? = null,
    val espInput: String = "",
    val eonInput: String = "",
    val exhaustError: String? = null,
    val fspInput: String = "",
    val fonInput: String = "",
    val fdpInput: String = "",
    val foggerError: String? = null,
    val foggerWarning: String? = null,
    val pendingStage: GrowthStage? = null,
    val returnToSummary: Boolean = false,
    val saving: Boolean = false,
    val persistError: String? = null,
    val showDiscardConfirm: Boolean = false,
    val showAdvancedConfirm: Boolean = false
) {
    val recommendation: ClimateRecommendation?
        get() {
            val crop = config.crop ?: return null
            val stageId = config.stage?.id ?: return null
            return runCatching { CropDefaults.recommendation(crop, stageId) }.getOrNull()
        }

    val tempInput: String
        get() = if (schedulePeriod == SchedulePeriod.DAY) dayTempInput else nightTempInput

    val rhInput: String
        get() = if (schedulePeriod == SchedulePeriod.DAY) dayRhInput else nightRhInput

    val tempError: String?
        get() = if (schedulePeriod == SchedulePeriod.DAY) dayTempError else nightTempError

    val rhError: String?
        get() = if (schedulePeriod == SchedulePeriod.DAY) dayRhError else nightRhError

    val tempWarning: String?
        get() = if (schedulePeriod == SchedulePeriod.DAY) dayTempWarning else nightTempWarning

    val rhWarning: String?
        get() = if (schedulePeriod == SchedulePeriod.DAY) dayRhWarning else nightRhWarning

    fun activeTemperature(): Double? = config.temperature(schedulePeriod)
        ?: recommendation?.temperatureFor(schedulePeriod)

    fun activeHumidity(): Double? = config.humidity(schedulePeriod)
        ?: recommendation?.humidityFor(schedulePeriod)
}

class HeatConfigViewModel(
    private val repository: HeatConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HeatUiState())
    val state: StateFlow<HeatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.workspace.collect { stored ->
                _state.update { current ->
                    val incoming = ProfileMigration212.migrate(stored.current())
                    val storedIsStale = current.config.stage != null &&
                        incoming.stage == null &&
                        current.workspace.configurations.isNotEmpty()
                    val draftsStale = current.dayTempInput.isBlank() && incoming.dayTemperatureC != null
                    when {
                        storedIsStale -> current.copy(workspace = stored, loaded = true)
                        !current.loaded || draftsStale -> hydrate(stored, current)
                        else -> current.copy(
                            workspace = stored,
                            config = incoming,
                            loaded = true
                        )
                    }
                }
            }
        }
    }

    fun selectCrop(crop: Crop) {
        if (!crop.available) return
        viewModelScope.launch {
            val updated = currentWorkspace().copy(selectedCrop = crop)
            repository.saveWorkspace(updated)
            _state.update {
                it.copy(
                    workspace = updated,
                    config = ProfileMigration212.migrate(updated.current()),
                    persistError = null
                )
            }
        }
    }

    fun onStageClicked(stage: GrowthStage): Boolean {
        val current = _state.value.config
        if (current.stage?.id == stage.id) return true
        return if (HeatStageChange.shouldConfirm(current, stage)) {
            _state.update { it.copy(pendingStage = stage) }
            false
        } else {
            applyStage(stage)
            true
        }
    }

    fun confirmStageChange() {
        val pending = _state.value.pendingStage ?: return
        applyStage(pending)
        _state.update { it.copy(pendingStage = null) }
    }

    fun cancelStageChange() {
        _state.update { it.copy(pendingStage = null) }
    }

    fun selectPeriod(period: SchedulePeriod) {
        _state.update { current ->
            val config = current.config
            current.copy(
                schedulePeriod = period,
                persistError = null,
                workspace = current.workspace.copy(lastSchedulePeriod = period)
            ).alsoSyncThresholds(config, period)
        }
        viewModelScope.launch {
            repository.saveWorkspace(currentWorkspace())
        }
    }

    fun onTempInput(value: String) {
        if (_state.value.schedulePeriod == SchedulePeriod.DAY) onDayTempInput(value) else onNightTempInput(value)
    }

    fun onDayTempInput(value: String) {
        _state.update { it.copy(dayTempInput = value, dayTempError = null, persistError = null) }
        refreshAutomaticPreview()
    }

    fun onNightTempInput(value: String) {
        _state.update { it.copy(nightTempInput = value, nightTempError = null, persistError = null) }
        refreshAutomaticPreview()
    }

    fun onRhInput(value: String) {
        if (_state.value.schedulePeriod == SchedulePeriod.DAY) onDayRhInput(value) else onNightRhInput(value)
    }

    fun onDayRhInput(value: String) {
        _state.update { it.copy(dayRhInput = value, dayRhError = null, persistError = null) }
        refreshAutomaticPreview()
    }

    fun onNightRhInput(value: String) {
        _state.update { it.copy(nightRhInput = value, nightRhError = null, persistError = null) }
        refreshAutomaticPreview()
    }

    fun startClimateEdit() {
        _state.update { it.copy(climateEditing = true) }
    }

    fun resetClimateToRecommended() {
        val crop = _state.value.config.crop ?: return
        val stage = _state.value.config.stage ?: return
        val rec = CropDefaults.recommendation(crop, stage.id)
        _state.update {
            it.copy(
                dayTempInput = formatOneDecimal(rec.uiDayTemperatureC()),
                nightTempInput = formatOneDecimal(rec.uiNightTemperatureC()),
                dayRhInput = rec.uiDayHumidityPercent()?.let { v -> formatOneDecimal(v) }.orEmpty(),
                nightRhInput = rec.uiNightHumidityPercent()?.let { v -> formatOneDecimal(v) }.orEmpty(),
                dayTempError = null,
                nightTempError = null,
                dayRhError = null,
                nightRhError = null,
                dayTempWarning = null,
                nightTempWarning = null,
                dayRhWarning = null,
                nightRhWarning = null,
                climateEditing = false,
                persistError = null
            )
        }
        refreshAutomaticPreview()
    }

    fun isClimateDirty(): Boolean {
        val ui = _state.value
        val saved = ui.config
        return ui.dayTempInput != saved.dayTemperatureC.asInput() ||
            ui.nightTempInput != saved.nightTemperatureC.asInput() ||
            ui.dayRhInput != (saved.dayHumidityPercent ?: saved.targetHumidityPercent).asInput() ||
            ui.nightRhInput != saved.nightHumidityPercent.asInput()
    }

    fun requestLeaveClimate(onLeave: () -> Unit) {
        if (isClimateDirty()) {
            _state.update { it.copy(showDiscardConfirm = true) }
        } else {
            onLeave()
        }
    }

    fun confirmDiscardClimate(onLeave: () -> Unit) {
        _state.update { it.copy(showDiscardConfirm = false) }
        restoreClimateDrafts(_state.value.config)
        onLeave()
    }

    fun cancelDiscardClimate() {
        _state.update { it.copy(showDiscardConfirm = false) }
    }

    fun saveClimate(): Boolean {
        var ok = false
        saveClimate { result -> ok = result }
        return ok
    }

    fun saveClimate(onResult: (Boolean) -> Unit) {
        if (_state.value.saving) return
        val prepared = prepareClimateSave() ?: run {
            onResult(false)
            return
        }
        _state.update { it.copy(saving = true, persistError = null) }
        viewModelScope.launch {
            try {
                repository.save(prepared)
                _state.update {
                    it.copy(
                        saving = false,
                        climateEditing = false,
                        config = prepared,
                        workspace = it.workspace.withSaved(prepared)
                    )
                }
                restoreClimateDrafts(prepared)
                syncThresholdDrafts(prepared, _state.value.schedulePeriod)
                onResult(true)
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        persistError = "Could not save configuration. Stay on this screen and try again."
                    )
                }
                onResult(false)
            }
        }
    }

    fun requestControlMode(mode: ControlMode) {
        if (mode == ControlMode.ADVANCED && _state.value.config.controlMode == ControlMode.AUTOMATIC) {
            _state.update { it.copy(showAdvancedConfirm = true) }
            return
        }
        setControlMode(mode)
    }

    fun confirmAdvancedMode() {
        _state.update { it.copy(showAdvancedConfirm = false) }
        setControlMode(ControlMode.ADVANCED)
    }

    fun cancelAdvancedMode() {
        _state.update { it.copy(showAdvancedConfirm = false) }
    }

    fun setControlMode(mode: ControlMode) {
        val config = _state.value.config
        val dayT = config.dayTemperatureC ?: return
        val nightT = config.nightTemperatureC ?: dayT
        val dayRh = config.dayHumidityPercent ?: config.targetHumidityPercent
        val nightRh = config.nightHumidityPercent ?: dayRh
        val updated = if (mode == ControlMode.AUTOMATIC) {
            val dayEq = HeatFormulas.configurationForTargets(dayT, dayRh)
            val nightEq = HeatFormulas.configurationForTargets(nightT, nightRh)
            config.copy(
                controlMode = mode,
                circulation = dayEq.circulation,
                exhaust = dayEq.exhaust,
                fogger = dayEq.fogger,
                nightCirculation = nightEq.circulation,
                nightExhaust = nightEq.exhaust,
                nightFogger = nightEq.fogger,
                saved = true
            )
        } else {
            config.copy(controlMode = mode, saved = true)
        }
        persist(updated)
        syncThresholdDrafts(updated, _state.value.schedulePeriod)
    }

    fun onCspInput(value: String) = _state.update { it.copy(cspInput = value, circulationError = null) }
    fun onCdpInput(value: String) = _state.update { it.copy(cdpInput = value, circulationError = null) }
    fun onConInput(value: String) = _state.update { it.copy(conInput = value, circulationError = null) }
    fun onEspInput(value: String) = _state.update { it.copy(espInput = value, exhaustError = null) }
    fun onEonInput(value: String) = _state.update { it.copy(eonInput = value, exhaustError = null) }
    fun onFspInput(value: String) = _state.update { it.copy(fspInput = value, foggerError = null) }
    fun onFonInput(value: String) = _state.update { it.copy(fonInput = value, foggerError = null) }
    fun onFdpInput(value: String) = _state.update { it.copy(fdpInput = value, foggerError = null) }

    fun resetCirculationToFormula(): Boolean = resetPeriodEquipment()

    fun resetExhaustToFormula(): Boolean = resetPeriodEquipment()

    fun resetFoggerToFormula(): Boolean = resetPeriodEquipment()

    fun resetPeriodEquipment(): Boolean {
        val ui = _state.value
        val period = ui.schedulePeriod
        val temp = parsedTempFor(period) ?: return false
        val rh = parsedRhFor(period)
        val eq = HeatFormulas.configurationForTargets(temp, rh)
        val updated = ui.config.withPeriodEquipment(period, eq.circulation, eq.exhaust, eq.fogger).copy(saved = true)
        persist(updated)
        syncThresholdDrafts(updated, period)
        _state.update {
            it.copy(
                circulationError = null,
                circulationWarning = null,
                exhaustError = null,
                foggerError = null,
                foggerWarning = null
            )
        }
        return true
    }

    fun saveCirculation(): Boolean {
        var ok = false
        saveCirculation { result -> ok = result }
        return ok
    }

    fun saveCirculation(onResult: (Boolean) -> Unit) {
        val ui = _state.value
        if (ui.saving) return
        val period = ui.schedulePeriod
        val topt = parsedTempFor(period) ?: run {
            onResult(false)
            return
        }
        val circ = if (ui.config.controlMode == ControlMode.AUTOMATIC) {
            HeatFormulas.circulation(topt)
        } else {
            val csp = TargetParser.parseDecimal(ui.cspInput)
            val cdp = TargetParser.parseDecimal(ui.cdpInput)
            val con = TargetParser.parseDecimal(ui.conInput)
            if (csp == null || cdp == null || con == null) {
                _state.update { it.copy(circulationError = "Enter valid start, stop and continuous-operation values") }
                onResult(false)
                return
            }
            val order = HeatValidation.circulationOrder(cdp, csp, con)
            if (order != null) {
                _state.update { it.copy(circulationError = order) }
                onResult(false)
                return
            }
            CirculationThresholds(csp, cdp, con)
        }
        val warning = HeatValidation.cspDiffersFromTarget(circ.csp, topt)
        val existingExhaust = ui.config.exhaust(period)
        val exhaust = if (ui.config.controlMode == ControlMode.AUTOMATIC || existingExhaust == null) {
            HeatFormulas.exhaustFromCirculation(circ)
        } else {
            val updated = existingExhaust.copy(edp = circ.con)
            if (HeatValidation.exhaustOrder(updated.edp, updated.esp, updated.eon) != null) {
                HeatFormulas.exhaustFromCirculation(circ)
            } else {
                updated
            }
        }
        val fogger = ui.config.fogger(period)
        val next = ui.config.withPeriodEquipment(period, circ, exhaust, fogger).copy(saved = true)
        persistAwait(next) { ok ->
            if (ok) {
                _state.update { it.copy(circulationWarning = warning, circulationError = null, saving = false) }
                syncThresholdDrafts(next, period)
            }
            onResult(ok)
        }
    }

    fun saveExhaust(): Boolean {
        var ok = false
        saveExhaust { result -> ok = result }
        return ok
    }

    fun saveExhaust(onResult: (Boolean) -> Unit) {
        val ui = _state.value
        if (ui.saving) return
        val period = ui.schedulePeriod
        val circ = ui.config.circulation(period) ?: run {
            onResult(false)
            return
        }
        val exhaust = if (ui.config.controlMode == ControlMode.AUTOMATIC) {
            HeatFormulas.exhaustFromCirculation(circ)
        } else {
            val esp = TargetParser.parseDecimal(ui.espInput)
            val eon = TargetParser.parseDecimal(ui.eonInput)
            val edp = circ.con
            if (esp == null || eon == null) {
                _state.update { it.copy(exhaustError = "Enter valid start and stop exhausting values") }
                onResult(false)
                return
            }
            val order = HeatValidation.exhaustOrder(edp, esp, eon)
            if (order != null) {
                _state.update { it.copy(exhaustError = order) }
                onResult(false)
                return
            }
            ExhaustThresholds(esp = esp, edp = edp, eon = eon)
        }
        val next = ui.config.withPeriodEquipment(
            period,
            circ,
            exhaust,
            ui.config.fogger(period)
        ).copy(saved = true)
        persistAwait(next) { ok ->
            if (ok) {
                _state.update { it.copy(exhaustError = null, saving = false) }
                syncThresholdDrafts(next, period)
            }
            onResult(ok)
        }
    }

    fun saveFogger(): Boolean {
        var ok = false
        saveFogger { result -> ok = result }
        return ok
    }

    fun saveFogger(onResult: (Boolean) -> Unit) {
        val ui = _state.value
        if (ui.saving) return
        val period = ui.schedulePeriod
        val rhopt = parsedRhFor(period) ?: run {
            onResult(false)
            return
        }
        val fogger = if (ui.config.controlMode == ControlMode.AUTOMATIC) {
            HeatFormulas.fogger(rhopt)
        } else {
            val fsp = TargetParser.parseDecimal(ui.fspInput)
            val fon = TargetParser.parseDecimal(ui.fonInput)
            val fdp = TargetParser.parseDecimal(ui.fdpInput)
            if (fsp == null || fon == null || fdp == null) {
                _state.update { it.copy(foggerError = "Enter valid fogging start, stop and set-point values") }
                onResult(false)
                return
            }
            val order = HeatValidation.foggerOrder(fon, fsp, fdp)
            if (order != null) {
                _state.update { it.copy(foggerError = order) }
                onResult(false)
                return
            }
            FoggerThresholds(fsp = fsp, fon = fon, fdp = fdp)
        }
        val warning = HeatValidation.fspDiffersFromTarget(fogger.fsp, rhopt)
        val next = ui.config.withPeriodEquipment(
            period,
            ui.config.circulation(period),
            ui.config.exhaust(period),
            fogger
        ).copy(saved = true)
        persistAwait(next) { ok ->
            if (ok) {
                _state.update { it.copy(foggerError = null, foggerWarning = warning, saving = false) }
                syncThresholdDrafts(next, period)
            }
            onResult(ok)
        }
    }

    fun markReturnToSummary() {
        _state.update { it.copy(returnToSummary = true) }
    }

    fun consumeReturnToSummary(): Boolean {
        val value = _state.value.returnToSummary
        _state.update { it.copy(returnToSummary = false) }
        return value
    }

    fun saveConfiguration(): Boolean {
        var ok = false
        saveConfiguration { result -> ok = result }
        return ok
    }

    fun saveConfiguration(onResult: (Boolean) -> Unit) {
        if (_state.value.saving) return
        val config = _state.value.config
        if (config.crop == null || config.stage == null) {
            onResult(false)
            return
        }
        val prepared = config.copy(saved = true)
        _state.update { it.copy(saving = true, persistError = null) }
        viewModelScope.launch {
            try {
                repository.save(prepared)
                _state.update {
                    it.copy(
                        saving = false,
                        config = prepared,
                        workspace = it.workspace.withSaved(prepared)
                    )
                }
                onResult(true)
            } catch (_: Exception) {
                _state.update {
                    it.copy(saving = false, persistError = "Could not save configuration.")
                }
                onResult(false)
            }
        }
    }

    fun resetEntireConfiguration() {
        val crop = _state.value.config.crop
        viewModelScope.launch {
            val ws = currentWorkspace()
            val cleared = if (crop == null) {
                HeatWorkspace(selectedCrop = ws.selectedCrop)
            } else {
                ws.copy(
                    selectedStageByCrop = ws.selectedStageByCrop - crop.id,
                    configurations = ws.configurations.filterKeys { !it.startsWith("${crop.id}_") }
                )
            }
            repository.saveWorkspace(cleared)
            _state.update {
                HeatUiState(
                    workspace = cleared,
                    config = cleared.current(),
                    loaded = true,
                    climateEditing = false
                )
            }
        }
    }

    private fun prepareClimateSave(): HeatConfiguration? {
        val ui = _state.value
        val crop = ui.config.crop ?: return null
        val stage = ui.config.stage ?: return null
        val rec = CropDefaults.recommendation(crop, stage.id)
        val dayRange = rec.toClimateRangeFor(SchedulePeriod.DAY)
        val nightRange = rec.toClimateRangeFor(SchedulePeriod.NIGHT)
        val day = HeatValidation.temperature(ui.dayTempInput, dayRange)
        val night = HeatValidation.temperature(ui.nightTempInput, nightRange)
        val dayRh = HeatValidation.humidity(ui.dayRhInput, dayRange)
        val nightRh = if (ui.nightRhInput.isBlank() && rec.uiNightHumidityPercent() == null) {
            dayRh
        } else {
            HeatValidation.humidity(ui.nightRhInput.ifBlank { ui.dayRhInput }, nightRange)
        }
        _state.update {
            it.copy(
                dayTempError = day.error,
                nightTempError = night.error,
                dayRhError = dayRh.error,
                nightRhError = nightRh.error,
                dayTempWarning = day.warning,
                nightTempWarning = night.warning,
                dayRhWarning = dayRh.warning,
                nightRhWarning = nightRh.warning
            )
        }
        if (!day.isValid || !night.isValid || !dayRh.isValid || !nightRh.isValid) return null
        val dayT = day.value!!
        val nightT = night.value!!
        val dayRhV = dayRh.value!!
        val nightRhV = nightRh.value!!
        val automatic = ui.config.controlMode == ControlMode.AUTOMATIC
        val dayEq = HeatFormulas.configurationForTargets(dayT, dayRhV)
        val nightEq = HeatFormulas.configurationForTargets(nightT, nightRhV)
        val draft = ui.config.copy(
            dayTemperatureC = dayT,
            nightTemperatureC = nightT,
            dailyMeanTemperatureC = rec.dailyMeanTemperatureC,
            targetTemperatureC = dayT,
            targetHumidityPercent = dayRhV,
            dayHumidityPercent = dayRhV,
            nightHumidityPercent = nightRhV,
            circulation = if (automatic) dayEq.circulation else ui.config.circulation ?: dayEq.circulation,
            exhaust = if (automatic) dayEq.exhaust else ui.config.exhaust ?: dayEq.exhaust,
            fogger = if (automatic) dayEq.fogger else ui.config.fogger ?: dayEq.fogger,
            nightCirculation = if (automatic) nightEq.circulation else ui.config.nightCirculation ?: nightEq.circulation,
            nightExhaust = if (automatic) nightEq.exhaust else ui.config.nightExhaust ?: nightEq.exhaust,
            nightFogger = if (automatic) nightEq.fogger else ui.config.nightFogger ?: nightEq.fogger,
            saved = true,
            profileVersion = rec.profileVersion
        )
        val customised = SuggestedComparison.isCustomised(draft, rec)
        return draft.copy(
            valuesAreCustomised = customised,
            lastSuggestedProfileVersion = if (customised) {
                ui.config.lastSuggestedProfileVersion ?: rec.profileVersion
            } else {
                rec.profileVersion
            }
        )
    }

    private fun applyStage(stage: GrowthStage) {
        val crop = _state.value.config.crop ?: CropProfileRegistry.profiles.first().crop
        val existing = currentWorkspace().configurations[HeatConfigCodec.key(crop.id, stage.id)]
        val updated = if (existing != null && existing.saved) {
            ProfileMigration212.migrate(existing.copy(crop = crop, stage = stage))
        } else {
            HeatStageChange.apply(_state.value.config, crop, stage)
        }
        persist(updated)
        restoreClimateDrafts(updated)
        _state.update {
            it.copy(
                climateEditing = false,
                dayTempError = null,
                nightTempError = null,
                dayRhError = null,
                nightRhError = null,
                dayTempWarning = null,
                nightTempWarning = null,
                dayRhWarning = null,
                nightRhWarning = null,
                schedulePeriod = SchedulePeriod.DAY
            )
        }
        syncThresholdDrafts(updated, SchedulePeriod.DAY)
    }

    private fun persist(config: HeatConfiguration) {
        persistAwait(config) {}
    }

    private fun persistAwait(config: HeatConfiguration, onDone: (Boolean) -> Unit) {
        _state.update { it.copy(config = config, workspace = it.workspace.withSaved(config), saving = true) }
        viewModelScope.launch {
            try {
                repository.save(config)
                _state.update { it.copy(saving = false) }
                onDone(true)
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, persistError = "Could not save configuration.") }
                onDone(false)
            }
        }
    }

    private fun hydrate(workspace: HeatWorkspace, current: HeatUiState): HeatUiState {
        val config = ProfileMigration212.migrate(workspace.current())
        val rec = config.crop?.let { crop ->
            config.stage?.let { stage ->
                runCatching { CropDefaults.recommendation(crop, stage.id) }.getOrNull()
            }
        }
        val newer = rec != null && SuggestedComparison.newerSuggestionsAvailable(config, rec)
        val period = workspace.lastSchedulePeriod
        return current.copy(
            workspace = workspace,
            config = config,
            loaded = true,
            schedulePeriod = workspace.lastSchedulePeriod,
            climateEditing = false,
            dayTempInput = config.dayTemperatureC.asInput(),
            nightTempInput = config.nightTemperatureC.asInput(),
            dayRhInput = (config.dayHumidityPercent ?: config.targetHumidityPercent).asInput(),
            nightRhInput = config.nightHumidityPercent.asInput(),
            persistError = if (newer) {
                "Newer suggested values are available. Reset to Suggested Values to apply them. Your customised values are kept until you reset."
            } else {
                current.persistError
            }
        ).alsoSyncThresholds(config, period)
    }

    private fun restoreClimateDrafts(config: HeatConfiguration) {
        _state.update {
            it.copy(
                dayTempInput = config.dayTemperatureC.asInput(),
                nightTempInput = config.nightTemperatureC.asInput(),
                dayRhInput = (config.dayHumidityPercent ?: config.targetHumidityPercent).asInput(),
                nightRhInput = config.nightHumidityPercent.asInput()
            )
        }
    }

    private fun syncThresholdDrafts(config: HeatConfiguration, period: SchedulePeriod) {
        val automatic = config.controlMode == ControlMode.AUTOMATIC
        val temp = config.temperature(period)
        val rh = config.humidity(period)
        val circ = if (automatic && temp != null) HeatFormulas.circulation(temp) else config.circulation(period)
        val exhaust = if (automatic && circ != null) HeatFormulas.exhaustFromCirculation(circ) else config.exhaust(period)
        val fogger = if (automatic && rh != null) HeatFormulas.fogger(rh) else config.fogger(period)
        _state.update {
            it.copy(
                cspInput = circ?.csp.asInput(),
                cdpInput = circ?.cdp.asInput(),
                conInput = circ?.con.asInput(),
                espInput = exhaust?.esp.asInput(),
                eonInput = exhaust?.eon.asInput(),
                fspInput = fogger?.fsp.asInput(),
                fonInput = fogger?.fon.asInput(),
                fdpInput = fogger?.fdp.asInput()
            )
        }
    }

    private fun HeatUiState.alsoSyncThresholds(
        config: HeatConfiguration,
        period: SchedulePeriod
    ): HeatUiState {
        val automatic = config.controlMode == ControlMode.AUTOMATIC
        val temp = config.temperature(period)
        val rh = config.humidity(period)
        val circ = if (automatic && temp != null) HeatFormulas.circulation(temp) else config.circulation(period)
        val exhaust = if (automatic && circ != null) HeatFormulas.exhaustFromCirculation(circ) else config.exhaust(period)
        val fogger = if (automatic && rh != null) HeatFormulas.fogger(rh) else config.fogger(period)
        return copy(
            cspInput = circ?.csp.asInput(),
            cdpInput = circ?.cdp.asInput(),
            conInput = circ?.con.asInput(),
            espInput = exhaust?.esp.asInput(),
            eonInput = exhaust?.eon.asInput(),
            fspInput = fogger?.fsp.asInput(),
            fonInput = fogger?.fon.asInput(),
            fdpInput = fogger?.fdp.asInput()
        )
    }

    private fun refreshAutomaticPreview() {
        val ui = _state.value
        if (ui.config.controlMode != ControlMode.AUTOMATIC) return
        val temp = parsedTempFor(ui.schedulePeriod) ?: return
        val rh = parsedRhFor(ui.schedulePeriod)
        val eq = HeatFormulas.configurationForTargets(temp, rh)
        _state.update {
            it.copy(
                cspInput = formatOneDecimal(eq.circulation.csp),
                cdpInput = formatOneDecimal(eq.circulation.cdp),
                conInput = formatOneDecimal(eq.circulation.con),
                espInput = formatOneDecimal(eq.exhaust.esp),
                eonInput = formatOneDecimal(eq.exhaust.eon),
                fspInput = eq.fogger?.fsp?.let { v -> formatOneDecimal(v) }.orEmpty(),
                fonInput = eq.fogger?.fon?.let { v -> formatOneDecimal(v) }.orEmpty(),
                fdpInput = eq.fogger?.fdp?.let { v -> formatOneDecimal(v) }.orEmpty()
            )
        }
    }

    private fun parsedTempFor(period: SchedulePeriod): Double? {
        val ui = _state.value
        val raw = if (period == SchedulePeriod.DAY) ui.dayTempInput else ui.nightTempInput
        return TargetParser.parseDecimal(raw) ?: ui.config.temperature(period)
    }

    private fun parsedRhFor(period: SchedulePeriod): Double? {
        val ui = _state.value
        val raw = if (period == SchedulePeriod.DAY) ui.dayRhInput else ui.nightRhInput
        return TargetParser.parseDecimal(raw) ?: ui.config.humidity(period)
    }

    private fun currentWorkspace(): HeatWorkspace = _state.value.workspace

    private fun Double?.asInput(): String = this?.let { formatOneDecimal(it) }.orEmpty()
}

private fun HeatConfiguration.withPeriodEquipment(
    period: SchedulePeriod,
    circulation: CirculationThresholds?,
    exhaust: ExhaustThresholds?,
    fogger: FoggerThresholds?
): HeatConfiguration = if (period == SchedulePeriod.DAY) {
    copy(circulation = circulation, exhaust = exhaust, fogger = fogger)
} else {
    copy(nightCirculation = circulation, nightExhaust = exhaust, nightFogger = fogger)
}

class HeatConfigViewModelFactory(
    private val repository: HeatConfigRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HeatConfigViewModel(repository) as T
    }
}
