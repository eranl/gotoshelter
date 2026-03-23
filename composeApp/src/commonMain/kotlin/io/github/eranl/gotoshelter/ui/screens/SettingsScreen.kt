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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gotoshelter.composeapp.generated.resources.Res
import gotoshelter.composeapp.generated.resources.activity_description
import gotoshelter.composeapp.generated.resources.activity_permission
import gotoshelter.composeapp.generated.resources.app_not_active_title
import gotoshelter.composeapp.generated.resources.approve_button
import gotoshelter.composeapp.generated.resources.background_location_description
import gotoshelter.composeapp.generated.resources.battery_optimization_description
import gotoshelter.composeapp.generated.resources.battery_optimization_title
import gotoshelter.composeapp.generated.resources.error_reporting_description
import gotoshelter.composeapp.generated.resources.error_reporting_title
import gotoshelter.composeapp.generated.resources.granted
import gotoshelter.composeapp.generated.resources.hfc_alerts_description
import gotoshelter.composeapp.generated.resources.hfc_alerts_title
import gotoshelter.composeapp.generated.resources.intro_description
import gotoshelter.composeapp.generated.resources.intro_title
import gotoshelter.composeapp.generated.resources.no_services_enabled_description
import gotoshelter.composeapp.generated.resources.overlay_permission_description
import gotoshelter.composeapp.generated.resources.overlay_permission_title
import gotoshelter.composeapp.generated.resources.privacy_note
import gotoshelter.composeapp.generated.resources.supplementary_note
import gotoshelter.composeapp.generated.resources.test_waze_button
import gotoshelter.composeapp.generated.resources.tzofar_alerts_description
import gotoshelter.composeapp.generated.resources.tzofar_alerts_description_notifications
import gotoshelter.composeapp.generated.resources.tzofar_alerts_description_location
import gotoshelter.composeapp.generated.resources.tzofar_alerts_title
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.AppPermission
import io.github.eranl.gotoshelter.AppStatus
import io.github.eranl.gotoshelter.Platform
import io.github.eranl.gotoshelter.getPlatform
import io.github.eranl.gotoshelter.monitoring.Logger
import io.github.eranl.gotoshelter.monitoring.SettingsProvider
import io.github.eranl.gotoshelter.ui.components.AppTopBar
import io.github.eranl.gotoshelter.ui.theme.SuccessGreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(platform: Platform = getPlatform()) {
  val status by platform.status.collectAsState()
  val errorReportingEnabled by if (LocalInspectionMode.current) {
    remember { mutableStateOf(false) }
  } else {
    SettingsProvider.errorReportingEnabled.collectAsState()
  }

  platform.BindPermissionHandler()

  SettingsContent(
    status = status,
    errorReportingEnabled = errorReportingEnabled,
    onToggleErrorReporting = { Logger.setCollectionEnabled(it) },
    platform = platform
  )
}

@Composable
private fun StringResource.safe(fallback: String): String =
  if (LocalInspectionMode.current) fallback else stringResource(this)

