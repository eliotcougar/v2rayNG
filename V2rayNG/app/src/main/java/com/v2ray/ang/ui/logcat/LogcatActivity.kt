package com.v2ray.ang.ui.logcat

import android.content.ClipData
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.LocalAppSnackbar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.tvContentPadding
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LogcatRow(val raw: String, val tag: String, val content: String)

private fun parseLogcatRow(log: String): LogcatRow {
    if (log.isEmpty()) return LogcatRow("", "", "")
    val parts = log.split("):", limit = 2)
    return LogcatRow(
        raw = log,
        tag = parts.first().split("(", limit = 2).first().trim(),
        content = if (parts.size > 1) parts.last().trim() else ""
    )
}

class LogcatActivity : BaseComponentActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        LogcatScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onShareLogcat = { shareLogcat() }
        )
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.filteredLogs.value.joinToString("\n")

            val result = try {
                val shareDir = File(cacheDir, "shared_logs").apply {
                    mkdirs()
                }

                shareDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "v2rayNG_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    this@LogcatActivity,
                    "${packageName}.cache",
                    logFile
                )

                uri to logFile.name
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to share Logcat", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, result.first)
                    putExtra(Intent.EXTRA_SUBJECT, result.second)
                    putExtra(Intent.EXTRA_TITLE, result.second)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(contentResolver, result.second, result.first)
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.logcat_share)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogcatScreen(
    viewModel: LogcatViewModel,
    onBackClick: () -> Unit,
    onShareLogcat: () -> Unit
) {
    val context = LocalContext.current
    val isTelevision = isTelevisionDevice()
    val scope = rememberCoroutineScope()
    val logs by viewModel.filteredLogs.collectAsStateWithLifecycle()
    val rows = remember(logs) { logs.map(::parseLogcatRow) }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val snackbar = LocalAppSnackbar.current
    LaunchedEffect(isTelevision) {
        if (!isTelevision) {
            snackbar.show(context.getString(R.string.pull_down_to_refresh))
        }
    }
    val listState = rememberLazyListState()
    val firstRowFocusRequester = remember { FocusRequester() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_logcat),
                onBackClick = onBackClick,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    viewModel.filter(it)
                },
                onSearchClose = {
                    searchQuery = ""
                    viewModel.filter("")
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                onMoveDown = { if (rows.isEmpty()) false else firstRowFocusRequester.requestFocus() },
                actionItems = buildList {
                    if (isTelevision) add(
                        AppTopBarAction(
                            icon = painterResource(R.drawable.ic_check_update_24dp),
                            label = stringResource(R.string.logcat_update),
                            onClick = viewModel::loadLogcat
                        )
                    )
                    if (!showSearch) add(
                        AppTopBarAction(
                            icon = painterResource(R.drawable.ic_search_24dp),
                            label = stringResource(R.string.menu_item_search),
                            onClick = { showSearch = true }
                        )
                    )
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_delete_24dp),
                        label = stringResource(R.string.logcat_clear),
                        onClick = { scope.launch(Dispatchers.IO) { viewModel.clearLogcat() } }
                    ))
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_copy),
                        label = stringResource(R.string.logcat_copy),
                        onClick = {
                            val all = viewModel.filteredLogs.value.joinToString("\n")
                            Utils.setClipboard(context, all)
                            snackbar.show(context.getString(R.string.toast_success), ToastType.SUCCESS)
                        }
                    ))
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_share_24dp),
                        label = stringResource(R.string.logcat_share),
                        onClick = onShareLogcat
                    ))
                }
            )
        },
        floatingActionButton = {
            if (!isTelevision) {
                FloatingActionButton(onClick = viewModel::loadLogcat) {
                    Icon(
                        painterResource(R.drawable.ic_restore_24dp),
                        contentDescription = stringResource(R.string.acc_refresh)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvContentPadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState),
                contentPadding = NavigationBarsBottomPadding()
            ) {
                itemsIndexed(items = rows, key = { index, _ -> index }) { index, row ->
                    LogcatItem(
                        row = row,
                        focusRequester = firstRowFocusRequester.takeIf { index == 0 },
                        onLongClick = { Utils.setClipboard(context, row.raw) }
                    )
                    ItemDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogcatItem(row: LogcatRow, focusRequester: FocusRequester?, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusOutline(focusRequester = focusRequester)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Text(text = row.tag, style = MaterialTheme.typography.bodySmall)
        if (row.content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = row.content, style = MaterialTheme.typography.bodySmall)
        }
    }
}
