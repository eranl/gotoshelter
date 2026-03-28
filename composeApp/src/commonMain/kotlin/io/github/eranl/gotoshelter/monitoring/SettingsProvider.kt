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

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Common settings provider using Multiplatform Settings.
 */
object SettingsProvider {
  private val settings: Settings = Settings()
  private const val KEY_ERROR_REPORTING = "error_reporting_enabled"

  private val _errorReportingEnabled = MutableStateFlow<Boolean>(settings.getBoolean(KEY_ERROR_REPORTING, false))
  val errorReportingEnabled: StateFlow<Boolean> = _errorReportingEnabled.asStateFlow()

  var isErrorReportingEnabled: Boolean
    get() = _errorReportingEnabled.value
    set(value) {
      settings.putBoolean(KEY_ERROR_REPORTING, value)
      _errorReportingEnabled.value = value
    }
}
