import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

fun resolveDevMachineHost(): String {
    val localProps = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { localProps.load(it) }
    }
    val override = sequenceOf("api.host", "API_HOST")
        .mapNotNull { key -> localProps.getProperty(key)?.trim()?.takeIf { it.isNotBlank() } }
        .firstOrNull()
    if (override != null) {
        return override
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
    }

    return try {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { host ->
                host.startsWith("192.168.") ||
                    host.startsWith("10.") ||
                    host.matches(Regex("""172\.(1[6-9]|2\d|3[0-1])\..*"""))
            } ?: "127.0.0.1"
    } catch (_: Exception) {
        "127.0.0.1"
    }
}

android {
    namespace = "com.greenhands.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.greenhands.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // LAN IP of the build machine (Expo debuggerHost equivalent) for physical devices.
        val apiHost = resolveDevMachineHost()
        buildConfigField("String", "API_HOST", "\"$apiHost\"")
        buildConfigField("int", "API_PORT", "8002")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Filament / SceneView native libs
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }
    androidResources {
        noCompress += listOf("filamat", "ktx", "tflite")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Navigation Compose 2.7.7 matches Compose 1.6 / Kotlin 1.9.0 used by Koala.
    implementation(libs.androidx.navigation.compose)
    // ViewModel Compose 2.8.3 matches the existing lifecycle-runtime-ktx catalog entry.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Extended icons come from Compose BOM 2024.04.01 (password visibility, climate, greenhouse).
    implementation(libs.androidx.compose.material.icons.extended)
    // Preferences DataStore 1.1.1 is the Koala-era stable line (Kotlin 1.9 / AGP 8.5). Not 1.2.x.
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // SceneView AR (ARCore + Filament Compose API)
    implementation(libs.sceneview.arsceneview)

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

}