package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel internal constructor(
    application: Application,
    private val readStartOnBoot: () -> Boolean,
    private val writeStartOnBoot: (Boolean) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel(application) {

    constructor(application: Application) : this(
        application, MmkvManager::decodeStartOnBoot,
        { enabled ->
            MmkvManager.encodeSettings(AppConfig.PREF_START_ON_BOOT, enabled).also { saved ->
                if (saved) SettingsChangeManager.notifySettingChanged(AppConfig.PREF_START_ON_BOOT)
            }
        },
        Dispatchers.IO
    )

    // Null means the persisted choice (including migration for old TV installs) is not loaded yet.
    private val _startupSettings = MutableStateFlow<Boolean?>(null)
    val startupSettings = _startupSettings.asStateFlow()
    private var startupWrite: Job = viewModelScope.launch(ioDispatcher) {
        accessStartupSetting { _startupSettings.value = readStartOnBoot() }
    }

    fun setStartOnBoot(enabled: Boolean) {
        if (startupSettings.value == null) return
        val previous = startupWrite
        startupWrite = viewModelScope.launch(ioDispatcher) {
            previous.join()
            accessStartupSetting {
                if (writeStartOnBoot(enabled)) _startupSettings.value = enabled
                else LogUtil.e(AppConfig.TAG, "Settings: failed to save start-on-boot preference")
            }
        }
    }

    private inline fun accessStartupSetting(action: () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed write must not kill the writer or publish an unsaved toggle value.
            LogUtil.e(AppConfig.TAG, "Settings: start-on-boot preference access failed", e)
        }
    }

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    suspend fun refreshSystemVpnSettingsAvailability() {
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(getApplication<Application>().packageManager) != null
        }
    }

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }
}
