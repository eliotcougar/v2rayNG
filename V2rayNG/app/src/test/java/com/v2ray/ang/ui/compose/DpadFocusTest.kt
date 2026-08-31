package com.v2ray.ang.ui.compose

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DpadFocusTest {

    @Test
    fun horizontalKeysFollowLogicalOrderInLtr() {
        assertEquals(DpadHorizontalDirection.Previous, logicalHorizontalDirection(Key.DirectionLeft, false))
        assertEquals(DpadHorizontalDirection.Next, logicalHorizontalDirection(Key.DirectionRight, false))
    }

    @Test
    fun horizontalKeysFollowLogicalOrderInRtl() {
        assertEquals(DpadHorizontalDirection.Next, logicalHorizontalDirection(Key.DirectionLeft, true))
        assertEquals(DpadHorizontalDirection.Previous, logicalHorizontalDirection(Key.DirectionRight, true))
    }

    @Test
    fun nonHorizontalKeysAreIgnored() {
        assertNull(logicalHorizontalDirection(Key.DirectionDown, false))
    }
}
