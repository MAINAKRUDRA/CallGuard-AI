# Phase 2 — Security and production-readiness hardening

This phase is development-only until the owner explicitly authorizes a release.

## Guardrails

- Work on `phase-2-security-hardening`; do not merge to `main`.
- Do not update `website/downloads/callguard-ai.apk` or `website/version.json`.
- Do not run the website publication workflow or create a production release.
- Keep screening allow-first: uncertainty or storage errors must never become an automatic block.
- Do not add SMS, call-log, microphone, overlay, or broad storage permissions without a separately reviewed requirement.
- Never log full caller numbers, contact names, tokens, or raw personal data.

## Uploaded APK audit — 2026-11-25

Artifact inspected: `callguard-ai.apk` (6,254,758 bytes; SHA-256 `0c6726529c2007ae26e6c4964455cd2c97e0c68c1fb69675a4e1c6ca814a2991`).

- Package: `com.mainak.callguardai`.
- Version: `0.3.124` (`versionCode=124`); min SDK 26; target SDK 35.
- Manifest permissions: `READ_CONTACTS`, `READ_PHONE_STATE`, `MODIFY_AUDIO_SETTINGS`, and the generated non-exported-receiver permission. It does not contain `ACCESS_NOTIFICATION_POLICY`, which is present in the current Phase 2 source manifest.
- Application manifest marks `debuggable=true` and `allowBackup=false`.
- APK v2 signing block is present; signer certificate is the generic Android Debug certificate, not a production release certificate.
- DEX files do not contain AndroidX Biometric / `BiometricPrompt` references or the new app-lock preference key.
- Website metadata currently identifies version `0.3.126` / code 126, so this upload is older than the current website version.
- The APK does not embed a verifiable Git source commit. Exact source-commit identity cannot be proven from this artifact alone.

**Audit result: NOT RELEASE-READY.** Do not publish this APK. Rebuild from the current source, use the production signing configuration, verify the final manifest/version, and complete CI plus physical-device tests first.

## Phase 2 work plan

### 2A — App access security
- [x] Add optional Android strong-biometric app lock using the system BiometricPrompt; biometric data remains managed by Android.
- [ ] Validate fingerprint enrollment, successful unlock, failed attempts, cancellation, and devices without biometric hardware on physical devices.
- [ ] Review whether sensitive rule/history access also needs cryptographic authentication rather than a UI-level app lock.


### 2A — Data protection and lifecycle
- [x] Confirm screening history writes masked caller numbers; history API persists only the masked value supplied by the screening service.
- [x] Encrypt caller number, action, and reason fields with AES-256-GCM using a non-exportable Android Keystore key.
- [x] Add a forward-only schema v2 to v3 migration that copies and encrypts rows before dropping the legacy plaintext table; migration failure leaves the transaction uncommitted.
- [x] Missing Keystore keys are not recreated during decryption; history access reports an error and preserves stored ciphertext. UI displays a recovery warning instead of crashing.
- [ ] Review Android backup/device-transfer behavior so encrypted data is not restored without its Keystore key.

Implementation and migration instrumentation test are committed. Unit tests and debug APK build passed on CI run 45486263741; Android API 35 emulator instrumentation/smoke test was still running at last check.
- [x] Add instrumentation coverage for the 1,000-event cap and clear-history behavior.
- [x] Retain the existing 90-day retention regression test.

### 2B — Screening reliability
- [x] Add unit tests for explicit VIP allow, allow-over-block precedence, explicit block, unknown caller safe-allow, and missing/blank caller ID safe-allow.
- [x] Add tests for duplicate, restricted, malformed, private, and unavailable caller presentations; invalid presentations canonicalize to no caller number and are safe-allowed.
- [x] Bound rule evaluation to 1.5 seconds on a dedicated single-worker executor; timeout, executor saturation, interruption, and evaluation errors use an explicit safe-allow decision.
- [ ] Verify repeated calls and call-end restoration do not leave the ringer changed.
- [ ] Add OEM-specific physical-device test records; emulator success alone is not proof of cellular call behavior.

### 2C — Release readiness (not authorized yet)
- [ ] Verify CI unit and instrumentation tests.
- [ ] Review manifest permissions and exported components.
- [ ] Review dependency versions and Android backup policy.
- [ ] Verify biometric app-lock behavior and document the supported biometric classes per OEM.
- [ ] Complete physical-device tests on at least two Android manufacturers.
- [ ] Obtain explicit owner approval before merging to `main` or publishing an APK.

## Current known facts

- Allow/block rules are AES-256-GCM encrypted with a non-exportable Android Keystore key.
- Call history currently stores masked caller numbers, has 90-day retention, and is capped at 1,000 rows.
- Call-history fields are encrypted at rest with AES-256-GCM using a non-exportable Android Keystore key; schema v2-to-v3 migration and key-loss handling are implemented but still require emulator and device validation.
- The current website workflow publishes on qualifying pushes to `main`; all Phase 2 work must remain on this branch until release approval.
