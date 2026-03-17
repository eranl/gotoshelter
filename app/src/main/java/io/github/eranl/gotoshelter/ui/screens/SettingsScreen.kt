package io.github.eranl.gotoshelter.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.AlertStore
import io.github.eranl.gotoshelter.BuildConfig
import io.github.eranl.gotoshelter.R
import io.github.eranl.gotoshelter.service.EmergencyMonitorService
import io.github.eranl.gotoshelter.ui.components.AppTopBar
import io.github.eranl.gotoshelter.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

private const val RECEIVE_EMERGENCY_BROADCAST = "android.permission.RECEIVE_EMERGENCY_BROADCAST"

@Composable
fun SettingsScreen(onAlertsClick: () -> Unit = {}) {
  val context = LocalContext.current

  BackHandler(onBack = onAlertsClick)

  var smsPermissionGranted by remember {
    mutableStateOf(checkPermission(context, Manifest.permission.RECEIVE_SMS))
  }
  var cellBroadcastPermissionGranted by remember {
    mutableStateOf(checkPermission(context, "android.permission.READ_CELL_BROADCASTS"))
  }
  var emergencyBroadcastPermissionGranted by remember {
    mutableStateOf(checkPermission(context, RECEIVE_EMERGENCY_BROADCAST))
  }
  var activityGranted by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= 29) {
        checkPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
      } else {
        true
      }
    )
  }
  var locationGranted by remember {
    mutableStateOf(checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
  }
  var notificationsGranted by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= 33) {
        checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
      } else {
        true
      }
    )
  }
  var notificationListenerGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    smsPermissionGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: smsPermissionGranted
    cellBroadcastPermissionGranted = permissions["android.permission.READ_CELL_BROADCASTS"] ?: cellBroadcastPermissionGranted
    emergencyBroadcastPermissionGranted = permissions[RECEIVE_EMERGENCY_BROADCAST] ?: emergencyBroadcastPermissionGranted

    if (Build.VERSION.SDK_INT >= 29) {
      activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: activityGranted
    }
    if (Build.VERSION.SDK_INT >= 33) {
      notificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: notificationsGranted
    }
    locationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: locationGranted
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      AppTopBar(
        title = stringResource(R.string.settings_title),
        navigationIcon = {
          IconButton(onClick = onAlertsClick) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.back_to_alerts),
              tint = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      )
    },
    contentWindowInsets = WindowInsets.systemBars
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Cellular Alerts (SMS & Cell Broadcasts)
      val allCellularGranted = smsPermissionGranted && cellBroadcastPermissionGranted
      PermissionRow(
        title = stringResource(R.string.cellular_alerts_title),
        description = stringResource(R.string.cellular_alerts_description),
        isGranted = allCellularGranted,
        onClick = {
          launcher.launch(
            arrayOf(
              Manifest.permission.RECEIVE_SMS,
              "android.permission.READ_CELL_BROADCASTS",
              RECEIVE_EMERGENCY_BROADCAST
            )
          )
        }
      )

      // 2. Tzofar (Location & Notifications)
      val allTzofarGranted = locationGranted && notificationsGranted
      PermissionRow(
        title = stringResource(R.string.tzofar_alerts_title),
        description = stringResource(R.string.tzofar_alerts_description),
        isGranted = allTzofarGranted,
        onClick = {
          val permissions = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
          if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
          }
          launcher.launch(permissions.toTypedArray())
        }
      )

      // 3. Home Front Command (Notification Access)
      PermissionRow(
        title = stringResource(R.string.hfc_alerts_title),
        description = stringResource(R.string.hfc_alerts_description),
        isGranted = notificationListenerGranted,
        onClick = {
          context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
      )

      // 4. Waze/Driving (Activity Recognition)
      PermissionRow(
        title = stringResource(R.string.activity_permission),
        description = stringResource(R.string.activity_description),
        isGranted = activityGranted,
        onClick = {
          if (Build.VERSION.SDK_INT >= 29) {
            launcher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
          }
        }
      )

      Spacer(modifier = Modifier.height(32.dp))

      Button(
        onClick = { AlertManager.onEmergencyAlert(context) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.test_waze_button))
      }

      Spacer(modifier = Modifier.height(32.dp))

      Text(
        text = stringResource(R.string.privacy_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
      )
    }
  }

  // Start services if permissions are granted
  LaunchedEffect(notificationsGranted, locationGranted, Settings.canDrawOverlays(context)) {
    EmergencyMonitorService.startIfPermissionsGranted(context)
  }

  // Periodically check permissions
  LaunchedEffect(Unit) {
    while (true) {
      notificationListenerGranted = isNotificationServiceEnabled(context)
      locationGranted = checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
      smsPermissionGranted = checkPermission(context, Manifest.permission.RECEIVE_SMS)
      cellBroadcastPermissionGranted = checkPermission(context, "android.permission.READ_CELL_BROADCASTS")
      emergencyBroadcastPermissionGranted = checkPermission(context, RECEIVE_EMERGENCY_BROADCAST)
      if (Build.VERSION.SDK_INT >= 33) {
        notificationsGranted = checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
      }
      delay(2000)
    }
  }
}

@Composable
fun PermissionRow(
  title: String,
  description: String,
  isGranted: Boolean,
  showCheckOnly: Boolean = false,
  onClick: () -> Unit = {}
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
    if (isGranted) {
      Icon(
        if (showCheckOnly) Icons.Default.Info else Icons.Default.CheckCircle,
        contentDescription = stringResource(R.string.granted),
        tint = if (showCheckOnly) MaterialTheme.colorScheme.primary else SuccessGreen
      )
    } else {
      Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.approve_button), fontSize = 12.sp)
      }
    }
  }
}

private fun checkPermission(context: Context, permission: String): Boolean {
  return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
  val pkgName = context.packageName
  val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
  if (!TextUtils.isEmpty(flat)) {
    val names = flat.split(":")
    for (name in names) {
      val cn = ComponentName.unflattenFromString(name)
      if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
        return true
      }
    }
  }
  return false
}
