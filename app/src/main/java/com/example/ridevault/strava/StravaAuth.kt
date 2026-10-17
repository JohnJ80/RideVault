package com.example.ridevault.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Strava OAuth helper.
 *
 * Uses OAuth2 authorization code flow.
 * Tokens are stored in encrypted shared preferences.
 * Handles refresh automatically when access token expires.
 */
object StravaAuth {

    private const val CLIENT_ID = "287312"
    private const val CLIENT_SECRET = "26655914d29ef12406290fde1d4a9de8a3c71ca3"
    private const val REDIRECT_URI = "ridevault://strava-auth/callback"

    // Auth goes through the Cloudflare Worker relay so both RideVault apps
    // share the same Strava Authorization Callback Domain.
    private const val AUTH_URL = "https://ridevault-auth.ridevaultkaroo.workers.dev/auth/ridevault"
    private const val TOKEN_URL = "https://www.strava.com/oauth/token"

    private const val PREFS_NAME = "strava_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_ATHLETE_NAME = "athlete_name"

    private val http = OkHttpClient()

    fun getAuthorizationIntent(): Intent {
        // The worker relay handles the Strava redirect parameters — just open it.
        return Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_URL))
    }

    /**
     * Exchange the authorization code for tokens.
     * Returns success boolean + diagnostic message.
     */
    suspend fun exchangeCodeForToken(
        context: Context,
        code: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext false to
                        "HTTP ${response.code}: ${response.body?.string()?.take(200) ?: "no body"}"
                }

                val responseBody = response.body?.string()
                    ?: return@withContext false to "Empty response body"

                val json = JSONObject(responseBody)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresAt = json.optLong("expires_at", 0)

                if (accessToken.isBlank()) {
                    return@withContext false to
                        "No access_token in response: ${responseBody.take(200)}"
                }

                // Extract athlete name if available
                val athlete = json.optJSONObject("athlete")
                val athleteName = if (athlete != null) {
                    val first = athlete.optString("firstname", "")
                    val last = athlete.optString("lastname", "")
                    "$first $last".trim()
                } else ""

                saveTokens(context, accessToken, refreshToken, expiresAt, athleteName)
                true to "OK"
            }
        } catch (e: Exception) {
            false to "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Get a valid access token, refreshing if expired.
     */
    suspend fun getValidAccessToken(context: Context): String? {
        val prefs = prefs(context)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            ?: return accessToken
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)

        // If token expires in the next 60 seconds, refresh it
        val now = System.currentTimeMillis() / 1000
        if (now < expiresAt - 60) {
            return accessToken
        }

        // Refresh the token
        return refreshAccessToken(context, refreshToken)
    }

    private suspend fun refreshAccessToken(
        context: Context,
        refreshToken: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val responseBody = response.body?.string()
                    ?: return@withContext null
                val json = JSONObject(responseBody)

                val newAccessToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val newExpiresAt = json.optLong("expires_at", 0)

                if (newAccessToken.isBlank()) return@withContext null

                val athleteName = prefs(context).getString(KEY_ATHLETE_NAME, "") ?: ""
                saveTokens(context, newAccessToken, newRefreshToken, newExpiresAt, athleteName)
                newAccessToken
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isLoggedIn(context: Context): Boolean {
        return !prefs(context).getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

    fun getAthleteName(context: Context): String {
        return prefs(context).getString(KEY_ATHLETE_NAME, "") ?: ""
    }

    fun clearToken(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun saveTokens(
        context: Context,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        athleteName: String
    ) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_ATHLETE_NAME, athleteName)
            .apply()
    }

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
