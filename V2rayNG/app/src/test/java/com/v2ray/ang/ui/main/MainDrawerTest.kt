package com.v2ray.ang.ui.main

import android.content.res.Configuration
import com.v2ray.ang.ui.isTetheringAvailable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MainDrawerTest {
    @Test
    fun tetheringRequiresBothPlatformSupportAndNonTelevisionUi() {
        for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            for (platformEnabled in listOf(false, true)) {
                assertFalse(isTetheringAvailable(platformEnabled, Configuration.UI_MODE_TYPE_TELEVISION or nightMode))
                assertEquals(
                    platformEnabled,
                    isTetheringAvailable(platformEnabled, Configuration.UI_MODE_TYPE_NORMAL or nightMode),
                )
            }
        }
    }

    @Test
    fun supportedDevicesKeepEveryDestination() {
        assertEquals(MainDestination.entries, visibleMainDrawerItems(tetheringEnabled = true))
    }

    @Test
    fun unsupportedDevicesHideTetheringWithoutLosingAboutOrChangingOrder() {
        val destinations = visibleMainDrawerItems(tetheringEnabled = false)
        assertFalse(MainDestination.Tethering in destinations)
        assertEquals(MainDestination.About, destinations.last())
        assertEquals(
            listOf(
                MainDestination.Subscriptions, MainDestination.PerAppProxy, MainDestination.Routing,
                MainDestination.UserAssets, MainDestination.Settings, MainDestination.Promotion,
                MainDestination.Logcat, MainDestination.CheckUpdate, MainDestination.BackupRestore,
                MainDestination.About,
            ),
            destinations,
        )
    }
}
