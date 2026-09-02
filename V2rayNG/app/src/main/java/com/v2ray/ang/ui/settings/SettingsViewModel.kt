package com.v2ray.ang.ui.settings

import android.app.Application
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface StartupSettingsStore {
    fun startOnBoot(): Boolean
    fun setStartOnBoot(enabled: Boolean): Boolean
}

private object MmkvStartupSettingsStore : StartupSettingsStore {
    override fun startOnBoot(): Boolean = MmkvManager.decodeStartOnBoot()
    override fun setStartOnBoot(enabled: Boolean): Boolean {
        if (!MmkvManager.encodeSettings(AppConfig.PREF_START_ON_BOOT, enabled)) {
            LogUtil.e(AppConfig.TAG, "Settings: failed to save start-on-boot preference")
            return false
        }
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_START_ON_BOOT)
        return true
    }
}

data class StartupSettingsState(val startOnBoot: Boolean = false, val isReady: Boolean = false)

class SettingsViewModel private constructor(
    application: Application,
    private val startupSettingsStore: StartupSettingsStore,
    private val ioDispatcher: CoroutineDispatcher
) : BaseViewModel(application) {

    constructor(application: Application) : this(application, MmkvStartupSettingsStore, Dispatchers.IO)

    internal companion object {
        fun createForTest(
            application: Application,
            startupSettingsStore: StartupSettingsStore,
            ioDispatcher: CoroutineDispatcher
        ) = SettingsViewModel(application, startupSettingsStore, ioDispatcher)
    }

    private val _startupSettings = MutableStateFlow(StartupSettingsState())
    val startupSettings: StateFlow<StartupSettingsState> = _startupSettings.asStateFlow()
    private val startupSettingsWrites = Channel<Boolean>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(ioDispatcher) {
            _startupSettings.value = StartupSettingsState(startupSettingsStore.startOnBoot(), isReady = true)
            for (enabled in startupSettingsWrites) {
                if (startupSettingsStore.setStartOnBoot(enabled)) {
                    _startupSettings.value = StartupSettingsState(enabled, isReady = true)
                }
            }
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        if (startupSettings.value.isReady) startupSettingsWrites.trySend(enabled)
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
