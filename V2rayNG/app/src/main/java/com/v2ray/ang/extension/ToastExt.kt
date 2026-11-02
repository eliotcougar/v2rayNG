package com.v2ray.ang.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.dto.UserMessage
import com.v2ray.ang.handler.AppLocaleManager

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

enum class AccessibilityLiveRegionMode { POLITE, ASSERTIVE }

// Keep the existing caller API; delivery uses snackbars, never platform toasts.
// The long option extends transient feedback; lengthy errors still require dismissal.
fun Context.toast(
    @StringRes message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) = toast(AppLocaleManager.localizedContext(this).getString(message), long, liveRegionMode)

fun Context.toast(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) = dispatchMessage(UserMessage(message.toString(), long = long, liveRegionMode = liveRegionMode))

fun Context.toastSuccess(
    @StringRes message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
    accessibilityMessage: CharSequence? = null,
) = toastSuccess(AppLocaleManager.localizedContext(this).getString(message), long, liveRegionMode, accessibilityMessage)

fun Context.toastSuccess(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
    accessibilityMessage: CharSequence? = null,
) = dispatchMessage(UserMessage(
    message.toString(), type = UserMessage.Type.SUCCESS, long = long,
    liveRegionMode = liveRegionMode, accessibilityText = accessibilityMessage?.toString(),
))

fun Context.toastError(
    @StringRes message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) = toastError(AppLocaleManager.localizedContext(this).getString(message), long, liveRegionMode)

fun Context.toastError(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) = dispatchMessage(UserMessage(
    message.toString(), type = UserMessage.Type.ERROR, long = long, liveRegionMode = liveRegionMode,
))

fun Context.toastInfo(
    @StringRes message: Int,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) = toastInfo(AppLocaleManager.localizedContext(this).getString(message), long, liveRegionMode)

fun Context.toastInfo(
    message: CharSequence,
    long: Boolean = false,
    liveRegionMode: AccessibilityLiveRegionMode = AccessibilityLiveRegionMode.POLITE,
) = dispatchMessage(UserMessage(
    message.toString(), type = UserMessage.Type.INFO, long = long, liveRegionMode = liveRegionMode,
))

/** Uses the identity supplied by the daemon, never the UI's potentially newer selection. */
internal fun Context.serviceStartedMessage(serverName: String): String {
    val localizedContext = AppLocaleManager.localizedContext(this)
    val name = serverName.trim()
    return if (name.isEmpty()) localizedContext.getString(R.string.toast_services_success)
    else localizedContext.getString(R.string.acc_service_started_connected_to, name)
}

private fun Context.dispatchMessage(message: UserMessage) {
    val appContext = applicationContext
    val deliver = Runnable {
        (appContext as AngApplication).snackbarManager.show(message)
    }
    if (Looper.myLooper() == Looper.getMainLooper()) deliver.run()
    else mainHandler.post(deliver)
}
