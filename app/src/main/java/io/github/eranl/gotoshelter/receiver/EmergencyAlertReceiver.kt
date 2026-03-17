package io.github.eranl.gotoshelter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.AlertStore
import io.github.eranl.gotoshelter.R

/**
 * Receiver for Emergency Cell Broadcasts.
 * Declared in manifest with android.permission.RECEIVE_EMERGENCY_BROADCAST
 */
open class EmergencyAlertReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "EmergencyAlertReceiver"
    private const val SMS_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_CB_RECEIVED"
    private const val SMS_EMERGENCY_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED"
    private const val ACTION_SMS_EMERGENCY_CB_RECEIVED = "android.provider.action.SMS_EMERGENCY_CB_RECEIVED"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    Log.d(TAG, "Broadcast received: $action in ${this.javaClass.simpleName}")

    AlertStore.addAlert(
      context = context,
      type = context.getString(R.string.type_cellular_raw),
      title = intent.toString() + intent.getStringExtra("message")
    )

    if (action == SMS_CB_RECEIVED_ACTION ||
      action == SMS_EMERGENCY_CB_RECEIVED_ACTION ||
      action == ACTION_SMS_EMERGENCY_CB_RECEIVED
    ) {

      val extras = intent.extras
      var alertMessage = ""

      // Log all extras for debugging
      extras?.keySet()?.forEach { key ->
        val value = extras.get(key)
        Log.d(TAG, "Extra: $key = $value")
      }

      // Try to extract using standard Android APIs if available (SmsCbMessage)
      try {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages != null && messages.isNotEmpty()) {
          alertMessage = messages.mapNotNull { it?.displayMessageBody }.joinToString("\n")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error parsing CB message via Telephony API", e)
      }

      // Fallback to manual extraction from extras if message is still empty
      if (alertMessage.isEmpty()) {
        extras?.keySet()?.forEach { key ->
          val lowerKey = key.lowercase()
          if (lowerKey.contains("message") || lowerKey == "body") {
            alertMessage = extras.get(key)?.toString() ?: ""
          }
        }
      }

      if (alertMessage.isNotEmpty()) {
        Log.d(TAG, "Emergency alert processed: $alertMessage")

        AlertStore.addAlert(
          context = context,
          type = if (action.contains("EMERGENCY")) context.getString(R.string.emergency_cellular) else context.getString(R.string.cellular),
          title = alertMessage
        )

        // Trigger driving check and potential navigation
        AlertManager.onEmergencyAlert(context)
      }
    }
  }
}

/**
 * Receiver for standard Cell Broadcasts.
 * Declared in manifest with android.permission.BROADCAST_SMS
 */
class StandardCellBroadcastReceiver : EmergencyAlertReceiver()
