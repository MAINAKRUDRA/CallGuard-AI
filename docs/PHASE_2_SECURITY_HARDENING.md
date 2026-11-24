# Phase 2 — Security and production-readiness hardening

This phase is development-only until the owner explicitly authorizes a release.

## Guardrails

- Work on `phase-2-security-hardening`; do not merge to `main`.
- Do not update `website/downloads/callguard-ai.apk` or `website/version.json`.
- Do not run the website publication workflow or create a production release.
- Keep screening allow-first: uncertainty or storage errors must never become an automatic block.
- Do not add SMS, call-log, microphone, overlay, or broad storage permissions without a separately reviewed requirement.
- Never log full caller numbers, contact names, tokens, or raw personal data.

## Phase 2 work plan

### 2A — Data protection and lifecycle
- [ ] Review the current history schema and confirm that only masked numbers are persisted.
- [ ] Design authenticated encryption for history fields or the database using an Android Keystore key.
- [ ] Add a forward-only migration from existing schema version 2 without losing existing user history.
- [ ] Define behavior when the Keystore key is invalidated: preserve evidence, report a recoverable storage error, and never silently reset data.
- [ ] Review Android backup/device-transfer behavior so encrypted data is not restored without its Keystore key.
- [x] Add instrumentation coverage for the 1,000-event cap and clear-history behavior.
- [x] Retain the existing 90-day retention regression test.

### 2B — Screening reliability
- [x] Add unit tests for explicit VIP allow, allow-over-block precedence, explicit block, unknown caller safe-allow, and missing/blank caller ID safe-allow.
- [ ] Add tests for duplicate, restricted, malformed, and unavailable caller presentations.
- [ ] Verify callback deadline behavior and safe allow fallback under database/Keystore failures.
- [ ] Verify repeated calls and call-end restoration do not leave the ringer changed.
- [ ] Add OEM-specific physical-device test records; emulator success alone is not proof of cellular call behavior.

### 2C — Release readiness (not authorized yet)
- [ ] Verify CI unit and instrumentation tests.
- [ ] Review manifest permissions and exported components.
- [ ] Review dependency versions and Android backup policy.
- [ ] Complete physical-device tests on at least two Android manufacturers.
- [ ] Obtain explicit owner approval before merging to `main` or publishing an APK.

## Current known facts

- Allow/block rules are AES-256-GCM encrypted with a non-exportable Android Keystore key.
- Call history currently stores masked caller numbers, has 90-day retention, and is capped at 1,000 rows.
- The SQLite history database is currently app-private but not encrypted at rest. Database encryption is a planned Phase 2 item, not a completed feature.
- The current website workflow publishes on qualifying pushes to `main`; all Phase 2 work must remain on this branch until release approval.
