package io.github.eranl.gotoshelter.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.AlertStore
import io.github.eranl.gotoshelter.MainActivity
import io.github.eranl.gotoshelter.R
import io.github.eranl.gotoshelter.util.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Foreground service that monitors for emergency alerts.
 * 1. Maintains a WebSocket connection to Tzofar for real-time alerts.
 * Runs constantly in the background to ensure immediate detection.
 */
class EmergencyMonitorService : Service() {
  private lateinit var client: OkHttpClient
  private var webSocket: WebSocket? = null
  private val TAG = "EmergencyMonitor"
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private var reconnectDelay = 5000L // Start with 5 seconds
  private val maxReconnectDelay = 60000L // Cap at 1 minute

  companion object {
    private const val MONITOR_CHANNEL_ID = "EmergencyMonitorChannel"
    private const val FOREGROUND_NOTIFICATION_ID = 102
    private const val WS_URL = "wss://ws.tzevaadom.co.il/socket?platform=ANDROID"
    private const val PING_INTERVAL_SECONDS = 45L

    /**
     * Starts the service if both location, notification, and overlay permissions are granted.
     */
    fun startIfPermissionsGranted(context: Context) {
      val notificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
      val locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
      val overlayGranted = Settings.canDrawOverlays(context)

      if (notificationsGranted && locationGranted && overlayGranted) {
        Log.d("EmergencyMonitor", "Permissions granted, starting EmergencyMonitorService")
        val intent = Intent(context, EmergencyMonitorService::class.java)
        try {
          if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
          } else {
            context.startService(intent)
          }
        } catch (e: Exception) {
          Log.e("EmergencyMonitor", "Failed to start service", e)
        }
      } else {
        Log.d(
          "EmergencyMonitor",
          "Missing permissions to start service: notifications=$notificationsGranted, location=$locationGranted, overlay=$overlayGranted"
        )
      }
    }
  }

  override fun onCreate() {
    super.onCreate()

    // Load the geofence map on startup
    LocationHelper.init(this)

    createNotificationChannels()

    val notification = createMonitoringNotification()
    if (Build.VERSION.SDK_INT >= 34) {
      ServiceCompat.startForeground(
        this,
        FOREGROUND_NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      )
    } else {
      startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    client = OkHttpClient.Builder()
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
      .build()
    connect()
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= 26) {
      val manager = getSystemService(NotificationManager::class.java)

      // Monitoring channel (low priority, ongoing)
      val monitorChannel = NotificationChannel(
        MONITOR_CHANNEL_ID,
        getString(R.string.monitoring_service_title),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = getString(R.string.monitoring_service_desc)
        setShowBadge(false)
      }
      manager.createNotificationChannel(monitorChannel)
    }
  }

  private fun createMonitoringNotification(): Notification {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.monitoring_service_desc))
      .setSmallIcon(android.R.drawable.stat_sys_warning)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(pendingIntent)
      .build()
  }

  private fun generateTzofar(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun connect() {
    val request = Request.Builder().url(WS_URL).headers(
      Headers.headersOf(
        "User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36",
        "Referer", "https://www.tzevaadom.co.il",
        "Origin", "https://www.tzevaadom.co.il",
        "tzofar", this.generateTzofar()
      )
    ).build()
    webSocket = client.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "WebSocket Connected to Tzofar")
        reconnectDelay = 5000L // Reset delay on successful connection
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        handleMessage(text)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WebSocket Closing: $reason")
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket Failure: ${t.message}. Reconnecting in ${reconnectDelay / 1000}s...")
        android.os.Handler(mainLooper).postDelayed({ connect() }, reconnectDelay)

        // Exponential backoff
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(maxReconnectDelay)
      }
    })
  }

  private fun handleMessage(text: String) {
    // Log raw message for debugging and record keeping
    Log.d(TAG, "Received WebSocket message: $text")
    val alert = JSONObject(text)
    if (alert.getString("type") != "SYSTEM_MESSAGE") {
      return
    }

    val data = alert.getJSONObject("data")
    if (data.getInt("instructionType") != 0) {
      return
    }

    val ids = mutableListOf<Int>()
    //addIds(data.getJSONArray("areasIds"), ids)
    addIds(data.getJSONArray("citiesIds"), ids)

    serviceScope.launch {
      val isInArea = LocationHelper.isLocationInArea(ids, this@EmergencyMonitorService)
      if (isInArea) {
        Log.d(TAG, "Current location is within the alert area. Triggering alert handling.")

        AlertStore.addAlert(
          context = this@EmergencyMonitorService,
          type = getString(R.string.type_tzofar),
          text = data.getString(
            "title${
              when (Locale.getDefault().language) {
                "iw", "he" -> "He"
                "ar" -> "Ar"
                "ru" -> "Ru"
                else -> "En"
              }
            }"
          )
        )
        AlertManager.onEmergencyAlert(this@EmergencyMonitorService)
      } else {
        Log.d(TAG, "Current location is NOT within the alert area. Ignoring alert.")
      }
    }
  }

  private fun addIds(areasIds: JSONArray, ids: MutableList<Int>) {
    for (i in 0 until areasIds.length()) {
      ids.add(areasIds.getInt(i))
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val message = intent?.getStringExtra("message")
    if (message != null) {
      handleMessage(message)
    }

    if (intent?.action == "ACTION_TEST_NAV") {
      AlertManager.triggerNavigation(this)
    }

    return START_STICKY
  }

  override fun onDestroy() {
    webSocket?.close(1000, "Service destroyed")
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
