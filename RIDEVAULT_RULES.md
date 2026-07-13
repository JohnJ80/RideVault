# RideVault Development Rules

## General
- Preserve existing working functionality unless explicitly requested.
- Prefer reusing existing code over creating duplicate implementations.
- Keep changes as small and localized as practical.
- Never introduce behavior changes outside the requested feature.

## Architecture
- Activities and Courses are separate domains.
- Reuse the generic MTP copy engine whenever possible.
- Do not duplicate download, verification, or folder rotation logic.
- Prefer adding new models and helper functions instead of growing MainActivity.

## UI
- Keep the UI simple and obvious.
- Activities and Courses have separate screens.
- Progress should always be visible during long operations.
- Completion dialogs should provide an Open Folder option when appropriate.

## Development Process
- One user-visible feature per revision.
- Build after every feature.
- Test every feature on a physical Garmin Edge 1050 before committing.
- Never bump the revision number until the feature is complete.

## Git
- GitHub is the source of truth.
- Tags ending in "-tested" mean verified on physical hardware.
- Do not modify previous tested behavior unless requested.

## Current Status
Current release:
v0.0.24-tested

Next feature:
Rev 0.0.25
- Backup One Course
- Backup All Courses
- Courses folder rotation
- Verification
- Completion dialog
- Open Folder
