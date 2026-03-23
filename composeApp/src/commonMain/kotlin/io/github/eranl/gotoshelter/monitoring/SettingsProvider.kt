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
