# RideVault

Garmin Edge backup, recovery, and storage management for Android — with Strava and Ride with GPS integration.

## Features

### Activities

- Detect and connect to a Garmin Edge 1050 over USB/MTP.
- Enumerate FIT files from `Garmin/Activities`.
- Display activities newest first with filename and file size.
- On activity selection, offer:
  - **Download** — copy the .FIT file to Downloads/RideVault/Activities/
  - **Download this + newer** — batch download from the selected activity forward
  - **Send to Strava** — upload the .FIT file directly to Strava (requires Strava login)
  - **Delete this + older** — remove the selected activity and everything before it

### Activity cleanup

- Select an activity as a chronological boundary.
- Delete all older activities from the Garmin.
- Require explicit confirmation before deletion.

### Courses

- List courses from `Garmin/Courses`.
- Back up one course or all courses to Downloads/RideVault/Courses/.
- Delete one course or all courses from the Garmin.
- Require confirmation for destructive actions.

### Strava integration

- OAuth2 login with automatic token refresh.
- Upload .FIT activity files directly from the Garmin to Strava.
- Duplicate activity detection.
- Upload progress and processing status feedback.
- Success/failure confirmation dialog.

### Ride with GPS integration

- OAuth2 login.
- Browse RWGPS collections and routes.
- Download routes as GPX and save to Downloads/RideVault/RWGPS-Routes/.
- Send individual routes or entire collections.

## Technical notes

- Garmin Edge 1050 blocks MTP writes from Android phones — course/route files are saved to Downloads instead and can be copied to the Garmin via a file manager.
- MTP reads work reliably with retry logic for transient failures.
- OAuth tokens stored in EncryptedSharedPreferences.
- Downloads use MediaStore (no special permissions needed on Android 10+).
- File integrity verified by size comparison after MTP transfer.

## Design principles

- Prefer small, working, testable increments.
- Keep the repository recoverable.
- Do not expose USB or MTP details in the normal UI.
- Activities are chronological; courses are simple lists.
- Preserve original filenames when copying.
- Preview destructive operations.
- Verify copied files before reporting success.

## Current version

0.0.40
