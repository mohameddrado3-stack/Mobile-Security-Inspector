# MSI Active Monitoring and Hourly Battery Insight Design

**Date:** 2026-11-27
**Status:** Implementation authorized by the project owner.

## Intent
Make Mobile Security Inspector a more useful on-device security observer by adding a local hourly battery telemetry record, exposing its measurement quality clearly, strengthening selected security-setting change observation, and improving restored scan-state consistency.

## Goals
- Capture an initial battery baseline and then schedule one best-effort sample per hour.
- Prefer Android public remaining-charge counter (µAh), then remaining-energy counter (nWh), then battery percentage points.
- Persist a bounded local history and derive the net change between two real samples.
- Show the measurement window, start/end percentage, source, and last sample time in Arabic RTL UI.
- Never represent whole-device battery change as MSI-only consumption or per-app attribution.
- Expand active monitoring to selected accessibility-service setting changes, alongside existing package and ADB/developer-option observations.
- Load history, timeline, and latest snapshot atomically to prevent mixed-generation UI state.
- Preserve opt-in active monitoring, visible persistent notification, private local processing, and no new tracking/network SDKs.

## Non-goals and Android boundaries
- No root, privileged/system app behavior, hidden Android APIs, or permission/settings bypass.
- No promise of exact wall-clock execution: WorkManager is subject to Doze, standby, OEM power policy, and constraints.
- No per-application battery attribution: public BatteryManager counters describe the device battery, not MSI or another package's individual draw.
- No claim to intercept, block, or scan an installation before Android's package installer completes it. Existing package event observation can trigger a local post-install inventory scan.
- No full Chrome extension, page interception, or real-time website reputation verdict in this change. Android Chrome does not expose desktop-style extension APIs to ordinary apps.
- No automatic changes to device permissions or settings.

## Approaches
1. Public BatteryManager + WorkManager + local SQLite (chosen): compare Android-reported remaining-charge/energy readings, persist timestamped samples, and refresh hourly on a best-effort schedule. This uses public Android APIs, no runtime permission, minimal background work, and an explicit measurement-availability state.
2. Continuous foreground sampling: would require an always-running foreground service and frequent polling, increasing battery drain and notification noise. Rejected because it is counterproductive for an inspector and still cannot attribute all battery usage.
3. Hidden BatteryStats/per-app power data: requires privileged/system-level access or unsupported APIs. Rejected as unavailable to a normal sideloaded application.

## Architecture
- BatterySample and BatteryUsageCalculator are pure domain components with unit tests.
- AndroidBatteryReader reads BatteryManager counters and sticky battery status without new runtime permissions.
- BatteryUsageDatabase stores a bounded sample history locally.
- BatteryUsageRepository captures due samples and derives the most recent measurement window.
- BatteryHourlyScheduler and BatteryHourlyWorker schedule sampling independently from the optional active-monitoring foreground service.
- MsiViewModel exposes battery telemetry and tracking preference to Compose.
- An animated RTL dashboard card displays the current result, measurement method, and quality caveat.
- The active-monitoring service observes accessibility-enabled settings in addition to current package/ADB/developer settings.
- AppAuditRepository returns history/timeline/latest snapshot from one synchronized read.

## Privacy, permissions, and compatibility
- Minimum SDK remains 26; compile/target SDK remain 36.
- Battery telemetry is stored on-device only and is not included in the update-check request or reports by default.
- No battery or monitoring feature requests location, Accessibility, Usage Access, or new network access.
- Existing notification permission remains required only for notifications/foreground monitoring on Android 13+.
- The existing Release-only update-check INTERNET permission policy remains unchanged.

## Acceptance criteria
- Calculator tests cover discharge, charge, unchanged counters, percentage fallback, unsupported counters, and invalid/rebooted timestamps.
- Database tests cover insertion, ordered reads, bounded retention, and persistence.
- The hourly worker saves a battery reading only while tracking is enabled.
- The dashboard never labels whole-device telemetry as per-app or MSI consumption.
- Existing active-monitoring disable semantics still cancel work, stop the foreground service, and remove its notification.
- Unit tests and Debug/Release builds pass; the Release manifest remains non-debuggable and the Debug manifest remains offline.
- Physical-device tests are reported as pending unless run on an actual device.
