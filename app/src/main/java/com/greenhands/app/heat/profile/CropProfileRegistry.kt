package com.greenhands.app.heat.profile

import com.greenhands.app.heat.model.ApplicabilityNote
import com.greenhands.app.heat.model.ClimateRecommendation
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.CropProfile
import com.greenhands.app.heat.model.DerivationMethod
import com.greenhands.app.heat.model.EvidenceLevel
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.GrowthStageProfile
import com.greenhands.app.heat.model.HumiditySubPeriod
import com.greenhands.app.heat.model.PROFILE_VERSION
import com.greenhands.app.heat.model.RecommendedRange
import com.greenhands.app.heat.model.SourceCitation
import com.greenhands.app.heat.model.TimedClimateBand

object DerivedValues {
    fun midpoint(min: Double, max: Double): Double = (min + max) / 2.0
}

object CropProfileRegistry {

    val citations: List<SourceCitation> = listOf(
        SourceCitation(
            id = "SRC-DOA-TOMATO",
            title = "Tomato (HORDI crop page)",
            authorsOrOrganisation = "Sri Lanka Department of Agriculture / HORDI",
            year = "n.d.",
            supportedCrops = listOf(Crop.TOMATO),
            supportedParameters = listOf("Field air temperature (supporting only)"),
            evidenceLevel = EvidenceLevel.SRI_LANKA_OFFICIAL_CROP_GUIDANCE,
            geographicApplicability = "Sri Lanka field / highland (1000–2000 m). Not a greenhouse setpoint.",
            doiOrUrl = "https://doa.gov.lk/hordi-crop-tomato/",
            locationInSource = "Climatic requirements: optimum temperature 21–24 °C"
        ),
        SourceCitation(
            id = "SRC-DOA-CUCUMBER",
            title = "Cucumber (HORDI crop page)",
            authorsOrOrganisation = "Sri Lanka Department of Agriculture / HORDI",
            year = "n.d.",
            supportedCrops = listOf(Crop.SALAD_CUCUMBER),
            supportedParameters = listOf("Field air temperature (supporting only)"),
            evidenceLevel = EvidenceLevel.SRI_LANKA_OFFICIAL_CROP_GUIDANCE,
            geographicApplicability = "Sri Lanka field (wet zone year-round / dry-zone Maha). Not a greenhouse setpoint.",
            doiOrUrl = "https://doa.gov.lk/hordi-crop-cucumber/",
            locationInSource = "Optimum temperature is 30 °C"
        ),
        SourceCitation(
            id = "SRC-DOA-CAPSICUM",
            title = "Capsicum (HORDI crop page)",
            authorsOrOrganisation = "Sri Lanka Department of Agriculture / HORDI",
            year = "n.d.",
            supportedCrops = listOf(Crop.BELL_PEPPER),
            supportedParameters = listOf("Protected-house mention; no T/RH setpoints"),
            evidenceLevel = EvidenceLevel.SRI_LANKA_OFFICIAL_CROP_GUIDANCE,
            geographicApplicability = "Sri Lanka; rain shelters / protected houses mentioned without climate setpoints",
            doiOrUrl = "https://doa.gov.lk/hordi-crop-capsicum/",
            locationInSource = "Cultivation notes; leaf spot favoured by 20–25 °C and higher RH (disease, not crop optimum)"
        ),
        SourceCitation(
            id = "SRC-JAYATHILAKA-2022",
            title = "Impact of an IoT-based micro-climate monitoring and control system on salad cucumber in protected houses",
            authorsOrOrganisation = "Jayathilaka, Adikaram, Kumarasinghe, Jayasinghe; Sri Lankan Journal of Agriculture and Ecosystems",
            year = "2022",
            supportedCrops = listOf(Crop.SALAD_CUCUMBER),
            supportedParameters = listOf(
                "Table 3 time-of-day air temperature",
                "Table 2 stage and time-of-day relative humidity"
            ),
            evidenceLevel = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            geographicApplicability = "Sri Lankan protected houses, salad cucumber",
            doiOrUrl = "https://doi.org/10.4038/sljae.v4i2.83",
            locationInSource = "Table 2 RH by nursery (days 1–4 and 5–8), vegetative (9–28) and reproductive/maturity (29–120), 6AM–6PM vs 6PM–6AM; Table 3 whole-cycle T 15/20/25/20 °C; Results: recommended temperature not economically feasible locally"
        ),
        SourceCitation(
            id = "SRC-GUNAWARDENA-2014",
            title = "The effects of temperature and water stresses on growth, yield and related characteristics of chilli (Capsicum annuum L.)",
            authorsOrOrganisation = "Gunawardena and De Silva; OUSL Journal",
            year = "2014",
            supportedCrops = listOf(Crop.CHILLI),
            supportedParameters = listOf("High-temperature polytunnel warning (not a setpoint)"),
            evidenceLevel = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            geographicApplicability = "Nawala, Sri Lanka; cv. MI-2 in temperature-regulated polytunnels",
            doiOrUrl = "https://doi.org/10.4038/ouslj.v7i0.7306",
            locationInSource = "Treatments: 32 °C max, 34 °C max, and ambient — stress experiment, not recommended setpoints"
        ),
        SourceCitation(
            id = "SRC-OH-2019",
            title = "Fruit Development and Quality of Hot Pepper (Capsicum annuum L.) under Various Temperature Regimes",
            authorsOrOrganisation = "Oh and Koh; Horticultural Science and Technology",
            year = "2019",
            supportedCrops = listOf(Crop.CHILLI),
            supportedParameters = listOf("Day/night air temperature", "Experimental chamber relative humidity"),
            evidenceLevel = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            geographicApplicability = "Korea; cv. Muhanjilju in walk-in growth chambers. Not Sri Lanka.",
            doiOrUrl = "https://doi.org/10.7235/HORT.20190032",
            locationInSource = "Introduction: favourable 25–28 °C day and 18–22 °C night; growth retarded below 15 °C or above 32 °C (citing Mercado 1997; Erickson and Markhart 2002). Methods: chambers held at 60–70 % RH."
        ),
        SourceCitation(
            id = "SRC-SHAMSHIRI-2018",
            title = "Review of optimum temperature, humidity, and vapour pressure deficit for microclimate evaluation and control in greenhouse cultivation of tomato",
            authorsOrOrganisation = "Shamshiri, Jones, Thorp, Ahmad, Man, Taheri; International Agrophysics",
            year = "2018",
            supportedCrops = listOf(Crop.TOMATO),
            supportedParameters = listOf("Day/night air temperature", "Relative humidity", "Fruit-set heat warning"),
            evidenceLevel = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            geographicApplicability = "International review; Table 4 is cultivar Caruso/Carusso, Ohio A-shade greenhouse",
            doiOrUrl = "https://doi.org/10.1515/intag-2017-0005",
            locationInSource = "Table 4 (Short et al. 2005 DSS); Optimum humidity section; compiled T table including Adams ≥32 °C fruit-set failure"
        ),
        SourceCitation(
            id = "SRC-ALBERTA-PEPPER",
            title = "Production of sweet bell peppers",
            authorsOrOrganisation = "Government of Alberta",
            year = "n.d.",
            supportedCrops = listOf(Crop.BELL_PEPPER),
            supportedParameters = listOf("Day/night air temperature", "Relative humidity", "Fruit-set heat warning"),
            evidenceLevel = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            geographicApplicability = "Commercial greenhouse, Alberta, Canada (temperate). Not Sri Lanka.",
            doiOrUrl = "https://www.alberta.ca/production-of-sweet-bell-peppers",
            locationInSource = "Germination 25–26 °C day and night, RH 75–80 %; after first transplant 24 °C day / 22 °C night; fruit set reduced above 27 °C"
        ),
        SourceCitation(
            id = "SRC-ALBERTA-ENV",
            title = "Management of the Greenhouse Environment",
            authorsOrOrganisation = "Government of Alberta",
            year = "n.d.",
            supportedCrops = listOf(Crop.BELL_PEPPER),
            supportedParameters = listOf("Night temperature at flowering/fruit set"),
            evidenceLevel = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            geographicApplicability = "Alberta greenhouse vegetable production",
            doiOrUrl = "https://www1.agric.gov.ab.ca/\$department/deptdocs.nsf/all/opp2902?opendocument",
            locationInSource = "Optimum night temperature for flowering and fruit setting 16–18 °C (Pressman 1998 cited)"
        ),
        SourceCitation(
            id = "SRC-CORNELL-LETTUCE",
            title = "Hydroponic Lettuce Handbook",
            authorsOrOrganisation = "Brechner, Both, Cornell University CEA Program",
            year = "2013",
            supportedCrops = listOf(Crop.LETTUCE),
            supportedParameters = listOf("Day/night air temperature", "Relative humidity", "Germination-room temperature"),
            evidenceLevel = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            geographicApplicability = "Cornell CEA hydroponic greenhouse / pond system, USA",
            doiOrUrl = "https://cea.cals.cornell.edu/files/2019/06/Cornell-CEA-Lettuce-Handbook-.pdf",
            locationInSource = "§3.3 Set-points p.19: 24 °C day / 19 °C night, RH 50–70 %; Ch.4 germination room 20 °C then 25 °C"
        ),
        SourceCitation(
            id = "SRC-CAROTTI-2020",
            title = "Plant factories are heating up: hunting for the best combination of light intensity, air temperature and root-zone temperature in lettuce production",
            authorsOrOrganisation = "Carotti et al.; Frontiers in Plant Science",
            year = "2020",
            supportedCrops = listOf(Crop.LETTUCE),
            supportedParameters = listOf("High air-temperature / tip-burn warning"),
            evidenceLevel = EvidenceLevel.CONTROLLED_ENVIRONMENT_RESEARCH,
            geographicApplicability = "Climate rooms, Wageningen, Netherlands; cv. Batavia Othilie. Plant factory, not a Sri Lankan greenhouse.",
            doiOrUrl = "https://doi.org/10.3389/fpls.2020.592171",
            locationInSource = "Germination 18 °C dark then 20 °C; tip-burn excessive for Tair ≥ 28 °C with cool roots and high PPFD"
        ),
        SourceCitation(
            id = "SRC-HAREL-2014",
            title = "The effect of mean daily temperature and relative humidity on pollen, fruit set and yield of tomato grown in naturally ventilated greenhouses",
            authorsOrOrganisation = "Harel, Fadida, Slepoy, Gantz, Shilo; Agronomy",
            year = "2014",
            supportedCrops = listOf(Crop.TOMATO),
            supportedParameters = listOf("Bibliographic (original PDF not retrieved in this audit)"),
            evidenceLevel = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            geographicApplicability = "Naturally ventilated protected cultivation",
            doiOrUrl = "https://doi.org/10.3390/agronomy4010167",
            locationInSource = "Not independently re-read. Shamshiri 2018 cites Harel for pollination RH around 60 %."
        ),
        SourceCitation(
            id = "SRC-PROJECT-CONTROL",
            title = "GreenHands Demo Mode equipment formulas",
            authorsOrOrganisation = "GreenHands project",
            year = "2026",
            supportedCrops = Crop.entries.toList(),
            supportedParameters = listOf("Circulation CSP/CDP/CON", "Exhaust ESP/EDP/EON", "Fogger FSP/FON/FDP"),
            evidenceLevel = EvidenceLevel.PROJECT_CONTROL_RULE,
            geographicApplicability = "Application Demo Mode only; not agronomic",
            doiOrUrl = "docs/DEMO_CONTROL_LOGIC.md",
            locationInSource = "CSP=Topt; CDP=CSP−2; CON=CSP+2; ESP=CSP+4; EDP=CON; EON=ESP+2; FSP=RHopt; FON=FSP−2; FDP=FSP+2"
        )
    )

