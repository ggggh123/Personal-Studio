# P0 · Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a runnable Android app shell with Compose + Hilt + Room + DataStore + 4-tab bottom navigation + a Settings screen whose "test Gemini" button proves the LLM provider works end-to-end.

**Architecture:** Single-module `:app` with strict package separation (`ui/`, `feature/`, `domain/`, `data/`, `core/`). MVVM via Hilt-injected ViewModels feeding Compose screens through `StateFlow`. `LLMProvider` is an interface in `data/remote/llm/` with `GeminiProvider` as the only implementation for now. Settings persists to DataStore and is the single source of truth for the runtime API key (falls back to a `BuildConfig` bundled default).

**Tech Stack:** Kotlin 2.0, Jetpack Compose (BOM), Material3, Navigation Compose, Hilt (+ KSP), Room + KSP, DataStore Preferences, Kotlinx Coroutines + Flow, Kotlinx Serialization JSON, Google Generative AI Android SDK, JUnit4 + MockK + coroutines-test + Turbine.

**Reference spec:** `docs/superpowers/specs/2026-04-20-personal-studio-design.md` (sections 2.1-2.4, 5).

---

## File Structure (what gets created/touched)

**Created:**

- `gradle/libs.versions.toml` — fully rewritten with all P0 versions
- `app/src/main/java/com/example/personal_studio/PersonalStudioApp.kt`
- `app/src/main/java/com/example/personal_studio/MainActivity.kt`
- `app/src/main/java/com/example/personal_studio/ui/theme/Color.kt`
- `app/src/main/java/com/example/personal_studio/ui/theme/Typography.kt`
- `app/src/main/java/com/example/personal_studio/ui/theme/Theme.kt`
- `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- `app/src/main/java/com/example/personal_studio/ui/MainScreen.kt`
- `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
- `app/src/main/java/com/example/personal_studio/ui/placeholder/FeaturePlaceholders.kt`
- `app/src/main/java/com/example/personal_studio/core/common/Result.kt`
- `app/src/main/java/com/example/personal_studio/core/common/AppError.kt`
- `app/src/main/java/com/example/personal_studio/core/util/CrashLogger.kt`
- `app/src/main/java/com/example/personal_studio/data/local/datastore/UserPreferencesKeys.kt`
- `app/src/main/java/com/example/personal_studio/data/local/datastore/UserPreferencesRepository.kt`
- `app/src/main/java/com/example/personal_studio/data/remote/llm/LlmChunk.kt`
- `app/src/main/java/com/example/personal_studio/data/remote/llm/LLMProvider.kt`
- `app/src/main/java/com/example/personal_studio/data/remote/llm/GeminiProvider.kt`
- `app/src/main/java/com/example/personal_studio/core/di/DatabaseModule.kt`
- `app/src/main/java/com/example/personal_studio/core/di/DataStoreModule.kt`
- `app/src/main/java/com/example/personal_studio/core/di/LlmModule.kt`
- `app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`
- `app/src/test/java/com/example/personal_studio/data/local/datastore/UserPreferencesRepositoryTest.kt`
- `app/src/test/java/com/example/personal_studio/data/remote/llm/FakeLLMProvider.kt`
- `app/src/test/java/com/example/personal_studio/feature/settings/vm/SettingsViewModelTest.kt`

**Modified:**

- `build.gradle.kts` (root)
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/themes.xml` (reduced to minimal Splash theme)
- `app/src/main/res/values-night/themes.xml` (reduced to minimal Splash theme)
- `app/src/main/res/values/colors.xml` (add brand tokens)
- `.gitignore` (add ignore for `local.properties`-generated BuildConfig stuff is already covered)

**Seed secret file (not committed):**

- `local.properties` — user appends `GEMINI_API_KEY=...`

---

## Task 1: Rewrite version catalog with all P0 dependencies

**Files:**
- Modify: `gradle/libs.versions.toml` (full rewrite)

- [ ] **Step 1: Replace `gradle/libs.versions.toml` with the full catalog**

Open `gradle/libs.versions.toml` and replace its entire contents with:

```toml
[versions]
# Build
agp = "9.1.1"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
javaToolchain = "17"

# AndroidX
coreKtx = "1.13.1"
lifecycle = "2.8.4"
activity = "1.9.1"
navigation = "2.8.0"
datastore = "1.1.1"
room = "2.6.1"
workManager = "2.9.1"

# Compose
composeBom = "2024.09.00"
composeMaterial3 = "1.3.0"

