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
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.onNotificationPosted
import io.github.eranl.gotoshelter.util.HFC_PACKAGE_NAME

/**
 * Service that listens for notifications from emergency alert apps.
 * Monitors Home Front Command, Tzofar, RedColor, and Cumta.
 */
class EmergencyAlertListenerService : NotificationListenerService() {
  companion object {
    private const val TAG = "EmergencyAlertListener"

    private val SUPPORTED_PACKAGES = setOf(
      HFC_PACKAGE_NAME,       // Home Front Command (Official)
      /*
                  "com.red.alert",        // RedColor / RedAlert
                  "com.tzofar",           // Tzofar
                  "com.rescue.israel"     // Cumta
      */
    )
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    Log.d(TAG, "Notification Listener Connected")
    AlertManager.bindContext(this)
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    Log.d(TAG, "Notification Listener Disconnected")
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (!SUPPORTED_PACKAGES.contains(sbn.packageName)) return

    // Ensure context is bound even if service was just restarted
    AlertManager.bindContext(this)

    val extras = sbn.notification.extras
    val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

    AlertManager.onNotificationPosted(
      title = title,
      text = text
    )
  }
}
