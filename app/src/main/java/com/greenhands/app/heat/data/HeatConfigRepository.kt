package com.greenhands.app.heat.data

import com.greenhands.app.heat.model.HeatConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HeatConfigRepository {
    val workspace: Flow<HeatWorkspace>
    val config: Flow<HeatConfiguration>
        get() = workspace.map { it.current() }

    suspend fun save(config: HeatConfiguration)
    suspend fun saveWorkspace(workspace: HeatWorkspace)
    suspend fun clear()
}
