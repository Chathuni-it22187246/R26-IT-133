package com.greenhands.app.telemetry

/**
 * Invisible Firebase Anonymous Authentication. Never shown in GreenHands login UI.
 */
interface AnonymousAuthClient {
    val currentUserPresent: Boolean
    suspend fun ensureSignedIn(): Result<Unit>
}

class FakeAnonymousAuthClient(
    override var currentUserPresent: Boolean = false,
    var fail: Boolean = false
) : AnonymousAuthClient {
    var signInCount: Int = 0
        private set

    override suspend fun ensureSignedIn(): Result<Unit> {
        if (currentUserPresent) return Result.success(Unit)
        signInCount += 1
        if (fail) {
            return Result.failure(IllegalStateException("Anonymous authentication failed"))
        }
        currentUserPresent = true
        return Result.success(Unit)
    }
}
