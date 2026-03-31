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
import kotlin.math.*

/**
 * Shared helper for location-based alert filtering.
 */
object LocationHelper {
  private val areaPolygons = mutableMapOf<Int, List<GeoPoint>>()
  private const val EARTH_RADIUS_METERS = 6371000.0
  private const val DEFAULT_TOLERANCE_METERS = 5000.0

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
   * Checks if the current location is inside or near any of the polygons associated with the areaIds.
   */
  internal suspend fun isLocationInArea(areaIds: List<Int>, toleranceMeters: Double = DEFAULT_TOLERANCE_METERS): Boolean {
    val currentLoc = getCurrentLocation() ?: return false
    for (areaId in areaIds) {
      val polygon = areaPolygons[areaId] ?: continue
      if (isPointInOrNearPolygon(currentLoc, polygon, toleranceMeters)) {
        return true
      }
    }
    return false
  }

  private fun isPointInOrNearPolygon(point: GeoPoint, polygon: List<GeoPoint>, toleranceMeters: Double): Boolean {
    // 1. Check if strictly inside
    if (containsLocation(point, polygon)) return true

    // 2. Check distance to each edge for tolerance
    for (i in polygon.indices) {
      val j = (i + 1) % polygon.size
      val distance = distanceToSegment(point, polygon[i], polygon[j])
      if (distance <= toleranceMeters) return true
    }

    return false
  }

  /**
   * Standard Ray-casting algorithm to determine if a point is inside a polygon.
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
   * Calculates the shortest distance from a point to a line segment (in meters).
   */
  private fun distanceToSegment(p: GeoPoint, s1: GeoPoint, s2: GeoPoint): Double {
    // Use a local projection factor for longitude to account for Earth's curvature
    val lonScale = cos(s1.latitude * PI / 180.0)
    
    val dx = (s2.longitude - s1.longitude) * lonScale
    val dy = s2.latitude - s1.latitude
    val d2 = dx * dx + dy * dy
    
    if (d2 == 0.0) return distanceBetween(p, s1)

    // Project point p onto line s1-s2, find parametric t
    val pdx = (p.longitude - s1.longitude) * lonScale
    val pdy = p.latitude - s1.latitude
    
    var t = (pdx * dx + pdy * dy) / d2
    t = max(0.0, min(1.0, t))

    val projection = GeoPoint(
      s1.latitude + t * (s2.latitude - s1.latitude),
      s1.longitude + t * (s2.longitude - s1.longitude)
    )
    return distanceBetween(p, projection)
  }

  /**
   * Haversine formula to calculate distance between two points in meters.
   */
  private fun distanceBetween(p1: GeoPoint, p2: GeoPoint): Double {
    val dLat = (p2.latitude - p1.latitude) * PI / 180.0
    val dLon = (p2.longitude - p1.longitude) * PI / 180.0
    val a = sin(dLat / 2).pow(2) +
            cos(p1.latitude * PI / 180.0) * cos(p2.latitude * PI / 180.0) *
            sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
  }

  private suspend fun getCurrentLocation(): GeoPoint? = currentLocationProvider()
}

internal expect suspend fun getPlatformLocation(): GeoPoint?
