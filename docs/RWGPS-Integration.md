# Ride with GPS Integration Notes

## OAuth Setup (one-time, developer only)

1. Log into Ride with GPS → API clients page: https://ridewithgps.com/api
2. Create an API client.
3. Add a redirect URI: `ridevault://oauth/callback`
4. Copy the `client_id` and `client_secret`.
5. They are already in `RwgpsAuth.kt`.

End users never need a developer account. They just log in and authorize the app.

## Current Scaffold (sprint-1)

- `rwgps/RwgpsAuth.kt` — OAuth authorization intent + code-for-token exchange + encrypted token storage.
- `rwgps/RwgpsApi.kt` — list collections + get pinned collection.
- `rwgps/RwgpsLoginButton.kt` — simple Login / Log out button + `handleRwgpsRedirect()` helper.
- `AndroidManifest.xml` — intent-filter for `ridevault://oauth/callback`.
- Dependencies: OkHttp + `androidx.security:security-crypto`.

## Wiring the login button

In your `RideVaultHome` (or wherever you want it):

```kotlin
RwgpsLoginSection()
```

In `MainActivity`, add (or extend) `onNewIntent`:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    lifecycleScope.launch {
        if (handleRwgpsRedirect(this@MainActivity, intent)) {
            // refresh UI / show success
        }
    }
}
```

Also call the same helper from `onCreate` if the activity was cold-started by the redirect.

## Next Steps

- Wire the button into the existing UI.
- List collections after login.
- Download route files for a selected collection (FIT Course or TCX Course preferred for Edge 1050).
- Push the downloaded files into the Garmin Courses folder over MTP (reuse existing MTP copy engine).

## Download Formats

Preferred for Edge 1050: FIT Course or TCX Course.
Exact download endpoint still to be confirmed. Once confirmed, add a `downloadRouteFile` method.
