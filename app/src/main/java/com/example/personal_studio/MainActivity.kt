package com.example.personal_studio

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.example.personal_studio.data.local.datastore.ThemePreferences
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import com.example.personal_studio.feature.auth.RootViewModel
import com.example.personal_studio.ui.MainScreen
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.example.personal_studio.ui.theme.SerifFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.personal_studio.ui.theme.AppTheme
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.daylight.DaylightPalette
import com.example.personal_studio.ui.theme.daylight.SolarCalculator
import com.example.personal_studio.ui.theme.daylight.ThemeFamily
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZonedDateTime
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var rescheduleAllUpcoming: RescheduleAllUpcomingUseCase
    @Inject lateinit var themePreferences: ThemePreferences

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        fadeOutSystemSplash()
        paintWindowForCurrentDaylight()
        holdFirstFrameUntilStartDestination()
        rescheduleAllUpcoming()
        setContent {
            AppTheme(preferences = themePreferences) {
                val family = Tg.colors.family
                LaunchedEffect(family) { rememberSplashFamily(family) }
                // Read both serif faces (2.4 MB each) on a background thread into Compose's
                // typeface cache; otherwise the first page that uses a weight reads the file on
                // the main thread in the middle of its transition (seen on 校园 with Bold).
                val fontResolver = LocalFontFamilyResolver.current
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) { runCatching { fontResolver.preload(SerifFamily) } }
                }
                MainScreen()
            }
        }
    }

    /** The window shows before Compose draws; give it today's 天光 background, not a fixed white. */
    private fun paintWindowForCurrentDaylight() {
        val solar = SolarCalculator()
        val now = ZonedDateTime.now(solar.zone)
        val bg = DaylightPalette.at(now.hour + now.minute / 60.0, solar.sunDay(now.toLocalDate())).bg
        window.setBackgroundDrawable(ColorDrawable(bg.toArgb()))
    }

    /**
     * Replaces the old 1.3 s branded splash: the first frame waits only until the start page
     * is known (one DataStore read), with a 1 s ceiling so a slow disk never blocks startup.
     */
    private fun holdFirstFrameUntilStartDestination() {
        val content: View = findViewById(android.R.id.content)
        val startedAt = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = rootViewModel.startDestination.value != null ||
                    SystemClock.uptimeMillis() - startedAt > 1_000
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }

    /**
     * The system splash (Android 12+) is drawn from resources before the process runs, so it
     * cannot know the sun. Fade it out instead of the default reveal: if its colour and the
     * 天光 page differ, the change reads as a short dissolve rather than a flash.
     */
    private fun fadeOutSystemSplash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { splash ->
            splash.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { splash.remove() }
                .start()
        }
    }

    /** Android 13+: the next cold start opens on the family the app was last showing. */
    private fun rememberSplashFamily(family: ThemeFamily) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        splashScreen.setSplashScreenTheme(
            if (family == ThemeFamily.DARK) R.style.Theme_PersonalStudio_Night
            else R.style.Theme_PersonalStudio_Day,
        )
    }

    private companion object {
        const val SPLASH_FADE_MS = 220L
    }
}
