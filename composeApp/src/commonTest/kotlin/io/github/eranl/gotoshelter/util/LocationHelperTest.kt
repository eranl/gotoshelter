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

import io.github.eranl.gotoshelter.model.GeoPoint
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.math.*

class LocationHelperTest {

  private val sampleJson = """
        {
          "1": [[0.0, 0.0], [0.0, 10.0], [10.0, 10.0], [10.0, 0.0]],
          "2": [[20.0, 20.0], [20.0, 30.0], [30.0, 30.0], [30.0, 20.0]]
        }
    """.trimIndent()

  @BeforeTest
  fun setup() {
    LocationHelper.initFromRawData(sampleJson)
  }

  @Test
  fun testIsLocationInArea() = runTest {
    // Mock location inside area 1
    LocationHelper.currentLocationProvider = { GeoPoint(5.0, 5.0) }
    assertTrue(LocationHelper.isLocationInArea(listOf(1)), "Should be in area 1")
    assertFalse(LocationHelper.isLocationInArea(listOf(2)), "Should NOT be in area 2")
    assertTrue(LocationHelper.isLocationInArea(listOf(1, 2)), "Should be in at least one area")

    // Mock location outside both but within tolerance (using default 20km set in code or 500m)
    // 0.001 degrees is ~111m. Let's use 500m explicitly.
    LocationHelper.currentLocationProvider = { GeoPoint(-0.002, 0.0) } 
    assertTrue(LocationHelper.isLocationInArea(listOf(1), 500.0), "Should be in area 1 due to 500m tolerance")

    // Mock location outside both and outside tolerance
    LocationHelper.currentLocationProvider = { GeoPoint(15.0, 15.0) }
    assertFalse(LocationHelper.isLocationInArea(listOf(1), 500.0), "Should NOT be in area 1")
    assertFalse(LocationHelper.isLocationInArea(listOf(2), 500.0), "Should NOT be in area 2")
  }

  @Test
  fun testPerformance() = runTest {
    // Create a complex polygon with 100 points
    val complexPolygon = (0 until 100).map { i ->
      val angle = 2 * PI * i / 100
      GeoPoint(32.0 + 0.1 * sin(angle), 34.0 + 0.1 * cos(angle))
    }
    
    val jsonBuilder = StringBuilder("{ \"999\": [")
    complexPolygon.forEachIndexed { index, point ->
      jsonBuilder.append("[${point.latitude}, ${point.longitude}]")
      if (index < complexPolygon.size - 1) jsonBuilder.append(",")
    }
    jsonBuilder.append("] }")
    
    LocationHelper.initFromRawData(jsonBuilder.toString())
    
    // User location just outside the polygon to force distance calculations
    val userLoc = GeoPoint(32.2, 34.2)
    LocationHelper.currentLocationProvider = { userLoc }

    val iterations = 1000
    val time = measureTime {
      repeat(iterations) {
        LocationHelper.isLocationInArea(listOf(999))
      }
    }

    println("Total time for $iterations checks: $time")
    println("Average time per check: ${time.toDouble(DurationUnit.MILLISECONDS) / iterations} ms")
  }

  @Test
  fun testIsLocationInArea_NullLocation() = runTest {
    LocationHelper.currentLocationProvider = { null }
    assertFalse(LocationHelper.isLocationInArea(listOf(1, 2)), "Should return false if location is unknown")
  }

  @Test
  fun testContainsLocation() {
    val polygon = listOf(
      GeoPoint(0.0, 0.0),
      GeoPoint(0.0, 10.0),
      GeoPoint(10.0, 10.0),
      GeoPoint(10.0, 0.0)
    )

    // Point inside
    assertTrue(LocationHelper.containsLocation(GeoPoint(5.0, 5.0), polygon))

    // Point outside
    assertFalse(LocationHelper.containsLocation(GeoPoint(15.0, 5.0), polygon))
  }
}
