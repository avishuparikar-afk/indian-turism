package com.example.data.api

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TravelMode(val label: String) {
    DRIVING("Drive"),
    TRANSIT("Train / Rail"),
    FLIGHT("Flight")
}

data class RouteStep(
    val instruction: String,
    val distanceFormatted: String,
    val durationFormatted: String,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val roadName: String = "",
    val maneuverType: String = "straight"
)

data class TravelRoute(
    val originName: String,
    val originLatLng: LatLng,
    val destinationName: String,
    val destinationLatLng: LatLng,
    val travelMode: TravelMode = TravelMode.DRIVING,
    val points: List<LatLng> = emptyList(),
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
    val distanceFormatted: String = "",
    val durationFormatted: String = "",
    val steps: List<RouteStep> = emptyList(),
    val summary: String = "",
    val highways: List<String> = emptyList()
)

class RoutingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun calculateRoute(
        origin: LatLng,
        originName: String,
        destination: LatLng,
        destinationName: String,
        travelMode: TravelMode,
        geoapifyApiKey: String = ""
    ): TravelRoute = withContext(Dispatchers.IO) {
        val directDistanceMeters = FloatArray(1)
        Location.distanceBetween(
            origin.latitude, origin.longitude,
            destination.latitude, destination.longitude,
            directDistanceMeters
        )
        val directKm = (directDistanceMeters[0] / 1000.0)

        when (travelMode) {
            TravelMode.DRIVING -> {
                // Try OSRM public routing first (free, real turn-by-turn road routing in India)
                val osrmRoute = fetchOsrmRoute(origin, originName, destination, destinationName)
                if (osrmRoute != null && osrmRoute.points.isNotEmpty()) {
                    return@withContext osrmRoute
                }

                // Try Geoapify if key is configured
                if (geoapifyApiKey.isNotBlank() && geoapifyApiKey != "MY_GEOAPIFY_API_KEY") {
                    val geoapifyRoute = fetchGeoapifyRoute(origin, originName, destination, destinationName, geoapifyApiKey)
                    if (geoapifyRoute != null && geoapifyRoute.points.isNotEmpty()) {
                        return@withContext geoapifyRoute
                    }
                }

                // High-quality Road interpolation fallback
                generateRoadFallback(origin, originName, destination, destinationName, directKm)
            }
            TravelMode.TRANSIT -> {
                generateTransitRoute(origin, originName, destination, destinationName, directKm)
            }
            TravelMode.FLIGHT -> {
                generateFlightRoute(origin, originName, destination, destinationName, directKm)
            }
        }
    }

    private fun fetchOsrmRoute(
        origin: LatLng,
        originName: String,
        destination: LatLng,
        destinationName: String
    ): TravelRoute? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "IndiaTourismExplorer/1.0 (Android App)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                Log.w("RoutingService", "OSRM returned ${response.code}: $body")
                return null
            }

            val json = JSONObject(body)
            val code = json.optString("code")
            if (code != "Ok") return null

            val routes = json.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null

            val primaryRoute = routes.getJSONObject(0)
            val geometry = primaryRoute.optJSONObject("geometry") ?: return null
            val coordinates = geometry.optJSONArray("coordinates") ?: return null

            val pointsList = mutableListOf<LatLng>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                val lon = coord.getDouble(0)
                val lat = coord.getDouble(1)
                pointsList.add(LatLng(lat, lon))
            }

            val distanceMeters = primaryRoute.optDouble("distance", 0.0)
            val durationSeconds = primaryRoute.optDouble("duration", 0.0)
            val distanceKm = distanceMeters / 1000.0
            val durationMinutes = (durationSeconds / 60.0).toInt()

            val legs = primaryRoute.optJSONArray("legs")
            val stepsList = mutableListOf<RouteStep>()
            val highwaysSet = mutableSetOf<String>()

            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        val name = step.optString("name", "").trim()
                        val stepDist = step.optDouble("distance", 0.0)
                        val stepDur = step.optDouble("duration", 0.0)
                        val maneuver = step.optJSONObject("maneuver")
                        val type = maneuver?.optString("type", "straight") ?: "straight"
                        val modifier = maneuver?.optString("modifier", "")

                        if (name.isNotBlank() && (name.contains("NH", ignoreCase = true) || name.contains("Expressway", ignoreCase = true) || name.contains("Highway", ignoreCase = true))) {
                            highwaysSet.add(name)
                        }

                        val instruction = when {
                            type == "depart" -> "Depart from $originName toward ${if (name.isNotBlank()) name else "main road"}"
                            type == "arrive" -> "Arrive at $destinationName"
                            type == "turn" && !modifier.isNullOrBlank() -> "Turn ${modifier.replace("-", " ")} onto ${if (name.isNotBlank()) name else "road"}"
                            type == "fork" -> "Keep ${modifier ?: "right"} at the fork onto ${if (name.isNotBlank()) name else "highway"}"
                            type == "merge" -> "Merge onto ${if (name.isNotBlank()) name else "expressway"}"
                            type == "roundabout" -> "Take exit at the roundabout onto ${if (name.isNotBlank()) name else "route"}"
                            else -> "Continue along ${if (name.isNotBlank()) name else "route"}"
                        }

                        stepsList.add(
                            RouteStep(
                                instruction = instruction,
                                distanceFormatted = formatDistance(stepDist / 1000.0),
                                durationFormatted = formatDuration((stepDur / 60.0).toInt()),
                                distanceMeters = stepDist,
                                durationSeconds = stepDur,
                                roadName = name,
                                maneuverType = type
                            )
                        )
                    }
                }
            }

            if (stepsList.isEmpty()) {
                stepsList.add(
                    RouteStep(
                        instruction = "Drive from $originName to $destinationName via state corridors",
                        distanceFormatted = formatDistance(distanceKm),
                        durationFormatted = formatDuration(durationMinutes)
                    )
                )
            }

            TravelRoute(
                originName = originName,
                originLatLng = origin,
                destinationName = destinationName,
                destinationLatLng = destination,
                travelMode = TravelMode.DRIVING,
                points = pointsList,
                distanceKm = distanceKm,
                durationMinutes = durationMinutes,
                distanceFormatted = formatDistance(distanceKm),
                durationFormatted = formatDuration(durationMinutes),
                steps = stepsList,
                summary = "Fastest road route via ${if (highwaysSet.isNotEmpty()) highwaysSet.take(2).joinToString(", ") else "National Corridors"}",
                highways = highwaysSet.toList()
            )
        } catch (e: Exception) {
            Log.e("RoutingService", "OSRM route calculation error", e)
            null
        }
    }

    private fun fetchGeoapifyRoute(
        origin: LatLng,
        originName: String,
        destination: LatLng,
        destinationName: String,
        apiKey: String
    ): TravelRoute? {
        return try {
            val url = "https://api.geoapify.com/v1/routing?waypoints=${origin.latitude},${origin.longitude}|${destination.latitude},${destination.longitude}&mode=drive&apiKey=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            if (!response.isSuccessful) return null

            val json = JSONObject(body)
            val features = json.optJSONArray("features") ?: return null
            if (features.length() == 0) return null

            val feature = features.getJSONObject(0)
            val geometry = feature.optJSONObject("geometry") ?: return null
            val coordinates = geometry.optJSONArray("coordinates") ?: return null

            val pointsList = mutableListOf<LatLng>()
            // Geoapify GeoJSON coordinates can be 3-level or 2-level array
            for (i in 0 until coordinates.length()) {
                val legCoords = coordinates.optJSONArray(i) ?: continue
                if (legCoords.length() > 0 && legCoords.opt(0) is Number) {
                    val lon = legCoords.getDouble(0)
                    val lat = legCoords.getDouble(1)
                    pointsList.add(LatLng(lat, lon))
                } else {
                    for (j in 0 until legCoords.length()) {
                        val pt = legCoords.optJSONArray(j) ?: continue
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        pointsList.add(LatLng(lat, lon))
                    }
                }
            }

            val properties = feature.optJSONObject("properties") ?: return null
            val distanceMeters = properties.optDouble("distance", 0.0)
            val durationSeconds = properties.optDouble("time", 0.0)
            val distanceKm = distanceMeters / 1000.0
            val durationMinutes = (durationSeconds / 60.0).toInt()

            TravelRoute(
                originName = originName,
                originLatLng = origin,
                destinationName = destinationName,
                destinationLatLng = destination,
                travelMode = TravelMode.DRIVING,
                points = pointsList,
                distanceKm = distanceKm,
                durationMinutes = durationMinutes,
                distanceFormatted = formatDistance(distanceKm),
                durationFormatted = formatDuration(durationMinutes),
                summary = "Geoapify Highway Driving Navigation",
                steps = listOf(
                    RouteStep("Depart $originName", formatDistance(0.0), "0 min"),
                    RouteStep("Proceed along national highway towards $destinationName", formatDistance(distanceKm), formatDuration(durationMinutes)),
                    RouteStep("Arrive at $destinationName", formatDistance(0.0), "0 min")
                )
            )
        } catch (e: Exception) {
            Log.e("RoutingService", "Geoapify routing error", e)
            null
        }
    }

    private fun generateRoadFallback(
        origin: LatLng,
        originName: String,
        destination: LatLng,
        destinationName: String,
        directKm: Double
    ): TravelRoute {
        // Road routes in India typically measure ~1.22x direct straight line
        val roadKm = (directKm * 1.22).coerceAtLeast(2.0)
        val averageSpeedKmh = 65.0
        val durationMin = ((roadKm / averageSpeedKmh) * 60).toInt().coerceAtLeast(10)

        // Interpolate points with gentle realistic highway curvature
        val points = interpolatePoints(origin, destination, segments = 25, jitterFactor = 0.04)

        val steps = listOf(
            RouteStep("Depart $originName onto connecting arterial road", "2.5 km", "8 mins", maneuverType = "depart"),
            RouteStep("Merge onto National Highway corridor (NH / Expressway)", formatDistance(roadKm * 0.75), formatDuration((durationMin * 0.75).toInt()), maneuverType = "merge"),
            RouteStep("Pass toll plazas (FASTag lanes enabled)", "Toll", "2 mins", maneuverType = "straight"),
            RouteStep("Take exit toward $destinationName city center", formatDistance(roadKm * 0.2), formatDuration((durationMin * 0.2).toInt()), maneuverType = "turn"),
            RouteStep("Arrive at $destinationName entrance gate", "0.5 km", "2 mins", maneuverType = "arrive")
        )

        return TravelRoute(
            originName = originName,
            originLatLng = origin,
            destinationName = destinationName,
            destinationLatLng = destination,
            travelMode = TravelMode.DRIVING,
            points = points,
            distanceKm = roadKm,
            durationMinutes = durationMin,
            distanceFormatted = formatDistance(roadKm),
            durationFormatted = formatDuration(durationMin),
            steps = steps,
            summary = "Direct Highway Corridor (NH Route)",
            highways = listOf("National Highway", "Expressway")
        )
    }

    private fun generateTransitRoute(
        origin: LatLng,
        originName: String,
        destination: LatLng,
        destinationName: String,
        directKm: Double
    ): TravelRoute {
        val railKm = (directKm * 1.15).coerceAtLeast(5.0)
        val avgRailSpeed = 82.0 // Vande Bharat / Superfast average
        val durationMin = ((railKm / avgRailSpeed) * 60).toInt() + 45 // include buffer

        val points = interpolatePoints(origin, destination, segments = 30, jitterFactor = 0.02)

        val steps = listOf(
            RouteStep("Board connecting local transit / metro to central rail terminal at $originName", "5 km", "25 mins"),
            RouteStep("Board Vande Bharat / Superfast Express train towards $destinationName region", formatDistance(railKm), formatDuration(durationMin - 35)),
            RouteStep("Arrive at destination railway junction and take cab/auto-rickshaw to $destinationName", "3 km", "10 mins")
        )

        return TravelRoute(
            originName = originName,
            originLatLng = origin,
            destinationName = destinationName,
            destinationLatLng = destination,
            travelMode = TravelMode.TRANSIT,
            points = points,
            distanceKm = railKm,
            durationMinutes = durationMin,
            distanceFormatted = formatDistance(railKm),
            durationFormatted = formatDuration(durationMin),
            steps = steps,
            summary = "IRCTC Indian Railways Rail Connectivity",
            highways = listOf("Indian Railways Mainline")
        )
    }

    private fun generateFlightRoute(
        origin: LatLng,
        originName: String,
        destination: LatLng,
        destinationName: String,
        directKm: Double
    ): TravelRoute {
        val flightSpeedKmh = 720.0
        val airDurationMin = ((directKm / flightSpeedKmh) * 60).toInt() + 90 // check-in + transit buffer

        // High parabolic arc for flight
        val points = interpolateGreatCircleArc(origin, destination, segments = 35)

        val steps = listOf(
            RouteStep("Check in at nearest international / domestic airport", "Airport Terminal", "60 mins"),
            RouteStep("Direct domestic flight cruising at 32,000 ft", formatDistance(directKm), formatDuration(((directKm / flightSpeedKmh) * 60).toInt())),
            RouteStep("Baggage collection and airport express taxi to $destinationName", "15 km", "30 mins")
        )

        return TravelRoute(
            originName = originName,
            originLatLng = origin,
            destinationName = destinationName,
            destinationLatLng = destination,
            travelMode = TravelMode.FLIGHT,
            points = points,
            distanceKm = directKm,
            durationMinutes = airDurationMin,
            distanceFormatted = formatDistance(directKm),
            durationFormatted = formatDuration(airDurationMin),
            steps = steps,
            summary = "Air Travel via Domestic Airline Corridor"
        )
    }

    private fun interpolatePoints(
        start: LatLng,
        end: LatLng,
        segments: Int,
        jitterFactor: Double
    ): List<LatLng> {
        val list = mutableListOf<LatLng>()
        val dLat = end.latitude - start.latitude
        val dLng = end.longitude - start.longitude

        // Perpendicular vector for realistic road winding
        val perpLat = -dLng * jitterFactor
        val perpLng = dLat * jitterFactor

        for (i in 0..segments) {
            val fraction = i.toDouble() / segments
            val baseLat = start.latitude + (dLat * fraction)
            val baseLng = start.longitude + (dLng * fraction)

            // Sine curve displacement
            val curve = sin(fraction * Math.PI)
            val offsetFactor = curve * (if ((i % 2) == 0) 1.0 else -0.7)

            val lat = baseLat + (perpLat * offsetFactor)
            val lng = baseLng + (perpLng * offsetFactor)
            list.add(LatLng(lat, lng))
        }
        return list
    }

    private fun interpolateGreatCircleArc(start: LatLng, end: LatLng, segments: Int): List<LatLng> {
        val list = mutableListOf<LatLng>()
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1
        val bX = cos(lat2) * cos(dLon)
        val bY = cos(lat2) * sin(dLon)

        val d = 2 * kotlin.math.asin(
            sqrt(
                sin((lat2 - lat1) / 2) * sin((lat2 - lat1) / 2) +
                        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            )
        )

        if (d < 1e-6) {
            return listOf(start, end)
        }

        for (i in 0..segments) {
            val f = i.toDouble() / segments
            val a = sin((1 - f) * d) / sin(d)
            val b = sin(f * d) / sin(d)

            val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
            val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
            val z = a * sin(lat1) + b * sin(lat2)

            val lat = atan2(z, sqrt(x * x + y * y))
            val lon = atan2(y, x)

            list.add(LatLng(Math.toDegrees(lat), Math.toDegrees(lon)))
        }
        return list
    }

    private fun formatDistance(km: Double): String {
        return if (km < 1.0) {
            "${(km * 1000).toInt()} m"
        } else {
            String.format("%.1f km", km)
        }
    }

    private fun formatDuration(minutes: Int): String {
        return if (minutes < 60) {
            "$minutes mins"
        } else {
            val hours = minutes / 60
            val remMin = minutes % 60
            if (remMin == 0) "$hours hr" else "${hours}h ${remMin}m"
        }
    }
}
