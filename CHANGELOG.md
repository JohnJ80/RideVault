# Changelog

## 0.0.40

- **Strava integration**: OAuth2 login, upload .FIT activities directly from Garmin to Strava, duplicate detection, processing status polling, success/failure dialog.

## 0.0.39

- Skip MTP write — Garmin Edge 1050 blocks USB writes from Android phones. RWGPS routes saved to Downloads instead.

## 0.0.38

- Last-ditch MTP write attempts with auto fallback to Downloads.

## 0.0.37 and earlier

- USB detection and permission handling.
- MTP session management with retry logic.
- Garmin storage traversal (Activities and Courses folders).
- Activity enumeration, download, batch download, and deletion.
- Course backup (single and all) and deletion.
- Ride with GPS OAuth login, collection/route browsing, GPX download.
- MediaStore-based Downloads with file rotation and size verification.
