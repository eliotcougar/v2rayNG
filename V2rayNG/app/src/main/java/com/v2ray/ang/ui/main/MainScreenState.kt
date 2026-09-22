package com.v2ray.ang.ui.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.tv.material3.DrawerState as TvDrawerState
import androidx.tv.material3.DrawerValue as TvDrawerValue
import androidx.tv.material3.rememberDrawerState as rememberTvDrawerState
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ProfileItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Stable
internal class MainTvDrawerCoordinator(val state: TvDrawerState) {
    var focusToRestore by mutableStateOf<(() -> Boolean)?>(null)
        private set

    val isOpen: Boolean
        get() = state.currentValue == TvDrawerValue.Open

    fun openFrom(restoreFocus: (() -> Boolean)?) {
        focusToRestore = restoreFocus
        state.setValue(TvDrawerValue.Open)
    }

    fun closeAndRestore() {
        // MainScreen restores after the drawer state change, including activity resume.
        state.setValue(TvDrawerValue.Closed)
    }
}

@Composable
internal fun rememberMainTvDrawerCoordinator(): MainTvDrawerCoordinator {
    val drawerState = rememberTvDrawerState(TvDrawerValue.Closed)
    return remember(drawerState) { MainTvDrawerCoordinator(drawerState) }
}

internal data class MainShareTarget(
    val guid: String,
    val profile: ProfileItem,
    val more: Boolean,
    val restoreFocus: () -> Boolean
)

internal sealed interface MainDialog {
    data object DeleteAll : MainDialog
    data object DeleteDuplicate : MainDialog
    data object DeleteInvalid : MainDialog
    data class DeleteServer(val guid: String) : MainDialog
    data class Share(val target: MainShareTarget) : MainDialog
}

@Stable
internal class MainPagerCoordinator(
    val pagerState: PagerState,
    val lazyListStates: SnapshotStateMap<String, LazyListState>,
    val lazyGridStates: SnapshotStateMap<String, LazyGridState>
) {
    var programmaticTargetPage by mutableStateOf<Int?>(null)
        private set
    private var navigationGeneration = 0L

    internal fun beginProgrammaticNavigation(targetPage: Int): Long {
        navigationGeneration += 1
        programmaticTargetPage = targetPage
        return navigationGeneration
    }

    internal fun finishProgrammaticNavigation(generation: Long) {
        if (navigationGeneration == generation) programmaticTargetPage = null
    }
}

internal fun mainSelectedGroupIndex(groups: List<GroupMapItem>, selectedGroupId: String): Int =
    groups.indexOfFirst { it.id == selectedGroupId }.takeIf { it >= 0 } ?: 0

internal fun shouldPublishSettledGroup(
    isTelevision: Boolean,
    programmaticTargetPage: Int?,
    settledPage: Int,
    selectedGroupIndex: Int
): Boolean = !isTelevision && programmaticTargetPage == null && settledPage != selectedGroupIndex

@Composable
internal fun rememberMainPagerCoordinator(
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    isTelevision: Boolean,
    onSelectGroup: (String) -> Unit
): MainPagerCoordinator {
    val latestPageCount by rememberUpdatedState(groups.size.coerceAtLeast(1))
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { latestPageCount })
    val coordinator = remember(pagerState) {
        MainPagerCoordinator(pagerState, mutableStateMapOf(), mutableStateMapOf())
    }
    MainPagerCoordinatorEffects(coordinator, groups, selectedGroupId, isTelevision, onSelectGroup)
    return coordinator
}

@Composable
private fun MainPagerCoordinatorEffects(
    coordinator: MainPagerCoordinator,
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    isTelevision: Boolean,
    onSelectGroup: (String) -> Unit
) {
    val groupIds = groups.map { it.id }
    val latestGroups by rememberUpdatedState(groups)
    val latestSelectedGroupId by rememberUpdatedState(selectedGroupId)
    val latestOnSelectGroup by rememberUpdatedState(onSelectGroup)

    LaunchedEffect(groupIds) {
        val validGroupIds = groupIds.toSet()
        coordinator.lazyListStates.keys.retainAll(validGroupIds)
        coordinator.lazyGridStates.keys.retainAll(validGroupIds)
    }

    // Selection is authoritative. This is the only path that moves the TV pager.
    LaunchedEffect(groupIds, selectedGroupId, isTelevision) {
        if (groups.isEmpty()) return@LaunchedEffect
        val targetPage = mainSelectedGroupIndex(groups, selectedGroupId)
        if (coordinator.pagerState.settledPage == targetPage) return@LaunchedEffect
        val generation = coordinator.beginProgrammaticNavigation(targetPage)
        try {
            coordinator.pagerState.navigateToPageOptimized(targetPage, animateAdjacentPage = !isTelevision)
        } finally {
            coordinator.finishProgrammaticNavigation(generation)
        }
    }

    // Only a settled, user-driven phone swipe may publish pager state as selection.
    LaunchedEffect(coordinator.pagerState, isTelevision) {
        snapshotFlow { coordinator.pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { settledPage ->
                val currentGroups = latestGroups
                if (settledPage !in currentGroups.indices) return@collect
                val selectedPage = mainSelectedGroupIndex(currentGroups, latestSelectedGroupId)
                if (shouldPublishSettledGroup(isTelevision, coordinator.programmaticTargetPage, settledPage, selectedPage)) {
                    latestOnSelectGroup(currentGroups[settledPage].id)
                }
            }
    }
}
