# MSI Product Completion Implementation Plan

> **For agentic workers:** Continue on `feature/msi-v0.6.0-product-suite` only. Keep PR6 as Draft; do not merge or change `main`, PR2, PR4, or PR5.

**Goal:** Complete the Mobile Security Inspector product specification while preserving its offline, read-only, indicator-based security model.

**Architecture:** Keep Android package inspection in the data/domain layers, UI state and navigation in Compose, local history/preferences on-device, and optional scheduled work isolated behind explicit user opt-ins. Each visible finding must remain evidence-based and distinguish requested, granted, active, unknown, and unavailable states.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, AndroidX Biometric, Room/SQLite history, WorkManager, Glance, Android PackageManager/AppOps/UsageStats, Gradle, R8, GitHub Actions.

**Spec:** Product brief supplied in the conversation on 2026-11-25.

## Global Constraints

- minSdk 26; compileSdk/targetSdk 36.
- No `INTERNET` permission, network stack, analytics SDK, or tracking.
- App remains read-only for other packages; all changes route to Android settings and require the user's action.
- Usage Access and periodic scanning remain opt-in; notifications are requested only when periodic scanning is enabled.
- Every semantic status uses a symbol and text in addition to color.
- A security indicator is not a malware verdict or a guarantee of safety.
- Keep debug and release build configurations distinct; release must be non-debuggable and minified.
- CI release output remains unsigned until a protected release keystore is configured outside the repository.
- No Terminal, shell, Termux, container, or PRoot features.

## Review Focus

- Unknown package metadata or unavailable signing fingerprints must remain explicitly unknown, not be converted to a positive or negative claim.
- Apps with no UsageStats event must not be described as unused unless Android returned a reliable last-used timestamp.
- Historical rows created before app snapshots existed cannot be compared at app-level; retain and label them as count-only.
- Large font scale, small screens, RTL, and missing application icons must not hide actions or important details.
- WorkManager retries, disabled notification permission, and opt-out must not cause duplicate work or unexpected prompts.

---

### Task 1: Domain ordering and app comparison

**Files:** `domain/analyzer/ApplicationOrdering.kt`, `domain/analyzer/AppComparison.kt`, their unit tests, `ui/applications/ApplicationsScreen.kt`.

- [x] Add pure functions for stable application ordering and difference-based comparison.
- [x] Test that newest-install sorting uses firstInstallTime, not lastUpdateTime, and puts missing timestamps last.
- [x] Test requested/granted permission differences and nullable signature comparison.
- [x] Show actual differing permission names in the two-app comparison instead of only comparing totals.

### Task 2: Application actions and status clarity

**Files:** `ui/applications/ApplicationsScreen.kt`, `ui/MsiApp.kt`, `ui/dashboard/DashboardScreen.kt`.

- [x] Sort by newest installation date.
- [x] Add swipe-to-copy-package and swipe-to-open-Android-settings actions without dismissing the row.
- [x] Add long-press actions to copy the package name or explicitly share the app's local indicator summary.
- [x] Pair the dashboard attention state with a symbol and label, not color alone.
- [x] Clarify unavailable signing metadata and permission totals.

### Task 3: History and device health affordances

**Files:** `ui/history/HistoryScreen.kt`, `ui/settings/ToolsScreen.kt`, `ui/MsiApp.kt`, `design/theme/MsiTheme.kt`.

- [x] Mark VPN-active scan points distinctly and add a written legend; retain the text marker on each scan row.
- [x] Make each read-only device-health indicator open the relevant Android settings area.
- [x] Keep the active theme consistent across system status/navigation bars.
- [ ] Add automated tests for VPN-marker state and health-row navigation semantics.

### Task 4: Build and privacy gates

**Files:** `.github/workflows/build-apk.yml`, `app/build.gradle.kts`, `app/proguard-rules.pro`.

- [x] Verify targetSdk >= 36, Release debuggable=false, Debug debuggable=true, and R8 mapping output.
- [x] Check both APK manifests for INTERNET permission.
- [x] Reject unexpected exported components and PreviewActivity in Release.
- [x] Reject known network/analytics/tracking SDKs from the Release runtime dependency graph.
- [x] Reject DebugProbes in the minified Release DEX files.
- [x] Report Debug and Release APK byte sizes and verify checksums.
- [ ] Configure protected signing secrets and produce a signed Release artifact; never commit a keystore.

### Task 5: Remaining experience requirements

**Files:** main Compose screens, Android resources, widget/worker, UI state and navigation.

- [x] Launcher monochrome icon, branded SplashScreen, short launcher label and static shortcuts.
- [x] Glance status widget and 24-hour WorkManager scan with local notifications after opt-in.
- [x] Local trust/ignore labels, Usage Access opt-in, local category/permission guidance, app comparison, scan comparison, full TXT/CSV/JSON/PDF output.
- [x] Empty states and screen Previews exist for the primary destinations.
- [ ] Complete Arabic/English resource migration and numeral display preference; current core UI copy remains primarily hard-coded Arabic.
- [ ] Add a licensed bundled Arabic typeface and review line-height at 200% font scale.
- [ ] Replace the width-only navigation breakpoint with WindowSizeClass and test foldable posture changes.
- [ ] Complete shared-element transitions, PullToRefreshBox on all relevant lists, skeleton states, and reduced-motion verification.
- [ ] Add accessibility semantics/TalkBack checks for all row actions, swipe actions, chart state, and content descriptions.
- [ ] Add swipe-action and long-press Compose UI tests plus rendered screenshot review for each screen.
- [ ] Validate the lock lifecycle, shortcuts, widget, periodic worker, permissions visibility, RTL, and large font on physical/emulated Android 8–16.

---

## Verification Commands

- `gradle testDebugUnitTest`
- `gradle clean assembleDebug assembleRelease`
- `apkanalyzer manifest target-sdk app/build/outputs/apk/release/app-release-unsigned.apk`
- `apkanalyzer manifest debuggable app/build/outputs/apk/release/app-release-unsigned.apk`
- Verify that release manifest has no INTERNET permission or unexpected exported component.
- Verify that `app/build/outputs/mapping/release/mapping.txt` exists and DebugProbes are absent from Release DEX.
- Verify SHA-256 checksums for both generated APKs.

## Visual Review Gate

Each main destination must retain a Compose Preview with both useful content and empty/loading/error states where applicable. A Preview function compiling is not the same as a rendered screenshot review; visual approval remains pending until the previews are rendered or the application is exercised on an Android emulator/device.
