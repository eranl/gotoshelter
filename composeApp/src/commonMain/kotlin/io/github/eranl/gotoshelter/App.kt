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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gotoshelter.composeapp.generated.resources.Res
import gotoshelter.composeapp.generated.resources.error_install_navigation_app
import gotoshelter.composeapp.generated.resources.exit_app
import io.github.eranl.gotoshelter.monitoring.Logger
import io.github.eranl.gotoshelter.ui.components.AppTopBar
import io.github.eranl.gotoshelter.ui.screens.SettingsScreen
import io.github.eranl.gotoshelter.ui.theme.GoToShelterTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(onExit: () -> Unit) {
  val platform = getPlatform()
  val status by platform.status.collectAsState()
  Logger.debugLog("UI started")

  // Periodically refresh status to catch permission changes or app installations
  // when returning from settings or Play Store.
  // Since we pass this platform instance down, it updates the state for the whole app.
  LaunchedEffect(platform) {
    while (true) {
      platform.refreshStatus()
      delay(2000)
    }
  }

  // Start services if permissions are granted.
  LaunchedEffect(status.canStartMonitorService, status.canStartListenerService) {
    platform.startServicesIfPermissionsGranted()
  }

  GoToShelterTheme {
    if (status.isNavigationAppInstalled) {
      MainScreen(platform)
    } else {
      NoNavigationAppScreen(onExit = onExit)
    }
  }
}

@Composable
fun NoNavigationAppScreen(onExit: () -> Unit) {
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
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = stringResource(Res.string.error_install_navigation_app),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(32.dp))
      Button(
        onClick = onExit,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Text(stringResource(Res.string.exit_app), color = Color.White)
      }
      Spacer(modifier = Modifier.navigationBarsPadding())
    }
  }
}

@Composable
fun MainScreen(platform: Platform) {
  SettingsScreen(platform)
}
