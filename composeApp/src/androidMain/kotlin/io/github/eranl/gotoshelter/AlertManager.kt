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
import io.github.eranl.gotoshelter.monitoring.Logger
import io.github.eranl.gotoshelter.shared.BuildConfig
import io.github.eranl.gotoshelter.util.ACTION_CHECK_DRIVING_AND_NAVIGATE
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
  actual fun onEmergencyAlert(type: String) {
    Logger.debugLog("${type} alert")

    val hasPermission = if (Build.VERSION.SDK_INT >= 29) {
      ContextCompat.checkSelfPermission(appContext!!, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

    if (!hasPermission) {
      Log.d(TAG, "Activity recognition permission not granted, triggering navigation directly")
      triggerNavigation()
      return
    }

    val intent =
      Intent(ACTION_CHECK_DRIVING_AND_NAVIGATE).setClassName(appContext!!.packageName, "io.github.eranl.gotoshelter.receiver.DrivingActivityReceiver")

    val pendingIntent = PendingIntent.getBroadcast(
      appContext!!,
      1,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    ActivityRecognition.getClient(appContext!!)
      .requestActivityUpdates(0, pendingIntent)
      .addOnFailureListener { e ->
        Logger.debugLog("Failed to request activity updates, falling back to direct navigation: $e")
        triggerNavigation()
      }
  }

  actual fun triggerNavigation() {
    val packageManager = appContext!!.packageManager

    Logger.debugLog("Launching waze")

    // Attempt to start Waze first
    if (wazeIntent.resolveActivity(packageManager) != null) {
      Log.d(TAG, "Starting Waze directly from background")
      appContext!!.startActivity(wazeIntent)
    } else if (mapsIntent.resolveActivity(packageManager) != null) {
      Log.d(TAG, "Starting Maps directly from background")
      appContext!!.startActivity(mapsIntent)
    } else {
      throw IllegalStateException("No navigation app resolved")
    }
  }
}
