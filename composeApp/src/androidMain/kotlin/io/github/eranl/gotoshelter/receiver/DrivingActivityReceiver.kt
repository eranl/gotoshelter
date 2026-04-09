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

package io.github.eranl.gotoshelter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.monitoring.Logger
import io.github.eranl.gotoshelter.util.ACTION_CHECK_DRIVING_AND_NAVIGATE

/**
 * Receiver for Activity Recognition updates.
 */
class DrivingActivityReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (ActivityRecognitionResult.hasResult(intent)) {
      val result = ActivityRecognitionResult.extractResult(intent)
      result?.let {
        val mostProbableActivity = it.mostProbableActivity
        val confidence = mostProbableActivity.confidence
        val type = mostProbableActivity.type

        Logger.debugLog("Detected activity: $mostProbableActivity")

        // User is considered driving if IN_VEHICLE with high confidence
        val isDriving = type == DetectedActivity.IN_VEHICLE && confidence >= 50

        if (intent.action == ACTION_CHECK_DRIVING_AND_NAVIGATE) {
          Log.d("DrivingReceiver", "One-time check result: isDriving=$isDriving")

          // If we were checking because of an alert, trigger navigation if driving
          if (isDriving) {
            AlertManager.triggerNavigation()
          } else {
            Logger.debugLog("User is not driving, not triggering navigation")
          }

          // Stop further updates to save battery, as we only needed this check for the alert
          ActivityRecognition.getClient(context).removeActivityUpdates(
            android.app.PendingIntent.getBroadcast(
              context,
              1,
              intent,
              android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )
          )
        }
      }
    }
  }
}
