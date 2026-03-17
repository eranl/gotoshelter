package io.github.eranl.gotoshelter.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.R

/**
 * Service that listens for notifications from emergency alert apps.
 * Monitors Home Front Command, Tzofar, RedColor, and Cumta.
 */
class EmergencyAlertListenerService : NotificationListenerService() {
  companion object {
    private const val TAG = "EmergencyAlertListener"

    private val SUPPORTED_PACKAGES = setOf(
      "com.alert.meserhadash",       // Home Front Command (Official)
      /*
                  "com.red.alert",        // RedColor / RedAlert
                  "com.tzofar",           // Tzofar
                  "com.rescue.israel"     // Cumta
      */
    )

    @Volatile
    private var isConnected = false

    private var lastRebindAttemptTime = 0L
    private const val REBIND_THRESHOLD_MS = 5000 // 5 seconds

    /**
     * Returns true if the listener is currently connected.
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Safely ensures the service is running.
     * Uses requestRebind on API 24+ to avoid the "could not be unbound" crash
     * associated with toggling component state.
     */
    fun ensureServiceRunning(context: Context) {
      if (isConnected) {
        Log.d(TAG, "Service is already connected, skipping ensureServiceRunning")
        return
      }

      if (!isNotificationServiceEnabled(context)) {
        Log.d(TAG, "Notification listener permission not granted, skipping ensureServiceRunning")
        return
      }

      val currentTime = SystemClock.elapsedRealtime()
      if (currentTime - lastRebindAttemptTime < REBIND_THRESHOLD_MS) {
        Log.d(TAG, "Too soon since last rebind attempt, skipping")
        return
      }
      lastRebindAttemptTime = currentTime

      val componentName = ComponentName(context, EmergencyAlertListenerService::class.java)

      if (Build.VERSION.SDK_INT >= 24) {
        Log.d(TAG, "Service not connected, requesting rebind via API")
        try {
          // requestRebind is the official way to ask the system to try binding again.
          // It's safer than toggling the component state.
          requestRebind(componentName)
        } catch (e: Exception) {
          Log.e(TAG, "Failed to request rebind", e)
          // On API 24+, we avoid toggleComponent fallback because it often causes
          // "Service not registered" crashes in the system's ManagedServices.
        }
      } else {
        toggleComponent(context, componentName)
      }
    }

    private fun toggleComponent(context: Context, componentName: ComponentName) {
      Log.d(TAG, "Toggling service component state to force re-bind")
      val pm = context.packageManager
      try {
        // We only disable if we really have to (pre-API 24).
        // This triggers an unbind in the system, which can be flaky.
        pm.setComponentEnabledSetting(
          componentName,
          PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
          PackageManager.DONT_KILL_APP
        )

        // Add a small delay before re-enabling to avoid racing with the system's unbind logic
        Handler(Looper.getMainLooper()).postDelayed({
          try {
            pm.setComponentEnabledSetting(
              componentName,
              PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
              PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "Service component re-enabled")
          } catch (e: Exception) {
            Log.e(TAG, "Failed to re-enable component", e)
          }
        }, 200) // Increased delay slightly
      } catch (e: Exception) {
        Log.e(TAG, "Failed to disable component", e)
      }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
      val pkgName = context.packageName
      val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
      if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":")
        for (name in names) {
          val cn = ComponentName.unflattenFromString(name)
          if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
            return true
          }
        }
      }
      return false
    }
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    isConnected = true
    Log.d(TAG, "Notification Listener Connected")
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    isConnected = false
    Log.d(TAG, "Notification Listener Disconnected")
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val packageName = sbn.packageName

    if (!SUPPORTED_PACKAGES.contains(packageName)) return

    val extras = sbn.notification.extras
    val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

    if (title != "מבזק | באזורך") {
      return
    }

    Log.d(TAG, "Emergency notification from $packageName: $title")

    // Trigger driving check and potential navigation
    AlertManager.onEmergencyAlert(
      context = this,
      type = "notification",
      text = "$title $text"
    )
  }
}
