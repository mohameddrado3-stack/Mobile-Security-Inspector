# MSI — Confidence-aware VPN observations

## Intent
Make device-wide VPN reporting truthful when Android cannot provide a definitive network observation. MSI must distinguish an observed active VPN, an observed absence of VPN transport, and an unavailable observation so the dashboard, history, and Timeline never turn missing system data into a factual "VPN off" claim.

## Goals
- Represent VPN state as three-valued data: `true` (active), `false` (observed inactive), and `null` (unknown/unavailable).
- Emit a VPN state-change Timeline event only when both adjacent scans have known values and those values differ.
- Preserve the distinction in local history, the dashboard, and historical trend visualization.
- Migrate legacy rows conservatively: old `false` values are ambiguous because prior scanner versions conflated inactive and unavailable; convert those rows to unknown while retaining known active rows.
- Keep the app offline, local-only, and read-only toward other apps.

## Non-goals
- Identifying which app owns a VPN connection.
- Changing, disabling, or configuring VPN services.
- Treating VPN presence or absence as a security verdict.
- Adding network access, analytics, or third-party services.

## Approaches
1. Keep Boolean and add a separate availability flag. This is explicit but creates redundant state that can contradict itself.
2. Use nullable Boolean. This is the smallest compatible domain representation and directly encodes unknown without a second field.
3. Introduce a dedicated enum across all layers. This is expressive but adds conversion code without a current need for more than three states.

## Decision
Use nullable Boolean (`true/false/null`) in domain and UI models, with a documented SQLite sentinel of `-1` for unknown. This is a deliberate implementation choice. Bump the database schema to v4 and migrate legacy `0` values to `-1`, because historical false values cannot be trusted as observed inactivity.

## Constraints
- Android Kotlin, Jetpack Compose, Material 3; retain existing architecture and Arabic RTL copy.
- No new runtime dependencies or network permission.
- Existing history remains available; migration must be non-destructive except for conservatively downgrading ambiguous legacy VPN values to unknown.
- No claims that VPN state identifies the originating application.
- Keep PR7 isolated from `main`.

## Acceptance
- Unit tests prove that known true→false and false→true changes are recorded.
- Unit tests prove that any transition involving unknown produces no VPN-change event.
- Database maps true/false/unknown to 1/0/-1 and migrates legacy 0 values to -1.
- Dashboard and history visibly distinguish unknown from inactive.
- Full unit suite, dependency audit, debug/release builds, manifest checks, and checksum generation pass in CI.
