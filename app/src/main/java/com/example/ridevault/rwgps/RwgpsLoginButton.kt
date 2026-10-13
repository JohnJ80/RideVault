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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Minimal login / status UI for Ride with GPS.
 * Drop this into RideVaultHome (or any screen) when ready.
 */
@Composable
fun RwgpsLoginSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(loginStatus(context)) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = status)
        Spacer(modifier = Modifier.height(8.dp))

        if (!RwgpsAuth.isLoggedIn(context)) {
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
                onClick = {
                    RwgpsAuth.clearToken(context)
                    status = loginStatus(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log out of Ride with GPS")
            }
        }
    }
}

private fun loginStatus(context: Context): String {
    return if (RwgpsAuth.isLoggedIn(context)) {
        "Ride with GPS: logged in"
    } else {
        "Ride with GPS: not logged in"
    }
}

/**
 * Call this from MainActivity.onNewIntent (or onCreate) when the
 * ridevault://oauth/callback redirect arrives.
 */
suspend fun handleRwgpsRedirect(context: Context, intent: Intent?): Boolean {
    val data: Uri = intent?.data ?: return false
    if (data.scheme != "ridevault" || data.host != "oauth") return false

    val code = data.getQueryParameter("code") ?: return false
    return try {
        RwgpsAuth.exchangeCodeForToken(context, code)
    } catch (_: Exception) {
        false
    }
}