    fun citation(id: String): SourceCitation =
        citations.first { it.id == id }

    fun citationOrNull(id: String): SourceCitation? =
        citations.find { it.id == id }

    val profiles: List<CropProfile> = listOf(
        tomatoProfile(),
        cucumberProfile(),
        pepperProfile(),
        chilliProfile(),
        lettuceProfile()
    )

    fun profile(crop: Crop): CropProfile = profiles.first { it.crop == crop }

    fun stageProfile(crop: Crop, stageId: String): GrowthStageProfile =
        profile(crop).stage(stageId) ?: error("Unknown stage $stageId for ${crop.id}")

    fun stagesFor(crop: Crop): List<GrowthStage> = profile(crop).stages.map { it.stage }

    fun climateFor(crop: Crop, stageId: String): ClimateRecommendation =
        stageProfile(crop, stageId).climate

    private fun cRange(min: Double, max: Double) = RecommendedRange(min, max, "°C")
    private fun rhRange(min: Double, max: Double) = RecommendedRange(min, max, "%")

    private fun tomatoProfile(): CropProfile {
        val nursery = GrowthStage(
            id = "germination",
            displayName = "Germination and Nursery",
            shortLabel = "Nursery",
            explanation = "Shamshiri Table 4 early growth (any light) for cultivar Caruso. Not a Sri Lankan greenhouse standard."
        )
        val vegetative = GrowthStage(
            id = "vegetative",
            displayName = "Vegetative Growth",
            shortLabel = "Vegetative",
            explanation = "Table 4 vegetative sun/night bands. Day and night are shown separately."
        )
        val flowering = GrowthStage(
            id = "flowering",
            displayName = "Flowering and Fruit Set",
            shortLabel = "Flowering",
            explanation = "Table 4 flowering-to-mature fruiting. Fruit-set risk rises at or above 32 °C in the compiled review."
        )
        val ripening = GrowthStage(
            id = "ripening",
            displayName = "Fruit Development, Ripening and Harvest",
            shortLabel = "Ripening",
            explanation = "Table 4 does not split ripening from flowering-to-mature fruiting. This stage inherits that band."
        )
        val table4FlowerDay = DerivedValues.midpoint(24.0, 27.0)
        val table4Night = DerivedValues.midpoint(18.0, 20.0)
        val sl = ApplicabilityNote("Ohio greenhouse DSS (Caruso). Sri Lankan greenhouse validation required. DOA 21–24 °C is field guidance, not this setpoint.")
        val nurseryClimate = ClimateRecommendation(
            generalTemperatureC = DerivedValues.midpoint(24.0, 26.1),
            generalTemperatureRange = cRange(24.0, 26.1),
            dayTemperatureC = DerivedValues.midpoint(24.0, 26.1),
            dayTemperatureRange = cRange(24.0, 26.1),
            nightTemperatureC = DerivedValues.midpoint(24.0, 26.1),
            nightTemperatureRange = cRange(24.0, 26.1),
            selectedTargetTemperatureC = DerivedValues.midpoint(24.0, 26.1),
            humidityPercent = 75.0,
            humidityRange = rhRange(75.0, 100.0),
            dayHumidityPercent = 75.0,
            nightHumidityPercent = 75.0,
            warningNotes = listOf(
                "Upper DSS RH bound of 100 % is not a practical production setpoint.",
                "Same review also cites 50–70 % RH as a crop-level optimal band across tomato growth.",
                "DOA field optimum 21–24 °C is supporting context only."
            ),
            sourceIds = listOf("SRC-SHAMSHIRI-2018", "SRC-DOA-TOMATO"),
            temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            humidityEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            temperatureDerivation = DerivationMethod.DERIVED_MIDPOINT,
            humidityDerivation = DerivationMethod.DIRECT,
            localValidationRequired = true,
            applicability = sl,
            presentationMapping = "Published general nursery target of 25.05°C is applied to both Day and Night."
        )
        val vegClimate = ClimateRecommendation(
            dayTemperatureC = table4FlowerDay,
            dayTemperatureRange = cRange(24.0, 27.0),
            nightTemperatureC = table4Night,
            nightTemperatureRange = cRange(18.0, 20.0),
            selectedTargetTemperatureC = table4FlowerDay,
            humidityPercent = DerivedValues.midpoint(70.0, 80.0),
            humidityRange = rhRange(70.0, 80.0),
            dayHumidityPercent = DerivedValues.midpoint(70.0, 80.0),
            nightHumidityPercent = DerivedValues.midpoint(70.0, 80.0),
            warningNotes = listOf(
                "Cloud-day Table 4 vegetative band is 22–24 °C; Demo selected target uses the sun/day band.",
                "Day and night are not collapsed into one research number."
            ),
            sourceIds = listOf("SRC-SHAMSHIRI-2018"),
            temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            humidityEvidence = EvidenceLevel.DERIVED_MIDPOINT,
            temperatureDerivation = DerivationMethod.DERIVED_MIDPOINT,
            humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
            localValidationRequired = true,
            applicability = sl
        )
        val flowerClimate = ClimateRecommendation(
            dayTemperatureC = table4FlowerDay,
            dayTemperatureRange = cRange(24.0, 27.0),
            nightTemperatureC = table4Night,
            nightTemperatureRange = cRange(18.0, 20.0),
            selectedTargetTemperatureC = table4FlowerDay,
            humidityPercent = DerivedValues.midpoint(60.0, 80.0),
            humidityRange = rhRange(60.0, 80.0),
            dayHumidityPercent = DerivedValues.midpoint(60.0, 80.0),
            nightHumidityPercent = DerivedValues.midpoint(60.0, 80.0),
            warningNotes = listOf(
                "Fruit-set progressively fails at or above 32 °C (Adams et al. 2001, compiled in Shamshiri).",
                "Shamshiri cites Harel et al. 2014 that pollination is enhanced when RH is around 60 %. Harel’s PDF was not independently re-read."
            ),
            operatorWarning = "Prolonged temperatures of 32°C or above may reduce fruit set.",
            sourceIds = listOf("SRC-SHAMSHIRI-2018", "SRC-HAREL-2014"),
            temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            humidityEvidence = EvidenceLevel.DERIVED_MIDPOINT,
            temperatureDerivation = DerivationMethod.DERIVED_MIDPOINT,
            humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
            localValidationRequired = true,
            applicability = sl
        )
        val ripeClimate = flowerClimate.copy(
            warningNotes = flowerClimate.warningNotes +
                "Ripening inherits Table 4 flowering-to-mature fruiting. Shamshiri also lists Omafra 19 °C day and night for harvest initiation; that original was not re-read and is not the app target.",
            temperatureEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
            humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
            temperatureDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
            humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED
        )
        return CropProfile(
            crop = Crop.TOMATO,
            evidenceBadge = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            stages = listOf(
                GrowthStageProfile(nursery, nurseryClimate),
                GrowthStageProfile(vegetative, vegClimate),
                GrowthStageProfile(flowering, flowerClimate),
                GrowthStageProfile(ripening, ripeClimate)
            )
        )
    }

