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

import io.github.eranl.gotoshelter.Platform
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User

/**
 * A multiplatform logger that handles Sentry initialization and reporting.
 */
object Logger {
  private val reporters = mutableListOf<ErrorReporter>()

  /**
   * Initializes Sentry with strict privacy settings.
   * @param dsn The Sentry DSN.
   */
  fun init(dsn: String, platform: Platform) {
    Sentry.init { options ->
      options.dsn = dsn
      options.debug = platform.status.value.isDebug
      options.environment = if (platform.status.value.isDebug) "debug" else "release"

      // Hardened privacy configuration
      options.enableAutoSessionTracking = false
      options.enableAppHangTracking = false
      options.enableCaptureFailedRequests = false
      options.enableWatchdogTerminationTracking = false
      options.isAnrEnabled = false
      options.maxBreadcrumbs = 0

      options.beforeSend = { event ->
        if (!SettingsProvider.isErrorReportingEnabled) {
          null
        } else {
          // 1. Scrub User info and prevent geo-inference from IP
          event.user = User().apply {
            ipAddress = "0.0.0.0"
            id = null
          }

          // 2. Clear device identity
          event.serverName = null

          // 3. Clear all breadcrumbs (prevents leaking UI state/navigation)
          event.breadcrumbs = mutableListOf()

          // 4. Scrub Contexts (Device info)
          // This requires an implementation for https://github.com/getsentry/sentry-kotlin-multiplatform/issues/537 to
          // have an effect, hence the use of a snapshot version
          (event.contexts as MutableMap).remove("device")

          for (permission in platform.status.value.specialPermissions) {
            event.setTag(permission.toString(), platform.status.value.permissions[permission].toString())
          }

          event
        }
      }
    }

    addReporter(CommonSentryReporter())
  }

  fun addReporter(reporter: ErrorReporter) {
    reporters.add(reporter)
  }

  /**
   * Globally enable or disable error reporting collection.
   */
  fun setCollectionEnabled(enabled: Boolean) {
    SettingsProvider.isErrorReportingEnabled = enabled
  }

  fun logError(throwable: Throwable, message: String? = null) {
    reporters.forEach { it.report(throwable, message) }
  }

}

interface ErrorReporter {
  fun report(throwable: Throwable, message: String?)
}

/**
 * Shared implementation of Sentry reporting.
 */
private class CommonSentryReporter : ErrorReporter {
  override fun report(throwable: Throwable, message: String?) {
    message?.let { log(it) }
    Sentry.captureException(throwable)
  }

  fun log(message: String) {
    Sentry.captureMessage(message)
  }
}
