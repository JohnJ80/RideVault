package com.example.ridevault.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Strava login / status UI.
 * Drop this into RideVaultHome alongside the RWGPS login section.
 */
@Composable
fun StravaLoginSection(
    loggedIn: Boolean,
    athleteName: String = "",
    status: String = "",
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = if (loggedIn) {
                if (athleteName.isNotBlank()) {
                    "Strava: $athleteName"
                } else {
                    "Strava: logged in"
                }
            } else {
                "Strava: not logged in"
            }
        )
        if (status.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = status)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (!loggedIn) {
            Button(
                onClick = {
                    val intent = StravaAuth.getAuthorizationIntent()
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login with Strava")
            }
        } else {
            Button(
                onClick = { onLogout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out of Strava")
            }
        }
    }
}

/**
 * Call this from MainActivity.onNewIntent (or onCreate) when the
 * ridevault://strava/callback redirect arrives.
 */
suspend fun handleStravaRedirect(context: Context, intent: Intent?): Pair<Boolean, String> {
    val data: Uri = intent?.data ?: return false to "No intent data"
    if (data.scheme != "ridevault" || data.host != "strava") return false to "Not a Strava redirect"

    val code = data.getQueryParameter("code") ?: return false to "No code parameter"
    return try {
        StravaAuth.exchangeCodeForToken(context, code)
    } catch (e: Exception) {
        false to "${e.javaClass.simpleName}: ${e.message}"
    }
}
