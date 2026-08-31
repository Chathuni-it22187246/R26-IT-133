plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    // Phase 10E-A spike: SceneView AR (AR Optional). Not wired to navigation yet.
    implementation(libs.sceneview.arsceneview)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}