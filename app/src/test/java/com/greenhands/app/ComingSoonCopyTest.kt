package com.greenhands.app

import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.comingSoonCopy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComingSoonCopyTest {

    @Test
    fun comingSoonPagesAreDescriptiveAndNotTodo() {
        listOf(
            Routes.SENSOR_PLACEMENT,
            Routes.HARVESTING,
            Routes.DECISION_MAKING,
            "unknown"
        ).forEach { id ->
            val (title, body) = comingSoonCopy(id)
            assertTrue(title.isNotBlank())
            assertTrue(body.length > 40)
            assertFalse(body.contains("TODO", ignoreCase = true))
            assertFalse(body.contains("Phase 1", ignoreCase = true))
        }
    }
}
