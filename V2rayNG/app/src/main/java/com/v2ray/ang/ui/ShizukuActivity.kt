package com.v2ray.ang.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

internal fun isTetheringAvailable(platformEnabled: Boolean, uiMode: Int): Boolean =
    platformEnabled && uiMode and Configuration.UI_MODE_TYPE_MASK != Configuration.UI_MODE_TYPE_TELEVISION

internal fun Context.isTetheringAvailable(): Boolean = isTetheringAvailable(
    platformEnabled = resources.getBoolean(R.bool.shizuku_tethering_enabled),
    uiMode = resources.configuration.uiMode,
)

class ShizukuActivity : BaseComponentActivity() {
    private val viewModel: ShizukuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTetheringAvailable()) finish()
    }

    @Composable
    override fun ScreenContent() {
        if (!isTetheringAvailable()) return
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TetheringScreen(
            state = state,
            onBackClick = { finish() },
            onAction = viewModel::onAction,
        )
    }

    override fun onResume() {
        super.onResume()
        if (isTetheringAvailable()) viewModel.onResume()
    }
}
