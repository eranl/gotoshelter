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

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.eranl.gotoshelter.monitoring.Logger

class GoToShelterApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    // Initialize our common Logger which handles Sentry
    val platform = AndroidPlatform.getInstance(this)
    Logger.init(BuildConfig.SENTRY_DSN, platform)
    Logger.debugLog("app created")
    
    // Log why we were last closed
    platform.logExitReasons()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    val levelStr = when (level) {
      ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
      ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
      else -> "LEVEL_$level"
    }
    Logger.debugLog("GoToShelterApplication: onTrimMemory - level: $levelStr")
  }
}
