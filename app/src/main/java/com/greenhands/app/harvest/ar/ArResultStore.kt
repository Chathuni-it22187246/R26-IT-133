package com.greenhands.app.harvest.ar

/**
 * Session holder for the AR visualization screen.
 * Not persisted and not used by scanning, HSV, or the classifier.
 */
object ArResultStore {
    @Volatile
    var current: ArResultData? = null

    fun clear() {
        current = null
    }
}
