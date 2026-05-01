package com.example.personal_studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.personal_studio.domain.timeline.RescheduleAllUpcomingUseCase
import com.example.personal_studio.ui.MainScreen
import com.example.personal_studio.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var rescheduleAllUpcoming: RescheduleAllUpcomingUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        rescheduleAllUpcoming()
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
