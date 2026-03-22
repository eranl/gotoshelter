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

import gotoshelter.composeapp.generated.resources.Res
import io.github.eranl.gotoshelter.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Shared helper for location-based alert filtering.
 */
object LocationHelper {
  private val areaPolygons = mutableMapOf<Int, List<GeoPoint>>()

  // Internal for testing
  internal var currentLocationProvider: suspend () -> GeoPoint? = ::getPlatformLocation

  @OptIn(ExperimentalResourceApi::class)
  suspend fun init() {
    val bytes = Res.readBytes("files/polygons.json")
    initFromRawData(bytes.decodeToString())
  }

  internal fun initFromRawData(jsonString: String) {
    val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
    val areaMap = mutableMapOf<Int, List<GeoPoint>>()

    jsonObject.keys.forEach { key ->
      val id = key.toInt()
      val element = jsonObject[key]
      val polygonArray = element!!.jsonArray
      val points = mutableListOf<GeoPoint>()

      for (i in 0 until polygonArray.size) {
        val coordPair = polygonArray[i].jsonArray
        val lat = coordPair[0].jsonPrimitive.double
        val lng = coordPair[1].jsonPrimitive.double
        points.add(GeoPoint(lat, lng))
      }
      areaMap[id] = points
    }

    areaPolygons.clear()
    areaPolygons.putAll(areaMap)
  }

  /**
   * Checks if the current location is inside any of the polygons associated with the areaIds.
   */
  internal suspend fun isLocationInArea(areaIds: List<Int>): Boolean {
    val currentLoc = getCurrentLocation() ?: return false
    for (areaId in areaIds) {
      val polygon = areaPolygons[areaId] ?: continue
      if (containsLocation(currentLoc, polygon)) {
        return true
      }
    }
    return false
  }

  /**
   * Standard Ray-casting algorithm to determine if a point is inside a polygon.
   * Works on both Android and iOS.
   */
  internal fun containsLocation(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
    var intersectCount = 0
    for (i in polygon.indices) {
      val j = (i + 1) % polygon.size
      val p1 = polygon[i]
      val p2 = polygon[j]

      if (((p1.latitude > point.latitude) != (p2.latitude > point.latitude)) &&
        (point.longitude < (p2.longitude - p1.longitude) * (point.latitude - p1.latitude) / (p2.latitude - p1.latitude) + p1.longitude)
      ) {
        intersectCount++
      }
    }
    return intersectCount % 2 != 0
  }

  /**
   * Platform-specific location retrieval.
   */
  private suspend fun getCurrentLocation(): GeoPoint? = currentLocationProvider()
}

/**
 * To be implemented in androidMain and iosMain.
 */
internal expect suspend fun getPlatformLocation(): GeoPoint?
