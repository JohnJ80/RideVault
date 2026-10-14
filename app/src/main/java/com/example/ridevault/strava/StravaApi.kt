package com.example.ridevault.strava

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Strava API client for uploading activities.
 *
 * Uploads .FIT files via POST /api/v3/uploads,
 * then polls for processing status.
 */
object StravaApi {

    private const val BASE = "https://www.strava.com/api/v3"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class UploadResult(
        val success: Boolean,
        val activityId: Long? = null,
        val message: String = ""
    )

    /**
     * Upload a .FIT file to Strava.
     *
     * @param accessToken Valid Strava access token
     * @param fileName The filename (e.g. "2024-10-13-12-00-00.fit")
     * @param fitData The raw .FIT file bytes
     * @param onStatus Callback for progress updates
     * @return UploadResult with success/failure and activity ID
     */
    suspend fun uploadFitFile(
        accessToken: String,
        fileName: String,
        fitData: ByteArray,
        onStatus: (String) -> Unit = {}
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            onStatus("Uploading $fileName to Strava...")

            val fileBody = fitData.toRequestBody(
                "application/octet-stream".toMediaType()
            )

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    fileBody
                )
                .addFormDataPart("data_type", "fit")
                .addFormDataPart("activity_type", "ride")
                .build()

            val request = Request.Builder()
                .url("$BASE/uploads")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()

            val uploadResponse = http.newCall(request).execute()

            if (!uploadResponse.isSuccessful) {
                val errorBody = uploadResponse.body?.string()?.take(300) ?: "no body"
                uploadResponse.close()

                // 409 = duplicate activity
                if (uploadResponse.code == 409) {
                    return@withContext UploadResult(
                        success = false,
                        message = "Duplicate: this activity is already on Strava"
                    )
                }

                return@withContext UploadResult(
                    success = false,
                    message = "HTTP ${uploadResponse.code}: $errorBody"
                )
            }

            val responseBody = uploadResponse.body?.string()
            uploadResponse.close()

            if (responseBody == null) {
                return@withContext UploadResult(
                    success = false,
                    message = "Empty upload response"
                )
            }

            val json = JSONObject(responseBody)
            val uploadId = json.optLong("id", 0)

            if (uploadId == 0L) {
                // Check for immediate error
                val error = json.optString("error", "")
                if (error.isNotBlank()) {
                    return@withContext UploadResult(
                        success = false,
                        message = error
                    )
                }
                return@withContext UploadResult(
                    success = false,
                    message = "No upload ID returned"
                )
            }

            // Check for duplicate in the initial response
            val dupError = json.optString("error", "")
            if (dupError.contains("duplicate", ignoreCase = true)) {
                val existingId = json.optLong("activity_id", 0)
                return@withContext UploadResult(
                    success = false,
                    activityId = if (existingId > 0) existingId else null,
                    message = "Duplicate: this activity is already on Strava"
                )
            }

            // Poll for processing status
            onStatus("Processing on Strava...")
            pollUploadStatus(accessToken, uploadId, onStatus)

        } catch (e: Exception) {
            UploadResult(
                success = false,
                message = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private suspend fun pollUploadStatus(
        accessToken: String,
        uploadId: Long,
        onStatus: (String) -> Unit
    ): UploadResult {
        // Poll up to 30 times with 2-second intervals (60 seconds max)
        repeat(30) { attempt ->
            delay(2000)

            try {
                val request = Request.Builder()
                    .url("$BASE/uploads/$uploadId")
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Keep polling on transient errors
                        if (attempt < 29) return@repeat
                        return UploadResult(
                            success = false,
                            message = "Status check failed: HTTP ${response.code}"
                        )
                    }

                    val body = response.body?.string() ?: return@repeat
                    val json = JSONObject(body)

                    val status = json.optString("status", "")
                    val error = json.optString("error", "")
                    val activityId = json.optLong("activity_id", 0)

                    if (error.isNotBlank()) {
                        return UploadResult(
                            success = false,
                            activityId = if (activityId > 0) activityId else null,
                            message = error
                        )
                    }

                    if (activityId > 0) {
                        return UploadResult(
                            success = true,
                            activityId = activityId,
                            message = "Uploaded to Strava"
                        )
                    }

                    onStatus("Processing on Strava... ${status.take(40)}")
                }
            } catch (_: Exception) {
                // Keep polling on transient errors
            }
        }

        return UploadResult(
            success = false,
            message = "Upload timed out waiting for Strava to process"
        )
    }
}