# Hilt
hilt = "2.52"
hiltNavCompose = "1.2.0"

# Kotlinx
coroutines = "1.8.1"
serialization = "1.7.1"

# LLM
googleGenAi = "0.9.0"

# Testing
junit = "4.13.2"
junitExt = "1.2.1"
espresso = "3.6.1"
mockk = "1.13.12"
turbine = "1.1.0"
coroutinesTest = "1.8.1"

[libraries]
# AndroidX core
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Compose (version from BOM)
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "composeMaterial3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# DataStore
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

# WorkManager (used later, but declare now so we don't revisit)
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavCompose" }

# Kotlinx
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

# LLM
google-generative-ai = { group = "com.google.ai.client.generativeai", name = "generativeai", version.ref = "googleGenAi" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 2: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: expand libs.versions.toml with Compose/Hilt/Room/Gemini deps"
```

---

## Task 2: Wire plugins in root `build.gradle.kts`

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Replace `build.gradle.kts` contents**

Replace the root `build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 2: Commit**

```bash
git add build.gradle.kts
git commit -m "build: declare all project plugins in root build.gradle.kts"
```

---

## Task 3: Rewrite `app/build.gradle.kts` with full P0 config

**Files:**
- Modify: `app/build.gradle.kts` (full rewrite)

- [ ] **Step 1: Replace `app/build.gradle.kts` contents**

Replace the file with:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Read optional default Gemini API key from local.properties (never commit it)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val defaultGeminiKey: String = localProps.getProperty("GEMINI_API_KEY", "")

android {
    namespace = "com.example.personal_studio"
    compileSdk = 35  // stay within the Compose BOM's tested SDK range

    defaultConfig {
        applicationId = "com.example.personal_studio"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-p0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_GEMINI_KEY", "\"$defaultGeminiKey\"")
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
        }
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

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // LLM
    implementation(libs.google.generative.ai)

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
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Note: we drop `compileSdk 36.1` in favor of stable 35 so the Compose BOM 2024.09 works without pre-release toolchain issues.

- [ ] **Step 2: Ensure `local.properties` is gitignored (already is), then add a placeholder line so you can fill in your key**

Append to `local.properties`:

```
# Personal-Studio: paste your Gemini API key below (leave blank for runtime-only key)
GEMINI_API_KEY=
```

You may fill in a real key later in your local file. This file is already gitignored.

- [ ] **Step 3: Sync Gradle**

Run (on a machine with Android SDK installed):

```bash
./gradlew :app:help
```

Expected: `BUILD SUCCESSFUL`. If it fails, read the error and fix (most common cause: `compileSdk` mismatch or missing plugin in catalog).

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: enable Compose + Hilt + KSP + Room in :app module"
```

---

## Task 4: Reset XML themes to minimal launcher theme

Compose will own our runtime theme; the XML theme is only used during the splash before Compose takes over.

**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Replace `values/themes.xml`**

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.PersonalStudio" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
    </style>
</resources>
```

- [ ] **Step 2: Replace `values-night/themes.xml`**

```xml
<resources>
    <style name="Theme.PersonalStudio" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
```

- [ ] **Step 3: Simplify `values/colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="brand_blue">#FF7CC4FF</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/main/res/values/colors.xml
git commit -m "style: reduce XML themes to minimal launcher theme; Compose owns runtime theming"
```

---

## Task 5: Create Compose theme (Color, Typography, AppTheme)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/ui/theme/Color.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/theme/Typography.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/theme/Theme.kt`

- [ ] **Step 1: Create `Color.kt`**

```kotlin
package com.example.personal_studio.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — aligned with the design spec's visual companion mockups
val BrandBlue = Color(0xFF7CC4FF)
val BrandYellow = Color(0xFFFFDC64)
val BrandGreen = Color(0xFF96FFB4)
val BrandRed = Color(0xFFFF8A8A)
val BrandPurple = Color(0xFFC89AFF)
val BrandOrange = Color(0xFFFFB46E)

// Neutrals
val Ink950 = Color(0xFF0F1014)
val Ink900 = Color(0xFF141418)
val Ink800 = Color(0xFF1A1B20)
val Ink700 = Color(0xFF25272F)
val Ink100 = Color(0xFFF4F5F7)
val Ink050 = Color(0xFFFFFFFF)
```

- [ ] **Step 2: Create `Typography.kt`**

```kotlin
package com.example.personal_studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
```

- [ ] **Step 3: Create `Theme.kt`**

```kotlin
package com.example.personal_studio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Ink950,
    secondary = BrandYellow,
    onSecondary = Ink950,
    tertiary = BrandPurple,
    background = Ink950,
    onBackground = Ink100,
    surface = Ink900,
    onSurface = Ink100,
    surfaceVariant = Ink800,
    onSurfaceVariant = Ink100,
    error = BrandRed,
    onError = Ink950,
)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Ink950,
    secondary = BrandYellow,
    onSecondary = Ink950,
    tertiary = BrandPurple,
    background = Ink050,
    onBackground = Ink950,
    surface = Ink050,
    onSurface = Ink950,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink950,
    error = BrandRed,
    onError = Ink050,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,  // keep brand identity; allow dynamic color later
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/theme/
git commit -m "ui: add Compose theme with brand color palette and typography"
```

---

## Task 6: Create `PersonalStudioApp` Application class

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/PersonalStudioApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `PersonalStudioApp.kt`**

```kotlin
package com.example.personal_studio

import android.app.Application
import com.example.personal_studio.core.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PersonalStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
```

- [ ] **Step 2: Register the Application class + declare needed permissions in `AndroidManifest.xml`**

Replace `app/src/main/AndroidManifest.xml` contents with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".PersonalStudioApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PersonalStudio"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.PersonalStudio">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Commit** (after Task 7 creates `CrashLogger`; or temporarily comment out the `CrashLogger.install(this)` line and un-comment after Task 7)

Do **not** commit yet — continue to Task 7 to satisfy the `CrashLogger` reference.

---

## Task 7: Create `CrashLogger` and error primitives

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/core/common/Result.kt`
- Create: `app/src/main/java/com/example/personal_studio/core/common/AppError.kt`
- Create: `app/src/main/java/com/example/personal_studio/core/util/CrashLogger.kt`

- [ ] **Step 1: Create `Result.kt`**

```kotlin
package com.example.personal_studio.core.common

/**
 * Lightweight Result wrapper used at repository/use-case boundaries.
 * Kotlin's stdlib kotlin.Result is intentionally awkward to return from public APIs,
 * so we use our own sealed type.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}
```

- [ ] **Step 2: Create `AppError.kt`**

```kotlin
package com.example.personal_studio.core.common

/** All user-surfaceable errors funnel through this hierarchy. */
sealed class AppError(val message: String, val cause: Throwable? = null) {
    class Network(message: String, cause: Throwable? = null) : AppError(message, cause)
    class LlmProvider(message: String, cause: Throwable? = null) : AppError(message, cause)
    class NotConfigured(message: String) : AppError(message)
    class Storage(message: String, cause: Throwable? = null) : AppError(message, cause)
    class Unknown(message: String, cause: Throwable? = null) : AppError(message, cause)
}
```

- [ ] **Step 3: Create `CrashLogger.kt`**

```kotlin
package com.example.personal_studio.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val TAG = "PersonalStudio"
    private const val DIR_NAME = "crash-logs"

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "crash-$timestamp.txt")
                file.writeText("Thread: ${thread.name}\n\n$sw")
                Log.e(TAG, "Crash written to ${file.absolutePath}")
            } catch (ioe: Throwable) {
                Log.e(TAG, "Failed to persist crash log", ioe)
            }
            prev?.uncaughtException(thread, throwable)
        }
    }
}
```

- [ ] **Step 4: Commit (together with Task 6's Application class)**

```bash
git add app/src/main/java/com/example/personal_studio/PersonalStudioApp.kt \
        app/src/main/java/com/example/personal_studio/core/ \
        app/src/main/AndroidManifest.xml
