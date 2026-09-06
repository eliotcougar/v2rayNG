package com.v2ray.ang.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.ThemeManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PhoneAccessibilityRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation: UiAutomation
        get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun selectedServerHasOneStateAndNoRedundantClickAcrossLayoutsAndThemes() {
        requireTalkBack()
        val selectedGuid = UUID.randomUUID().toString()
        val otherGuid = UUID.randomUUID().toString()
        val previousGuid = MmkvManager.getSelectServer()
        val previousGroup = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID)
        val previousColumns = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY)
        val previousMode = ThemeManager.themeMode.value
        val previousDynamic = ThemeManager.dynamicColorEnabled.value
        val selectedName = "Phone QA primary profile"
        val otherName = "Phone QA alternate profile"
        try {
            assertNotNull(MmkvManager.encodeServerConfig(selectedGuid, ProfileItem(configType = EConfigType.VMESS, remarks = selectedName)))
            assertNotNull(MmkvManager.encodeServerConfig(otherGuid, ProfileItem(configType = EConfigType.VMESS, remarks = otherName)))
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, "")
            for (dark in listOf(false, true)) for (dynamic in listOf(false, true)) for (columns in listOf(false, true)) {
                ThemeManager.setThemeMode(if (dark) "2" else "1")
                ThemeManager.setDynamicColorEnabled(dynamic)
                MmkvManager.encodeSettings(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, columns)
                MmkvManager.setSelectServer(selectedGuid)
                ActivityScenario.launch(MainActivity::class.java).use {
                    val selected = awaitNode { it.contentDescription?.toString()?.startsWith(selectedName) == true }
                    assertEquals(context.getString(R.string.acc_selected_server), selected.stateDescription?.toString())
                    assertFalse(selected.isClickable)
                    assertFalse(selected.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                    assertFalse(selected.contentDescription.toString().contains(context.getString(R.string.acc_selected_server)))
                    val deleteLabel = context.getString(R.string.acc_delete_config_named, selectedName)
                    assertTrue(selected.actionList.any { it.label?.toString() == deleteLabel })
                    assertTrue(selected.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                    screenshot("server-dark-$dark-dynamic-$dynamic-columns-$columns")
                    val other = awaitNode { it.contentDescription?.toString()?.startsWith(otherName) == true }
                    assertTrue(other.isClickable)
                    assertTrue(other.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    awaitNode { it.contentDescription?.toString()?.startsWith(otherName) == true && !it.isClickable }
                    assertEquals(otherGuid, MmkvManager.getSelectServer())
                    assertTrue(awaitNode { it.contentDescription?.toString()?.startsWith(selectedName) == true }.isClickable)
                }
            }
        } finally {
            MmkvManager.removeServer(selectedGuid)
            MmkvManager.removeServer(otherGuid)
            previousGuid?.let(MmkvManager::setSelectServer)
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, previousGroup)
            MmkvManager.encodeSettings(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, previousColumns)
            ThemeManager.setThemeMode(previousMode)
            ThemeManager.setDynamicColorEnabled(previousDynamic)
        }
    }

    @Test
    fun disabledEditorsAndListsExposeDisabledStateAndCannotOpen() {
        requireTalkBack()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AppTheme {
                        Surface {
                            Column {
                                SettingsEditItem(title = "Disabled editor", value = "Existing value", enabled = false, onValueChanged = { fail("Disabled editor changed") })
                                SettingsListItem(title = "Disabled list", entries = listOf("Existing choice"), values = listOf("choice"), selectedValue = "choice", enabled = false, onSelected = { fail("Disabled list changed") })
                                SettingsEditItem(title = "Enabled editor", value = "Editable value", onValueChanged = {})
                            }
                        }
                    }
                }
            }
            for (title in listOf("Disabled editor", "Disabled list")) {
                val node = awaitNode { it.text?.toString()?.contains(title) == true }
                assertFalse(node.isEnabled)
                assertFalse(node.isClickable)
                assertFalse(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            }
            val enabled = awaitNode { it.text?.toString()?.contains("Enabled editor") == true }
            assertTrue(enabled.isEnabled)
            assertTrue(enabled.isClickable)
            screenshot("settings-disabled")
        }
    }

    @Test
    fun namedDeletionStartsOnCancelAndKeyboardCannotAccidentallyDelete() {
        requireTalkBack()
        val deleted = AtomicInteger()
        val dismissed = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AppTheme {
                        DeleteConfirmDialog(
                            message = context.getString(R.string.confirm_delete_profile_named, "Phone QA profile"),
                            onConfirm = { deleted.incrementAndGet() },
                            onDismiss = { dismissed.incrementAndGet() },
                        )
                    }
                }
            }
            val cancel = awaitNode { it.text?.toString() == context.getString(R.string.action_cancel) && it.isFocused }
            assertTrue(cancel.isClickable)
            assertTrue(cancel.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            awaitNode { it.text?.toString()?.contains("Phone QA profile") == true }
            screenshot("delete-cancel-focus")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            instrumentation.waitForIdleSync()
            assertEquals(0, deleted.get())
            assertEquals(1, dismissed.get())
        }
    }

    private fun requireTalkBack() {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        assertTrue("Enable TalkBack before this regression suite", manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty())
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            automation.rootInActiveWindow?.let(::descendants)?.firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected accessibility node not found")
    }

    private fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(node)
        for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(descendants(it)) }
    }

    private fun screenshot(name: String) {
        automation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
