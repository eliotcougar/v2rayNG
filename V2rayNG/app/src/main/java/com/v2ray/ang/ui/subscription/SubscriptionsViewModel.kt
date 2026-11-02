package com.v2ray.ang.ui.subscription

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.compose.ReorderCommand
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun subscriptionAutoUpdateChange(current: SubscriptionItem?, enabled: Boolean): SubscriptionItem? =
    current?.takeIf { it.url.isNotEmpty() && it.autoUpdate != enabled }?.copy(autoUpdate = enabled)

internal fun subscriptionEnabledChange(current: SubscriptionItem?, enabled: Boolean): SubscriptionItem? =
    current?.takeIf { it.enabled != enabled }?.copy(enabled = enabled)

class SubscriptionsViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel(application) {
    private val pendingAutoUpdates = mutableSetOf<String>()
    private val _autoUpdateChanges = MutableSharedFlow<Boolean>()
    internal val autoUpdateChanges = _autoUpdateChanges.asSharedFlow()
    private var qrCodeJob: Job? = null
    private val _qrCode = MutableStateFlow<Bitmap?>(null)
    internal val qrCode = _qrCode.asStateFlow()
    private val _subsFlow = MutableStateFlow<List<SubscriptionCache>>(emptyList())
    val subsFlow: StateFlow<List<SubscriptionCache>> = _subsFlow.asStateFlow()
    private var orderPersistenceJob: Job? = null
    private var reloadJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            orderPersistenceJob?.join()
            val observedOrderJob = orderPersistenceJob
            val persisted = withContext(ioDispatcher) { MmkvManager.decodeSubscriptions() }
            if (orderPersistenceJob === observedOrderJob) _subsFlow.value = persisted
        }
    }

    fun remove(subId: String) {
        launchLoading {
            val result = withContext(ioDispatcher) {
                SettingsManager.removeSubscriptionWithDefault(subId)
            }
            if (result == SettingsManager.SubscriptionRemovalResult.FAILED) {
                toastError(R.string.toast_failure)
                return@launchLoading
            }

            reload()
            SettingsChangeManager.makeSetupGroupTab()
            if (result == SettingsManager.SubscriptionRemovalResult.REMOVED_WITHOUT_DEFAULT) {
                toastError(R.string.toast_failure)
            }
        }
    }

    fun update(subId: String, item: SubscriptionItem) {
        val expected = _subsFlow.value
            .firstOrNull { it.guid == subId }
            ?.subscription
            ?.copy()
            ?: return
        launchLoading {
            val saved = withContext(ioDispatcher) {
                MmkvManager.updateSubscription(subId, expected, item)
            }
            if (!saved) toastError(R.string.toast_failure)
            reload()
        }
    }

    internal fun setEnabled(subId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = withContext(ioDispatcher) {
                    val current = MmkvManager.decodeSubscription(subId) ?: return@withContext null
                    val replacement = subscriptionEnabledChange(current, enabled) ?: return@withContext current
                    check(MmkvManager.updateSubscription(subId, current, replacement)) {
                        "Failed to persist subscription update setting"
                    }
                    MmkvManager.decodeSubscription(subId)
                } ?: return@launch
                publishSubscription(subId, updated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update toggle failed: $subId", error)
                toastError(R.string.toast_failure)
                reload()
            }
        }
    }

    internal fun setAutoUpdate(subId: String, enabled: Boolean): Boolean {
        if (!pendingAutoUpdates.add(subId)) return false
        viewModelScope.launch {
            try {
                val current = withContext(ioDispatcher) { MmkvManager.decodeSubscription(subId) } ?: return@launch
                val updated = subscriptionAutoUpdateChange(current, enabled) ?: return@launch
                if (enabled && updated.updateInterval < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
                    toastError(R.string.toast_invalid_update_interval)
                    return@launch
                }
                val persisted = withContext(ioDispatcher) {
                    check(MmkvManager.updateSubscription(subId, current, updated)) {
                        "Failed to persist subscription periodic update setting"
                    }
                    SubscriptionUpdater.syncOne(subId = subId)
                    MmkvManager.decodeSubscription(subId)
                } ?: return@launch
                publishSubscription(subId, persisted)
                _autoUpdateChanges.emit(persisted.autoUpdate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription periodic update change failed: $subId", error)
                toastError(R.string.toast_failure)
                reload()
            } finally {
                pendingAutoUpdates.remove(subId)
            }
        }
        return true
    }

    private fun publishSubscription(subId: String, updated: SubscriptionItem) {
        _subsFlow.value = _subsFlow.value.map { item ->
            if (item.guid == subId) SubscriptionCache(subId, updated) else item
        }
    }

    internal fun shareQRCode(url: String) {
        dismissQRCode()
        qrCodeJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(url) }
            if (bitmap == null) toastError(R.string.toast_failure)
            _qrCode.value = bitmap
        }
    }

    internal fun dismissQRCode() {
        qrCodeJob?.cancel()
        qrCodeJob = null
        _qrCode.value = null
    }

    fun move(fromPosition: Int, toPosition: Int) {
        move(fromPosition, toPosition, null)
    }

    private fun move(fromPosition: Int, toPosition: Int, onMoved: (() -> Unit)?): Boolean {
        val subscriptions = _subsFlow.value.toMutableList()
        if (!subscriptions.moveItem(fromPosition, toPosition)) return false

        _subsFlow.value = subscriptions
        val previous = orderPersistenceJob
        orderPersistenceJob = viewModelScope.launch {
            previous?.join()
            val saved = withContext(ioDispatcher) {
                MmkvManager.reorderSubscriptions(subscriptions.map { it.guid })
            }
            if (saved) {
                SettingsChangeManager.makeSetupGroupTab()
                onMoved?.invoke()
            } else toastError(R.string.toast_failure)
            val thisJob = currentCoroutineContext()[Job]
            if (orderPersistenceJob === thisJob) {
                orderPersistenceJob = null
                reload()
            }
        }
        return true
    }

    internal fun move(subId: String, command: ReorderCommand, onMoved: () -> Unit): Boolean {
        val from = _subsFlow.value.indexOfFirst { it.guid == subId }
        val to = command.targetIndex(from, _subsFlow.value.size) ?: return false
        return move(from, to, onMoved)
    }

    fun updateSubscriptions() {
        val updateSubscription = MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        val autoTestAfterUpdateSubscription = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)

        when {
            // If auto test is enabled, trigger background service for long-running task
            autoTestAfterUpdateSubscription -> updateSubscriptionsMore()
            // If only update is enabled, perform local update with UI loading state
            updateSubscription -> updateSubscriptionsOnly()
        }
    }

    fun updateSubscriptionsOnly() {
        launchLoading {
            try {
                val result = withContext(Dispatchers.IO) {
                    AngConfigManager.updateConfigViaSubAll()
                }

                when {
                    result.successCount + result.failureCount + result.skipCount == 0 ->
                        toast(R.string.title_update_subscription_no_subscription)

                    result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                        toast(
                            getQuantityString(
                                R.plurals.title_update_config_count,
                                result.configCount,
                                result.configCount,
                            ),
                        )

                    else ->
                        toast(
                            getString(
                                R.string.title_update_subscription_result,
                                result.configCount,
                                result.successCount,
                                result.failureCount,
                                result.skipCount,
                            ),
                        )
                }
                if (result.configCount > 0) {
                    SettingsChangeManager.makeSetupGroupTab()
                }
                reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    fun updateSubscriptionsMore() {
        val subIds = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled && it.subscription.url.isNotEmpty() }
            .map { it.guid }

        if (subIds.isNotEmpty()) {
            MessageHelper.sendMsg2SubscriptionService(app, SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_START, false, subIds))
        }

        toast(R.string.subscription_updater_job_tips)
    }
}
