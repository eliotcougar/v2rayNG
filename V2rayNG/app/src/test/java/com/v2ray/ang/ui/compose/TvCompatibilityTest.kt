package com.v2ray.ang.ui.compose

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TvCompatibilityTest {

    @Test
    fun bringIntoViewDistanceIsRoundedToPhysicalPixels() {
        assertEquals(13f, pixelAlignedScrollDistance(12.6f))
        assertEquals(-3f, pixelAlignedScrollDistance(-3.4f))
    }

    @Test
    fun imeFallbackIsReservedOnlyForZeroInsetTelevisionOverlays() {
        assertEquals(240.dp, tvImeOverlayPadding(true, true, 0, 240.dp))
        assertEquals(0.dp, tvImeOverlayPadding(true, true, 400, 240.dp))
        assertEquals(0.dp, tvImeOverlayPadding(false, true, 0, 240.dp))
        assertEquals(0.dp, tvImeOverlayPadding(true, false, 0, 240.dp))
    }

    @Test
    fun textFieldRecoveryCoversImeCloseFocusAndIdleStates() {
        assertEquals(
            TvTextFieldRecoveryAction.FinishEditing,
            tvTextFieldRecoveryAction(true, true, true, false)
        )
        assertEquals(
            TvTextFieldRecoveryAction.MaintainEditor,
            tvTextFieldRecoveryAction(true, true, false, false)
        )
        assertEquals(
            TvTextFieldRecoveryAction.RequestEditorFocus,
            tvTextFieldRecoveryAction(true, false, false, false)
        )
        assertEquals(
            TvTextFieldRecoveryAction.None,
            tvTextFieldRecoveryAction(false, false, false, false)
        )
    }
}
