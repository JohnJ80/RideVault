# Ride with GPS Integration Notes

## OAuth Setup (one-time, developer only)

1. Log into Ride with GPS → API clients page: https://ridewithgps.com/api
2. Create an API client.
3. Add a redirect URI: `ridevault://oauth/callback`
4. Copy the `client_id` and `client_secret`.
5. Paste them into `RwgpsAuth.kt` (CLIENT_ID / CLIENT_SECRET).

End users never need a developer account. They just log in and authorize the app.

## Current Scaffold (sprint-1)

- `rwgps/RwgpsAuth.kt` — OAuth authorization intent + code-for-token exchange + encrypted token storage.
- `rwgps/RwgpsApi.kt` — list collections + get pinned collection.
- AndroidManifest will need an intent-filter for the redirect URI (to be added when wiring the UI).

## Next Steps

- Wire a “Login with Ride with GPS” button and handle the redirect.
- List collections in the UI.
- Download route files for a selected collection (FIT Course or TCX Course preferred for Edge 1050).
- Push the downloaded files into the Garmin Courses folder over MTP (reuse existing MTP copy engine).

## Download Formats

Preferred for Edge 1050: FIT Course or TCX Course.
Exact download endpoint still to be confirmed (legacy `/routes/{id}.tcx` or newer v1 support). Once confirmed, add a `downloadRouteFile` method.
