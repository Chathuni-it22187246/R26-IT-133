package com.greenhands.app.decision

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class PlantRequest(
    val crop: String,
    val stage: String,
    val disease: String,
    @SerializedName("infection_text")
    val infectionText: String = disease
)

data class DecisionResponse(
    val title: String = "",
    val description: String = "",
    val urgency: String = "",
    @SerializedName("infection_name")
    val infectionName: String = "",
    @SerializedName("severity_level")
    val severityLevel: String = "",
    @SerializedName("immediate_action")
    val immediateAction: String = "",
    @SerializedName("biological_treatment")
    val biologicalTreatment: List<String> = emptyList(),
    @SerializedName("chemical_control")
    val chemicalControl: List<String> = emptyList(),
    val prevention: List<String> = emptyList(),
    @SerializedName("environmental_adjustments")
    val environmentalAdjustments: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val crop: String = "",
    val stage: String = "",
    @SerializedName("updated_at")
    val updatedAt: String = "",
    @SerializedName("log_line")
    val logLine: String = "",
    @SerializedName("log_path")
    val logPath: String = "",
    @SerializedName("decision_id")
    val decisionId: String = "",
    @SerializedName("line_index")
    val lineIndex: Int = 0,
    val kind: String = "infection",
    val category: String = "infections",
    @SerializedName("heater_speed")
    val heaterSpeed: Double? = null,
    @SerializedName("current_temperature")
    val currentTemperature: Double? = null,
    @SerializedName("target_temperature")
    val targetTemperature: Double? = null,
    val humidity: Double? = null,
    @SerializedName("climate_status")
    val climateStatus: String = "",
    val lifecycle: String = ""
) {
    val displayTitle: String
        get() = title.ifBlank { infectionName.ifBlank { "AI Decision" } }

    val displayUrgency: String
        get() = urgency.ifBlank { severityLevel.ifBlank { "Info" } }

    val isHeaterAction: Boolean
        get() = kind.equals("heater", ignoreCase = true) ||
            kind.equals("climate", ignoreCase = true) ||
            kind.equals("humidity", ignoreCase = true)

    val isFanAction: Boolean
        get() = kind.equals("fan", ignoreCase = true)

    val isWaterAction: Boolean
        get() = kind.equals("water", ignoreCase = true) ||
            kind.equals("pump", ignoreCase = true) ||
            category.equals("water_pump", ignoreCase = true)

    val isCompletedClimateAction: Boolean
        get() = (isHeaterAction || isFanAction) &&
            lifecycle.equals("Completed", ignoreCase = true)

    val isLiveClimateAction: Boolean
        get() = (isHeaterAction || isFanAction) &&
            !isCompletedClimateAction &&
            (lifecycle.equals("Active", ignoreCase = true) || lifecycle.isBlank())

    val hasDetailedGuide: Boolean
        get() = immediateAction.isNotBlank() ||
            biologicalTreatment.isNotEmpty() ||
            chemicalControl.isNotEmpty() ||
            prevention.isNotEmpty() ||
            environmentalAdjustments.isNotEmpty()
}

data class ActiveDecisionsResponse(
    val count: Int = 0,
    @SerializedName("updated_at")
    val updatedAt: String = "",
    @SerializedName("log_path")
    val logPath: String = "",
    val decisions: List<DecisionResponse> = emptyList()
)

data class HeaterPredictRequest(
    @SerializedName("current_temperature")
    val currentTemperature: Double,
    @SerializedName("target_temperature")
    val targetTemperature: Double,
    val humidity: Double
)

data class HeaterPredictResponse(
    @SerializedName("heater_speed")
    val heaterSpeed: Double = 0.0,
    val unit: String = "percent",
    val status: String = "Optimal",
    val urgency: String = "Info",
    @SerializedName("climate_optimal")
    val climateOptimal: Boolean = true,
    val logged: Boolean = false,
    val timestamp: String = "",
    @SerializedName("immediate_action")
    val immediateAction: String = ""
)

data class InfectionDecisionRequest(
    val query: String? = null,
    val symptoms: String? = null,
    @SerializedName("plant_type")
    val plantType: String? = null,
    @SerializedName("infection_name")
    val infectionName: String? = null
)

