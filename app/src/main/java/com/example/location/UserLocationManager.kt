package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

data class UserLocation(
    val lat: Double,
    val lon: Double,
    val placeName: String = "現在地",
    val accuracyMeters: Float = 0f
)

class UserLocationManager(private val context: Context) {
    private val tag = "UserLocationManager"
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Default to Tokyo if location not yet fetched
    private val _userLocation = MutableStateFlow<UserLocation?>(
        UserLocation(lat = 35.6812, lon = 139.7671, placeName = "東京都千代田区（デフォルト）")
    )
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    fun updatePermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            refreshLocation()
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        if (!_hasPermission.value) return

        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        updateLocation(loc)
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) updateLocation(lastLoc)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "Failed to get current location", e)
                }
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException while accessing location", e)
        }
    }

    private fun updateLocation(loc: Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        val accuracy = loc.accuracy

        CoroutineScope(Dispatchers.IO).launch {
            var place = "現在地 (${String.format("%.2f", lat)}°, ${String.format("%.2f", lon)}°)"
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.JAPAN)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            val addr = addresses.firstOrNull()
                            if (addr != null) {
                                val pref = addr.adminArea ?: ""
                                val city = addr.locality ?: addr.subAdminArea ?: ""
                                val resolved = if (pref.isNotEmpty()) "$pref $city" else place
                                _userLocation.value = UserLocation(lat, lon, resolved, accuracy)
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        val addr = addresses?.firstOrNull()
                        if (addr != null) {
                            val pref = addr.adminArea ?: ""
                            val city = addr.locality ?: addr.subAdminArea ?: ""
                            place = if (pref.isNotEmpty()) "$pref $city" else place
                        }
                        _userLocation.value = UserLocation(lat, lon, place, accuracy)
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(tag, "Reverse geocoding failed: ${e.message}")
            }
            _userLocation.value = UserLocation(lat, lon, place, accuracy)
        }
    }

    companion object {
        fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (6371 * c).toFloat()
        }
    }
}
