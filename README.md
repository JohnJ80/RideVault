# RideVault

Garmin Edge backup, recovery, and storage management for Android.

## Norway MVP

### Activities

- Detect and connect to a Garmin Edge 1050 over USB/MTP.
- Enumerate FIT files from `Garmin/Activities`.
- Display activities newest first using:
  - Timestamp as `YYYY-MM-DD HH:MM:SS`
  - File size on the same line
- Keep the full `.fit` filename hidden from the main list.
- On activity selection, offer:
  - Download
  - Download this activity and all newer activities
  - Display filename
- Show the complete original filename when requested so it can be verified on the Moto or Mac.

### Activity cleanup

- Select an activity as a chronological boundary.
- Keep the selected activity and all newer activities.
- Delete all older activities.
- Before deletion, preview:
  - Number kept
  - Number deleted
  - Oldest and newest dates being deleted
  - Estimated storage reclaimed
- Require explicit confirmation.

### Courses

- List courses.
- Back up one course.
- Back up all courses.
- Delete one course.
- Delete all courses.
- Require confirmation for destructive actions.

## Deferred until after Norway

- Map management
- FIT parsing for distance, duration, elevation, or ride titles
- Cloud sync
- Multi-device support
- Nonessential UI polish

## Design principles

- Norway first.
- Perfect is the enemy of good.
- Prefer small, working, testable increments.
- Keep the repository recoverable.
- Do not expose USB or MTP details in the normal UI.
- Activities are chronological; courses are simple lists.
- Preserve original filenames when copying.
- Preview destructive operations.
- Verify copied files before reporting success.

## Current status

- USB detection and permission: working
- MTP session with retries: working
- Garmin storage traversal: working
- FIT enumeration and metadata: working
- Activity list UI: next
- Single-file download: next after the list
- Range download and activity cleanup: after single-file download
