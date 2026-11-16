package com.example.personal_studio.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.DebugMoment
import com.example.personal_studio.data.local.datastore.ThemePreferences
import com.example.personal_studio.ui.theme.daylight.DaylightPalette
import com.example.personal_studio.ui.theme.daylight.FamilyGate
import com.example.personal_studio.ui.theme.daylight.SolarCalculator
import com.example.personal_studio.ui.theme.daylight.SunDay
import com.example.personal_studio.ui.theme.daylight.TgColors
import com.example.personal_studio.ui.theme.daylight.ThemeFamily
import com.example.personal_studio.ui.theme.daylight.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZonedDateTime

/** Where the day is right now, for components that draw time (the horizon, the now line). */
@Immutable
data class DaylightState(
    val date: LocalDate,
    val hour: Double,
    val sun: SunDay,
    val mode: ThemeMode,
    /** True while the user scrubs the 今日 horizon and [hour] is a preview, not the clock. */
    val isPreview: Boolean = false,
    /** The clock's hour (or the debug moment) even while [hour] is a preview. */
    val liveHour: Double = hour,
)

val LocalTgColors = compositionLocalOf { DaylightPalette.Noon }
val LocalDaylight = compositionLocalOf<DaylightState?> { null }

/**
 * Call at a safe moment for a pending light/dark switch, i.e. on navigation. Resuming the
 * app is handled by [AppTheme] itself.
 */
val LocalThemeSafePoint = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Previews the palette at another hour of today (long-press scrubbing on the 今日 horizon);
 * pass null to return to the live palette. The preview respects the chosen theme mode.
 */
val LocalDaylightPreview = staticCompositionLocalOf<(Double?) -> Unit> { {} }

/** How many [DarkCanvas] screens are showing; while any is, the system bars stay light-on-dark. */
private val LocalDarkCanvasCount = staticCompositionLocalOf<MutableIntState?> { null }

/** Entry point to the 天光 tokens for redesigned screens. */
object Tg {
    val colors: TgColors
        @Composable @ReadOnlyComposable get() = LocalTgColors.current

    val daylight: DaylightState?
        @Composable @ReadOnlyComposable get() = LocalDaylight.current
}

/**
 * The 天光 theme: palette from the real sun over 北京, light/dark switches only at safe
 * moments, Material components bridged onto the same tokens.
 */
