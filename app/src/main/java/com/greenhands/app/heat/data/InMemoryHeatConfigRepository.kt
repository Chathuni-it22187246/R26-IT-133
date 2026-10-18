package com.greenhands.app.heat.data

import com.greenhands.app.heat.model.HeatConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryHeatConfigRepository(
    initial: HeatWorkspace = HeatWorkspace()
) : HeatConfigRepository {
    private val _workspace = MutableStateFlow(initial)
    override val workspace: Flow<HeatWorkspace> = _workspace.asStateFlow()

    override suspend fun save(config: HeatConfiguration) {
        _workspace.update { it.withSaved(config) }
    }

    override suspend fun saveWorkspace(workspace: HeatWorkspace) {
        _workspace.value = workspace
    }

    override suspend fun clear() {
        _workspace.value = HeatWorkspace()
    }

    fun snapshot(): HeatWorkspace = _workspace.value
}
