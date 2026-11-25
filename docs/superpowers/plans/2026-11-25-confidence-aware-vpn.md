# Confidence-aware VPN Observations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the difference between observed VPN inactivity and unavailable VPN state throughout MSI.

**Architecture:** Carry nullable Boolean state from Android scanner through snapshot, repository persistence, history, Timeline comparison, and Compose UI. Encode unknown as SQLite -1 and conservatively migrate ambiguous legacy false values.

**Tech Stack:** Kotlin, Android Package/Connectivity APIs, SQLiteOpenHelper, Jetpack Compose, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-25-confidence-aware-vpn-design.md`

## Global Constraints
- No INTERNET permission or network/analytics dependencies.
- Never infer inactive VPN from missing Android network/capability data.
- Never identify a VPN owner app from device-wide transport state.
- Do not label apps safe or malicious.
- Preserve local-only storage and user-controlled settings.

## Review Focus
- Android returns no active network: state must be unknown.
- Android returns a network but no capabilities: state must be unknown.
- Legacy database rows stored false for unavailable state: migrate to unknown.
- First scan has unknown VPN: do not emit a VPN transition event.
- Timeline compares unknown to known: do not infer a transition.

---

### Task 1: Domain semantics and regression tests

**Files:**
- Modify: `app/src/main/java/com/msi/inspector/domain/model/AppAudit.kt`
- Modify: `app/src/main/java/com/msi/inspector/domain/timeline/TimelineEventDetector.kt`
- Test: `app/src/test/java/com/msi/inspector/domain/TimelineEventDetectorTest.kt`

**Interfaces:**
- `ScanSnapshot.vpnActive: Boolean?`
- `TimelineEventDetector.detect(previous, current)` emits VPN_STATE_CHANGED only if both values are non-null and unequal.

- [ ] Add tests for known true→false, false→true, unknown→true, true→unknown, and unknown baseline.
- [ ] Run `gradle testDebugUnitTest` and confirm new transition tests fail before implementation.
- [ ] Change the snapshot field to nullable and guard Timeline comparison on both known values.
- [ ] Re-run focused and full unit tests.

### Task 2: Scanner and persistent history

**Files:**
- Modify: `app/src/main/java/com/msi/inspector/data/scanner/AndroidAppScanner.kt`
- Modify: `app/src/main/java/com/msi/inspector/domain/model/AppAudit.kt`
- Modify: `app/src/main/java/com/msi/inspector/data/database/ScanHistoryDatabase.kt`
- Test: CI build and existing history/domain tests.

**Interfaces:**
- `isVpnActive(): Boolean?` returns null if active network or capabilities cannot be read; otherwise returns whether VPN transport is present.
- `ScanHistoryEntry.vpnActive: Boolean?`
- SQLite values: 1=true, 0=false, -1=unknown.

- [ ] Update scanner and database round-trip semantics.
- [ ] Bump schema to v4; migrate legacy `vpn_active=0` rows to -1 because previous versions conflated false and unknown.
- [ ] Keep legacy active rows (1) unchanged.
- [ ] Run full unit suite and Android build.

### Task 3: User-facing confidence

**Files:**
- Modify: `app/src/main/java/com/msi/inspector/ui/MsiViewModel.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/msi/inspector/ui/history/HistoryScreen.kt`

**Interfaces:**
- `MsiUiState.vpnActive: Boolean?`
- Dashboard/history copy distinguishes active, inactive, and unknown.
- Trend chart uses a neutral marker for unknown and explains the third state.

- [ ] Add tests for the domain behavior first (Task 1).
- [ ] Update state and UI branches.
- [ ] Inspect all VPN-state references for Boolean-only assumptions.
- [ ] Run full CI verification.

### Task 4: Documentation and final verification

**Files:**
- Modify: `README.md`
- Modify: `.github/workflows/build-apk.yml` only if verification gaps are found.

- [ ] Document the three-state observation and legacy migration.
- [ ] Run unit tests, dependency audit, Debug/Release builds, manifest checks, and checksum validation through GitHub Actions.
- [ ] Inspect final PR diff and report any unverified device-only behavior.
