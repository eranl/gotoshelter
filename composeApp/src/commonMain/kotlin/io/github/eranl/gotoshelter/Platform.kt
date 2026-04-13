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

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

enum class AppPermission {
  ACTIVITY_RECOGNITION,
  COARSE_LOCATION,
  BACKGROUND_LOCATION,
  POST_NOTIFICATIONS,
  NOTIFICATION_ACCESS,
  OVERLAY,
  BATTERY_OPTIMIZATIONS
}

enum class UpdateStatus {
  NONE,
  AVAILABLE,
  DOWNLOADING,
  DOWNLOADED,
  FAILED
}

data class AppStatus(
  val permissions: Map<AppPermission, Boolean>,
  val runtimePermissions: Set<AppPermission>,
  val specialPermissions: Set<AppPermission> = emptySet(),
  val isHfcInstalled: Boolean,
  val isNavigationAppInstalled: Boolean,
  val debugBuild: Boolean = false
) {
  /**
   * Returns true if the permission is granted, or if it's not a runtime permission
   * on the current platform/version (handled by the platform implementation pre-filling the map).
   */
  fun isGranted(permission: AppPermission): Boolean = permissions[permission] ?: true

  // Derived properties calculated once at initialization
  val hfcEnabled = isGranted(AppPermission.NOTIFICATION_ACCESS)

  val basicTzofarGranted = isGranted(AppPermission.COARSE_LOCATION) &&
          isGranted(AppPermission.POST_NOTIFICATIONS)

  val tzofarEnabled = basicTzofarGranted && isGranted(AppPermission.BACKGROUND_LOCATION)

  val anyServiceEnabled = hfcEnabled || tzofarEnabled

  val overlayBatteryMissing = !isGranted(AppPermission.OVERLAY) ||
          !isGranted(AppPermission.BATTERY_OPTIMIZATIONS)

  val noServicesEnabled = !anyServiceEnabled
  val isCriticalState = overlayBatteryMissing || noServicesEnabled || !isNavigationAppInstalled

  val canStartMonitorService = tzofarEnabled && !isCriticalState
  val canStartListenerService = hfcEnabled && !isCriticalState
}

interface Platform {
  val name: String
  val status: StateFlow<AppStatus>
  val updateStatus: StateFlow<UpdateStatus>

  fun requestPermissions(permissions: List<AppPermission>)
  fun refreshStatus()

  /**
   * Binds platform-specific handlers (like activity result launchers) to the Composable lifecycle.
   * This should be called once from a top-level screen that requires permission or update flows.
   */
  @Composable
  fun BindHandlers()

  fun startServicesIfPermissionsGranted()
  fun getExternalFilesDir(): okio.Path

  fun checkForUpdates()
  fun requestUpdate()
  fun completeUpdate()

  fun logExitReasons()
}

@Composable
expect fun getPlatform(): Platform
