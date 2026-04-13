/*
 * Copyright 2026 Eran L.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eranl.gotoshelter

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.provider.Settings.canDrawOverlays
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.github.eranl.gotoshelter.monitoring.Logger
import io.github.eranl.gotoshelter.service.EmergencyMonitorService
import io.github.eranl.gotoshelter.shared.BuildConfig
import io.github.eranl.gotoshelter.util.HFC_PACKAGE_NAME
import io.github.eranl.gotoshelter.util.LocationHelper
import io.github.eranl.gotoshelter.util.bindContext
import io.github.eranl.gotoshelter.util.isNotificationServiceEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toOkioPath

class AndroidPlatform private constructor(private val appContext: Context) : Platform {
  override val name: String = "Android ${Build.VERSION.SDK_INT}"
  private val TAG = "AndroidPlatform"

  private val _status = MutableStateFlow(getAppStatusInternal())
  override val status: StateFlow<AppStatus> = _status.asStateFlow()

  private val _updateStatus = MutableStateFlow(UpdateStatus.NONE)
  override val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

  private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)
  private var cachedAppUpdateInfo: AppUpdateInfo? = null

  // Bound when BindHandlers is active in the UI
  private var activeLauncher: ActivityResultLauncher<Array<String>>? = null
  private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
  private var activeActivity: Activity? = null
  private var isBackgroundLocationPending: Boolean = false

  private val installStateListener = InstallStateUpdatedListener { state ->
    when (state.installStatus()) {
      InstallStatus.DOWNLOADING -> _updateStatus.value = UpdateStatus.DOWNLOADING
      InstallStatus.DOWNLOADED -> _updateStatus.value = UpdateStatus.DOWNLOADED
      InstallStatus.FAILED -> _updateStatus.value = UpdateStatus.FAILED
      else -> {}
    }
  }

  init {
    // Ensure singletons are bound to the application context as soon as the platform is used.
    AlertManager.bindContext(appContext)
    LocationHelper.bindContext(appContext)
    appUpdateManager.registerListener(installStateListener)
  }

  private fun isHfcAppInstalled(): Boolean = try {
    appContext.packageManager.getPackageInfo(HFC_PACKAGE_NAME, 0)
    true
  } catch (e: PackageManager.NameNotFoundException) {
    false
  }

  private fun openNotificationAccessSettings() {
    appContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
  }

  private fun openOverlaySettings() {
    appContext.startActivity(
      Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${appContext.packageName}".toUri()).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }

  private fun openAppSettings() {
    appContext.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", appContext.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
    )
  }

  private fun checkPermission(permission: AppPermission): Boolean {
    val androidPermissions = getAndroidPermissionStrings(permission)
    if (androidPermissions.isEmpty()) {
      return when (permission) {
        AppPermission.NOTIFICATION_ACCESS -> appContext.isNotificationServiceEnabled()
        AppPermission.OVERLAY -> canDrawOverlays(appContext)
        AppPermission.BATTERY_OPTIMIZATIONS -> isIgnoringBatteryOptimizations()
        else -> true
      }
    }

    return androidPermissions.all {
      ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun getAppStatusInternal(): AppStatus {
    val runtimePermissions = AppPermission.entries.filter { isRuntimePermission(it) }.toSet()
    val permissionsMap = AppPermission.entries.associateWith {
      if (it in runtimePermissions) checkPermission(it) else true
    }

    return AppStatus(
      permissions = permissionsMap,
      runtimePermissions = runtimePermissions,
      specialPermissions = setOf(AppPermission.OVERLAY, AppPermission.BATTERY_OPTIMIZATIONS, AppPermission.NOTIFICATION_ACCESS),
      isHfcInstalled = isHfcAppInstalled(),
      isNavigationAppInstalled = isNavigationAppInstalled(),
      debugBuild = BuildConfig.DEBUG
    )
  }

  private fun isRuntimePermission(permission: AppPermission): Boolean {
    val sdk = Build.VERSION.SDK_INT
    return when (permission) {
      AppPermission.COARSE_LOCATION, AppPermission.NOTIFICATION_ACCESS -> true
      AppPermission.OVERLAY, AppPermission.BATTERY_OPTIMIZATIONS -> sdk >= 23
      AppPermission.ACTIVITY_RECOGNITION, AppPermission.BACKGROUND_LOCATION -> sdk >= 29
      AppPermission.POST_NOTIFICATIONS -> sdk >= 33
    }
  }

  private fun isNavigationAppInstalled(): Boolean {
    val packageManager = appContext.packageManager
    val wazeIntent = Intent(Intent.ACTION_VIEW, "waze://".toUri()).setPackage("com.waze")
    val mapsIntent = Intent(Intent.ACTION_VIEW, "google.navigation:".toUri()).setPackage("com.google.android.apps.maps")
    return wazeIntent.resolveActivity(packageManager) != null || mapsIntent.resolveActivity(packageManager) != null
  }

  override fun requestPermissions(permissions: List<AppPermission>) {
    val currentStatus = status.value

    // Filter out permissions that are already granted (handles non-runtime automatically)
    val toProcess = permissions.filter { !currentStatus.isGranted(it) }
    if (toProcess.isEmpty()) {
      isBackgroundLocationPending = false
      return
    }

    // Special permissions first
    if (AppPermission.NOTIFICATION_ACCESS in toProcess) {
      openNotificationAccessSettings(); return
    }
    if (AppPermission.OVERLAY in toProcess) {
      openOverlaySettings(); return
    }
    if (AppPermission.BATTERY_OPTIMIZATIONS in toProcess) {
      requestIgnoreBatteryOptimizations(); return
    }

    // Runtime permissions require a launcher and optionally an activity for rationale
    val launcher = activeLauncher ?: return
    val activity = activeActivity
    val prefs = appContext.getSharedPreferences("permissions_state", Context.MODE_PRIVATE)
    var permanentlyDenied = false

    // Background location must be requested alone on Android 11+
    val finalPermissions = if (AppPermission.BACKGROUND_LOCATION in toProcess && toProcess.size > 1) {
      isBackgroundLocationPending = true
      toProcess.filter { it != AppPermission.BACKGROUND_LOCATION }
    } else {
      isBackgroundLocationPending = false
      toProcess
    }

    val toRequest = mutableSetOf<String>()
    finalPermissions.forEach { permission ->
      getAndroidPermissionStrings(permission).forEach { perm ->
        if (activity != null) {
          val rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
          val previouslyAsked = prefs.getBoolean(perm, false)
          if (!rationale && previouslyAsked) {
            permanentlyDenied = true
          } else {
            prefs.edit { putBoolean(perm, true) }
            toRequest.add(perm)
          }
        } else {
          toRequest.add(perm)
        }
      }
    }

    if (toRequest.isNotEmpty()) {
      launcher.launch(toRequest.toTypedArray())
    } else if (permanentlyDenied) {
      openAppSettings()
    }
  }

  @Composable
  override fun BindHandlers() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
      refreshStatus()
      val wasPending = isBackgroundLocationPending
      isBackgroundLocationPending = false
      
      val foregroundLocationGranted = status.value.isGranted(AppPermission.COARSE_LOCATION)
      
      // If foreground was granted, and we were waiting to ask for background, do it now.
      // This allows the background popup to appear immediately after the foreground one.
      if (wasPending && foregroundLocationGranted) {
        requestPermissions(listOf(AppPermission.BACKGROUND_LOCATION))
      }
    }

    val updateFlowLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "Update flow failed! Result code: ${result.resultCode}")
        }
    }

    DisposableEffect(launcher, updateFlowLauncher, context) {
      activeLauncher = launcher
      updateLauncher = updateFlowLauncher
      activeActivity = findActivity(context)
      onDispose {
        activeLauncher = null
        updateLauncher = null
        activeActivity = null
      }
    }
  }

  private fun findActivity(context: Context): Activity? {
    var currentContext = context
    while (currentContext is ContextWrapper) {
      if (currentContext is Activity) return currentContext
      currentContext = currentContext.baseContext
    }
    return null
  }

  override fun refreshStatus() {
    _status.value = getAppStatusInternal()
  }

  override fun checkForUpdates() {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
      cachedAppUpdateInfo = info
      when {
        info.installStatus() == InstallStatus.DOWNLOADED -> {
          _updateStatus.value = UpdateStatus.DOWNLOADED
        }
        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
          _updateStatus.value = UpdateStatus.AVAILABLE
        }
      }
    }
  }

  override fun requestUpdate() {
    val info = cachedAppUpdateInfo ?: return
    val launcher = updateLauncher ?: return
    appUpdateManager.startUpdateFlowForResult(
      info,
      launcher,
      AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
    )
  }

  override fun completeUpdate() {
    appUpdateManager.completeUpdate()
  }

  override fun logExitReasons() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val exitReasons = activityManager.getHistoricalProcessExitReasons(null, 0, 0)
      Logger.debugLog("--- Historical Process Exit Reasons (Found: ${exitReasons.size}) ---")
      exitReasons.forEach { info ->
        val reasonStr = when (info.reason) {
          ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
          ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (Signal: ${info.status})"
          ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
          ApplicationExitInfo.REASON_CRASH -> "CRASH"
          ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
          ApplicationExitInfo.REASON_ANR -> "ANR"
          ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
          ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
          ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
          ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
          ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
          ApplicationExitInfo.REASON_OTHER -> "OTHER"
          else -> "UNKNOWN (${info.reason})"
        }
        val dateTime = Instant.fromEpochMilliseconds(info.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        val importanceStr = when (info.importance) {
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
          ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
          else -> "IMPORTANCE_${info.importance}"
        }
        val memoryInfo = "PSS: ${info.pss / 1024}MB, RSS: ${info.rss / 1024}MB, Importance: $importanceStr"
        Logger.debugLog("Time: $dateTime, Reason: $reasonStr, $memoryInfo, Desc: ${info.description}")
      }
      Logger.debugLog("--- End of Exit Reasons ---")
    } else {
      Logger.debugLog("Exit reasons not supported on this Android version")
    }
  }

  private fun getAndroidPermissionStrings(permission: AppPermission): List<String> = when (permission) {
    AppPermission.ACTIVITY_RECOGNITION -> listOf(Manifest.permission.ACTIVITY_RECOGNITION)
    AppPermission.COARSE_LOCATION -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    AppPermission.BACKGROUND_LOCATION -> listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    AppPermission.POST_NOTIFICATIONS -> listOf(Manifest.permission.POST_NOTIFICATIONS)
    else -> emptyList()
  }

  override fun startServicesIfPermissionsGranted() {
    val currentStatus = status.value
    Log.d(
      TAG,
      "Checking if services should start. monitor=${currentStatus.canStartMonitorService}, listener=${currentStatus.canStartListenerService}"
    )
    if (currentStatus.canStartMonitorService) {
      Log.d(TAG, "Starting EmergencyMonitorService")
      EmergencyMonitorService.start(appContext)
    } else {
      Log.d(TAG, "Stopping EmergencyMonitorService")
      EmergencyMonitorService.stop(appContext)
    }

    if (currentStatus.canStartListenerService) {
      Log.d(TAG, "Starting EmergencyAlertListenerService - Handled by OS via NotificationListenerService")
      // NotificationListenerService is managed by the system.
      // Once the user grants permission, the system binds it automatically.
    }
  }

  override fun getExternalFilesDir() = appContext.getExternalFilesDir(null)!!.toOkioPath()

  private fun isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
  }

  private fun requestIgnoreBatteryOptimizations() {
    appContext.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
      data = "package:${appContext.packageName}".toUri()
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
  }

  companion object {
    @Volatile
    private var INSTANCE: AndroidPlatform? = null

    fun getInstance(context: Context): AndroidPlatform {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AndroidPlatform(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}

@Composable
actual fun getPlatform(): Platform {
  val context = LocalContext.current
  return remember(context) { AndroidPlatform.getInstance(context) }
}