data class InfectionDecisionResponse(
    @SerializedName("plant_type")
    val plantType: String = "",
    @SerializedName("infection_short_name")
    val infectionShortName: String = "",
    @SerializedName("infection_full_name")
    val infectionFullName: String = "",
    @SerializedName("severity_level")
    val severityLevel: String = "",
    @SerializedName("visible_symptoms")
    val visibleSymptoms: String = "",
    @SerializedName("treatment_description")
    val treatmentDescription: String = "",
    @SerializedName("biological_control")
    val biologicalControl: String = "",
    @SerializedName("chemical_control")
    val chemicalControl: String = "",
    @SerializedName("prevention_steps")
    val preventionSteps: String = ""
)

data class GreenhouseTelemetryResponse(
    @SerializedName("temperature_c")
    val temperatureC: Double = 0.0,
    @SerializedName("humidity_percent")
    val humidityPercent: Double = 0.0,
    @SerializedName("light_lux")
    val lightLux: Double = 0.0,
    @SerializedName("infection_count")
    val infectionCount: Int = 0,
    @SerializedName("connection_state")
    val connectionState: String = "PREVIEW",
    @SerializedName("updated_at")
    val updatedAt: String = "",
    @SerializedName("sensor_id")
    val sensorId: String = "",
    @SerializedName("heater_speed")
    val heaterSpeed: Double = 0.0,
    @SerializedName("heater_status")
    val heaterStatus: String = "",
    @SerializedName("heater_urgency")
    val heaterUrgency: String = "",
    @SerializedName("climate_optimal")
    val climateOptimal: Boolean = true,
    @SerializedName("target_temperature")
    val targetTemperature: Double = 26.0,
    @SerializedName("heater_logged")
    val heaterLogged: Boolean = false,
    val health: String = "",
    @SerializedName("health_color")
    val healthColor: String = "",
    @SerializedName("health_summary")
    val healthSummary: String = "",
    @SerializedName("climate_level")
    val climateLevel: String = "",
    @SerializedName("infection_level")
    val infectionLevel: String = "",
    @SerializedName("active_actuator")
    val activeActuator: String = ""
)

data class GreenhouseHealthResponse(
    val health: String = "Optimal",
    @SerializedName("health_color")
    val healthColor: String = "green",
    @SerializedName("health_summary")
    val healthSummary: String = "",
    @SerializedName("climate_status")
    val climateStatus: String = "",
    @SerializedName("climate_level")
    val climateLevel: String = "Optimal",
    @SerializedName("climate_optimal")
    val climateOptimal: Boolean = true,
    @SerializedName("infection_count")
    val infectionCount: Int = 0,
    @SerializedName("infection_level")
    val infectionLevel: String = "Optimal",
    @SerializedName("temperature_c")
    val temperatureC: Double = 0.0,
    @SerializedName("humidity_percent")
    val humidityPercent: Double = 0.0,
    @SerializedName("target_temperature")
    val targetTemperature: Double = 26.0,
    @SerializedName("updated_at")
    val updatedAt: String = ""
)

interface DecisionApiService {
    @POST("api/v1/ai-decision")
    suspend fun getAiDecision(@Body request: PlantRequest): DecisionResponse

    @GET("api/v1/ai-decision/latest")
    suspend fun getLatestAiDecision(): DecisionResponse

    @GET("api/v1/ai-decision/active")
    suspend fun getActiveAiDecisions(): ActiveDecisionsResponse

    @GET("api/v1/greenhouse/telemetry")
    suspend fun getGreenhouseTelemetry(): GreenhouseTelemetryResponse

    @GET("api/v1/greenhouse/health")
    suspend fun getGreenhouseHealth(): GreenhouseHealthResponse

    @POST("api/v1/predict-heater")
    suspend fun predictHeater(@Body request: HeaterPredictRequest): HeaterPredictResponse

    @POST("api/v1/infection-decision")
    suspend fun getInfectionDecision(@Body request: InfectionDecisionRequest): InfectionDecisionResponse

    companion object {
        fun create(): DecisionApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(DecisionApiService::class.java)
        }
    }
}