@Composable
fun AppTheme(
    preferences: ThemePreferences? = null,
    content: @Composable () -> Unit,
) {
    val mode by remember(preferences) { preferences?.mode ?: flowOf(ThemeMode.DAYLIGHT) }
        .collectAsStateWithLifecycle(ThemeMode.DAYLIGHT)
    val debugMoment by remember(preferences) { preferences?.debugMoment ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)

    val solar = remember { SolarCalculator() }
    var now by remember { mutableStateOf(ZonedDateTime.now(solar.zone)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Tick on the minute, only while the app is visible.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = ZonedDateTime.now(solar.zone)
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            }
        }
    }

    val sun = solar.sunDay(now.toLocalDate())
    val moment = if (BuildConfig.DEBUG) debugMoment else null
    val liveHour = moment?.let { hourOf(it, sun) } ?: (now.hour + now.minute / 60.0)
    var previewHour by remember { mutableStateOf<Double?>(null) }
    val setPreview: (Double?) -> Unit = remember { { previewHour = it } }
    val hour = previewHour ?: liveHour
    val natural = DaylightPalette.naturalFamily(liveHour, sun)

    val gate = remember(mode, moment) { FamilyGate() }
    var held by remember(mode, moment) { mutableStateOf<ThemeFamily?>(null) }
    LaunchedEffect(gate, natural) { held = gate.propose(natural) }
    val safePoint: () -> Unit = remember(gate) { { if (gate.safePoint()) held = gate.applied } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { safePoint() }

    val systemDark = isSystemInDarkTheme()
    val target = previewHour?.let { DaylightPalette.resolve(mode, it, sun, systemDark, null) }
        ?: DaylightPalette.resolve(mode, liveHour, sun, systemDark, if (moment != null) null else held)
    val colors = animatedColors(target, scrubbing = previewHour != null)

    val darkCanvases = remember { mutableIntStateOf(0) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        val lightBars = colors.isLight && darkCanvases.intValue == 0
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    CompositionLocalProvider(
        LocalTgColors provides colors,
        LocalDaylight provides DaylightState(now.toLocalDate(), hour, sun, mode, previewHour != null, liveHour),
        LocalThemeSafePoint provides safePoint,
        LocalDaylightPreview provides setPreview,
        LocalDarkCanvasCount provides darkCanvases,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = TgTypography,
            shapes = TgShapes,
        ) {
            // Text and icons without an explicit colour follow the theme instead of
            // Material's black default (no Scaffold/Surface sits above the screens).
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

/**
 * Camera, crop and photo-review screens stay dark whatever the time (design rule): the
 * chrome must never tint or brighten a photo. Each such screen wraps itself, so the page it
 * slides over keeps its own colours during the transition. [enabled] switches the palette
 * without changing the composition structure.
 */
@Composable
fun DarkCanvas(enabled: Boolean = true, content: @Composable () -> Unit) {
    val counter = LocalDarkCanvasCount.current
    DisposableEffect(counter, enabled) {
        if (enabled) counter?.let { it.intValue++ }
        onDispose { if (enabled) counter?.let { it.intValue-- } }
    }
    val colors = if (enabled) DaylightPalette.Night else LocalTgColors.current
    CompositionLocalProvider(LocalTgColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = TgTypography,
            shapes = TgShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

/** Debug moments relative to today's sun, matching the design canvas. */
private fun hourOf(moment: DebugMoment, sun: SunDay): Double = when (moment) {
    DebugMoment.DAWN -> sun.sunrise + 0.33
    DebugMoment.MORNING -> sun.sunrise + 3.0
    DebugMoment.NOON -> sun.solarNoon
    DebugMoment.SUNSET -> sun.sunset - 0.15
    DebugMoment.NIGHT -> 23.5
}

/**
 * Minute-by-minute drift is applied instantly (it is imperceptible and keeps recomposition
 * cheap); a light/dark switch crossfades over [TgMotion.COLOR_CHANGE_MS]. While scrubbing,
 * every change follows the finger over [TgMotion.COLOR_SCRUB_MS]; releasing eases back.
 */
@Composable
private fun animatedColors(target: TgColors, scrubbing: Boolean): TgColors {
    var lastFamily by remember { mutableStateOf(target.family) }
    var lastScrubbing by remember { mutableStateOf(scrubbing) }
    val spec: AnimationSpec<Color> = when {
        scrubbing -> tween(TgMotion.COLOR_SCRUB_MS)
        lastScrubbing -> tween(TgMotion.COLOR_RELEASE_MS)
        target.family != lastFamily -> tween(TgMotion.COLOR_CHANGE_MS)
        else -> snap()
    }
    SideEffect {
        lastFamily = target.family
        lastScrubbing = scrubbing
    }

    @Composable
    fun animate(c: Color): Color = animateColorAsState(c, spec, label = "tg").value

    return TgColors(
        family = target.family,
        bg = animate(target.bg),
        surface = animate(target.surface),
        surfaceRaised = animate(target.surfaceRaised),
        text = animate(target.text),
        textSecondary = animate(target.textSecondary),
        textTertiary = animate(target.textTertiary),
        line = animate(target.line),
        accent = animate(target.accent),
        accentSoft = animate(target.accentSoft),
        onAccent = animate(target.onAccent),
        error = animate(target.error),
    )
}

/** Bridges the tokens onto Material so stock components match without per-call colours. */
private fun TgColors.toColorScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accent,
        inversePrimary = accentSoft,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = accent,
        tertiary = textSecondary,
        onTertiary = bg,
        tertiaryContainer = surface,
        onTertiaryContainer = text,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = bg,
        error = error,
        onError = bg,
        errorContainer = surface,
        onErrorContainer = error,
        outline = line,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = surfaceRaised,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
    )
}
