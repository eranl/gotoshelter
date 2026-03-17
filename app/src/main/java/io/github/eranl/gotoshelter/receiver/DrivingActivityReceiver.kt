package io.github.eranl.gotoshelter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import io.github.eranl.gotoshelter.AlertManager

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

        Log.d("DrivingReceiver", "Detected activity: $type with confidence $confidence")

        // User is considered driving if IN_VEHICLE with high confidence
        val isDriving = type == DetectedActivity.IN_VEHICLE && confidence >= 70

        val action = intent.getStringExtra("action")
        if (action == "CHECK_DRIVING_AND_NAVIGATE") {
          Log.d("DrivingReceiver", "One-time check result: isDriving=$isDriving")

          // If we were checking because of an alert, trigger navigation if driving
          if (isDriving) {
            AlertManager.triggerNavigation(context)
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