    private fun tempPoint(id: String, label: String, value: Double, applicability: String): TimedClimateBand =
        TimedClimateBand(
            id = id,
            label = label,
            applicability = applicability,
            target = value,
            range = cRange(value, value),
            unit = "°C",
            derivation = DerivationMethod.DIRECT,
            evidence = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE
        )

    private fun rhBand(
        id: String,
        label: String,
        min: Double,
        max: Double,
        applicability: String,
        evidence: EvidenceLevel,
        derivation: DerivationMethod
    ): TimedClimateBand {
        val target = if (min == max) min else DerivedValues.midpoint(min, max)
        return TimedClimateBand(
            id = id,
            label = label,
            applicability = applicability,
            target = target,
            range = rhRange(min, max),
            unit = "%",
            derivation = derivation,
            evidence = evidence
        )
    }

    private fun jayathilakaTable3(): List<TimedClimateBand> {
        val applicability = "Jayathilaka Table 3, whole crop cycle"
        return listOf(
            tempPoint("t_21_06", "9PM–6AM", 15.0, applicability),
            tempPoint("t_06_09", "6AM–9AM", 20.0, applicability),
            tempPoint("t_09_17", "9AM–5PM", 25.0, applicability),
            tempPoint("t_17_21", "5PM–9PM", 20.0, applicability)
        )
    }

