package com.greenhands.app.decision

import android.os.Build
import com.greenhands.app.BuildConfig

/**
 * Resolves the GreenHands AI backend base URL for emulators and physical devices.
 *
 * Android equivalent of Expo's `Constants.expoConfig?.debuggerHost` host extraction:
 * Gradle injects the development machine LAN IP into [BuildConfig.API_HOST] at build time
 * (override with `api.host=` in local.properties). Emulators use `10.0.2.2`; otherwise
 * falls back toward localhost-style hosts. Always connects on port 8002.
 */
object ApiConfig {
    const val API_PORT = 8002

    val baseUrl: String
        get() = "http://${resolveHost()}:$API_PORT/"

    fun resolveHost(): String {
        val configured = BuildConfig.API_HOST.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()

        if (isEmulatorOrLocalHost()) {
            // Emulator reaches the development machine via the special alias.
            return "10.0.2.2"
        }

        if (configured.isNotBlank() &&
            configured != "localhost" &&
            configured != "127.0.0.1"
        ) {
            return configured
        }

        return "localhost"
    }

    private fun isEmulatorOrLocalHost(): Boolean {
        fun str(value: String?): String = value.orEmpty()
        return str(Build.FINGERPRINT).startsWith("generic") ||
            str(Build.FINGERPRINT).startsWith("unknown") ||
            str(Build.MODEL).contains("google_sdk", ignoreCase = true) ||
            str(Build.MODEL).contains("Emulator", ignoreCase = true) ||
            str(Build.MODEL).contains("Android SDK built for", ignoreCase = true) ||
            str(Build.MANUFACTURER).contains("Genymotion", ignoreCase = true) ||
            (str(Build.BRAND).startsWith("generic") && str(Build.DEVICE).startsWith("generic")) ||
            str(Build.PRODUCT).contains("sdk", ignoreCase = true) ||
            str(Build.HARDWARE).contains("goldfish", ignoreCase = true) ||
            str(Build.HARDWARE).contains("ranchu", ignoreCase = true)
    }
}
