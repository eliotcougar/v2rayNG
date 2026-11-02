package com.v2ray.ang.ui.compose

import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.extension.AccessibilityLiveRegionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LiveRegionMessageStateTest {

    @Test
    fun mirrorsEveryMessageTypeToAPoliteLiveRegionByDefault() {
        for (type in UserMessage.Type.entries) {
            val state = LiveRegionMessageState { 0L }
            assertNull(state.current)
            state.offer(UserMessage(text = "Message", type = type))

            assertEquals("Message", state.current?.text)
            assertEquals(AccessibilityLiveRegionMode.POLITE, state.current?.mode)
        }
    }

    @Test
    fun ignoresBlankMessages() {
        val state = LiveRegionMessageState { 0L }
        state.offer(UserMessage(text = " "))
        assertNull(state.current)

        state.offer(UserMessage(text = "Result"))
        state.offer(UserMessage(text = "\n"))
        assertEquals("Result", state.current?.text)
        state.advance(state.current!!.id)
        assertNull(state.current)
    }

    @Test
    fun suppressesOnlyRecentDuplicatesWithTheSameMode() {
        var now = 100L
        val state = LiveRegionMessageState { now }
        val polite = UserMessage(text = "Service state")
        state.offer(polite)
        state.offer(polite)
        state.advance(state.current!!.id)
        assertNull(state.current)

        val assertive = polite.copy(liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE)
        state.offer(assertive)
        assertEquals(AccessibilityLiveRegionMode.ASSERTIVE, state.current?.mode)
        state.advance(state.current!!.id)

        now += DuplicateLiveRegionMessageWindowMs
        state.offer(assertive)
        assertEquals("Service state", state.current?.text)
    }

    @Test
    fun usesTheAccessibilityMessageWithoutChangingTheVisibleMessage() {
        val state = LiveRegionMessageState { 0L }
        val event = UserMessage(
            text = "Service started successfully",
            liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE,
            accessibilityText = "Service started successfully. Now connected to Lab.",
        )
        state.offer(event)

        assertEquals("Service started successfully. Now connected to Lab.", state.current?.text)
        assertEquals("Service started successfully", event.text)
    }

    @Test
    fun aBurstCannotReplaceTheCurrentServiceResultBeforePublication() {
        val state = LiveRegionMessageState { 0L }
        state.offer(UserMessage("Connected", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE))
        val resultId = state.current!!.id
        state.offer(UserMessage("Copied"))
        state.offer(UserMessage("Updated"))

        assertEquals("Connected", state.current?.text)
        state.advance(resultId)
        assertEquals("Copied", state.current?.text)
        // A cancelled expiry must not dismiss the next message.
        state.advance(resultId)
        assertEquals("Copied", state.current?.text)
        state.advance(state.current!!.id)
        assertEquals("Updated", state.current?.text)
        state.advance(state.current!!.id)
        assertNull(state.current)
    }

    @Test
    fun assertiveResultsCannotOvertakeEarlierPoliteStateChanges() {
        val state = LiveRegionMessageState { 0L }
        state.offer(UserMessage("Downloading"))
        state.offer(UserMessage("Starting service"))
        state.offer(UserMessage("Connected", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE))
        state.offer(UserMessage("Stopped", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE))
        assertEquals("Downloading", state.current?.text)

        for ((text, mode) in listOf(
            "Starting service" to AccessibilityLiveRegionMode.POLITE,
            "Connected" to AccessibilityLiveRegionMode.ASSERTIVE,
            "Stopped" to AccessibilityLiveRegionMode.ASSERTIVE,
        )) {
            state.advance(state.current!!.id)
            assertEquals(text, state.current?.text)
            assertEquals(mode, state.current?.mode)
        }
        state.advance(state.current!!.id)
        assertNull(state.current)
    }

    @Test
    fun backgroundingDropsPendingFeedbackAndResetsDeduplication() {
        val state = LiveRegionMessageState { 0L }
        val message = UserMessage("Connected", liveRegionMode = AccessibilityLiveRegionMode.ASSERTIVE)
        state.offer(message)
        val previousId = state.current!!.id
        state.offer(UserMessage("Copied"))
        state.clear()
        assertNull(state.current)

        state.offer(message)
        state.advance(previousId)
        assertEquals("Connected", state.current?.text)
        state.advance(state.current!!.id)
        assertNull(state.current)
    }
}
