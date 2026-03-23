package io.github.eranl.gotoshelter.monitoring

/**
 * A simple multiplatform logger interface that can be implemented by platform-specific
 * crash reporting tools like Firebase Crashlytics or Sentry.
 */
object Logger {
    private val reporters = mutableListOf<ErrorReporter>()

    fun addReporter(reporter: ErrorReporter) {
        reporters.add(reporter)
        // Sync the initial state
        reporter.setCollectionEnabled(SettingsProvider.isErrorReportingEnabled)
    }

    /**
     * Globally enable or disable error reporting collection.
     */
    fun setCollectionEnabled(enabled: Boolean) {
        SettingsProvider.isErrorReportingEnabled = enabled
        reporters.forEach { it.setCollectionEnabled(enabled) }
    }

    fun logError(throwable: Throwable, message: String? = null) {
        reporters.forEach { it.report(throwable, message) }
    }

    fun logMessage(message: String) {
        reporters.forEach { it.log(message) }
    }
}

interface ErrorReporter {
    fun report(throwable: Throwable, message: String?)
    fun log(message: String)
    fun setCollectionEnabled(enabled: Boolean)
}
