package com.example.ridevault.rwgps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Minimal Ride with GPS API client for collections.
 * Requires a valid Bearer token from RwgpsAuth.
 */
object RwgpsApi {

    private const val BASE = "https://ridewithgps.com/api/v1"
    private val http = OkHttpClient()

    data class CollectionSummary(
        val id: Int,
        val name: String,
        val routeCount: Int = 0,
        val tripCount: Int = 0
    )

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
}
