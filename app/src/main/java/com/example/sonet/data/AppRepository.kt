package com.example.sonet.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Telephony
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.roundToInt

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val pm = context.packageManager

    /** Emits once immediately, then whenever installed apps or the locale change. */
    fun changes(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
        }
        val localeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(
            context, localeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(Unit)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(localeReceiver)
        }
    }

    /** All launchable apps for the current user, sorted alphabetically for the current locale. */
    suspend fun loadApps(): InstalledApps = withContext(Dispatchers.IO) {
        val user = Process.myUserHandle()
        val density = context.resources.displayMetrics
        val iconPx = (ICON_SIZE_DP * density.density).roundToInt()
        val essentials = essentialPackages()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

        val apps = launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                val flags = info.applicationInfo.flags
                val isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUpdated = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                AppEntry(
                    key = info.componentName.flattenToShortString(),
                    component = info.componentName,
                    user = user,
                    label = info.label.toString().trim(),
                    icon = info.getIcon(density.densityDpi)
                        .toBitmap(iconPx, iconPx)
                        .asImageBitmap(),
                    // A fixed list, plus a heuristic: preinstalled system apps that were never
                    // updated through the store are usually rarely used utilities.
                    inMoreByDefault = info.componentName.packageName in DefaultApps.MORE_PACKAGES ||
                        (isSystem && !isUpdated && info.componentName.packageName !in essentials),
                    canUninstall = !isSystem || isUpdated,
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }

        val installed = apps.mapTo(HashSet()) { it.packageName }
        InstalledApps(
            apps = apps,
            buttons = ButtonApps(
                cameraPackage = resolvePackage(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    ?.takeIf { it in installed },
                walletPackage = DefaultApps.WALLET_PACKAGES.firstOrNull { it in installed },
            ),
        )
    }

    private fun resolvePackage(intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" } // the chooser, when no default is set

    /** Default handlers for core tasks stay in the main list even if they're stock system apps. */
    private fun essentialPackages(): Set<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
        )
        return buildSet {
            intents.mapNotNullTo(this, ::resolvePackage)
            Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)
            add(PLAY_STORE)
        }
    }

    companion object {
        const val ICON_SIZE_DP = 32
        private const val PLAY_STORE = "com.android.vending"
    }
}
