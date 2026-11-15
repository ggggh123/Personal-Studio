package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 冷启动决定起始目的地：见过登录 → 今日；否则 → 登录。初值 null 表示仍在读取（首帧被挂起）。 */
@HiltViewModel
class RootViewModel @Inject constructor(
    loginPrefs: LoginPrefs,
) : ViewModel() {
    val startDestination: StateFlow<String?> =
        loginPrefs.observe
            .map { seen -> if (seen) NavRoutes.TIMELINE else NavRoutes.LOGIN }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
