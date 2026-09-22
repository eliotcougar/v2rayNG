package com.v2ray.ang.ui.compose

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadFocusTargetsTest {
    @Test
    fun survivingKeysRetainTheirTargetsAcrossInsertRemoveAndReorder() {
        val store = DpadFocusTargets<String, Any>()
        val original = store.snapshot(listOf("a", "b"), ::Any)
        val changed = store.snapshot(listOf("c", "b"), ::Any)
        assertSame(original["b"], changed["b"])
        assertEquals(setOf("b", "c"), changed.keys)
        val reordered = store.snapshot(listOf("b", "c", "a"), ::Any)
        assertSame(changed["c"], reordered["c"])
        assertNotSame(original["a"], reordered["a"])
        assertTrue(store.snapshot(emptyList(), ::Any).isEmpty())
    }

    @Test
    fun keyboardAndRemoteActivationKeysUseTheSameContract() {
        listOf(Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar).forEach {
            assertTrue(it.isDpadActivationKey())
        }
        listOf(Key.DirectionUp, Key.DirectionDown, Key.Back, Key.Escape).forEach {
            assertFalse(it.isDpadActivationKey())
        }
    }
}
