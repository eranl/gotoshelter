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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // Mock location outside both
    LocationHelper.currentLocationProvider = { GeoPoint(15.0, 15.0) }
    assertFalse(LocationHelper.isLocationInArea(listOf(1)), "Should NOT be in area 1")
    assertFalse(LocationHelper.isLocationInArea(listOf(2)), "Should NOT be in area 2")
    assertFalse(LocationHelper.isLocationInArea(listOf(1, 2)), "Should NOT be in any area")

    // Mock location in area 2
    LocationHelper.currentLocationProvider = { GeoPoint(25.0, 25.0) }
    assertFalse(LocationHelper.isLocationInArea(listOf(1)), "Should NOT be in area 1")
    assertTrue(LocationHelper.isLocationInArea(listOf(2)), "Should be in area 2")
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

    // Point on edge
    assertFalse(LocationHelper.containsLocation(GeoPoint(-1.0, -1.0), polygon))
  }
}
