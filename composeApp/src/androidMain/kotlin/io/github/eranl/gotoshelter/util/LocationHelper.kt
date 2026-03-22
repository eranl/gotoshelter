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

package io.github.eranl.gotoshelter.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices
import io.github.eranl.gotoshelter.model.GeoPoint
import kotlinx.coroutines.tasks.await

private var appContext: Context? = null

/**
 * Bind the Android context to the location system.
 */
fun LocationHelper.bindContext(context: Context) {
  appContext = context.applicationContext
}

@SuppressLint("MissingPermission")
internal actual suspend fun getPlatformLocation(): GeoPoint? {
  val context = appContext ?: return null
  val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
  return try {
    val location = fusedLocationClient.lastLocation.await() ?: return null
    GeoPoint(location.latitude, location.longitude)
  } catch (e: Exception) {
    Log.e("LocationHelper", "failed to get location", e)
    null
  }
}
