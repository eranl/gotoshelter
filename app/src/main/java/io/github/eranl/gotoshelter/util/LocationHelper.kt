package io.github.eranl.gotoshelter.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.github.eranl.gotoshelter.R
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object LocationHelper {
  private val TAG = LocationHelper.javaClass.simpleName

  // Map of Area ID to its polygon (list of coordinates)
  private val areaPolygons = mutableMapOf<Int, List<LatLng>>()

  /**
   * Loads the polygons from the raw resource polygons.json on startup.
   */
  fun init(context: Context) {
    try {
      val inputStream = context.resources.openRawResource(R.raw.polygons)
      val jsonString = inputStream.bufferedReader().use { it.readText() }
      val jsonObject = JSONObject(jsonString)

      val keys = jsonObject.keys()
      var count = 0
      while (keys.hasNext()) {
        val key = keys.next()
        val id = key.toIntOrNull() ?: continue
        val polygonArray = jsonObject.getJSONArray(key)
        val points = mutableListOf<LatLng>()

        for (i in 0 until polygonArray.length()) {
          val coordPair = polygonArray.getJSONArray(i)
          // GeoJSON/Standard often uses [lng, lat], but Tzofar polygons usually [lat, lng]
          // Based on the snippet provided: [29.5813, 34.9746] -> lat is ~29.5, lng is ~34.9 (Israel)
          points.add(LatLng(coordPair.getDouble(0), coordPair.getDouble(1)))
        }
        areaPolygons[id] = points
        count++
      }
      Log.d(TAG, "Successfully loaded $count area polygons into memory.")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize AlertAreaMapper: ${e.message}", e)
    }
  }

  /**
   * Checks if the current location is inside any of the polygons associated with the areaIds.
   */
  suspend fun isLocationInArea(areaIds: List<Int>, context: Context): Boolean {
    getLastKnownLocation(context)?.let {
      for (areaId in areaIds) {
        if (PolyUtil.containsLocation(it.latitude, it.longitude, areaPolygons[areaId] ?: continue, true)) {
          return true
        }
      }
    }
    return false
  }

  @SuppressLint("MissingPermission")
  suspend fun getLastKnownLocation(context: Context): Location? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    return try {
      fusedLocationClient.lastLocation.await()
    } catch (e: Exception) {
      Log.e("getLastKnownLocation", "failed", e)
      null
    }
  }
}
