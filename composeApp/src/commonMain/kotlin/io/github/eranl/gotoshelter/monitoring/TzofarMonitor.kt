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

package io.github.eranl.gotoshelter.monitoring

import io.github.eranl.gotoshelter.AlertManager
import io.github.eranl.gotoshelter.util.LocationHelper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Multi-platform monitor for Tzofar emergency alerts.
 * Uses Ktor WebSockets to connect and kotlinx.serialization for parsing.
 */
class TzofarMonitor(
  private val scope: CoroutineScope
) {
  private val client = HttpClient {
    install(WebSockets) {
      pingIntervalMillis = 45000
    }
  }

  private var monitoringJob: Job? = null

  companion object {
    private const val MAX_CONNECT_FAILURES = 50
    private const val RETRY_DELAY_MS = 30000L
  }

  fun start() {
    if (monitoringJob?.isActive == true) return

    monitoringJob = scope.launch {
      var consecutiveConnectFailures = 0
      while (isActive) {
        var connectionWasEstablished = false
        try {
          connectAndListen {
            connectionWasEstablished = true
            consecutiveConnectFailures = 0 // Reset on successful handshake
          }
        } catch (e: Exception) {
          if (connectionWasEstablished) {
            // The connection was active but dropped later (e.g. hourly abort)
            Logger.debugLog("TzofarMonitor: Connection lost: ${e.message}. Reconnecting immediately...")
          } else {
            // Failed to even establish the connection
            consecutiveConnectFailures++
            Logger.debugLog("TzofarMonitor: Failed to connect ($consecutiveConnectFailures/$MAX_CONNECT_FAILURES): ${e.message}. Retrying in ${RETRY_DELAY_MS / 1000}s...")
            
            if (consecutiveConnectFailures > MAX_CONNECT_FAILURES) {
              Logger.debugLog("TzofarMonitor: Max consecutive connection failures reached. Crashing.")
              throw e
            }
            delay(RETRY_DELAY_MS)
          }
        }
      }
    }
  }

  private suspend fun connectAndListen(onConnect: () -> Unit) {
    // Use wss for secure connection to avoid CLEARTEXT policy errors
    client.wss(
      method = HttpMethod.Get,
      host = "ws.tzevaadom.co.il",
      path = "/socket?platform=ANDROID",
      request = {
        header(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36")
        header(HttpHeaders.Referrer, "https://www.tzevaadom.co.il")
        header(HttpHeaders.Origin, "https://www.tzevaadom.co.il")
        header("tzofar", generateTzofarToken())
      }
    ) {
      Logger.debugLog("TzofarMonitor: Connected to WebSocket")
      onConnect()
      for (frame in incoming) {
        if (frame is Frame.Text) {
          handleMessage(frame.readText())
        }
      }
      Logger.debugLog("TzofarMonitor: WebSocket connection closed by server")
    }
  }

  suspend fun handleMessage(text: String) {
    val json = Json.parseToJsonElement(text).jsonObject
    if (json["type"]!!.jsonPrimitive.content != "SYSTEM_MESSAGE") return

    val data = json["data"]!!.jsonObject
    if (data["instructionType"]!!.jsonPrimitive.int != 0) return

    val cityIds = data["citiesIds"]!!.jsonArray.map { it.jsonPrimitive.int }

    if (LocationHelper.isLocationInArea(cityIds)) {
      AlertManager.onEmergencyAlert("Tzofar")
    } else {
      Logger.debugLog("Tzofar city ids: $cityIds")
    }
  }

  private fun generateTzofarToken(): String {
    val chars = "0123456789abcdef"
    return (1..32).map { chars[Random.nextInt(chars.length)] }.joinToString("")
  }

  fun stop() {
    monitoringJob?.cancel()
    client.close()
  }
}