    private fun cucumberSharedWarnings(): List<String> = listOf(
        "Jayathilaka Table 3 temperatures are the IoT-house schedule that was attempted. The authors wrote that the recommended temperature was not feasible under local conditions due to the high cost.",
        "Measured IoT-house mean day temperature 31.61 °C and conventional 34.29 °C are observations, not suggested targets.",
        "Jayathilaka also cites a 22–30 °C leaf-unfolding band (Berghage 1998). That band is supporting background only and does not replace Table 3.",
        "DOA field optimum of 30 °C is supporting field guidance, not a greenhouse setpoint.",
        "Demo selected temperature uses the Table 3 9AM–5PM value of 25 °C. The four periods are not collapsed into one research number."
    )

    private fun cucumberClimate(
        humidityPercent: Double,
        humidityRange: RecommendedRange,
        humidityEvidence: EvidenceLevel,
        humidityDerivation: DerivationMethod,
        humiditySchedule: List<TimedClimateBand> = emptyList(),
        humiditySubPeriods: List<HumiditySubPeriod> = emptyList(),
        extraWarnings: List<String> = emptyList()
    ): ClimateRecommendation {
        val schedule = jayathilakaTable3()
        return ClimateRecommendation(
            selectedTargetTemperatureC = 25.0,
            dayTemperatureC = 25.0,
            dayTemperatureRange = cRange(20.0, 25.0),
            nightTemperatureC = 15.0,
            nightTemperatureRange = cRange(15.0, 15.0),
            humidityPercent = humidityPercent,
            humidityRange = humidityRange,
            dayHumidityPercent = humidityPercent,
            nightHumidityPercent = humiditySchedule.lastOrNull()?.target
                ?: humiditySubPeriods.firstOrNull()?.bands?.getOrNull(1)?.target
                ?: humidityPercent,
            warningNotes = cucumberSharedWarnings() + extraWarnings,
            sourceIds = listOf("SRC-JAYATHILAKA-2022", "SRC-DOA-CUCUMBER"),
            temperatureEvidence = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            humidityEvidence = humidityEvidence,
            temperatureDerivation = DerivationMethod.DIRECT,
            humidityDerivation = humidityDerivation,
            localValidationRequired = true,
            applicability = ApplicabilityNote(
                "Jayathilaka et al. 2022 Sri Lankan protected-house salad cucumber. Table 3 is a whole-cycle schedule; Table 2 is stage RH. Not a government greenhouse setpoint."
            ),
            temperatureSchedule = schedule,
            humiditySchedule = humiditySchedule,
            humiditySubPeriods = humiditySubPeriods,
            presentationMapping = "App Day 25°C is Table 3 9AM–5PM. App Night 15°C is Table 3 9PM–6AM. Morning 6AM–9AM and evening 5PM–9PM remain 20°C in Sources and View Source."
        )
    }

