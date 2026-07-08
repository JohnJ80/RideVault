# RideVault Product Charter

RideVault is a small, sharp tool for managing Garmin files over USB.

It is not Garmin Connect.
It is not Strava.
It is not a ride analysis app.

RideVault exists to make a Moto phone a reliable offline field repository for travel.

## Version 1.0 Scope

### Activities
- List activity FIT files
- Copy selected activities to Moto storage
- Share copied files using Android's standard share sheet

### Courses
- List course files
- Back up selected/all courses to Moto storage
- Delete selected courses from the Garmin
- Delete all courses with confirmation

### Maps
- List map-related files
- Show map storage usage
- Help identify large map files

## Design Principles

- KISS: keep it simple.
- Offline first.
- Moto is the repository.
- Store files as ordinary files.
- No cloud dependency.
- No Garmin login.
- No Strava login.
- No background sync.
- No hidden state.
- No destructive operation without confirmation.
- Never delete before copy/verification when backup is requested.

RideVault is intentionally small. It is designed to solve a handful of Garmin file management problems exceptionally well rather than becoming a general-purpose fitness application. Every feature should earn its place by making travel with a Garmin device simpler and more reliable.
