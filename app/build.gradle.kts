import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The built-in AI endpoint (DeepSeek, OpenAI format) comes from local.properties, which is
// gitignored: the key must never be committed. Supported keys:
//   API_KEY=sk-...                         (the built-in DeepSeek key)
//   API_BASE_URL=https://api.deepseek.com
//   DEFAULT_LLM_MODEL=deepseek-flash       (deepseek-flash or deepseek-v4-pro)
// Without a key the app still builds; AI features then ask for a custom endpoint.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val defaultApiKey: String = localProps.getProperty("API_KEY", "")
val defaultApiBaseUrl: String = localProps.getProperty(
    "API_BASE_URL",
    "https://api.deepseek.com",
)
val defaultLlmModel: String = localProps.getProperty(
    "DEFAULT_LLM_MODEL",
    "deepseek-flash",
)

android {
    namespace = "com.example.personal_studio"
    compileSdk = 35  // stay within the Compose BOM's tested SDK range

    defaultConfig {
        applicationId = "com.example.personal_studio"
        minSdk = 30
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_API_KEY", "\"$defaultApiKey\"")
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$defaultApiBaseUrl\"")
        buildConfigField("String", "DEFAULT_LLM_MODEL", "\"$defaultLlmModel\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        ndk {
            // 只打 arm64-v8a:覆盖几乎所有现代设备;去掉 armeabi-v7a(老 32 位)省 ~27MB 原生库。
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release code on a local install: not debuggable (so ART precompiles it and Compose's
        // bundled baseline profiles apply), signed with the debug key so it installs over a
        // debug build and keeps its data. Use it to judge animation smoothness; debug builds
        // are much slower on first use of each screen.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    androidResources {
        // Don't compress the ONNX model — it's already not very compressible
        // and decompression on load would add startup latency + memory churn.
        noCompress += "onnx"
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose BOM + UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt-Work integration (used by P4 timeline reminders)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // HTTP (OpenRouter API client)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp.logging)

    testImplementation(libs.okhttp.mockwebserver)

    // Camera (CameraX + OpenCV)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.opencv.android)

    // ONNXRuntime for document-corner ML model (fastvit_sa24)
    implementation(libs.onnxruntime.android)

    // Drag-to-reorder for the scan detail page grid
    implementation(libs.reorderable)

    // Coil — image loading for KB thumbnails (File source via AsyncImage)
    implementation(libs.coil.compose)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)

    // Android tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}