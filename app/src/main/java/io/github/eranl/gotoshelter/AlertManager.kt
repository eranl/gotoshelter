package io.github.eranl.gotoshelter

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.location.ActivityRecognition
import io.github.eranl.gotoshelter.BuildConfig.DEBUG
import io.github.eranl.gotoshelter.receiver.DrivingActivityReceiver
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Singleton to coordinate emergency alerts and navigation logic.
 */
object AlertManager {
  private const val TAG = "AlertManager"
  private const val ALERTS_FILE_NAME = "alerts_log.txt"

  private val wazeIntent = Intent(Intent.ACTION_VIEW, "waze://?q=shelter&navigate=yes&force=yes".toUri()).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    setPackage("com.waze")
  }

  private val mapsIntent = Intent(Intent.ACTION_VIEW, "google.navigation:q=shelter".toUri()).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    setPackage("com.google.android.apps.maps")
  }

  /**
   * Entry point for any emergency alert (WebSocket, Notification, SMS).
   * Triggers a driving status check and potentially navigation.
   */
  @SuppressLint("MissingPermission")
  fun onEmergencyAlert(context: Context, type: String, text: String) {
    Log.d(TAG, "Emergency alert received: $type | $text")

    appendAlertToFile(context, type, text)

    val hasPermission = if (Build.VERSION.SDK_INT >= 29) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

    if (!hasPermission) {
      Log.d(TAG, "Activity recognition permission not granted. Triggering navigation immediately.")
      triggerNavigation(context)
      return
    }

    val intent = Intent(context, DrivingActivityReceiver::class.java).apply {
      putExtra("action", "CHECK_DRIVING_AND_NAVIGATE")
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      1, // Unique request code for alert-triggered checks
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    // Request an immediate activity update.
    // This avoids continuous monitoring and only checks when necessary.
    ActivityRecognition.getClient(context)
      .requestActivityUpdates(0, pendingIntent)
      .addOnSuccessListener {
        Log.d(TAG, "Successfully requested one-time activity update")
      }
      .addOnFailureListener {
        Log.e(TAG, "Failed to request activity update: ${it.message}. Falling back to immediate navigation.")
        triggerNavigation(context)
      }
  }

  // save to app files only in debug versions
  fun appendAlertToFile(context: Context, type: String, text: String) {
    if (! DEBUG) {
      return
    }

    try {
      val file = File(context.getExternalFilesDir(null), ALERTS_FILE_NAME)
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      val logEntry = StringBuilder().apply {
        append("[${LocalDateTime.now().format(formatter)}] ")
        append("type: ${type} | ")
        append("text: ${text}\n")
      }.toString()

      FileOutputStream(file, true).use { it.write(logEntry.toByteArray()) }
      Log.d(TAG, "Alert appended to ${file.absolutePath}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to append alert to file", e)
    }
  }

  /**
   * Triggers navigation to the nearest shelter.
   * Tries Waze first, falls back to Google Maps.
   * Uses explicit package names to avoid "Select App" popups.
   */
  fun triggerNavigation(context: Context) {
    val packageManager = context.packageManager

    // 1. Try Waze
    if (wazeIntent.resolveActivity(packageManager) != null) {
      Log.d(TAG, "Launching Waze...")
      context.startActivity(wazeIntent)
      return
    }

    // 2. Fallback to Google Maps
    Log.w(TAG, "Waze not installed or resolved, falling back to Google Maps")
    if (mapsIntent.resolveActivity(packageManager) != null) {
      Log.d(TAG, "Launching Google Maps...")
      context.startActivity(mapsIntent)
    } else {
      Log.e(TAG, "No navigation app found.")
    }
  }

  fun isNavigationAppInstalled(context: Context): Boolean {
    val packageManager = context.packageManager
    return wazeIntent.resolveActivity(packageManager) != null ||
            mapsIntent.resolveActivity(packageManager) != null
  }
}
