package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadReorderStateTest {

    @Test
    fun shortPressClicksWithoutEnteringMovement() {
        val state = DpadReorderState()

        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("a", 1, false, 0, 500))
        assertEquals(DpadReorderPhase.Pressed, state.phase)
        assertEquals(DpadReorderActivation.Click, state.onActivationKeyUp("a", 20, 500))
        assertEquals(DpadReorderPhase.Idle, state.phase)
        assertFalse(state.isMoving)
    }

    @Test
    fun repeatedDiscreteEnterEventsCannotDropOrClickAfterLongPress() {
        val state = DpadReorderState()

        state.onActivationKeyDown("a", 0, false, 0, 500)
        assertEquals(DpadReorderActivation.StartedMoving, state.onLongPressTimeout("a"))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyUp("a", 100, 500))
        assertEquals(DpadReorderPhase.MovingReady, state.phase)

        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("a", 0, false, 200, 500))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyUp("a", 210, 500))
        assertTrue(state.isMoving("a"))

        assertEquals(DpadReorderActivation.Dropped, state.onActivationKeyDown("a", 0, false, 711, 500))
        assertEquals(DpadReorderPhase.Idle, state.phase)
    }

    @Test
    fun backOrCenterDropRequestOnlyDropsTheMovingItem() {
        val state = DpadReorderState()

        state.onPointerLongPress("a", 0)
        assertEquals(DpadReorderActivation.None, state.onDropRequest("b"))
        assertEquals(DpadReorderActivation.Dropped, state.onDropRequest("a"))
        assertEquals(DpadReorderActivation.None, state.onDropRequest("a"))
    }

    @Test
    fun moveConsumesDirectionsAndChangesOnlyValidDestinations() {
        val state = DpadReorderState()
        val moves = mutableListOf<Pair<Int, Int>>()
        state.onPointerLongPress("a", 1)

        assertTrue(state.move(DpadReorderDirection.Up, 3, ::verticalDpadReorderTarget) { from, to -> moves += from to to })
        assertEquals(listOf(1 to 0), moves)
        assertEquals(0, state.movingIndex)
        assertTrue(state.move(DpadReorderDirection.Up, 3, ::verticalDpadReorderTarget) { from, to -> moves += from to to })
        assertEquals(listOf(1 to 0), moves)
    }

    @Test
    fun disappearingMovingKeyCancelsMovement() {
        val state = DpadReorderState()
        state.onPointerLongPress("a", 0)

        state.syncItems(listOf("b"))

        assertFalse(state.isMoving)
        assertEquals(-1, state.movingIndex)
    }

    @Test
    fun repeatGuardUsesConservativeFloorOrSlowerPlatformDelay() {
        assertEquals(500L, dpadRepeatReleaseGuardMillis(100))
        assertEquals(1_400L, dpadRepeatReleaseGuardMillis(700))
    }
}
