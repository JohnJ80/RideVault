package com.example.ridevault.rwgps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Ride with GPS API client.
 * Requires a valid Bearer token from RwgpsAuth.
 */
object RwgpsApi {

    private const val BASE = "https://ridewithgps.com/api/v1"
    private const val WEB_BASE = "https://ridewithgps.com"
    private val http = OkHttpClient()

    data class CollectionSummary(
        val id: Int,
        val name: String,
        val routeCount: Int = 0,
        val tripCount: Int = 0
    )

    data class RouteSummary(
        val id: Int,
        val name: String,
        val distanceMeters: Double = 0.0,
        val elevationGainMeters: Double = 0.0
    )

    /**
     * Result of downloading a route file from RWGPS.
     */
    data class RouteDownload(
        val routeId: Int,
        val fileName: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RouteDownload) return false
            return routeId == other.routeId
        }

        override fun hashCode(): Int = routeId
    }

    // ── Collections ──────────────────────────────────────────

    suspend fun listCollections(accessToken: String): List<CollectionSummary> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE/collections.json")
                .header("Authorization", "Bearer $accessToken")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                val array = json.optJSONArray("collections") ?: return@withContext emptyList()

                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        add(
                            CollectionSummary(
                                id = obj.optInt("id"),
                                name = obj.optString("name", "Unnamed"),
                                routeCount = obj.optInt("route_count", 0),
                                tripCount = obj.optInt("trip_count", 0)
                            )
                        )
                    }
                }
            }
        }

    suspend fun getPinnedCollection(accessToken: String): CollectionSummary? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE/collections/pinned.json")
                .header("Authorization", "Bearer $accessToken")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val obj = json.optJSONObject("collection") ?: return@withContext null

                CollectionSummary(
                    id = obj.optInt("id"),
                    name = obj.optString("name", "Pinned"),
                    routeCount = obj.optInt("route_count", 0),
                    tripCount = obj.optInt("trip_count", 0)
                )
            }
        }

    // ── Routes in a collection ───────────────────────────────

    /**
     * List routes in a given collection.
     * The RWGPS API returns collection details including embedded route objects.
     */
    suspend fun listCollectionRoutes(
        accessToken: String,
        collectionId: Int
    ): Pair<String, List<RouteSummary>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE/collections/$collectionId.json")
                .header("Authorization", "Bearer $accessToken")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "HTTP ${response.code}" to emptyList()
                }

                val body = response.body?.string()
                    ?: return@withContext "Empty response" to emptyList()
                val json = JSONObject(body)

                // Try collection.routes first, then top-level routes
                val collObj = json.optJSONObject("collection")
                val routesArray = collObj?.optJSONArray("routes")
                    ?: json.optJSONArray("routes")
                    ?: return@withContext "OK" to emptyList()

                val routes = buildList {
                    for (i in 0 until routesArray.length()) {
                        val r = routesArray.getJSONObject(i)
                        add(
                            RouteSummary(
                                id = r.optInt("id"),
                                name = r.optString("name", "Unnamed route"),
                                distanceMeters = r.optDouble("distance", 0.0),
                                elevationGainMeters = r.optDouble("elevation_gain", 0.0)
                            )
                        )
                    }
                }
                "OK" to routes
            }
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}" to emptyList()
        }
    }

    // ── Route file download ──────────────────────────────────

    /**
     * Download a route as a GPX file.
     *
     * Fetches the route JSON with track_points from the API,
     * then builds a GPX file from the coordinates. The Edge 1050
     * loads GPX files placed in Garmin/Courses as courses.
     */
    suspend fun downloadRouteGpx(
        accessToken: String,
        route: RouteSummary
    ): Pair<String, RouteDownload?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE/routes/${route.id}.json")
                .header("Authorization", "Bearer $accessToken")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext (
                        "HTTP ${response.code}: ${response.body?.string()?.take(200) ?: "no body"}"
                    ) to null
                }

                val body = response.body?.string()
                    ?: return@withContext "Empty response body" to null
                val json = JSONObject(body)

                // The route object may be at top level or under "route"
                val routeObj = json.optJSONObject("route") ?: json

                val trackPoints = routeObj.optJSONArray("track_points")
                if (trackPoints == null || trackPoints.length() == 0) {
                    return@withContext "No track points in route" to null
                }

                // Also grab course_points for turn-by-turn cues
                val coursePoints = routeObj.optJSONArray("course_points")

                val gpx = buildGpx(route.name, trackPoints, coursePoints)
                val bytes = gpx.toByteArray(Charsets.UTF_8)

                val safeName = route.name
                    .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                    .trim()
                    .take(80)
                val fileName = "$safeName.gpx"

                "OK" to RouteDownload(
                    routeId = route.id,
                    fileName = fileName,
                    data = bytes
                )
            }
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}" to null
        }
    }

    private fun buildGpx(
        name: String,
        trackPoints: org.json.JSONArray,
        coursePoints: org.json.JSONArray?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="RideVault"
  xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""")

        // Metadata
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(name)}</name>")
        sb.appendLine("  </metadata>")

        // Route waypoints from course_points (turn-by-turn cues)
        if (coursePoints != null && coursePoints.length() > 0) {
            for (i in 0 until coursePoints.length()) {
                val cp = coursePoints.getJSONObject(i)
                val lat = cp.optDouble("lat", Double.NaN)
                val lng = cp.optDouble("lng", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue

                val cpName = cp.optString("name", "")
                val cpType = cp.optString("type", "")
                // Map RWGPS cue types to Garmin-recognized types
                val garminType = mapCueType(cpType)

                sb.appendLine("""  <wpt lat="$lat" lon="$lng">""")
                if (cpName.isNotBlank()) {
                    sb.appendLine("    <name>${escapeXml(cpName)}</name>")
                }
                if (garminType.isNotBlank()) {
                    sb.appendLine("    <type>$garminType</type>")
                }
                sb.appendLine("  </wpt>")
            }
        }

        // Track with trackpoints
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(name)}</name>")
        sb.appendLine("    <trkseg>")

        for (i in 0 until trackPoints.length()) {
            val pt = trackPoints.getJSONObject(i)
            val lat = pt.optDouble("y", Double.NaN)
            val lng = pt.optDouble("x", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue

            val ele = pt.optDouble("e", Double.NaN)
            sb.append("""      <trkpt lat="$lat" lon="$lng">""")
            if (!ele.isNaN()) {
                sb.append("<ele>$ele</ele>")
            }
            sb.appendLine("</trkpt>")
        }

        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private fun mapCueType(rwgpsType: String): String {
        return when (rwgpsType.lowercase()) {
            "left" -> "Left"
            "right" -> "Right"
            "straight" -> "Straight"
            "sharp left" -> "Sharp Left"
            "sharp right" -> "Sharp Right"
            "slight left" -> "Slight Left"
            "slight right" -> "Slight Right"
            "u-turn" -> "U-Turn"
            "summit" -> "Summit"
            "valley" -> "Valley"
            "water" -> "Water"
            "food" -> "Food"
            "danger" -> "Danger"
            "generic" -> "Generic"
            "first aid" -> "First Aid"
            else -> "Generic"
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
