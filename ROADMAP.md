# RideVault Roadmap

## Platform Bring-up ✅
- USB detection
- USB permission
- USB connection
- MTP session open
- USB interface inspector

## Norway MVP
- List activities
- Copy activities to Moto
- Share copied activities
- List courses
- Back up courses to Moto
- Delete selected courses
- Delete all courses with confirmation
- Inspect map files and storage use

## Next Priorities (in order)
1. **Grok + Cursor workflow on Mac** — Establish reliable AI-assisted development flow (Grok as primary interface managing GitHub; Cursor + Android Studio for local build/test).
2. **Ride with GPS collection → Garmin** — OAuth to any user’s RWGPS account, download an entire collection, and push the route files (FIT/TCX Course) onto an Edge 1050 (or other modern Garmin bike computer) over MTP. Replaces cumbersome pin/unpin for trip planning.
3. **Single-ride Strava upload** — Occasional recovery tool: take one selected activity FIT (already downloaded via MTP) and upload it directly to the user’s Strava account via the API. One-at-a-time only; no bulk.

## Future
- Recovery tools
- Save/share diagnostic logs
- Additional Garmin folders
- Additional device profiles