git commit -m "core: add Hilt Application, CrashLogger, AppResult and AppError"
```

---

## Task 8: Create `MainActivity` and navigation shell (routes + NavHost stub)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/MainActivity.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`

- [ ] **Step 1: Create `NavRoutes.kt`**

```kotlin
package com.example.personal_studio.ui.navigation

object NavRoutes {
    // Bottom-nav tabs
    const val CHAT = "chat"
    const val SCANNER = "scanner"
    const val KNOWLEDGE = "knowledge"
    const val TIMELINE = "timeline"

    // Non-tab destinations
    const val SETTINGS = "settings"
}
```

- [ ] **Step 2: Create `AppNavHost.kt`**

```kotlin
package com.example.personal_studio.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.personal_studio.feature.settings.ui.SettingsScreen
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.placeholder.ChatPlaceholder
import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder
import com.example.personal_studio.ui.placeholder.ScannerPlaceholder
import com.example.personal_studio.ui.placeholder.TimelinePlaceholder

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.CHAT,
    ) {
        composable(NavRoutes.CHAT) { ChatPlaceholder() }
        composable(NavRoutes.SCANNER) { ScannerPlaceholder() }
        composable(NavRoutes.KNOWLEDGE) { KnowledgePlaceholder() }
        composable(NavRoutes.TIMELINE) { TimelinePlaceholder() }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
```