@Composable
fun SettingsContent(
  status: AppStatus,
  errorReportingEnabled: Boolean = false,
  onToggleErrorReporting: (Boolean) -> Unit = {},
  platform: Platform? = null
) {
  var debugClickCount by remember { mutableStateOf(0) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      AppTopBar()
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0)
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = innerPadding.calculateTopPadding())
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = Res.string.intro_title.safe("Welcome"),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .padding(bottom = 8.dp)
          .pointerInput(Unit) {
            detectTapGestures(onTap = {
              debugClickCount++
              if (debugClickCount >= 5) {
                debugClickCount = 0
                throw RuntimeException("Test Crash triggered by user")
              }
            })
          }
      )

      Text(
        text = Res.string.intro_description.safe("Description"),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      if (status.isCriticalState) {
        Spacer(modifier = Modifier.height(24.dp))
        WarningSection(
          showOverlayWarning = !status.isGranted(AppPermission.OVERLAY),
          showBatteryWarning = !status.isGranted(AppPermission.BATTERY_OPTIMIZATIONS),
          showNoServicesWarning = status.isGranted(AppPermission.OVERLAY) &&
                  status.isGranted(AppPermission.BATTERY_OPTIMIZATIONS) && status.noServicesEnabled,
          platform = platform
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // 1. Home Front Command (Notification Access)
        if (status.isHfcInstalled && status.runtimePermissions.contains(AppPermission.NOTIFICATION_ACCESS)) {
          PermissionRow(
            title = Res.string.hfc_alerts_title.safe("HFC"),
            description = Res.string.hfc_alerts_description.safe("HFC Desc"),
            isGranted = status.hfcEnabled,
            enabled = !status.overlayBatteryMissing,
            onClick = { platform?.requestPermissions(listOf(AppPermission.NOTIFICATION_ACCESS)) }
          )
        }

        // 2. Tzofar (Location & Notifications & Background)
        val needTzofarLocation = status.runtimePermissions.contains(AppPermission.COARSE_LOCATION)
        val needTzofarNotifications = status.runtimePermissions.contains(AppPermission.POST_NOTIFICATIONS)
        val needTzofarBackground = status.runtimePermissions.contains(AppPermission.BACKGROUND_LOCATION)

        if (needTzofarLocation || needTzofarNotifications || needTzofarBackground) {
          PermissionRow(
            title = Res.string.tzofar_alerts_title.safe("Tzofar"),
            description = when {
              status.basicTzofarGranted && !status.isGranted(AppPermission.BACKGROUND_LOCATION) ->
                Res.string.background_location_description

              needTzofarLocation && needTzofarNotifications ->
                Res.string.tzofar_alerts_description

              needTzofarLocation ->
                Res.string.tzofar_alerts_description_location

              needTzofarNotifications ->
                Res.string.tzofar_alerts_description_notifications

              else -> throw IllegalStateException("At least one Tzofar permission must be actionable")
            }.safe("Tzofar Desc"),
            isGranted = status.tzofarEnabled,
            enabled = !status.overlayBatteryMissing,
            onClick = {
              platform?.requestPermissions(
                listOf(
                  AppPermission.COARSE_LOCATION,
                  AppPermission.POST_NOTIFICATIONS,
                  AppPermission.BACKGROUND_LOCATION
                )
              )
            }
          )
        }

        // 3. Waze/Driving (Activity Recognition)
        if (status.runtimePermissions.contains(AppPermission.ACTIVITY_RECOGNITION)) {
          PermissionRow(
            title = Res.string.activity_permission.safe("Driving"),
            description = Res.string.activity_description.safe("Driving Desc"),
            isGranted = status.isGranted(AppPermission.ACTIVITY_RECOGNITION),
            enabled = !status.isCriticalState,
            onClick = {
              platform?.requestPermissions(listOf(AppPermission.ACTIVITY_RECOGNITION))
            }
          )
        }

        // 4. Error Reporting
        ReportingRow(
            title = Res.string.error_reporting_title.safe("Error Reporting"),
            description = Res.string.error_reporting_description.safe("Error Desc"),
            isGranted = errorReportingEnabled,
            onToggle = { Logger.setCollectionEnabled(it) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
          onClick = { AlertManager.triggerNavigation() },
          modifier = Modifier.fillMaxWidth(),
          enabled = !status.isCriticalState,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(Res.string.test_waze_button.safe("Test"))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
          text = Res.string.supplementary_note.safe("Note"),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
          text = Res.string.privacy_note.safe("Privacy"),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
      }
    }
  }
}

@Composable
fun WarningSection(
  showOverlayWarning: Boolean,
  showBatteryWarning: Boolean,
  showNoServicesWarning: Boolean,
  platform: Platform? = null
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
      .padding(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = Res.string.app_not_active_title.safe("Inactive"),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold
      )
    }

    if (showOverlayWarning) {
      Spacer(modifier = Modifier.height(8.dp))
      PermissionRow(
        title = Res.string.overlay_permission_title.safe("Overlay"),
        description = Res.string.overlay_permission_description.safe("Overlay Desc"),
        isGranted = false,
        onClick = { platform?.requestPermissions(listOf(AppPermission.OVERLAY)) }
      )
    }

    if (showBatteryWarning) {
      if (showOverlayWarning) {
        Spacer(modifier = Modifier.height(8.dp))
      }
      PermissionRow(
        title = Res.string.battery_optimization_title.safe("Battery"),
        description = Res.string.battery_optimization_description.safe("Battery Desc"),
        isGranted = false,
        onClick = { platform?.requestPermissions(listOf(AppPermission.BATTERY_OPTIMIZATIONS)) }
      )
    }

    if (showNoServicesWarning) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = Res.string.no_services_enabled_description.safe("No Services"),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }
    }
  }
}

@Composable
fun PermissionRow(
  title: String,
  description: String,
  isGranted: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit = {}
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp)
      .alpha(if (enabled) 1.0f else 0.5f),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Start
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start
      )
    }
    Spacer(Modifier.width(8.dp))
    if (isGranted) {
      Icon(
        Icons.Default.CheckCircle,
        contentDescription = Res.string.granted.safe("Granted"),
        tint = SuccessGreen
      )
    } else {
      Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
      ) {
        Text(Res.string.approve_button.safe("Grant"), fontSize = 12.sp)
      }
    }
  }
}

@Composable
fun ReportingRow(
  title: String,
  description: String,
  isGranted: Boolean,
  onToggle: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Start
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start
      )
    }
    Spacer(Modifier.width(8.dp))
    Switch(
      checked = isGranted,
      onCheckedChange = onToggle
    )
  }
}
