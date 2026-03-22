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

package io.github.eranl.gotoshelter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.AndroidPlatform
import io.github.eranl.gotoshelter.monitoring.TzofarMonitor
import io.github.eranl.gotoshelter.shared.R
import io.github.eranl.gotoshelter.util.LocationHelper
import io.github.eranl.gotoshelter.util.bindContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that monitors for emergency alerts.
 * Uses TzofarMonitor (shared KMP logic) to detect alerts.
 */
class EmergencyMonitorService : Service() {
  private val TAG = "EmergencyMonitor"
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var tzofarMonitor: TzofarMonitor? = null

  companion object {
    private const val MONITOR_CHANNEL_ID = "EmergencyMonitorChannel"
    private const val FOREGROUND_NOTIFICATION_ID = 102

    @Volatile
    private var isRunning = false

    fun start(context: Context) {
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
    }

    fun stop(context: Context) {
      val intent = Intent(context, EmergencyMonitorService::class.java)
      context.stopService(intent)
    }

    fun isRunning() = isRunning
  }

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "onCreate")
    // One-time setup of helpers and channels
    LocationHelper.bindContext(this)
    AlertManager.bindContext(this)
    createNotificationChannels()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand")

    // Check if we should actually be running.
    // This handles cases where the service might be started but permissions/settings are missing.
    val platform = AndroidPlatform.getInstance(this)
    if (!platform.status.value.canStartMonitorService) {
      Log.w(TAG, "Service started but should not be running according to current status. Stopping.")
      stopSelf()
      return START_NOT_STICKY
    }

    isRunning = true

    // Must call startForeground within 5 seconds of startForegroundService
    val notification = createMonitoringNotification()
    if (Build.VERSION.SDK_INT >= 34) {
      ServiceCompat.startForeground(
        this,
        FOREGROUND_NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
      )
    } else if (Build.VERSION.SDK_INT >= 29) {
      startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    } else {
      startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    // Initialize monitoring if not already initialized
    if (tzofarMonitor == null) {
      serviceScope.launch {
        Log.d(TAG, "Initializing monitoring components...")
        // 1. Load shared resources (polygons)
        LocationHelper.init()

        // 2. Start monitoring
        val monitor = TzofarMonitor(scope = serviceScope)
        tzofarMonitor = monitor
        monitor.start()
      }
    }

    // Handle intent extras if any
    val message = intent?.getStringExtra("message")
    if (message != null) {
      serviceScope.launch { tzofarMonitor?.handleMessage(message) }
    }

    if (intent?.action == "ACTION_TEST_NAV") {
      AlertManager.triggerNavigation()
    }

    return START_STICKY
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= 26) {
      val manager = getSystemService(NotificationManager::class.java)
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
    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val flags = if (Build.VERSION.SDK_INT >= 23) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = intent?.let {
      PendingIntent.getActivity(this, 0, it, flags)
    }

    val appIconId = applicationInfo.icon
    val largeIcon = BitmapFactory.decodeResource(resources, appIconId)

    return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.monitoring_service_desc))
      .setSmallIcon(appIconId)
      .setLargeIcon(largeIcon)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .apply {
        pendingIntent?.let { setContentIntent(it) }
      }
      .build()
  }

  override fun onDestroy() {
    Log.d(TAG, "onDestroy")
    isRunning = false
    tzofarMonitor?.stop()
    tzofarMonitor = null
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
