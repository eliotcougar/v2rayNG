package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.v2ray.ang.extension.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

data class ScrollbarConfig(
    val thickness: Dp = 4.dp,
    val minThumbSize: Dp = 24.dp,
    val thumbColor: Color = Color.Unspecified,
    val trackColor: Color = Color.Transparent,
    val padding: Dp = 2.dp,
    val cornerRadius: Dp = 2.dp,
    val fadeOutDurationMs: Int = 1500,
    val fadeAnimDurationMs: Int = 300
)

private data class ScrollbarThumb(val offset: Float, val length: Float, val viewport: Float)

@Composable
private fun rememberScrollbarAlpha(
    key: Any,
    config: ScrollbarConfig,
    position: () -> Any
): State<Float> {
    val alpha = remember(key) { Animatable(0f) }
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val currentPosition = rememberUpdatedState(position)
    val changes = remember(key) {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(key) {
        snapshotFlow { currentPosition.value() }.collect { changes.tryEmit(Unit) }
    }
    LaunchedEffect(changes, config.fadeOutDurationMs, config.fadeAnimDurationMs) {
        changes.collectLatest {
            alpha.snapTo(1f)
            delay(config.fadeOutDurationMs.toLong())
            alpha.animateTo(0f, tween(config.fadeAnimDurationMs))
        }
    }
    return remember(alpha, lifecycleState) {
        derivedStateOf {
            if (lifecycleState.value == Lifecycle.State.RESUMED) alpha.value else 0f
        }
    }
}

@Composable
private fun scrollbarThumbColor(config: ScrollbarConfig): Color =
    if (config.thumbColor == Color.Unspecified) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
    else config.thumbColor

private fun calculateThumb(
    viewport: Float,
    estimatedContent: Float,
    scrolled: Float,
    minLength: Float
): ScrollbarThumb? {
    if (viewport <= 0f || estimatedContent <= viewport) return null
    val length = (viewport * viewport / estimatedContent).coerceIn(minLength, viewport * 0.5f)
    val maxScroll = estimatedContent - viewport
    val offset = (scrolled / maxScroll).coerceIn(0f, 1f) * (viewport - length)
    return ScrollbarThumb(offset, length, viewport)
}

private fun DrawScope.drawVerticalThumb(
    thumb: ScrollbarThumb,
    alpha: Float,
    config: ScrollbarConfig,
    thumbColor: Color
) {
    val thickness = config.thickness.toPx()
    val padding = config.padding.toPx()
    val radius = CornerRadius(config.cornerRadius.toPx())
    val x = if (layoutDirection == LayoutDirection.Rtl) padding else size.width - thickness - padding
    if (config.trackColor != Color.Transparent) {
        drawRoundRect(
            config.trackColor.copy(alpha = config.trackColor.alpha * alpha),
            Offset(x, 0f),
            Size(thickness, thumb.viewport),
            radius
        )
    }
    drawRoundRect(
        thumbColor.copy(alpha = thumbColor.alpha * alpha),
        Offset(x, thumb.offset),
        Size(thickness, thumb.length),
        radius
    )
}

private fun DrawScope.drawHorizontalThumb(
    thumb: ScrollbarThumb,
    alpha: Float,
    config: ScrollbarConfig,
    thumbColor: Color
) {
    val thickness = config.thickness.toPx()
    val padding = config.padding.toPx()
    val radius = CornerRadius(config.cornerRadius.toPx())
    val y = size.height - thickness - padding
    if (config.trackColor != Color.Transparent) {
        drawRoundRect(
            config.trackColor.copy(alpha = config.trackColor.alpha * alpha),
            Offset(0f, y),
            Size(thumb.viewport, thickness),
            radius
        )
    }
    drawRoundRect(
        thumbColor.copy(alpha = thumbColor.alpha * alpha),
        Offset(thumb.offset, y),
        Size(thumb.length, thickness),
        radius
    )
}

fun Modifier.verticalScrollbar(scrollState: ScrollState, config: ScrollbarConfig = ScrollbarConfig()): Modifier =
    composed {
        val alpha = rememberScrollbarAlpha(scrollState, config) { scrollState.value }
        val thumbColor = scrollbarThumbColor(config)
        drawWithContent {
            drawContent()
            if (alpha.value <= 0f) return@drawWithContent
            val viewport = size.height
            val thumb = calculateThumb(
                viewport,
                viewport + scrollState.maxValue,
                scrollState.value.toFloat(),
                config.minThumbSize.toPx()
            ) ?: return@drawWithContent
            drawVerticalThumb(thumb, alpha.value, config, thumbColor)
        }
    }

fun Modifier.horizontalScrollbar(scrollState: ScrollState, config: ScrollbarConfig = ScrollbarConfig()): Modifier =
    composed {
        val alpha = rememberScrollbarAlpha(scrollState, config) { scrollState.value }
        val thumbColor = scrollbarThumbColor(config)
        drawWithContent {
            drawContent()
            if (alpha.value <= 0f) return@drawWithContent
            val viewport = size.width
            val thumb = calculateThumb(
                viewport,
                viewport + scrollState.maxValue,
                scrollState.value.toFloat(),
                config.minThumbSize.toPx()
            ) ?: return@drawWithContent
            drawHorizontalThumb(thumb, alpha.value, config, thumbColor)
        }
    }

fun Modifier.verticalScrollbar(
    lazyListState: LazyListState,
    config: ScrollbarConfig = ScrollbarConfig()
): Modifier = composed {
    val alpha = rememberScrollbarAlpha(lazyListState, config) {
        lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
    }
    val thumbColor = scrollbarThumbColor(config)
    drawWithContent {
        drawContent()
        val layout = lazyListState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (alpha.value <= 0f || visible.isEmpty()) return@drawWithContent
        val viewport = layout.viewportSize.height.toFloat()
        val averageItemHeight = visible.sumOf { it.size } / visible.size.toFloat()
        val scrolled = visible.first().index * averageItemHeight +
            layout.viewportStartOffset - visible.first().offset
        val thumb = calculateThumb(
            viewport,
            averageItemHeight * layout.totalItemsCount,
            scrolled,
            config.minThumbSize.toPx()
        ) ?: return@drawWithContent
        drawVerticalThumb(thumb, alpha.value, config, thumbColor)
    }
}

fun Modifier.verticalScrollbar(
    lazyGridState: LazyGridState,
    config: ScrollbarConfig = ScrollbarConfig()
): Modifier = composed {
    val alpha = rememberScrollbarAlpha(lazyGridState, config) {
        lazyGridState.firstVisibleItemIndex to lazyGridState.firstVisibleItemScrollOffset
    }
    val thumbColor = scrollbarThumbColor(config)
    drawWithContent {
        drawContent()
        val layout = lazyGridState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (alpha.value <= 0f || visible.isEmpty()) return@drawWithContent
        val rows = visible.groupBy { it.row }
        val averageRowHeight = rows.values.map { row -> row.maxOf { it.size.height } }.average().toFloat()
        val columns = visible.maxOf { it.column } + 1
        val totalRows = (layout.totalItemsCount + columns - 1) / columns
        val first = visible.first()
        val scrolled = first.row * averageRowHeight + layout.viewportStartOffset - first.offset.y
        val thumb = calculateThumb(
            layout.viewportSize.height.toFloat(),
            averageRowHeight * totalRows,
            scrolled,
            config.minThumbSize.toPx()
        ) ?: return@drawWithContent
        drawVerticalThumb(thumb, alpha.value, config, thumbColor)
    }
}
