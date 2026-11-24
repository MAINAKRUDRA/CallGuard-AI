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
- [ ] Validate strong-biometric enrollment, successful unlock, failed attempts, cancellation, background/foreground relock, and devices without biometric hardware on physical devices.
- [x] Review sensitive rule/history access: encrypted storage keys remain Android-Keystore protected, but the optional biometric gate is a UI-level access control, not per-operation cryptographic user authentication. This residual distinction is documented; do not describe the switch as biometric-bound encryption.


### 2A — Data protection and lifecycle
- [x] Confirm screening history writes masked caller numbers; history API persists only the masked value supplied by the screening service.
- [x] Encrypt caller number, action, and reason fields with AES-256-GCM using a non-exportable Android Keystore key.
- [x] Add a forward-only schema v2 to v3 migration that copies and encrypts rows before dropping the legacy plaintext table; migration failure leaves the transaction uncommitted.
- [x] Missing Keystore keys are not recreated during decryption; history access reports an error and preserves stored ciphertext. UI displays a recovery warning instead of crashing.
- [x] Review Android backup policy: application-level backup is disabled with `android:allowBackup="false"`; encrypted history/rules therefore are not intended to migrate through Android Auto Backup.
- [ ] Validate backup and device-to-device transfer behavior on OEM devices. Android 12+ documentation notes that some manufacturers may still support device-to-device transfer even when cloud backup is disabled; do not assume this manifest flag alone proves every OEM transfer path is blocked.

Implementation, migration instrumentation test, and missing-Keystore-key preservation tests for both rules and history are committed. The biometric gate's automatic-success path also now records the unlocked state. Historical CI runs on older commits have failed during compilation; the newest commits are queued and must pass before this checklist item can be closed.
- [x] Add instrumentation coverage for the 1,000-event cap and clear-history behavior.
- [x] Retain the existing 90-day retention regression test.

### 2B — Screening reliability
- [x] Add unit tests for explicit VIP allow, allow-over-block precedence, explicit block, unknown caller safe-allow, and missing/blank caller ID safe-allow.
- [x] Add tests for duplicate, restricted, malformed, private, and unavailable caller presentations; invalid presentations canonicalize to no caller number and are safe-allowed.
- [x] Bound rule evaluation to 1.5 seconds on a dedicated single-worker executor; timeout, executor saturation, interruption, and evaluation errors use an explicit safe-allow decision.
- [x] Add unit coverage ensuring restoration does not overwrite a newer Silent/Vibrate choice made by the user during a VIP call.
- [ ] Verify repeated calls, phone-state IDLE delivery, and call-end restoration on emulator and physical devices.
- [x] Add an OEM physical-device validation record template and required scenarios; actual device records remain pending because emulator success alone is not proof of cellular call behavior.

The bounded evaluator and caller-presentation tests are committed. Ringer restoration now preserves a newer user-selected ringer mode; actual telephony lifecycle validation remains pending.

### 2C — Release readiness (not authorized yet)
- [ ] Verify CI unit and instrumentation tests on the latest branch head; the current CI queue has not yet reported a result for the newest commits.
- [x] Review manifest permissions and exported components: only the launcher activity is exported for user launch; the call-screening service is exported because Android must bind to it and is protected by `android.permission.BIND_SCREENING_SERVICE`; the phone-state receiver is non-exported.
- [x] Review dependency declarations for pinned versions; no blind upgrades were made because dependency updates require a separate compatibility/build validation pass.
- [ ] Verify biometric app-lock lifecycle, enrollment, success, failure, cancellation, and unsupported-hardware behavior on physical devices. Source uses AndroidX BiometricPrompt with BIOMETRIC_STRONG; successful automatic prompt completion now marks the app unlocked so background/foreground relock can work. The implementation requests `BIOMETRIC_STRONG`, which may be fingerprint or another platform-approved strong biometric; UI wording now says biometric rather than fingerprint.
- [ ] Complete physical-device tests on at least two Android manufacturers.
- [ ] Obtain explicit owner approval before merging to `main` or publishing an APK.

## Current known facts

- Allow/block rules are AES-256-GCM encrypted with a non-exportable Android Keystore key. Decryption now requires the existing key and never creates a replacement when the key is missing, preserving ciphertext and reporting a storage error.
- Call history currently stores masked caller numbers, has 90-day retention, and is capped at 1,000 rows.
- Call-history fields are encrypted at rest with AES-256-GCM using a non-exportable Android Keystore key; schema v2-to-v3 migration and key-loss handling are implemented but still require emulator and device validation.
- Manifest review found no unnecessary exported app components: the screening service is protected by the Android binding permission and the phone-state receiver is private.
- `android:allowBackup="false"` is set. OEM device-to-device migration behavior still requires physical validation.
- The current website workflow publishes on qualifying pushes to `main`; all Phase 2 work must remain on this branch until release approval.


## Physical-device validation record — required before release

No physical-device results are claimed by this document. Fill one row per actual device and retain the build SHA used.

| Manufacturer / model | Android version | Build SHA | Call screening role enabled | Biometric tested | Ringer / DND tested | Backup transfer tested | Result / issue |
|---|---|---|---|---|---|---|---|
| Pending device 1 | Pending | Pending | Pending | Pending | Pending | Pending | Not tested |
| Pending device 2 | Pending | Pending | Pending | Pending | Pending | Pending | Not tested |

Required scenarios on each device:
- Fresh install, permission denial/grant, app restart, and app update.
- Enable and disable the Android call-screening role; verify the OS still identifies CallGuard as the selected screening app.
- Unknown, private/restricted, malformed, VIP, and blocked caller presentations.
- Repeated calls, missed/ended calls, phone-state IDLE delivery, and no stuck temporary ringer/DND override.
- Incoming VIP call while Normal, Silent, and Vibrate are selected; change the ringer mode during the call and verify CallGuard does not overwrite the user's newer choice.
- DND access granted and revoked; verify behavior without DND access.
- Biometric success, failed attempt, explicit cancellation, no enrolled biometric, unavailable sensor, app background/foreground relock, and screen rotation if supported.
- App backup and device-to-device transfer behavior; confirm encrypted local data is not restored without the matching Android Keystore key.
- Verify the app remains usable when optional permissions are denied and that uncertain screening conditions use the documented safe-allow behavior.

Do not mark any device row complete based on an emulator or a different manufacturer's device.
