package com.example.sonet.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.example.sonet.R
import com.example.sonet.data.AppEntry

private const val TAG = "SonetSystemActions"

fun launchApp(context: Context, app: AppEntry, sourceBounds: Rect? = null) {
    runCatching {
        context.getSystemService(LauncherApps::class.java)
            .startMainActivity(app.component, app.user, sourceBounds, null)
    }.onFailure { Log.w(TAG, "Could not launch ${app.key}", it) }
}

fun openAppInfo(context: Context, app: AppEntry) {
    context.getSystemService(LauncherApps::class.java)
        .startAppDetailsActivity(app.component, app.user, null, null)
}

fun uninstallApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openCamera(context: Context) {
    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_camera, Toast.LENGTH_SHORT).show()
    }
}

/** Opens the wallet app the launcher detected (see DefaultApps.WALLET_PACKAGES). */
fun openWallet(context: Context, packageName: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        Toast.makeText(context, R.string.no_wallet, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Pulls down the notification shade (notifications + quick settings row).
 * There's no public API for this; launchers use StatusBarManager's hidden
 * `expandNotificationsPanel`, guarded by the EXPAND_STATUS_BAR permission.
 */
@SuppressLint("WrongConstant")
fun expandNotificationShade(context: Context) {
    runCatching {
        val statusBar = context.getSystemService("statusbar") ?: return
        statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
    }.onFailure { Log.w(TAG, "Could not expand notification shade", it) }
}
