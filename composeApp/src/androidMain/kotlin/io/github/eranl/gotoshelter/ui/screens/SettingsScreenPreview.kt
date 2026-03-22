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

package io.github.eranl.gotoshelter.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import io.github.eranl.gotoshelter.AppPermission
import io.github.eranl.gotoshelter.AppStatus
import io.github.eranl.gotoshelter.ui.theme.GoToShelterTheme

@Preview(name = "Phone", device = Devices.PIXEL_4, showBackground = true)
@Preview(name = "Tablet", device = Devices.PIXEL_C, showBackground = true)
@Composable
fun SettingsScreenPreview() {
  GoToShelterTheme {
    SettingsContent(
      status = AppStatus(
        permissions = mapOf(
          AppPermission.ACTIVITY_RECOGNITION to true,
          AppPermission.COARSE_LOCATION to true,
          AppPermission.BACKGROUND_LOCATION to false,
          AppPermission.POST_NOTIFICATIONS to true,
          AppPermission.NOTIFICATION_ACCESS to true,
          AppPermission.OVERLAY to true,
          AppPermission.BATTERY_OPTIMIZATIONS to true
        ),
        runtimePermissions = setOf(
          AppPermission.ACTIVITY_RECOGNITION,
          AppPermission.COARSE_LOCATION,
          AppPermission.BACKGROUND_LOCATION,
          AppPermission.POST_NOTIFICATIONS,
          AppPermission.NOTIFICATION_ACCESS,
          AppPermission.OVERLAY,
          AppPermission.BATTERY_OPTIMIZATIONS
        ),
        isHfcInstalled = true,
        isNavigationAppInstalled = true
      )
    )
  }
}

@Preview(name = "Warning Section", showBackground = true)
@Composable
fun WarningSectionPreview() {
  GoToShelterTheme {
    WarningSection(
      showOverlayWarning = true,
      showBatteryWarning = true,
      showNoServicesWarning = false
    )
  }
}
