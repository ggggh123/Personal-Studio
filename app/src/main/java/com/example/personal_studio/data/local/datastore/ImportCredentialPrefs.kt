package com.example.personal_studio.data.local.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.personal_studio.data.network.bit.NetworkMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SavedCredentials(
    val username: String,
    val password: String,
    val lastMode: NetworkMode?,
)

@Singleton
class ImportCredentialPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, FILE_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow<SavedCredentials?>(load())
    fun observeAll(): StateFlow<SavedCredentials?> = _state.asStateFlow()

    fun save(username: String, password: String, mode: NetworkMode) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_LAST_MODE, mode.name)
            .apply()
        _state.value = SavedCredentials(username, password, mode)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = null
    }

    /** 用户在登录页手动指定的网络模式(强制);null = 自动(按需探测/回退)。独立于 [SavedCredentials.lastMode]
     *  ——lastMode 是「上次成功的 mode」(自动的记忆),这个是「用户强制要求」,优先级更高。 */
    fun modeOverride(): NetworkMode? =
        prefs.getString(KEY_MODE_OVERRIDE, null)?.let { runCatching { NetworkMode.valueOf(it) }.getOrNull() }

    fun setModeOverride(mode: NetworkMode?) {
        prefs.edit().apply {
            if (mode == null) remove(KEY_MODE_OVERRIDE) else putString(KEY_MODE_OVERRIDE, mode.name)
        }.apply()
    }

    private fun load(): SavedCredentials? {
        val u = prefs.getString(KEY_USERNAME, null) ?: return null
        val p = prefs.getString(KEY_PASSWORD, null) ?: return null
        val m = prefs.getString(KEY_LAST_MODE, null)?.let {
            runCatching { NetworkMode.valueOf(it) }.getOrNull()
        }
        return SavedCredentials(u, p, m)
    }

    companion object {
        private const val FILE_NAME = "bit_import_creds"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LAST_MODE = "last_mode"
        private const val KEY_MODE_OVERRIDE = "mode_override"
    }
}
