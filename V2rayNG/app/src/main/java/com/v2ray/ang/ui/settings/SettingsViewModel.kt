package com.v2ray.ang.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface StartupSettingsStore {
    fun autoConnectOnAppStart(): Boolean
    fun startOnBoot(): Boolean
    fun setAutoConnectOnAppStart(enabled: Boolean)
    fun setStartOnBoot(enabled: Boolean)
}

private object MmkvStartupSettingsStore : StartupSettingsStore {
    override fun autoConnectOnAppStart(): Boolean = MmkvManager.decodeAutoConnectOnAppStart()
    override fun startOnBoot(): Boolean = MmkvManager.decodeStartOnBoot()
    override fun setAutoConnectOnAppStart(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_IS_BOOTED, enabled)
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_IS_BOOTED)
    }
    override fun setStartOnBoot(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_START_ON_BOOT, enabled)
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_START_ON_BOOT)
    }
}

data class StartupSettingsState(val autoConnectOnAppStart: Boolean = false, val startOnBoot: Boolean = false)

private sealed interface StartupSettingsWrite {
    data class AutoConnectOnAppStart(val enabled: Boolean) : StartupSettingsWrite
    data class StartOnBoot(val enabled: Boolean) : StartupSettingsWrite
}

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

    private val _startupSettings = MutableStateFlow(
        StartupSettingsState(
            autoConnectOnAppStart = startupSettingsStore.autoConnectOnAppStart(),
            startOnBoot = startupSettingsStore.startOnBoot()
        )
    )
    val startupSettings: StateFlow<StartupSettingsState> = _startupSettings.asStateFlow()
    private val startupSettingsWrites = Channel<StartupSettingsWrite>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(ioDispatcher) {
            for (write in startupSettingsWrites) {
                when (write) {
                    is StartupSettingsWrite.AutoConnectOnAppStart -> startupSettingsStore.setAutoConnectOnAppStart(write.enabled)
                    is StartupSettingsWrite.StartOnBoot -> startupSettingsStore.setStartOnBoot(write.enabled)
                }
            }
        }
    }

    fun setAutoConnectOnAppStart(enabled: Boolean) {
        _startupSettings.update { it.copy(autoConnectOnAppStart = enabled) }
        startupSettingsWrites.trySend(StartupSettingsWrite.AutoConnectOnAppStart(enabled))
    }

    fun setStartOnBoot(enabled: Boolean) {
        _startupSettings.update { it.copy(startOnBoot = enabled) }
        startupSettingsWrites.trySend(StartupSettingsWrite.StartOnBoot(enabled))
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
