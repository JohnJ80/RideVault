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
     * Download a route as a FIT Course file.
     *
     * Uses the web download URL pattern: /routes/{id}.fit_course
     * with Bearer auth. Falls back to trying the .json endpoint
     * and returning track_points if the file download fails.
     */
    suspend fun downloadRouteFitCourse(
        accessToken: String,
        route: RouteSummary
    ): Pair<String, RouteDownload?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$WEB_BASE/routes/${route.id}.fit_course")
                .header("Authorization", "Bearer $accessToken")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext (
                        "HTTP ${response.code}: ${response.body?.string()?.take(200) ?: "no body"}"
                    ) to null
                }

                val bytes = response.body?.bytes()
                    ?: return@withContext "Empty response body" to null

                if (bytes.size < 12) {
                    return@withContext "Downloaded file too small (${bytes.size} bytes)" to null
                }

                // Sanitize route name for filename
                val safeName = route.name
                    .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                    .trim()
                    .take(80)
                val fileName = "$safeName.fit"

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
}
