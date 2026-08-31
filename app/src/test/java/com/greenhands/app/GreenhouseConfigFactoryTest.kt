package com.greenhands.app

import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.domain.GreenhouseConfigResult
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_CELL_SIZE_METERS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_HEIGHT_CELLS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_HEIGHT_METERS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_LENGTH_METERS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_WIDTH_CELLS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_WIDTH_METERS
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenhouseConfigFactoryTest {

    @Test
    fun defaultConfigProducesTwelveByEightGridWithNinetySixCells() {
        val result = GreenhouseConfigFactory.validate(GreenhousePhysicalConfig.default())
        assertTrue(result is GreenhouseConfigResult.Success)
        val success = result as GreenhouseConfigResult.Success
        assertEquals(DEFAULT_GREENHOUSE_LENGTH_METERS, success.config.lengthMeters, 0.0)
        assertEquals(DEFAULT_GREENHOUSE_WIDTH_METERS, success.config.widthMeters, 0.0)
        assertEquals(DEFAULT_GREENHOUSE_HEIGHT_METERS, success.config.heightMeters, 0.0)
        assertEquals(DEFAULT_GREENHOUSE_CELL_SIZE_METERS, success.config.cellSizeMeters, 0.0)
        assertEquals(DEFAULT_GREENHOUSE_WIDTH_CELLS, success.greenhouse.widthCells)
        assertEquals(DEFAULT_GREENHOUSE_HEIGHT_CELLS, success.greenhouse.heightCells)
        assertEquals(96, success.greenhouse.totalCells)
    }

    @Test
    fun validTenBySixAtOneMeterProducesTenBySixGrid() {
        val config = GreenhousePhysicalConfig(
            lengthMeters = 10.0,
            widthMeters = 6.0,
            heightMeters = 3.5,
            cellSizeMeters = 1.0
        )
        val result = GreenhouseConfigFactory.validate(config)
        assertTrue(result is GreenhouseConfigResult.Success)
        val greenhouse = (result as GreenhouseConfigResult.Success).greenhouse
        assertEquals(10, greenhouse.widthCells)
        assertEquals(6, greenhouse.heightCells)
        assertEquals(60, greenhouse.totalCells)
    }

    @Test
    fun validTwentyByTenAtHalfMeterProducesFortyByTwentyGrid() {
        val config = GreenhousePhysicalConfig(
            lengthMeters = 20.0,
            widthMeters = 10.0,
            heightMeters = 4.0,
            cellSizeMeters = 0.5
        )
        val result = GreenhouseConfigFactory.validate(config)
        assertTrue(result is GreenhouseConfigResult.Success)
        val greenhouse = (result as GreenhouseConfigResult.Success).greenhouse
        assertEquals(40, greenhouse.widthCells)
        assertEquals(20, greenhouse.heightCells)
        assertEquals(800, greenhouse.totalCells)
    }

    @Test
    fun zeroValuesAreRejected() {
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 0.0, widthMeters = 8.0, heightMeters = 4.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = 0.0, heightMeters = 4.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = 8.0, heightMeters = 0.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = 8.0, heightMeters = 4.0, cellSizeMeters = 0.0)
        )
    }

    @Test
    fun negativeValuesAreRejected() {
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = -12.0, widthMeters = 8.0, heightMeters = 4.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = -8.0, heightMeters = 4.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = 8.0, heightMeters = -4.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = 8.0, heightMeters = 4.0, cellSizeMeters = -1.0)
        )
    }

    @Test
    fun nonFiniteValuesAreRejected() {
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = Double.NaN, widthMeters = 8.0, heightMeters = 4.0, cellSizeMeters = 1.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(
                lengthMeters = 12.0,
                widthMeters = Double.POSITIVE_INFINITY,
                heightMeters = 4.0,
                cellSizeMeters = 1.0
            )
        )
        assertInvalid(
            GreenhousePhysicalConfig(
                lengthMeters = 12.0,
                widthMeters = 8.0,
                heightMeters = Double.NEGATIVE_INFINITY,
                cellSizeMeters = 1.0
            )
        )
    }

    @Test
    fun cellSizeLargerThanDimensionsIsRejected() {
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 5.0, widthMeters = 8.0, heightMeters = 4.0, cellSizeMeters = 6.0)
        )
        assertInvalid(
            GreenhousePhysicalConfig(lengthMeters = 12.0, widthMeters = 3.0, heightMeters = 4.0, cellSizeMeters = 4.0)
        )
    }

    @Test
    fun physicalHelpersConvertGridCentreToMeters() {
        val config = GreenhousePhysicalConfig.default()
        assertEquals(0.5, GreenhouseConfigFactory.physicalXMeters(0, config), 1e-9)
        assertEquals(1.5, GreenhouseConfigFactory.physicalYMeters(1, config), 1e-9)
        assertEquals(2.5, GreenhouseConfigFactory.physicalXMeters(2.0, config), 1e-9)
    }

    private fun assertInvalid(config: GreenhousePhysicalConfig) {
        val result = GreenhouseConfigFactory.validate(config)
        assertTrue(result is GreenhouseConfigResult.Invalid)
        assertTrue((result as GreenhouseConfigResult.Invalid).message.isNotBlank())
    }
}
