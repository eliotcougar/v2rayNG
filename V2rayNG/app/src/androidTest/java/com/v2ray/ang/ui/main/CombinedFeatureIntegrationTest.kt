package com.v2ray.ang.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in integration check for an isolated QA AVD; it owns that AVD's test configuration. */
class CombinedFeatureIntegrationTest {
    @Test
    fun combinedProfilesRoutingAndDaemonSurviveRecreationAndRepeatedStart() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedCombinedAvd") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val profile = ProfileItem.create(EConfigType.CUSTOM).apply { remarks = "Combined QA direct" }
        val raw = """{"log":{"loglevel":"warning"},"inbounds":[{"listen":"127.0.0.1","port":10808,"protocol":"socks","settings":{"auth":"noauth","udp":true},"tag":"socks"}],"outbounds":[{"protocol":"freedom","tag":"proxy"}]}"""
        assertEquals("combined-qa", MmkvManager.encodeServerConfig("combined-qa", profile, raw))
        MmkvManager.setSelectServer("combined-qa")
        MmkvManager.encodeSettings(AppConfig.PREF_MODE, "PROXY")

        // Imported IDs may collide with another rule and with the routing screen header.
        MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_RULESET,
            """[{"id":"domain_strategy","remarks":"QA first"},{"id":"domain_strategy","remarks":"QA second"},{"id":null,"remarks":"QA third"}]""")
        val rules = MmkvManager.decodeRoutingRulesetsForEditing()!!
        assertEquals(3, rules.map { it.id }.toSet().size)
        ActivityScenario.launch(RoutingSettingActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(rules, MmkvManager.decodeRoutingRulesetsForEditing())
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals("combined-qa", MmkvManager.getSelectServer())
            try {
                LauncherManager.startService(context)
                assertTrue("Cold core start must acknowledge running", awaitRunning(context))
                LauncherManager.startService(context)
                assertTrue("Repeated start must retain the running core", awaitRunning(context))
                scenario.recreate()
                assertTrue("Activity recreation must retain daemon state", awaitRunning(context))
            } finally {
                LauncherManager.stopService(context)
                LauncherManager.stopService(context)
            }
        }
    }

    private fun awaitRunning(context: Context): Boolean {
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getIntExtra("key", 0) == AppConfig.MSG_STATE_RUNNING ||
                    intent?.getIntExtra("key", 0) == AppConfig.MSG_STATE_START_SUCCESS) latch.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < deadline) {
                MessageHelper.sendMsg2Service(context, AppConfig.MSG_REGISTER_CLIENT, "")
                if (latch.await(250, TimeUnit.MILLISECONDS)) return true
            }
            return false
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