- [ ] **Step 3: Create `MainActivity.kt`**

```kotlin
package com.example.personal_studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.personal_studio.ui.MainScreen
import com.example.personal_studio.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
```

Do **not** commit yet — MainScreen and placeholders are next.

---

## Task 9: Create `MainScreen` (4-tab scaffold) and placeholder tab screens

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/ui/MainScreen.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/placeholder/FeaturePlaceholders.kt`

- [ ] **Step 1: Create `FeaturePlaceholders.kt`**

```kotlin
package com.example.personal_studio.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ChatPlaceholder() = Placeholder("Chat · coming in P1")

@Composable
fun ScannerPlaceholder() = Placeholder("Scanner · coming in P2")

@Composable
fun KnowledgePlaceholder() = Placeholder("Knowledge · coming in P3")

@Composable
fun TimelinePlaceholder() = Placeholder("Timeline · coming in P4")

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
```

- [ ] **Step 2: Create `MainScreen.kt`**

```kotlin
package com.example.personal_studio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personal_studio.ui.navigation.NavRoutes

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(NavRoutes.CHAT, "Chat", Icons.Filled.ChatBubbleOutline),
    TabSpec(NavRoutes.SCANNER, "Scan", Icons.Filled.CameraAlt),
    TabSpec(NavRoutes.KNOWLEDGE, "知识", Icons.Filled.MenuBook),
    TabSpec(NavRoutes.TIMELINE, "日程", Icons.Filled.CalendarMonth),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(tabs.firstOrNull { it.route == currentRoute }?.label ?: "Personal-Studio")
                },
                actions = {
                    IconButton(onClick = { navController.navigate(NavRoutes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            // Hide bottom bar on non-tab destinations (e.g. Settings)
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(NavRoutes.CHAT) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        androidx.compose.foundation.layout.Box(Modifier.padding(inner)) {
            AppNavHost(navController = navController)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/MainActivity.kt \
        app/src/main/java/com/example/personal_studio/ui/
git commit -m "ui: add MainActivity, 4-tab Scaffold, NavHost and placeholder screens"
```

---

## Task 10: Room — DEFERRED TO P1 (no action in P0)

Room is already declared as a dependency in `app/build.gradle.kts` (Task 3), so `ksp(libs.androidx.room.compiler)` and `implementation(libs.androidx.room.ktx)` are ready for P1. We intentionally do **not** create `AppDatabase` or a `DatabaseModule` in P0 because:

1. Room's `@Database` annotation requires a non-empty `entities = []` list; a placeholder class would fail to compile.
2. Hilt's `@InstallIn(SingletonComponent::class)` provider would need a concrete AppDatabase instance. Without entities, we can't meaningfully provide one.
3. Nothing in P0 reads or writes from Room; DataStore handles all P0 persistence (API key only).

**No files created. No commit in this task.** This placeholder task exists only to document the decision and keep task numbers aligned with the dependency graph at the bottom of the plan. P1's plan will add `AppDatabase`, the first entity (`ChatSessionEntity`), the first DAO, and `DatabaseModule` in the first task of that phase.

---

## Task 11: Create DataStore preferences repository (TDD)

Approach: the `UserPreferencesRepository` interface contract is tested against an in-memory `FakeUserPreferencesRepository`. This tests the **behavioral contract** (Flow emits null → saved value after set) that all implementations must honor. The production `UserPreferencesRepositoryImpl` wraps Android DataStore; a full Robolectric / androidTest verification is deferred to P1 when Room tests set up the same infrastructure.

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/local/datastore/UserPreferencesKeys.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/local/datastore/UserPreferencesRepository.kt`
- Create: `app/src/test/java/com/example/personal_studio/data/local/datastore/FakeUserPreferencesRepository.kt`
- Create: `app/src/test/java/com/example/personal_studio/data/local/datastore/UserPreferencesRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/personal_studio/data/local/datastore/UserPreferencesRepositoryTest.kt` with exactly this content:

```kotlin
package com.example.personal_studio.data.local.datastore

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserPreferencesRepositoryTest {

    @Test
    fun `api key flow emits null initially, then the saved value after set`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.geminiApiKey.test {
            assertNull(awaitItem())
            fake.setGeminiApiKey("secret-key")
            assertEquals("secret-key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing api key emits null`() = runTest {
        val fake = FakeUserPreferencesRepository()
        fake.setGeminiApiKey("secret-key")
        fake.geminiApiKey.test {
            assertEquals("secret-key", awaitItem())
            fake.setGeminiApiKey(null)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

Also create `app/src/test/java/com/example/personal_studio/data/local/datastore/FakeUserPreferencesRepository.kt`:

```kotlin
package com.example.personal_studio.data.local.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow<String?>(null)
    override val geminiApiKey: Flow<String?> = state
    override suspend fun setGeminiApiKey(key: String?) {
        state.value = if (key.isNullOrBlank()) null else key
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.data.local.datastore.UserPreferencesRepositoryTest"
```

Expected: FAIL — `UserPreferencesRepository` does not exist.

- [ ] **Step 3: Create `UserPreferencesKeys.kt`**

```kotlin
package com.example.personal_studio.data.local.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

internal object UserPreferencesKeys {
    val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
}
```

- [ ] **Step 4: Create `UserPreferencesRepository.kt`**

```kotlin
package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserPreferencesRepository {
    val geminiApiKey: Flow<String?>
    suspend fun setGeminiApiKey(key: String?)
}

class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val geminiApiKey: Flow<String?> =
        dataStore.data.map { it[UserPreferencesKeys.GEMINI_API_KEY]?.takeIf { v -> v.isNotBlank() } }

    override suspend fun setGeminiApiKey(key: String?) {
        dataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(UserPreferencesKeys.GEMINI_API_KEY)
            else prefs[UserPreferencesKeys.GEMINI_API_KEY] = key
        }
    }
}
```

- [ ] **Step 5: Run tests and verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.data.local.datastore.UserPreferencesRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/ \
        app/src/test/java/com/example/personal_studio/data/local/
git commit -m "data: add UserPreferencesRepository + DataStore key for Gemini API key"
```

---

## Task 12: Create LLM abstraction (`LLMProvider`, `LlmChunk`) with a fake for tests

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/remote/llm/LlmChunk.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/remote/llm/LLMProvider.kt`
- Create: `app/src/test/java/com/example/personal_studio/data/remote/llm/FakeLLMProvider.kt`

- [ ] **Step 1: Create `LlmChunk.kt`**

```kotlin
package com.example.personal_studio.data.remote.llm

/**
 * Stream chunks returned by [LLMProvider]. A single generation produces zero or more
 * [Text] values followed by exactly one [Done] or one [Error].
 */
sealed interface LlmChunk {
    data class Text(val delta: String) : LlmChunk
    data class Done(val totalTokens: Int?) : LlmChunk
    data class Error(val message: String, val retryable: Boolean) : LlmChunk
}
```

- [ ] **Step 2: Create `LLMProvider.kt`**

```kotlin
package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic LLM contract. Implementations:
 *  - [GeminiProvider] (P0, only one for now)
 *  - future: OpenAIProvider, ClaudeProvider (P6+ if desired)
 */
interface LLMProvider {
    /** Human-readable name for UI/debug. */
    val name: String

    /** Text-only streaming generation. */
    fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk>

    /** Multimodal streaming generation: image bytes + text prompt. */
    fun generateMultimodal(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String? = null,
    ): Flow<LlmChunk>

    /**
     * Non-streaming structured output. Implementations should instruct the model
     * to return JSON matching [jsonSchema] and return the raw JSON string.
     * Callers handle parsing + retry-on-failure policy.
     */
    suspend fun generateStructured(
        prompt: String,
        jsonSchema: String,
    ): String
}
```

- [ ] **Step 3: Create `FakeLLMProvider.kt`** for tests (so VM tests don't depend on real network):

```kotlin
package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLLMProvider(
    private val textChunks: List<String> = listOf("hello, ", "world"),
) : LLMProvider {
    override val name: String = "fake"

    override fun generateText(prompt: String, systemPrompt: String?, temperature: Float): Flow<LlmChunk> = flow {
        textChunks.forEach { emit(LlmChunk.Text(it)) }
        emit(LlmChunk.Done(totalTokens = textChunks.sumOf { it.length }))
    }

    override fun generateMultimodal(prompt: String, images: List<ByteArray>, systemPrompt: String?): Flow<LlmChunk> =
        generateText(prompt, systemPrompt)

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String = "{}"
}
```

- [ ] **Step 4: Build to ensure compiles**

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/remote/llm/ \
        app/src/test/java/com/example/personal_studio/data/remote/llm/
git commit -m "llm: add LLMProvider interface + LlmChunk + FakeLLMProvider for tests"
```

---

## Task 13: Implement `GeminiProvider` against Google GenAI SDK

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/remote/llm/GeminiProvider.kt`

- [ ] **Step 1: Create `GeminiProvider.kt`**

```kotlin
package com.example.personal_studio.data.remote.llm

import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class GeminiProvider(
    private val prefs: UserPreferencesRepository,
    private val bundledDefaultKey: String,
    private val modelName: String = "gemini-1.5-flash",
) : LLMProvider {

    override val name: String = "gemini ($modelName)"

    private suspend fun resolveApiKey(): String? =
        prefs.geminiApiKey.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: bundledDefaultKey.takeIf { it.isNotBlank() }

    private suspend fun buildModel(temperature: Float): GenerativeModel? {
        val key = resolveApiKey() ?: return null
        return GenerativeModel(
            modelName = modelName,
            apiKey = key,
            generationConfig = generationConfig {
                this.temperature = temperature
            },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            ),
        )
    }

    override fun generateText(prompt: String, systemPrompt: String?, temperature: Float): Flow<LlmChunk> = flow {
        val model = buildModel(temperature) ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val full = buildString {
            if (!systemPrompt.isNullOrBlank()) appendLine(systemPrompt).appendLine()
            append(prompt)
        }
        model.generateContentStream(full).collect { resp ->
            resp.text?.takeIf { it.isNotEmpty() }?.let { emit(LlmChunk.Text(it)) }
        }
        emit(LlmChunk.Done(totalTokens = null))
    }.catch { t ->
        emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true))
    }

    override fun generateMultimodal(prompt: String, images: List<ByteArray>, systemPrompt: String?): Flow<LlmChunk> = flow {
        val model = buildModel(temperature = 0.7f) ?: run {
            emit(LlmChunk.Error("No API key configured — open Settings to add one.", retryable = false))
            return@flow
        }
        val bitmaps = images.map { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val content = content {
            if (!systemPrompt.isNullOrBlank()) text(systemPrompt)
            bitmaps.forEach { image(it) }
            text(prompt)
        }
        model.generateContentStream(content).collect { resp ->
            resp.text?.takeIf { it.isNotEmpty() }?.let { emit(LlmChunk.Text(it)) }
        }
        emit(LlmChunk.Done(totalTokens = null))
        bitmaps.forEach(Bitmap::recycle)
    }.catch { t ->
        emit(LlmChunk.Error(t.message ?: "Unknown LLM error", retryable = true))
    }

    override suspend fun generateStructured(prompt: String, jsonSchema: String): String {
        val model = buildModel(temperature = 0.2f)
            ?: throw IllegalStateException("No API key configured")
        val instructed = """
            You must respond with valid JSON conforming to this schema:
            $jsonSchema

            Return only the JSON, no Markdown fences, no prose.

            Task:
            $prompt
        """.trimIndent()
        val resp = model.generateContent(instructed)
        return resp.text ?: error("Gemini returned empty response")
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/remote/llm/GeminiProvider.kt
git commit -m "llm: implement GeminiProvider with text/multimodal streaming + structured"
```

---

## Task 14: Wire Hilt DI modules

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/core/di/DataStoreModule.kt`
- Create: `app/src/main/java/com/example/personal_studio/core/di/LlmModule.kt`

(No DatabaseModule yet — Room wiring deferred to P1 when first entity exists.)

- [ ] **Step 1: Create `DataStoreModule.kt`**

```kotlin
package com.example.personal_studio.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.local.datastore.UserPreferencesRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore("user-preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPreferencesDataStore

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(dataStore: DataStore<Preferences>): UserPreferencesRepository =
        UserPreferencesRepositoryImpl(dataStore)
}
```

- [ ] **Step 2: Create `LlmModule.kt`**

```kotlin
package com.example.personal_studio.core.di

import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.remote.llm.GeminiProvider
import com.example.personal_studio.data.remote.llm.LLMProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideLlmProvider(prefs: UserPreferencesRepository): LLMProvider =
        GeminiProvider(
            prefs = prefs,
            bundledDefaultKey = BuildConfig.DEFAULT_GEMINI_KEY,
        )
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/di/
git commit -m "di: add Hilt modules for DataStore and LLMProvider"
```

---

## Task 15: Implement `SettingsViewModel` + tests (TDD)

**Files:**
- Create: `app/src/test/java/com/example/personal_studio/feature/settings/vm/SettingsViewModelTest.kt`
- Create: `app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.personal_studio.feature.settings.vm

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.FakeUserPreferencesRepository
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // Unconfined so that flows launched inside the VM's init execute eagerly,
    // keeping the test deterministic without explicit advanceUntilIdle() calls.
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state shows empty key and idle test result`() = runTest {
        val vm = SettingsViewModel(FakeUserPreferencesRepository(), FakeLLMProvider())
        vm.uiState.test {
            val first = awaitItem()
            assertEquals("", first.apiKeyDraft)
            assertEquals(null, first.savedApiKey)
            assertEquals(TestConnectionState.Idle, first.testConnection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving api key updates savedApiKey in state`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(prefs, FakeLLMProvider())

        vm.onApiKeyDraftChanged("abc123")
        vm.onSaveApiKey()

        vm.uiState.test {
            val state = awaitItem()
            // savedApiKey updates after DataStore emits; turbine waits
            if (state.savedApiKey == null) {
                val next = awaitItem()
                assertEquals("abc123", next.savedApiKey)
            } else {
                assertEquals("abc123", state.savedApiKey)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `testConnection transitions Idle → Running → Success on happy path`() = runTest {
        val prefs = FakeUserPreferencesRepository()
        prefs.setGeminiApiKey("configured")
        val vm = SettingsViewModel(prefs, FakeLLMProvider(textChunks = listOf("pong")))
        vm.onTestConnection()

        vm.uiState.test {
            // sequence may collapse; assert final Success
            var reached = false
            while (!reached) {
                val s = awaitItem()
                if (s.testConnection is TestConnectionState.Success) {
                    assertTrue(s.testConnection.replyPreview.contains("pong"))
                    reached = true
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.settings.vm.SettingsViewModelTest"
```

Expected: compilation FAIL — `SettingsViewModel` not defined.

- [ ] **Step 3: Create `SettingsViewModel.kt`**

```kotlin
package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKeyDraft: String = "",
    val savedApiKey: String? = null,
    val testConnection: TestConnectionState = TestConnectionState.Idle,
)

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Running : TestConnectionState
    data class Success(val replyPreview: String) : TestConnectionState
    data class Failure(val message: String) : TestConnectionState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val llm: LLMProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        prefs.geminiApiKey
            .onEach { saved ->
                _uiState.update { it.copy(savedApiKey = saved) }
            }
            .launchIn(viewModelScope)
    }

    fun onApiKeyDraftChanged(value: String) {
        _uiState.update { it.copy(apiKeyDraft = value) }
    }

    fun onSaveApiKey() {
        val key = _uiState.value.apiKeyDraft
        viewModelScope.launch {
            prefs.setGeminiApiKey(key)
        }
    }

    fun onClearApiKey() {
        viewModelScope.launch {
            prefs.setGeminiApiKey(null)
            _uiState.update { it.copy(apiKeyDraft = "") }
        }
    }

    fun onTestConnection() {
        _uiState.update { it.copy(testConnection = TestConnectionState.Running) }
        viewModelScope.launch {
            val accumulator = StringBuilder()
            llm.generateText("Reply with exactly the word 'pong' and nothing else.")
                .collect { chunk ->
                    when (chunk) {
                        is LlmChunk.Text -> accumulator.append(chunk.delta)
                        is LlmChunk.Done -> _uiState.update {
                            it.copy(
                                testConnection = TestConnectionState.Success(
                                    replyPreview = accumulator.toString().take(200)
                                )
                            )
                        }
                        is LlmChunk.Error -> _uiState.update {
                            it.copy(testConnection = TestConnectionState.Failure(chunk.message))
                        }
                    }
                }
        }
    }
}
```

- [ ] **Step 4: Run the test and verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.settings.vm.SettingsViewModelTest"
```

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/vm/ \
        app/src/test/java/com/example/personal_studio/feature/settings/
git commit -m "settings: add SettingsViewModel with API key editor and test-connection action"
```

---

## Task 16: Build `SettingsScreen` UI

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`

- [ ] **Step 1: Create `SettingsScreen.kt`**

```kotlin
package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.settings.vm.TestConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
            Text(
                "留空将使用构建时内置的默认 key（release 包可能未内置）。填写后优先使用你的 key。",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = vm::onApiKeyDraftChanged,
                placeholder = {
                    Text(if (state.savedApiKey != null) "•••• 已设置（输入以覆盖）" else "粘贴你的 Gemini API key")
                },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveApiKey, enabled = state.apiKeyDraft.isNotBlank()) {
                    Text("保存")
                }
                OutlinedButton(onClick = vm::onClearApiKey, enabled = state.savedApiKey != null) {
                    Text("清除")
                }
            }

            Spacer(Modifier.width(8.dp))
            Text("连通性测试", style = MaterialTheme.typography.titleMedium)
            Text("点击下面按钮让 Gemini 回一句话，验证 key 与网络。", style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = vm::onTestConnection,
                enabled = state.testConnection !is TestConnectionState.Running,
            ) {
                if (state.testConnection is TestConnectionState.Running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("测试中…")
                } else {
                    Text("测试 Gemini")
                }
            }

            when (val tc = state.testConnection) {
                TestConnectionState.Idle -> Unit
                TestConnectionState.Running -> Unit
                is TestConnectionState.Success -> Text(
                    "✓ 成功：${tc.replyPreview}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is TestConnectionState.Failure -> Text(
                    "✕ 失败：${tc.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/ui/
git commit -m "settings: add SettingsScreen with API key editor and Gemini test button"
```

---

## Task 17: Final verification (P0 DoD)

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all PASS.

- [ ] **Step 2: Assemble a debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Install and run on a device or emulator**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 4: Manual smoke test — capture screenshots to `docs/superpowers/checkpoints/P0/`**

On the device:
1. App launches to the Chat tab (placeholder text visible) — screenshot as `p0-01-chat-tab.png`
2. Switch through Scan / Knowledge / Timeline tabs — each shows its placeholder — screenshot as `p0-02-tabs.png`
3. Tap the gear icon in top bar → Settings screen opens — screenshot as `p0-03-settings-empty.png`
4. Paste a real Gemini API key → tap "保存" — the field label updates to "已设置" on next focus — screenshot as `p0-04-settings-saved.png`
5. Tap "测试 Gemini" — spinner appears, then a success message containing "pong" — screenshot as `p0-05-gemini-success.png`

If Step 5 fails with "No API key configured": re-check the saved value. If it fails with a network error: check network access to Gemini endpoints.

- [ ] **Step 5: Commit the checkpoint screenshots**

```bash
mkdir -p docs/superpowers/checkpoints/P0
# copy the 5 PNGs you captured into that folder, then:
git add docs/superpowers/checkpoints/P0/
git commit -m "docs: add P0 DoD verification screenshots"
```

- [ ] **Step 6: Tag the P0 completion**

```bash
git tag -a p0-foundation -m "P0: foundation complete — app runs, 4 tabs, Gemini test passes"
```

---

## Appendix: Dependency graph between tasks

```
T1 libs.versions.toml
  └─ T2 root build.gradle.kts
       └─ T3 app/build.gradle.kts
            └─ T4 XML themes reset
                 └─ T5 Compose theme
                      └─ T6+T7 Application + Core common    (same commit)
                           └─ T8+T9 MainActivity + MainScreen + NavHost  (same commit)
                                └─ T10 Room deferred (no-op)
                                     └─ T11 DataStore repo (TDD)
                                          └─ T12 LLMProvider interface + fake
                                               └─ T13 GeminiProvider
                                                    └─ T14 Hilt modules
                                                         └─ T15 SettingsViewModel (TDD)
                                                              └─ T16 SettingsScreen
                                                                   └─ T17 Verification
```

Tasks are strictly sequential for P0 — do not attempt to parallelize. Each task produces a commit so rollback is cheap if anything breaks.

---

## Notes for the executor

1. **You MUST read `docs/superpowers/specs/2026-04-20-personal-studio-design.md` before starting.** This plan assumes sections 2.1–2.4 of that spec as context for naming conventions, package structure, and architectural rules.

2. **If a step compiles successfully but the app crashes at runtime, stop and investigate** — do not proceed to the next task with unverified regressions. P0 is the foundation everything else stands on.

3. **Do not modify any file outside this plan's "Files" lists** without surfacing it explicitly. This is the scope-control discipline.

4. **If Gemini's SDK API differs from this plan's calls** (Google updates the SDK occasionally), prefer: check the SDK's README/changelog → adapt only the `GeminiProvider` calls → keep the `LLMProvider` interface contract unchanged.

5. **`GEMINI_API_KEY` in `local.properties` is optional for building.** Leaving it blank just means the runtime falls back to the user's key entered in Settings. The build will still compile; only the "Test Gemini" button without user input will fail.
