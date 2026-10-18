package com.greenhands.app.telemetry

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reuses [FirebaseAuth.currentUser] when present; otherwise signs in anonymously once.
 * Failures are returned as [Result] and must not crash the app.
 */
class FirebaseAnonymousAuthClient(
    private val auth: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) : AnonymousAuthClient {
    private val mutex = Mutex()

    override val currentUserPresent: Boolean
        get() = auth().currentUser != null

    override suspend fun ensureSignedIn(): Result<Unit> = mutex.withLock {
        try {
            val instance = auth()
            if (instance.currentUser != null) {
                return@withLock Result.success(Unit)
            }
            suspendCancellableCoroutine { continuation ->
                instance.signInAnonymously().addOnCompleteListener { task ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    if (task.isSuccessful && instance.currentUser != null) {
                        continuation.resume(Result.success(Unit))
                    } else {
                        continuation.resume(
                            Result.failure(
                                task.exception ?: IllegalStateException("Anonymous authentication failed")
                            )
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
