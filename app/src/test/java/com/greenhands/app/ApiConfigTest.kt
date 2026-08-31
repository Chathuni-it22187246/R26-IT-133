package com.greenhands.app

import com.greenhands.app.decision.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {

    @Test
    fun apiPortIs8002() {
        assertEquals(8002, ApiConfig.API_PORT)
    }

    @Test
    fun baseUrlUsesPort8002() {
        assertTrue(ApiConfig.baseUrl.endsWith(":8002/"))
        assertTrue(ApiConfig.baseUrl.startsWith("http://"))
    }
}
