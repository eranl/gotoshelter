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
import io.github.eranl.gotoshelter.shared.BuildConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.io.FileOutputStream

actual object AlertManager {
  private const val TAG = "AlertManager"
  private const val ALERTS_FILE_NAME = "alerts_log.txt"
  private var appContext: Context? = null

  private val wazeIntent = Intent(Intent.ACTION_VIEW, "waze://?q=shelter&navigate=yes&force=yes".toUri()).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    setPackage("com.waze")
  }

  private val mapsIntent = Intent(Intent.ACTION_VIEW, "google.navigation:q=shelter".toUri()).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    setPackage("com.google.android.apps.maps")
  }

  fun bindContext(context: Context) {
    if (appContext == null) {
      Log.d(TAG, "Binding appContext from ${context.javaClass.simpleName}")
      appContext = context.applicationContext
    }
  }

  @SuppressLint("MissingPermission")
  actual fun onEmergencyAlert(type: String, text: String) {
    val context = getContext()
    Log.d(TAG, "Emergency alert received: $type | $text")

    appendAlertToFile(type, text)

    val hasPermission = if (Build.VERSION.SDK_INT >= 29) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

    if (!hasPermission) {
      Log.d(TAG, "Activity recognition permission not granted, triggering navigation directly")
      triggerNavigation()
      return
    }

    val intent =
      Intent().setClassName(context.packageName, "io.github.eranl.gotoshelter.receiver.DrivingActivityReceiver").apply {
        putExtra("action", "CHECK_DRIVING_AND_NAVIGATE")
      }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      1,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    ActivityRecognition.getClient(context)
      .requestActivityUpdates(0, pendingIntent)
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to request activity updates, falling back to direct navigation", e)
        triggerNavigation()
      }
  }

  actual fun appendAlertToFile(type: String, text: String) {
    if (!BuildConfig.DEBUG) return

    try {
      val context = getContext()
      val file = File(context.getExternalFilesDir(null), ALERTS_FILE_NAME)
      val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      val logEntry = "[$now] type: $type | text: $text\n"

      FileOutputStream(file, true).use { it.write(logEntry.toByteArray()) }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to append alert to file", e)
    }
  }

  actual fun triggerNavigation() {
    val context = getContext()
    val packageManager = context.packageManager

    // Attempt to start Waze first
    if (wazeIntent.resolveActivity(packageManager) != null) {
      Log.d(TAG, "Starting Waze directly from background")
      try {
        context.startActivity(wazeIntent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start Waze. Background activity start likely blocked.", e)
        // If Waze fails, try Maps as a fallback
        tryMaps(context, packageManager)
      }
      return
    }

    tryMaps(context, packageManager)
  }

  private fun tryMaps(context: Context, packageManager: PackageManager) {
    if (mapsIntent.resolveActivity(packageManager) != null) {
      Log.d(TAG, "Starting Maps directly from background")
      try {
        context.startActivity(mapsIntent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start Maps.", e)
      }
    } else {
      Log.e(TAG, "No navigation app resolved")
    }
  }

  private fun getContext(): Context = appContext ?: run {
    throw IllegalStateException("appContext is null")
  }
}
