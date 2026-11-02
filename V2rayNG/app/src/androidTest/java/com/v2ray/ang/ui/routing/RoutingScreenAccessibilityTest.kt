package com.v2ray.ang.ui.routing

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutingScreenAccessibilityTest {
    @Test
    fun populatedScreenLoadsAndRecreatesWithTalkBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        // Keep the real screen reader running while inspecting the exported accessibility tree.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assertTrue("Enable TalkBack before running this test", accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty())
        assertTrue("Load routing rules before running this test", !MmkvManager.decodeRoutingRulesets().isNullOrEmpty())
        val editLabel = context.getString(R.string.action_edit)

        repeat(5) {
            ActivityScenario.launch(RoutingSettingActivity::class.java).use { scenario ->
                assertRuleAccessible(automation, editLabel)
                scenario.recreate()
                assertRuleAccessible(automation, editLabel)
            }
        }
    }

    private fun assertRuleAccessible(automation: UiAutomation, editLabel: String) {
        automation.waitForIdle(300, 5_000)
        val nodes = automation.rootInActiveWindow?.let(::descendants).orEmpty()
        val edit = nodes.firstOrNull { node ->
            val children = descendants(node)
            node.isVisibleToUser && node.isClickable && children.count { it.isClickable } == 1 &&
                children.any { it.contentDescription?.toString() == editLabel }
        }
        assertTrue("The loaded routing rule must expose its Edit action", edit != null)
        assertTrue("The Edit action must accept accessibility focus", edit!!.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
    }

    private fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(node)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { addAll(descendants(it)) }
        }
    }
}
