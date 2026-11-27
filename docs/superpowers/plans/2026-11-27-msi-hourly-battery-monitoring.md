# Hourly Battery Insight and Monitoring Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a privacy-preserving hourly device-battery insight, strengthen selected background security observations, and improve dashboard clarity without claiming capabilities Android does not expose.

**Architecture:** Use public BatteryManager readings, a bounded local SQLite history, and a WorkManager hourly worker. Expose a confidence-aware summary through the existing ViewModel and Compose dashboard; keep the optional foreground security monitor separate.

**Tech Stack:** Kotlin, Android SDK 36, Jetpack Compose Material 3, WorkManager 2.12, SQLiteOpenHelper, JUnit 4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-27-msi-hourly-battery-monitoring-design.md`

## Global Constraints
- Minimum SDK 26; compileSdk and targetSdk 36.
- Battery data remains local and is never attributed to one app.
- The hourly cadence is best-effort; the UI shows actual sample timestamps and elapsed duration.
- Do not add permissions, tracking SDKs, or network calls for battery telemetry.
- Preserve active monitoring as opt-in and keep its service non-exported with its existing notification stop action.
- Keep Debug without INTERNET and preserve the Release-only GitHub update-check policy.
- Do not merge PR #14 or publish a signed production release.

## Review Focus
- Unsupported or negative BatteryManager values: use percentage fallback or show unavailable.
- Charging between readings: report net charge gain, not discharge.
- Device reboot or monotonic-clock rollback: discard the invalid interval.
- Delayed WorkManager execution: display measured duration, not a false exact one-hour interval.
- Database contention during scans and battery sampling: keep battery storage independent and the scan triple read atomic.

---

### Task 1: Pure battery telemetry model and calculation

**Files:**
- Create: `app/src/main/java/com/msi/inspector/domain/battery/BatterySample.kt`
- Create: `app/src/main/java/com/msi/inspector/domain/battery/BatteryUsageCalculator.kt`
- Test: `app/src/test/java/com/msi/inspector/domain/battery/BatteryUsageCalculatorTest.kt`

**Interfaces:**
- Produces `BatterySample(sampledAtEpochMillis, elapsedRealtimeMillis, levelPercent, chargeCounterMicroAmpHours, energyCounterNanoWattHours, isCharging, isPlugged)`.
- Produces `BatteryUsageCalculator.calculate(previous: BatterySample?, current: BatterySample): BatteryUsageSummary?`.

- [ ] Write tests for discharge, charge gain, stable counters, µAh-to-mAh conversion, energy fallback, percentage fallback, unsupported counters, and elapsed-time rollback.
- [ ] Run `gradle testDebugUnitTest --tests '*BatteryUsageCalculatorTest'` and confirm missing model/calculator causes the expected compilation failure.
- [ ] Implement immutable models and pure calculator; prefer charge-counter delta, then energy-counter delta, then percentage-point delta.
- [ ] Run the targeted test and full `gradle testDebugUnitTest`.
- [ ] Commit the domain model and tests.

### Task 2: Android reader, local history, and repository

**Files:**
- Create: `app/src/main/java/com/msi/inspector/data/battery/AndroidBatteryReader.kt`
- Create: `app/src/main/java/com/msi/inspector/data/battery/BatteryUsageDatabase.kt`
- Create: `app/src/main/java/com/msi/inspector/data/battery/BatteryUsageRepository.kt`
- Test: `app/src/test/java/com/msi/inspector/data/battery/BatteryUsageDatabaseTest.kt`

**Interfaces:**
- `AndroidBatteryReader.read(): BatterySample`.
- `BatteryUsageDatabase.insert(sample: BatterySample)` and `recent(limit: Int = 48): List<BatterySample>`.
- `BatteryUsageRepository.captureIfDue(minIntervalMillis: Long = 50 * 60 * 1000L): BatteryUsageSummary?`, `captureNow(): BatteryUsageSummary?`, and `latestSummary(): BatteryUsageSummary?`.

- [ ] Write database tests for ordering, retention at 48 samples, nullable counters, and persistence.
- [ ] Run the targeted test and verify it fails because the new database is not implemented.
- [ ] Implement a dedicated local database, bounded to 48 readings, and a reader using only public BatteryManager properties.
- [ ] Prevent duplicate near-hour samples when the app resumes; use elapsed realtime to detect reboot/clock rollback.
- [ ] Run targeted and full unit suites.
- [ ] Commit reader, database, repository, and tests.

### Task 3: Hourly background scheduling

**Files:**
- Create: `app/src/main/java/com/msi/inspector/background/BatteryHourlyScheduler.kt`
- Create: `app/src/main/java/com/msi/inspector/background/BatteryHourlyWorker.kt`
- Modify: `app/src/main/java/com/msi/inspector/MainActivity.kt`

**Interfaces:**
- `BatteryHourlyScheduler.ensureScheduled(context)`, `setEnabled(context, enabled)`, and `isEnabled(context): Boolean`.
- Worker captures one reading only while tracking is enabled.

- [ ] Add scheduling/preference tests for default-enabled behavior and cancellation on opt-out.
- [ ] Implement unique periodic work with a one-hour interval and a 15-minute flex window; do not add a foreground service or special permission.
- [ ] Capture an initial baseline when the app first opens; use WorkManager for later samples.
- [ ] Verify worker cancellation and that no network constraint or INTERNET permission is introduced.
- [ ] Run all tests and build Debug.

### Task 4: ViewModel and dashboard UX

**Files:**
- Modify: `app/src/main/java/com/msi/inspector/ui/state/MsiUiState.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/MsiViewModel.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/settings/ToolsScreen.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/navigation/MsiDestinationContent.kt`

**Interfaces:**
- State exposes `batteryUsage`, `isLoadingBatteryUsage`, and `batteryTrackingEnabled`.
- ViewModel exposes `refreshBatteryUsage()` and `setBatteryTrackingEnabled(enabled: Boolean)`.

- [ ] Add tests for display formatting and report classification where testable without Compose.
- [ ] Load telemetry in the ViewModel and refresh on app resume.
- [ ] Add an animated RTL dashboard card showing net mAh/mWh/percentage points, start/end level, measured duration, last sample time, and source/limitations.
- [ ] Add a Tools switch to pause hourly local sampling.
- [ ] Check large text, narrow screens, RTL alignment, reduced system animations, and unavailable sensor states in UI previews/code review.
- [ ] Run all tests and build Debug + Release.

### Task 5: Monitoring coverage and scan-state consistency

**Files:**
- Modify: `app/src/main/java/com/msi/inspector/background/MsiMonitoringService.kt`
- Modify: `app/src/main/java/com/msi/inspector/data/repository/AppAuditRepository.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/MsiViewModel.kt`
- Test: add a repository snapshot consistency test.

- [ ] Add observer registration for `Settings.Secure.ACCESSIBILITY_ENABLED` and `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, with safe unregister behavior.
- [ ] Introduce one atomic `loadScanState()` read returning history, timeline, and latest snapshot under the existing shared lock; use it at initialization and refresh.
- [ ] Add a test proving history, timeline, and snapshot are loaded as one repository state.
- [ ] Verify service stop still cancels its own periodic/event work, unregisters observers, and removes its foreground notification.
- [ ] Run all tests.

### Task 6: Release metadata, documentation, and CI guards

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/build-apk.yml`
- Modify: `README.md`

- [ ] Update app version to 0.9.0 and versionCode to 12.
- [ ] Add CI checks that hourly battery telemetry remains local-only and Debug has no INTERNET permission.
- [ ] Document battery measurement semantics and Android scheduling limitations.
- [ ] Run `gradle clean testDebugUnitTest assembleDebug assembleRelease`.
- [ ] Verify Release is non-debuggable/minified and remains unsigned unless a protected production signing key is configured.
- [ ] Commit the final change set and wait for the GitHub Actions artifact build.

### Task 7: Final verification and review

- [ ] Inspect the final diff against the previous PR head.
- [ ] Review Manifest, exported components, foreground-service lifecycle, battery retention, notification privacy, and update-check networking.
- [ ] Read the full CI job result and verify all tests/build/manifest steps pass on the final commit.
- [ ] Download the exact workflow artifact, verify SHA-256, extract Debug and unsigned Release APKs, and report their exact status.
- [ ] List physical-device checks that remain unverified; do not claim runtime validation without a real device.