    private fun cucumberProfile(): CropProfile {
        val nurseryDay14 = rhBand(
            "rh_n14_day", "6AM–6PM", 80.0, 90.0,
            "Table 2 nursery days 1–4",
            EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            DerivationMethod.DERIVED_MIDPOINT
        )
        val nurseryNight14 = rhBand(
            "rh_n14_night", "6PM–6AM", 50.0, 50.0,
            "Table 2 nursery days 1–4",
            EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            DerivationMethod.DIRECT
        )
        val nurseryDay58 = rhBand(
            "rh_n58_day", "6AM–6PM", 65.0, 70.0,
            "Table 2 nursery days 5–8",
            EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            DerivationMethod.DERIVED_MIDPOINT
        )
        val nurseryNight58 = rhBand(
            "rh_n58_night", "6PM–6AM", 50.0, 50.0,
            "Table 2 nursery days 5–8",
            EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            DerivationMethod.DIRECT
        )
        val vegDay = rhBand(
            "rh_veg_day", "6AM–6PM", 65.0, 75.0,
            "Table 2 vegetative days 9–28",
            EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            DerivationMethod.DERIVED_MIDPOINT
        )
        val vegNight = rhBand(
            "rh_veg_night", "6PM–6AM", 45.0, 50.0,
            "Table 2 vegetative days 9–28",
            EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            DerivationMethod.DERIVED_MIDPOINT
        )
        val reproDay = rhBand(
            "rh_repro_day", "6AM–6PM", 55.0, 65.0,
            "Table 2 reproductive and maturity days 29–120",
            EvidenceLevel.CROP_LEVEL_INHERITED,
            DerivationMethod.DERIVED_MIDPOINT
        )
        val reproNight = rhBand(
            "rh_repro_night", "6PM–6AM", 50.0, 55.0,
            "Table 2 reproductive and maturity days 29–120",
            EvidenceLevel.CROP_LEVEL_INHERITED,
            DerivationMethod.DERIVED_MIDPOINT
        )
        val nurseryClimate = cucumberClimate(
            humidityPercent = nurseryDay14.target,
            humidityRange = rhRange(80.0, 90.0),
            humidityEvidence = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
            humiditySubPeriods = listOf(
                HumiditySubPeriod(
                    id = "nursery_1_4",
                    label = "Nursery days 1–4",
                    dayRangeNote = "Table 2, days 1–4",
                    bands = listOf(nurseryDay14, nurseryNight14)
                ),
                HumiditySubPeriod(
                    id = "nursery_5_8",
                    label = "Nursery days 5–8",
                    dayRangeNote = "Table 2, days 5–8",
                    bands = listOf(nurseryDay58, nurseryNight58)
                )
            ),
            extraWarnings = listOf(
                "Demo humidity uses the days 1–4 6AM–6PM midpoint (85 %). Days 5–8 daytime RH is 65–70 % (target 67.5 %)."
            )
        )
        val vegetativeClimate = cucumberClimate(
            humidityPercent = vegDay.target,
            humidityRange = rhRange(65.0, 75.0),
            humidityEvidence = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
            humiditySchedule = listOf(vegDay, vegNight),
            extraWarnings = listOf("Demo humidity uses the vegetative 6AM–6PM midpoint (70 %). Night RH is 45–50 % (target 47.5 %).")
        )
        val reproductiveClimate = cucumberClimate(
            humidityPercent = reproDay.target,
            humidityRange = rhRange(55.0, 65.0),
            humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
            humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
            humiditySchedule = listOf(reproDay, reproNight),
            extraWarnings = listOf(
                "Flowering and harvest inherit Table 2 reproductive and maturity RH (days 29–120). Demo humidity uses the 6AM–6PM midpoint (60 %)."
            )
        )
        return CropProfile(
            crop = Crop.SALAD_CUCUMBER,
            evidenceBadge = EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE,
            stages = listOf(
                GrowthStageProfile(
                    GrowthStage(
                        "germination",
                        "Germination and Nursery",
                        "Nursery",
                        "Jayathilaka Table 2 nursery days 1–4 and 5–8. Table 3 temperature is the whole-cycle schedule."
                    ),
                    nurseryClimate
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "vegetative",
                        "Vegetative and Vine Development",
                        "Vegetative",
                        "Jayathilaka Table 2 vegetative days 9–28. Table 3 temperature is the whole-cycle schedule."
                    ),
                    vegetativeClimate
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "flowering",
                        "Flowering and Fruit Set",
                        "Flowering",
                        "Inherits Table 2 reproductive and maturity RH (days 29–120). Table 3 temperature is the whole-cycle schedule."
                    ),
                    reproductiveClimate
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "harvest",
                        "Fruit Development and Harvest",
                        "Harvest",
                        "Inherits Table 2 reproductive and maturity RH (days 29–120). Table 3 temperature is the whole-cycle schedule."
                    ),
                    reproductiveClimate
                )
            )
        )
    }

    private fun pepperClimateNursery(): ClimateRecommendation = ClimateRecommendation(
        generalTemperatureC = DerivedValues.midpoint(25.0, 26.0),
        generalTemperatureRange = cRange(25.0, 26.0),
        dayTemperatureC = DerivedValues.midpoint(25.0, 26.0),
        dayTemperatureRange = cRange(25.0, 26.0),
        nightTemperatureC = DerivedValues.midpoint(25.0, 26.0),
        nightTemperatureRange = cRange(25.0, 26.0),
        selectedTargetTemperatureC = DerivedValues.midpoint(25.0, 26.0),
        humidityPercent = DerivedValues.midpoint(75.0, 80.0),
        humidityRange = rhRange(75.0, 80.0),
        dayHumidityPercent = DerivedValues.midpoint(75.0, 80.0),
        nightHumidityPercent = DerivedValues.midpoint(75.0, 80.0),
        warningNotes = listOf(
            "Alberta: after emergence (~4 days) drop RH to 65–70 % and plug temperature to 23–24 °C.",
            "Temperate Canadian greenhouse. Sri Lankan validation required."
        ),
        sourceIds = listOf("SRC-ALBERTA-PEPPER", "SRC-DOA-CAPSICUM"),
        temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
        humidityEvidence = EvidenceLevel.DERIVED_MIDPOINT,
        temperatureDerivation = DerivationMethod.DERIVED_MIDPOINT,
        humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
        localValidationRequired = true,
        applicability = ApplicabilityNote("Alberta greenhouse sweet pepper. DOA capsicum page has no T/RH setpoints."),
        presentationMapping = "Published general germination target of 25.5°C is applied to both Day and Night."
    )

    private fun pepperClimateVegetative(): ClimateRecommendation = ClimateRecommendation(
        dayTemperatureC = 24.0,
        dayTemperatureRange = cRange(24.0, 24.0),
        nightTemperatureC = 22.0,
        nightTemperatureRange = cRange(22.0, 22.0),
        dailyMeanTemperatureC = 22.0,
        selectedTargetTemperatureC = 24.0,
        humidityPercent = DerivedValues.midpoint(70.0, 80.0),
        humidityRange = rhRange(70.0, 80.0),
        warningNotes = listOf(
            "24-hour average after first transplant is 22 °C. Demo selected target uses day 24 °C so day/night evidence is not collapsed.",
            "Alberta also lists 20 °C day and night at about 5 weeks, and 20–21 °C constant in the first greenhouse week.",
            "Vegetative growth optimum 21–23 °C and 24-h yield ~21 °C (Bakker 1989 cited by Alberta) are additional context."
        ),
        sourceIds = listOf("SRC-ALBERTA-PEPPER"),
        temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
        humidityEvidence = EvidenceLevel.DERIVED_MIDPOINT,
        temperatureDerivation = DerivationMethod.DIRECT,
        humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
        localValidationRequired = true,
        applicability = ApplicabilityNote("After first transplant, Alberta greenhouse pepper.")
    )

    private fun pepperClimateFlowering(): ClimateRecommendation = ClimateRecommendation(
        dayTemperatureC = DerivedValues.midpoint(21.0, 23.0),
        dayTemperatureRange = cRange(21.0, 23.0),
        nightTemperatureC = DerivedValues.midpoint(16.0, 18.0),
        nightTemperatureRange = cRange(16.0, 18.0),
        dailyMeanTemperatureC = 21.0,
        selectedTargetTemperatureC = DerivedValues.midpoint(21.0, 23.0),
        humidityPercent = DerivedValues.midpoint(70.0, 80.0),
        humidityRange = rhRange(70.0, 80.0),
        warningNotes = listOf(
            "Fruit set reduced above 27 °C; high temperatures 32–38 °C reduce set; night ≤14 °C inhibits female organs (Alberta pepper guide).",
            "Pressman 1998 flowering optimum of 16 °C cited by Alberta conflicts with 24-h yield ~21 °C. Both are shown; they are not collapsed.",
            "RH is inherited from Alberta first-week greenhouse 70–80 %; flowering-specific RH was not numbered except that low RH reduces fruit set."
        ),
        sourceIds = listOf("SRC-ALBERTA-PEPPER", "SRC-ALBERTA-ENV"),
        temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
        humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
        temperatureDerivation = DerivationMethod.DERIVED_MIDPOINT,
        humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
        localValidationRequired = true,
        applicability = ApplicabilityNote("Alberta greenhouse pepper flowering/fruit set. Not a Sri Lankan setpoint.")
    )

    private fun pepperProfile(): CropProfile {
        val flowering = pepperClimateFlowering()
        return CropProfile(
            crop = Crop.BELL_PEPPER,
            evidenceBadge = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            stages = listOf(
                GrowthStageProfile(
                    GrowthStage(
                        "germination",
                        "Germination and Nursery",
                        "Nursery",
                        "Alberta germination: 25–26 °C day and night, RH 75–80 %."
                    ),
                    pepperClimateNursery()
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "vegetative",
                        "Vegetative Establishment",
                        "Vegetative",
                        "Alberta after first transplant: 24 °C day / 22 °C night."
                    ),
                    pepperClimateVegetative()
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "flowering",
                        "Flowering and Fruit Set",
                        "Flowering",
                        "Alberta production/flowering guidance with day/night kept separate."
                    ),
                    flowering
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "fruiting",
                        "Fruit Development and Colouring",
                        "Colouring",
                        "No separate colouring setpoints were published. Inherits flowering/production band."
                    ),
                    flowering.copy(
                        temperatureEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
                        humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
                        temperatureDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
                        humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
                        warningNotes = flowering.warningNotes + "Colouring inherits the flowering/production climate band."
                    )
                )
            )
        )
    }

    private fun chilliCropLevelClimate(): ClimateRecommendation {
        val dayTarget = DerivedValues.midpoint(25.0, 28.0)
        val nightTarget = DerivedValues.midpoint(18.0, 22.0)
        val rhTarget = DerivedValues.midpoint(60.0, 70.0)
        return ClimateRecommendation(
            dayTemperatureC = dayTarget,
            dayTemperatureRange = cRange(25.0, 28.0),
            nightTemperatureC = nightTarget,
            nightTemperatureRange = cRange(18.0, 22.0),
            selectedTargetTemperatureC = dayTarget,
            humidityPercent = rhTarget,
            humidityRange = rhRange(60.0, 70.0),
            dayHumidityPercent = rhTarget,
            nightHumidityPercent = rhTarget,
            warningNotes = listOf(
                "Oh et al. 2019: growth is usually retarded and yield decreased below 15 °C or above 32 °C (citing Mercado 1997 and Erickson and Markhart 2002).",
                "Oh’s own chamber treatments found 20–25 °C favourable for fruit development of cv. Muhanjilju. That experimental result is supporting context, not a replacement for the 25–28 / 18–22 °C guidance.",
                "Chamber RH was held at 60–70 %. That is an experimental growing condition, not a demonstrated universal RH optimum.",
                "Sri Lankan polytunnel maxima of 32 °C and 34 °C (Gunawardena and De Silva 2014, cv. MI-2) were heat-stress treatments, not suggested setpoints.",
                "This profile is not Alberta sweet-pepper (bell) guidance. Local validation is required."
            ),
            operatorWarning = "Prolonged temperatures of 32°C or above may reduce fruit set.",
            sourceIds = listOf("SRC-OH-2019", "SRC-GUNAWARDENA-2014"),
            temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            humidityEvidence = EvidenceLevel.CONTROLLED_ENVIRONMENT_RESEARCH,
            temperatureDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
            humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
            localValidationRequired = true,
            applicability = ApplicabilityNote(
                "Crop-level hot-pepper guidance from Oh et al. 2019. Display targets 26.5 °C day and 20 °C night are midpoints of 25–28 °C and 18–22 °C. Not a Sri Lankan greenhouse standard."
            )
        )
    }

    private fun chilliProfile(): CropProfile {
        val cropLevel = chilliCropLevelClimate()
        fun stage(id: String, display: String, short: String, explanation: String) = GrowthStageProfile(
            GrowthStage(id, display, short, explanation),
            cropLevel
        )
        return CropProfile(
            crop = Crop.CHILLI,
            evidenceBadge = EvidenceLevel.LOCAL_VALIDATION_REQUIRED,
            stages = listOf(
                stage(
                    "germination",
                    "Germination and Nursery",
                    "Nursery",
                    "No chilli-specific nursery setpoints were published in Oh et al. Inherits the hot-pepper crop-level profile."
                ),
                stage(
                    "vegetative",
                    "Vegetative Growth",
                    "Vegetative",
                    "Inherits the hot-pepper crop-level day/night profile from Oh et al. 2019."
                ),
                stage(
                    "flowering",
                    "Flowering and Fruit Set",
                    "Flowering",
                    "Inherits the hot-pepper crop-level profile. Oh cites growth/yield risk below 15 °C or above 32 °C."
                ),
                stage(
                    "ripening",
                    "Fruit Development and Ripening",
                    "Ripening",
                    "Inherits the hot-pepper crop-level profile. Not a copy of Tomato or Bell Pepper values."
                )
            )
        )
    }

    private fun lettuceProfile(): CropProfile {
        val prodDay = 24.0
        val prodNight = 19.0
        val rhMid = DerivedValues.midpoint(50.0, 70.0)
        val cornell = ApplicabilityNote("Cornell CEA hydroponic lettuce handbook (USA, 2013). Not a Sri Lankan greenhouse standard.")
        val production = ClimateRecommendation(
            dayTemperatureC = prodDay,
            dayTemperatureRange = cRange(24.0, 24.0),
            nightTemperatureC = prodNight,
            nightTemperatureRange = cRange(19.0, 19.0),
            selectedTargetTemperatureC = prodDay,
            humidityPercent = rhMid,
            humidityRange = rhRange(50.0, 70.0),
            warningNotes = listOf(
                "High humidity encourages botrytis and mildew (Cornell handbook).",
                "Carotti et al. 2020: tip-burn increased with air temperature; Tair ≥ 28 °C with cool roots and high light was discarded. Experimental plant factory, Netherlands."
            ),
            sourceIds = listOf("SRC-CORNELL-LETTUCE", "SRC-CAROTTI-2020"),
            temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            humidityEvidence = EvidenceLevel.DERIVED_MIDPOINT,
            temperatureDerivation = DerivationMethod.DIRECT,
            humidityDerivation = DerivationMethod.DERIVED_MIDPOINT,
            localValidationRequired = true,
            applicability = cornell
        )
        val nursery = ClimateRecommendation(
            generalTemperatureC = 20.0,
            generalTemperatureRange = cRange(20.0, 25.0),
            dayTemperatureC = 20.0,
            dayTemperatureRange = cRange(20.0, 25.0),
            nightTemperatureC = 20.0,
            nightTemperatureRange = cRange(20.0, 25.0),
            selectedTargetTemperatureC = 20.0,
            humidityPercent = rhMid,
            humidityRange = rhRange(50.0, 70.0),
            dayHumidityPercent = rhMid,
            nightHumidityPercent = rhMid,
            warningNotes = listOf(
                "Cornell Ch.4: germination room 20 °C; temperature is raised to 25 °C after the first 24 hours.",
                "First two days: high humidity to prevent desiccation (no numeric RH).",
                "Carotti germinated seed dark at 18 °C then 20 °C — supporting, not the app target."
            ),
            sourceIds = listOf("SRC-CORNELL-LETTUCE", "SRC-CAROTTI-2020"),
            temperatureEvidence = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
            temperatureDerivation = DerivationMethod.DIRECT,
            humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
            localValidationRequired = true,
            applicability = cornell,
            presentationMapping = "Published general germination-room target of 20°C is applied to both Day and Night."
        )
        return CropProfile(
            crop = Crop.LETTUCE,
            evidenceBadge = EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE,
            stages = listOf(
                GrowthStageProfile(
                    GrowthStage(
                        "germination",
                        "Germination and Nursery",
                        "Nursery",
                        "Cornell germination room 20 °C, then 25 °C for the remaining nursery period."
                    ),
                    nursery
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "vegetative",
                        "Vegetative Leaf Expansion",
                        "Vegetative",
                        "Cornell production set-points: 24 °C day / 19 °C night, RH 50–70 %."
                    ),
                    production
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "heading",
                        "Head or Marketable Biomass Development",
                        "Heading",
                        "Handbook does not split heading climate. Inherits production set-points."
                    ),
                    production.copy(
                        temperatureEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
                        humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
                        temperatureDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
                        humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED
                    )
                ),
                GrowthStageProfile(
                    GrowthStage(
                        "harvest",
                        "Harvest Readiness",
                        "Harvest",
                        "No separate harvest climate set-point. Inherits production set-points."
                    ),
                    production.copy(
                        temperatureEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
                        humidityEvidence = EvidenceLevel.CROP_LEVEL_INHERITED,
                        temperatureDerivation = DerivationMethod.CROP_LEVEL_INHERITED,
                        humidityDerivation = DerivationMethod.CROP_LEVEL_INHERITED
                    )
                )
            )
        )
    }
}

fun CropProfileRegistry.allNumericSourceIds(): Set<String> {
    val ids = mutableSetOf<String>()
    profiles.forEach { profile ->
        profile.stages.forEach { stage ->
            ids += stage.climate.sourceIds
        }
    }
    return ids
}
