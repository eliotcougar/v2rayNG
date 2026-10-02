package com.v2ray.ang.extension

import android.content.Context
import com.v2ray.ang.R
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.compose.AppSnackbarMessage
import com.v2ray.ang.ui.compose.AppSnackbarManager
import com.v2ray.ang.ui.compose.ToastType

enum class AccessibilityLiveRegionMode {
    POLITE,
    ASSERTIVE,
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toast(
    message: Int,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.NORMAL, liveRegionMode)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toast(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(message, ToastType.NORMAL, liveRegionMode)
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastSuccess(
    message: Int,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
    accessibilityMessage: CharSequence? = null,
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.SUCCESS, liveRegionMode, accessibilityMessage)
}

/**
 * Shows a long error toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastErrorLong(message: Int) {
    val text = getString(message)
    dispatchMessage(text, ToastType.ERROR, AccessibilityLiveRegionMode.POLITE, long = true)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastSuccess(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
    accessibilityMessage: CharSequence? = null,
) {
    dispatchMessage(message, ToastType.SUCCESS, liveRegionMode, accessibilityMessage)
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 */
fun Context.toastError(
    message: Int,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    val text = getString(message)
    dispatchMessage(text, ToastType.ERROR, liveRegionMode)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 */
fun Context.toastError(
    message: CharSequence,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) {
    dispatchMessage(message, ToastType.ERROR, liveRegionMode)
}

/** Shared text for the service's background notification and foreground live region. */
internal fun Context.serviceStartedMessage(serverName: String): String {
    val name = serverName.trim()
    return if (name.isEmpty()) {
        getString(R.string.toast_services_success)
    } else {
        getString(R.string.acc_service_started_connected_to, name)
    }
}

private fun Context.dispatchMessage(
    message: CharSequence,
    type: ToastType,
    liveRegionMode: AccessibilityLiveRegionMode,
    accessibilityMessage: CharSequence? = null,
    long: Boolean = false,
) {
    val event = AppSnackbarMessage(
        message = message,
        type = type,
        liveRegionMode = liveRegionMode,
        accessibilityMessage = accessibilityMessage,
        duration = if (long) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
    )
    if (AppSnackbarManager.show(event)) {
        NotificationHelper.cancelTransientMessage(this)
    } else {
        NotificationHelper.notifyTransientMessage(this, accessibilityMessage ?: message)
    }
}

/**
 * Shows a toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toast(message: Int, long: Boolean) {
    dispatchMessage(getString(message), ToastType.NORMAL, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows a toast message with the given text.
 *
 * @param message The text of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toast(message: CharSequence, long: Boolean) {
    dispatchMessage(message, ToastType.NORMAL, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows a success toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toastSuccess(message: Int, long: Boolean) {
    dispatchMessage(getString(message), ToastType.SUCCESS, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows a success toast message with the given text.
 *
 * @param message The text of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toastSuccess(message: CharSequence, long: Boolean) {
    dispatchMessage(message, ToastType.SUCCESS, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows an error toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toastError(message: Int, long: Boolean) {
    dispatchMessage(getString(message), ToastType.ERROR, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows an error toast message with the given text.
 *
 * @param message The text of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toastError(message: CharSequence, long: Boolean) {
    dispatchMessage(message, ToastType.ERROR, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows an info toast message with the given resource ID.
 *
 * @param message The resource ID of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toastInfo(message: Int, long: Boolean = false) {
    dispatchMessage(getString(message), ToastType.INFO, AccessibilityLiveRegionMode.POLITE, long = long)
}

/**
 * Shows an info toast message with the given text.
 *
 * @param message The text of the message to show.
 * @param long Whether to display the message for a longer duration.
 */
fun Context.toastInfo(message: CharSequence, long: Boolean = false) {
    dispatchMessage(message, ToastType.INFO, AccessibilityLiveRegionMode.POLITE, long = long)
}
