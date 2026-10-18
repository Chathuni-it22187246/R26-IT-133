package com.greenhands.app

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.PreviewEnvironment
import com.greenhands.app.identity.GreenHandsCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentPreviewTest {

    @Test
    fun previewModeUsesSampleValuesWithoutATimestamp() {
        val snapshot = PreviewEnvironment.snapshot
        assertEquals(GreenhouseConnectionState.PREVIEW, snapshot.connectionState)
        assertEquals(25.0, snapshot.temperatureC!!, 0.0)
        assertEquals(70.0, snapshot.relativeHumidityPercent!!, 0.0)
        assertNull(snapshot.serverTimestampMillis)
        assertFalse(snapshot.showsLiveTimestamp)
        assertTrue(snapshot.isSamplePreview)
        assertEquals("Preview Mode", GreenHandsCopy.PREVIEW_MODE)
        assertEquals("Sample values", GreenHandsCopy.SAMPLE_VALUES)
        assertTrue(GreenHandsCopy.PREVIEW_EXPLANATION.contains("No live greenhouse is connected"))
        assertFalse(GreenHandsCopy.PREVIEW_EXPLANATION.contains("14:20"))
    }

    @Test
    fun liveStateCanShowANullableServerTimestampLater() {
        val liveWithoutTime = GreenhouseEnvironmentSnapshot(
            connectionState = GreenhouseConnectionState.LIVE,
            temperatureC = 24.0,
            relativeHumidityPercent = 68.0,
            sensorOrGreenhouseId = "gh-1",
            serverTimestampMillis = null
        )
        assertFalse(liveWithoutTime.showsLiveTimestamp)
        val liveWithTime = liveWithoutTime.copy(serverTimestampMillis = 1_720_000_000_000L)
        assertTrue(liveWithTime.showsLiveTimestamp)
        assertEquals(GreenhouseConnectionState.OFFLINE_DELAYED, GreenhouseConnectionState.OFFLINE_DELAYED)
    }
}
