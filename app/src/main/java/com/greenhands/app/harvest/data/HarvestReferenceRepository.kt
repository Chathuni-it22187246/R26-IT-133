package com.greenhands.app.harvest.data

import android.content.Context
import com.greenhands.app.harvest.data.csv.AssetCsvLoader
import com.greenhands.app.harvest.data.csv.CropReferenceCsvParser
import com.greenhands.app.harvest.data.csv.CsvLoadException
import com.greenhands.app.harvest.data.csv.DiseaseReferenceCsvParser
import com.greenhands.app.harvest.data.csv.HarvestRulesCsvParser
import com.greenhands.app.harvest.data.csv.ObservationCsvParser
import com.greenhands.app.harvest.data.csv.VarietyReferenceCsvParser
import com.greenhands.app.harvest.model.HarvestReferenceData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads crop, variety, disease, and harvest-rule reference data from assets CSVs.
 * Observation schema is supported; the current observations file may have zero rows.
 */
interface HarvestReferenceRepository {
    suspend fun load(): HarvestReferenceData

    suspend fun isReady(): Boolean

    /** Schema-ready observation rows from 05_real_observations_updated.csv (may be empty). */
    suspend fun loadObservations(): List<com.greenhands.app.harvest.model.ObservationRecord>
}

class AssetHarvestReferenceRepository(
    context: Context,
    private val loader: AssetCsvLoader = AssetCsvLoader(),
    private val cropParser: CropReferenceCsvParser = CropReferenceCsvParser(loader),
    private val varietyParser: VarietyReferenceCsvParser = VarietyReferenceCsvParser(loader),
    private val diseaseParser: DiseaseReferenceCsvParser = DiseaseReferenceCsvParser(loader),
    private val rulesParser: HarvestRulesCsvParser = HarvestRulesCsvParser(loader),
    private val observationParser: ObservationCsvParser = ObservationCsvParser(loader)
) : HarvestReferenceRepository {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var cached: HarvestReferenceData? = null

    override suspend fun load(): HarvestReferenceData = withContext(Dispatchers.IO) {
        mutex.withLock {
            cached?.let { return@withContext it }
            val data = HarvestReferenceData(
                crops = cropParser.parse(
                    loader.readTableFromAsset(appContext, AssetCsvLoader.CROP_REFERENCE)
                ),
                varieties = varietyParser.parse(
                    loader.readTableFromAsset(appContext, AssetCsvLoader.VARIETY_REFERENCE)
                ),
                diseases = diseaseParser.parse(
                    loader.readTableFromAsset(appContext, AssetCsvLoader.DISEASE_REFERENCE)
                ),
                harvestRules = rulesParser.parse(
                    loader.readTableFromAsset(appContext, AssetCsvLoader.HARVEST_RULES)
                ),
                observations = observationParser.parse(
                    loader.readTableFromAsset(appContext, AssetCsvLoader.REAL_OBSERVATIONS)
                )
            )
            cached = data
            data
        }
    }

    override suspend fun isReady(): Boolean = try {
        load().isLoaded
    } catch (_: CsvLoadException) {
        false
    }

    override suspend fun loadObservations() = load().observations
}

/**
 * In-memory repository for tests and compile-safe defaults without assets.
 */
class InMemoryHarvestReferenceRepository(
    private val data: HarvestReferenceData = HarvestReferenceData(
        crops = emptyList(),
        varieties = emptyList(),
        diseases = emptyList(),
        harvestRules = emptyList(),
        observations = emptyList()
    )
) : HarvestReferenceRepository {
    override suspend fun load(): HarvestReferenceData = data
    override suspend fun isReady(): Boolean = data.isLoaded
    override suspend fun loadObservations() = data.observations
}

@Deprecated(
    message = "Use InMemoryHarvestReferenceRepository or AssetHarvestReferenceRepository",
    replaceWith = ReplaceWith("InMemoryHarvestReferenceRepository()")
)
class StubHarvestReferenceRepository : HarvestReferenceRepository by InMemoryHarvestReferenceRepository()
