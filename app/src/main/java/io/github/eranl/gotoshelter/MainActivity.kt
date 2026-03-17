package io.github.eranl.gotoshelter

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.eranl.gotoshelter.service.EmergencyAlertListenerService
import io.github.eranl.gotoshelter.service.EmergencyMonitorService
import io.github.eranl.gotoshelter.ui.components.AppTopBar
import io.github.eranl.gotoshelter.ui.screens.AlertsScreen
import io.github.eranl.gotoshelter.ui.screens.SettingsScreen
import io.github.eranl.gotoshelter.ui.theme.GoToShelterTheme
import io.github.eranl.gotoshelter.ui.theme.SuccessGreen

class MainActivity : ComponentActivity() {
  private val TAG = "MainActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val isNavInstalled = AlertManager.isNavigationAppInstalled(this)
    if (isNavInstalled) {
      startEligibleServices(this)
    }

    setContent {
      GoToShelterTheme {
        if (!isNavInstalled) {
          NoNavigationAppScreen(onExit = { finish() })
        } else {
          MainScreen()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (AlertManager.isNavigationAppInstalled(this)) {
      startEligibleServices(this)
    }
  }

  private fun startEligibleServices(context: Context) {
    val overlayGranted = Settings.canDrawOverlays(context)
    if (!overlayGranted) {
      Log.d(TAG, "Overlay permission missing, skipping service startup")
      return
    }

    // 1. Notification Listener only needs Notification Access + Overlay
    if (isNotificationServiceEnabled(context)) {
      Log.d(TAG, "Ensuring EmergencyAlertListenerService is running")
      EmergencyAlertListenerService.ensureServiceRunning(context)
    }

    // 2. Monitor Service needs Location + Notifications + Overlay (checked inside the method)
    Log.d(TAG, "Attempting to start EmergencyMonitorService")
    EmergencyMonitorService.startIfPermissionsGranted(context)
  }
}

@Composable
fun NoNavigationAppScreen(onExit: () -> Unit) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      AppTopBar(title = stringResource(R.string.no_navigation_app_installed))
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = stringResource(R.string.error_install_navigation_app),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(32.dp))
      Button(
        onClick = onExit,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Text(stringResource(R.string.exit_app), color = Color.White)
      }
    }
  }
}

enum class Screen {
  ALERTS, SETTINGS
}

@Composable
fun MainScreen() {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var currentScreen by remember { mutableStateOf<Screen?>(null) }
  var showOverlayDialog by remember { mutableStateOf(false) }

  val checkAndStartServices = {
    val overlayGranted = Settings.canDrawOverlays(context)
    Log.d("MainScreen", "Checking permissions. Overlay granted: $overlayGranted")

    if (!overlayGranted) {
      showOverlayDialog = true
      currentScreen = Screen.SETTINGS
    } else {
      showOverlayDialog = false

      // Start background tasks immediately based on eligibility
      if (isNotificationServiceEnabled(context)) {
        EmergencyAlertListenerService.ensureServiceRunning(context)
      }
      EmergencyMonitorService.startIfPermissionsGranted(context)

      if (!areAllPermissionsGranted(context)) {
        currentScreen = Screen.SETTINGS
      } else if (currentScreen == null) {
        currentScreen = Screen.ALERTS
      }
    }
  }

  val overlayLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) {
    checkAndStartServices()
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        checkAndStartServices()
      } else if (event == Lifecycle.Event.ON_STOP) {
        currentScreen = null
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  if (showOverlayDialog) {
    AlertDialog(
      onDismissRequest = { /* Mandatory */ },
      title = { Text(stringResource(R.string.overlay_permission_title)) },
      text = { Text(stringResource(R.string.overlay_permission_description)) },
      confirmButton = {
        TextButton(onClick = {
          showOverlayDialog = false
          val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:\${context.packageName}")
          )
          overlayLauncher.launch(intent)
        }) {
          Text(stringResource(R.string.approve_button))
        }
      },
      dismissButton = {
        TextButton(onClick = { (context as? ComponentActivity)?.finish() }) {
          Text(stringResource(R.string.exit_app))
        }
      }
    )
  }

  val screenToDisplay = currentScreen
    ?: if (areAllPermissionsGranted(context) && Settings.canDrawOverlays(context)) Screen.ALERTS else Screen.SETTINGS

  when (screenToDisplay) {
    Screen.ALERTS -> AlertsScreen(onSettingsClick = { currentScreen = Screen.SETTINGS })
    Screen.SETTINGS -> SettingsScreen(onAlertsClick = { currentScreen = Screen.ALERTS })
  }
}

@Composable
fun PermissionRow(
  title: String,
  description: String,
  isGranted: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontWeight = FontWeight.Bold)
      Text(text = description, style = MaterialTheme.typography.bodySmall)
    }
    if (isGranted) {
      Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.granted), tint = SuccessGreen)
    } else {
      Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.approve_button), fontSize = 12.sp)
      }
    }
  }
}

private fun areAllPermissionsGranted(context: Context): Boolean {
  val location = checkPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

  val activity = if (Build.VERSION.SDK_INT >= 29) {
    checkPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
  } else true

  val notifications = if (Build.VERSION.SDK_INT >= 33) {
    checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
  } else true

  val notificationListener = isNotificationServiceEnabled(context)
  val overlay = Settings.canDrawOverlays(context)

  return location && activity && notifications && notificationListener && overlay
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
