package com.v2ray.ang.ui.compose

import androidx.compose.ui.focus.FocusRequester
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

    @Test
    fun adjacentFocusIndexStopsAtBothEdges() {
        assertNull(adjacentDpadFocusIndex(0, 3, DpadHorizontalDirection.Previous))
        assertEquals(1, adjacentDpadFocusIndex(0, 3, DpadHorizontalDirection.Next))
        assertEquals(1, adjacentDpadFocusIndex(2, 3, DpadHorizontalDirection.Previous))
        assertNull(adjacentDpadFocusIndex(2, 3, DpadHorizontalDirection.Next))
    }

    @Test
    fun topBarActionsFollowNavigationWithoutSearch() {
        val navigation = FocusRequester()
        val actions = listOf(FocusRequester(), FocusRequester())

        assertEquals(listOf(navigation) + actions, appTopBarFocusOrder(navigation, actions))
    }

    @Test
    fun topBarSearchAndClearPrecedeActions() {
        val navigation = FocusRequester()
        val searchInput = FocusRequester()
        val searchClear = FocusRequester()
        val actions = listOf(FocusRequester())

        assertEquals(listOf(navigation, searchInput, searchClear) + actions,
            appTopBarFocusOrder(navigation, actions, searchInput, searchClear))
    }
}
