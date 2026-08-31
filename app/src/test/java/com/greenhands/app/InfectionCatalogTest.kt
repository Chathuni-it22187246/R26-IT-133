package com.greenhands.app

import com.greenhands.app.decision.InfectionCatalog
import com.greenhands.app.decision.InfectionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfectionCatalogTest {

    private val rows = listOf(
        InfectionRecord(
            plantType = "Tomato",
            infectionShortName = "Powdery Mildew",
            infectionFullName = "Powdery Mildew (Oidium spp.)",
            severityLevel = "High",
            visibleSymptoms = "White powdery patches on upper leaf surface",
            treatmentDescription = "On Tomato: improve airflow and apply sulfur program",
            biologicalControl = "Bacillus subtilis foliar sprays",
            chemicalControl = "Labeled mildew fungicide",
            preventionSteps = "Avoid dense wet canopies"
        ),
        InfectionRecord(
            plantType = "Cucumber",
            infectionShortName = "Downy Mildew",
            infectionFullName = "Downy Mildew (Pseudoperonospora cubensis)",
            severityLevel = "Critical",
            visibleSymptoms = "Yellow angular lesions on leaves",
            treatmentDescription = "On Cucumber: reduce leaf wetness immediately",
            biologicalControl = "Phosphite alternatives",
            chemicalControl = "Rotate labeled downy mildew fungicides",
            preventionSteps = "Use resistant cultivars"
        )
    )

    @Test
    fun matchesPowderyMildewFromCameraLabel() {
        val match = InfectionCatalog.match(
            records = rows,
            query = "Powdery Mildew",
            infectionName = "Powdery Mildew",
            plantType = "Tomato"
        )
        assertEquals("Powdery Mildew", match?.infectionShortName)
        assertEquals("Tomato", match?.plantType)
        assertEquals("High", match?.severityLevel)
    }

    @Test
    fun parsesCsvLineWithNineColumns() {
        val line = "Tomato,Leaf Spot,Leaf Spot (Septoria),Medium,Circular spots,Remove leaves,Biologicals,Fungicide,Sanitize"
        val cols = InfectionCatalog.parseCsvLine(line)
        assertEquals(9, cols.size)
        assertEquals("Leaf Spot", cols[1])
    }

    @Test
    fun infectionScanRouteEncodesCrop() {
        assertTrue(com.greenhands.app.ui.navigation.Routes.infectionScan("Tomato").contains("Tomato"))
    }
}
