package com.example.ridevault.rwgps

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
 * Ride with GPS OAuth helper.
 *
 * Uses the OAuth client ID and secret (not the API key).
 * End users authorize with their own accounts — no developer account required.
 */
object RwgpsAuth {

    private const val CLIENT_ID = "QeVg8vPQvcMfb75rgTYf4e5E_nT-F4phQILYvDoWq54"
    private const val CLIENT_SECRET = "eUeklKMEbaEX0qrMS0yQ6NfB2AB2FaMIFOs5fjfA6xc"
    private const val REDIRECT_URI = "ridevault://oauth/callback"

    private const val AUTH_URL = "https://ridewithgps.com/oauth/authorize"
    private const val TOKEN_URL = "https://ridewithgps.com/oauth/token.json"

    private const val PREFS_NAME = "rwgps_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"

    private val http = OkHttpClient()

    fun getAuthorizationIntent(): Intent {
        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .build()

        return Intent(Intent.ACTION_VIEW, uri)
    }

    suspend fun exchangeCodeForToken(context: Context, code: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false

                val json = JSONObject(response.body?.string() ?: return@withContext false)
                val token = json.optString("access_token")
                val userId = json.optInt("user_id", -1)

                if (token.isBlank()) return@withContext false

                saveToken(context, token, userId)
                true
            }
        }

    fun getAccessToken(context: Context): String? {
        return prefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getAccessToken(context).isNullOrBlank()
    }

    fun clearToken(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun saveToken(context: Context, token: String, userId: Int) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
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
