# CallGuard AI

Android-first call screening and personal call management.

> **Current product scope:** local incoming-call screening, explicit allow/block rules, VIP contacts, optional VIP ringer override, explainable decisions, and encrypted local activity history. The AI receptionist is not part of the current MVP; it requires a separately validated carrier-forwarding or SIP/VoIP audio route.

## Product principles

- **Allow-first safety:** unknown, malformed, restricted, or uncertain callers are allowed by default.
- **VIP precedence:** explicit allow/VIP rules take priority over block rules.
- **Explainability:** screening decisions retain an action and reason.
- **Local-first privacy:** screening rules and activity history are stored on-device.
- **User control:** VIP ring override is optional and permission-gated. It temporarily changes supported ringer/DND states for explicit VIP matches and attempts to restore the previous state after the call.
- **No false AI claims:** ordinary Android apps do not receive general cellular call audio. Live AI answering is not included in this MVP.

## Security and data handling

- Allow/block rules are stored as AES-256-GCM authenticated ciphertext.
- Rule and call-history encryption keys are non-exportable Android Keystore keys.
- Decryption requires the existing key. If a key is missing or invalidated, CallGuard reports a storage error and does not silently create a replacement that would make old ciphertext appear recoverable.
- Legacy plaintext rule preferences are migrated on first read; legacy keys are removed only after the encrypted write succeeds.
- Call-history number, action, and reason fields are encrypted at rest with AES-256-GCM. Database schema v2-to-v3 migration encrypts legacy rows transactionally before dropping the plaintext table.
- Call history is capped at 1,000 events and pruned after 90 days.
- Phone numbers are validated and canonicalized to E.164 using Google's libphonenumber. Users can select a supported two-letter region; international numbers beginning with + are parsed independently of that region.
- The optional biometric lock is a system BiometricPrompt UI gate. It is **not** biometric-bound encryption or per-operation cryptographic authentication.

## Spam screening behaviour

- The encrypted local block list is the device's user-maintained spam list. Adding a number to Block causes Android CallScreeningService to reject it before it rings; removing the rule restores normal screening.
- Explicit allow/VIP rules take precedence over the local spam list.
- Unknown numbers remain allowed unless the user has explicitly blocked/reported them. This avoids disconnecting legitimate callers based only on an unverified or spoofable signal.
- Android caller-number verification is a network-provided verification signal, not a Truecaller/Phone-app spam verdict. The app cannot read private spam labels from Truecaller or another dialer. No third-party label integration is claimed.
- A future verified spam-feed integration can add corroborated reputation data, with VIP allow rules always taking precedence.

## Technical baseline

- Native Android / Kotlin; minimum API 26; target API 35
- Android Telecom CallScreeningService
- Android Keystore AES-256-GCM
- Google libphonenumber
- SQLiteOpenHelper with forward-only schema migrations
- AndroidX BiometricPrompt for optional strong-biometric app access gating

## Build and test

From the repository root with Android SDK 35 installed:

```bash
gradle :android:app:testDebugUnitTest
gradle :android:app:assembleDebug
```

GitHub Actions runs JVM unit tests, builds a debug APK, and runs Android instrumentation plus an API 35 emulator launch smoke test. A successful CI debug artifact is for testing only; it is not a production release APK.

For a local device smoke test, enable Developer options and USB debugging, connect an Android 10+ device, then run:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.mainak.callguardai 1
```

Grant the Call Screening role inside the app and test with a second phone. Confirm allow, block, unknown-caller fallback, VIP behavior, and contact matching on the target OEM/device. An emulator launch does not prove real cellular call screening on every OEM.

## Platform and security limitations

- Android's CallScreeningService callback has a strict response deadline. Rule evaluation is bounded; timeout, worker saturation, interruption, or evaluation failure uses the allow-first fallback.
- Restricted/private caller presentations may not expose a usable number to the screening callback.
- VIP ring override requires user-granted Contacts, Phone state, and Android Notification Policy Access. It only changes supported ringer/DND states and preserves a newer ringer/DND choice made by the user during a call. OEM behavior, ringtone volume, and physical alert switches can differ.
- CallGuard does not request SMS, call-log, microphone, or overlay permissions for the current feature set. Contacts access is requested for saved-contact screening and selected-contact VIP setup; Phone state is used for VIP call-end restoration.
- Application backup is disabled with `android:allowBackup="false"`. OEM device-to-device transfer behavior still requires physical-device validation; do not assume this flag alone proves every OEM transfer path is blocked.
- If app data is restored without the matching Android Keystore key, encrypted rules/history cannot be decrypted. The app reports an error rather than silently replacing the key.
- A third-party app cannot generally capture both sides of an ordinary cellular call. AI receptionist functionality requires a separately validated telephony bridge.
- Sideloaded APK installation and updates require the user to confirm Android's package installer. An ordinary sideloaded app cannot silently self-install updates.

Never commit API keys, signing credentials, real call records, contact exports, or production secrets. Do not put privileged backend tokens in the APK. Use synthetic data in tests and demos.

## Development status

### Phase 1 — MVP functionality

- [x] Android call-screening role onboarding and service
- [x] Allow-first policy and explicit allow/block rules
- [x] VIP allow precedence over block rules
- [x] Phone-number normalization and selectable region
- [x] Contact selection and VIP matching
- [x] Local screening activity with clear-history control
- [x] Optional VIP ringer override

### Phase 2 — security and production-readiness hardening

- [x] AES-256-GCM encrypted rules and call-history fields
- [x] Android Keystore-backed key handling and key-loss-safe decryption
- [x] Transactional migration of legacy plaintext call history
- [x] Bounded screening evaluation with allow-first fallback
- [x] Regression tests for caller normalization and ringer restoration policy
- [x] Biometric app-lock lifecycle source fixes
- [x] Release workflow hardened to require production signing and emulator smoke checks
- [ ] Latest API 35 emulator instrumentation/smoke run must complete successfully
- [ ] Physical-device validation on at least two Android manufacturers
- [ ] OEM backup/device-to-device transfer validation
- [ ] Configure and verify production signing secrets outside source control
- [ ] Explicit owner approval before merge or release

See [Phase 2 security hardening](docs/PHASE_2_SECURITY_HARDENING.md) for implementation details and the physical-device test matrix.

### Phase 3 — product reliability and usability

- [ ] Review permission onboarding and recovery paths on Android 10–16
- [ ] Improve accessibility: content descriptions, touch targets, text scaling, and TalkBack order
- [ ] Verify layouts and controls at small, large, and landscape screen sizes
- [ ] Add automated UI coverage for primary settings and rule-management flows
- [ ] Verify app behavior after process death, reboot, permission revocation, and app update
- [ ] Validate contact matching and VIP ring override across OEM devices
- [ ] Keep product claims and setup instructions synchronized with actual Android platform behavior

Phase 3 work is development-only until separately approved.

## Website and release automation

- Website source: [`website/`](website/)
- Vercel setup: [`docs/VERCEL_WEBSITE_DEPLOYMENT.md`](docs/VERCEL_WEBSITE_DEPLOYMENT.md)
- GitHub Release workflow: [`.github/workflows/publish-apk-release.yml`](.github/workflows/publish-apk-release.yml)
- Website APK workflow: [`.github/workflows/publish-website-apk.yml`](.github/workflows/publish-website-apk.yml)

Production release workflows require the configured Android signing secrets, a signed non-debuggable release APK, and successful emulator smoke checks. CI debug APK artifacts are not production releases. No merge, tag, GitHub Release, or website APK publication is authorized as part of this development phase.
