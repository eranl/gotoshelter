package io.github.eranl.gotoshelter

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data class representing a single emergency alert
 */
data class Alert(
  val id: String = java.util.UUID.randomUUID().toString(),
  val type: String,
  val timestamp: LocalDateTime = LocalDateTime.now(),
  val title: String = ""
)

/**
 * Singleton store for managing all emergency alerts.
 * Provides a reactive flow of alerts that can be observed by UI components.
 */
object AlertStore {
  private const val TAG = "AlertStore"
  private const val MAX_ALERTS = 100
  private const val ALERTS_FILE_NAME = "alerts_log.txt"

  private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
  val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

  /**
   * Add a new alert to the store and save it to a file
   */
  fun addAlert(context: Context, type: String, title: String) {
    try {
      val alert = Alert(
        type = type,
        timestamp = LocalDateTime.now(),
        title = title
      )

      val currentAlerts = _alerts.value.toMutableList()
      currentAlerts.add(0, alert) // Add to beginning (most recent first)

      // Keep only the latest MAX_ALERTS
      if (currentAlerts.size > MAX_ALERTS) {
        currentAlerts.drop(MAX_ALERTS)
      }

      _alerts.value = currentAlerts
      Log.d(TAG, "Alert added: $alert. Total alerts: ${_alerts.value.size}")

      // Automatically save to app files only in debug versions
      if (BuildConfig.DEBUG) {
        appendAlertToFile(context, alert)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error adding alert: ${e.message}", e)
    }
  }

  /**
   * Remove an alert by id
   */
  fun removeAlert(alertId: String) {
    val currentAlerts = _alerts.value.toMutableList()
    currentAlerts.removeAll { it.id == alertId }
    _alerts.value = currentAlerts
    Log.d(TAG, "Alert removed: $alertId. Remaining: ${_alerts.value.size}")
  }

  /**
   * Clear all alerts
   */
  fun clearAllAlerts() {
    _alerts.value = emptyList()
    Log.d(TAG, "All alerts cleared")
  }

  /**
   * Get all alerts (snapshot)
   */
  fun getAllAlerts(): List<Alert> = _alerts.value

  /**
   * Get alert count
   */
  fun getAlertCount(): Int = _alerts.value.size

  private fun appendAlertToFile(context: Context, alert: Alert) {
    try {
      val file = File(context.getExternalFilesDir(null), ALERTS_FILE_NAME)
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      val logEntry = StringBuilder().apply {
        append("[${alert.timestamp.format(formatter)}] ")
        append("EVENT: ${alert.type} | ")
        append("RAW: ${alert.title}\n")
      }.toString()

      FileOutputStream(file, true).use { it.write(logEntry.toByteArray()) }
      Log.d(TAG, "Alert appended to ${file.absolutePath}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to append alert to file", e)
    }
  }
}
