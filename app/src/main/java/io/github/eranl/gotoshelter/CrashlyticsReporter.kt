package io.github.eranl.gotoshelter

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.eranl.gotoshelter.monitoring.ErrorReporter

class CrashlyticsReporter : ErrorReporter {
    override fun report(throwable: Throwable, message: String?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }
}
