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

/**
 * Singleton to coordinate emergency alerts and navigation logic.
 * Platform-specific implementations handle activity recognition and app launching.
 */
expect object AlertManager {
  /**
   * Entry point for any emergency alert.
   */
  fun onEmergencyAlert(type: String)

  /**
   * Triggers navigation to the nearest shelter.
   */
  fun triggerNavigation()
}

/**
 * Set of notification titles that indicate an actual emergency alert in the user's area.
 */
private val notificationTitles =
  setOf("מבזק | באזורך", "News Flash | In your area", "عاجل | في منطقتك", "Краткая сводка | в вашем районе")

/**
 * Common logic to filter and process notifications from emergency alert apps.
 */
fun AlertManager.onNotificationPosted(title: String) {
  if (notificationTitles.contains(title)) {
    onEmergencyAlert("notification")
  }
}
