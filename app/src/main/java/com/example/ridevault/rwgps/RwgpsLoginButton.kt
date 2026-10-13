package com.example.ridevault.rwgps

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
 * Minimal login / status UI for Ride with GPS.
 * Drop this into RideVaultHome (or any screen) when ready.
 */
@Composable
fun RwgpsLoginSection(
    loggedIn: Boolean,
    status: String = "",
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = if (loggedIn) "Ride with GPS: logged in"
                   else "Ride with GPS: not logged in"
        )
        if (status.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = status)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (!loggedIn) {
            Button(
                onClick = {
                    val intent = RwgpsAuth.getAuthorizationIntent()
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login with Ride with GPS")
            }
        } else {
            Button(
                onClick = { onLogout() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out of Ride with GPS")
            }
        }
    }
}

/**
 * Call this from MainActivity.onNewIntent (or onCreate) when the
 * ridevault://oauth/callback redirect arrives.
 */
suspend fun handleRwgpsRedirect(context: Context, intent: Intent?): Pair<Boolean, String> {
    val data: Uri = intent?.data ?: return false to "No intent data"
    if (data.scheme != "ridevault" || data.host != "oauth") return false to "Not an OAuth redirect"

    val code = data.getQueryParameter("code") ?: return false to "No code parameter"
    return try {
        RwgpsAuth.exchangeCodeForToken(context, code)
    } catch (e: Exception) {
        false to "${e.javaClass.simpleName}: ${e.message}"
    }
}
