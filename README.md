# CallGuard AI

Android-first call screening and personal call management.

> **Current scope:** Phase 1 MVP — local incoming-call screening, contacts/VIP allow-list, explicit allow/block rules, explainable decisions, and a private activity log. The AI receptionist is a separate later phase requiring a supported carrier-forwarding or SIP/VoIP audio route.

## Product principles

- **Allow-first safety:** uncertain callers are allowed by default.
- **VIP protection:** explicit allow/VIP rules take priority over block rules.
- **Explainability:** every screening decision records a rule/reason.
- **Local-first privacy:** screening policy and event data stay on-device in the MVP.
- **VIP ring override:** optional, permission-gated behavior temporarily switches Silent/Vibrate to Normal for explicit VIP matches, then restores the prior mode when the phone reports the call has ended.
- **No false AI claims:** ordinary Android apps do not receive general cellular call audio. Live AI answering is not part of this MVP.

## Security and number handling

- Allow/block rules are stored as a single AES-256-GCM authenticated ciphertext.
- The AES key is generated and retained in Android Keystore; it is not stored in app preferences.
- Existing plaintext SharedPreferences rules are migrated on first read; legacy keys are removed only after the encrypted write succeeds.
- Phone numbers are validated and canonicalized to E.164 using Google's libphonenumber.
- Users can select a supported two-letter phone region in the app (for example IN, US, or GB); the initial value comes from device locale. Local-format input uses this selected region.
- International numbers beginning with + are parsed independently of the selected region.
- Call history stores masked numbers, retains at most 1,000 events, and deletes events older than 90 days. The history database itself is app-private but not encrypted.

## Technical baseline

- Native Android / Kotlin, API 35 target, API 26 minimum
- Android Telecom CallScreeningService
- Android Keystore AES-256-GCM
- Google libphonenumber
- SQLiteOpenHelper with forward-only schema migrations

## Build and test

From the repository root with Android SDK 35 installed:

```bash
gradle :android:app:testDebugUnitTest
gradle :android:app:assembleDebug
```

GitHub Actions runs unit tests and builds a debug APK on pushes and pull requests to main. A separate API 35 emulator job installs and launches the app. Download the callguard-debug-apk artifact from a successful workflow run.

For a local device smoke test, enable Developer options and USB debugging, connect an Android 10+ device, then run:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.mainak.callguardai 1
```

Grant the Call Screening role inside the app and test with a second phone. Confirm allow, block, unknown-caller fallback, and contact import behavior on the target OEM/device. An emulator launch does not prove real cellular call screening on every OEM.

## Platform and security limitations

- The screening service must respond within Android's callback deadline. Persistence is performed after the Telecom response.
- If the Keystore key is invalidated or app data is restored without its key, encrypted rules cannot be decrypted. The app reports a storage error rather than silently replacing rules.
- Restricted/private caller presentations may not expose a number to the screening callback.
- VIP ring override requires the user to grant Phone state permission and Android Notification Policy Access. It only changes Silent/Vibrate when Do Not Disturb is off, and restores the prior ringer mode after the call. OEM behavior and ringtone-volume settings can still differ; physical alert switches may not be controllable.
- CallGuard deliberately does not request SMS, call-log, microphone, or storage permissions: they are not required for the current call-screening features. Contacts access is requested only when the user chooses contact import; Phone state is requested only when the user enables VIP ring override.
- CI derives a monotonically increasing Android versionCode from the full Git commit history and publishes a new APK to the website after successful unit tests/build. Android will still require the user to confirm installation of a downloaded APK; silent self-installation is not available to an ordinary sideloaded app.
- A third-party app cannot generally capture both sides of an ordinary cellular call. AI receptionist functionality requires a separately validated telephony bridge.
- Activity history uses masked numbers but SQLite is not encrypted in this phase.

Never commit API keys, signing credentials, real call records, contact exports, or production secrets. Do not put privileged backend tokens in the APK. Use synthetic data in tests and demos.

## Status

### Phase 1A — screening foundation
- [x] Android screening-role onboarding
- [x] Kotlin screening callback
- [x] Allow-first deterministic policy
- [x] Explicit allow/block rules with VIP precedence

### Phase 1B — usable MVP controls
- [x] Manual add/remove for allow and block rules
- [x] Optional contacts permission and contact-to-VIP import
- [x] Local SQLite screening activity history
- [x] Caller numbers masked in activity history
- [x] Clear local activity history

### Phase 1C — security and reliability
- [x] AES-256-GCM encrypted rules using Android Keystore
- [x] Migration from legacy plaintext rule preferences
- [x] E.164 normalization using libphonenumber
- [x] User-selectable phone region and validation
- [x] Explicit confirmation before importing all contacts as VIPs
- [x] SQLite schema version 2 migration and timestamp index
- [x] 90-day retention and 1,000-event cap
- [x] Allow-first callback fallback on rule-store errors
- [x] Optional VIP Silent/Vibrate override with call-end restoration (requires user-granted Phone state and Notification Policy Access; DND is not bypassed)
- [x] Automatic versionCode/versionName generation from full Git history in CI
- [ ] Verify latest GitHub Actions results for VIP override and versioning
- [ ] Verify real call screening on physical Android devices

**Validation status:** GitHub Actions unit tests and debug APK assembly passed on run [45402061040](https://github.com/MAINAKRUDRA/CallGuard-AI/actions/runs/45402061040). The API 35 emulator job was still running at the last check. The subsequent region-setting changes trigger a fresh workflow run and must pass separately. No physical-device call test is claimed.

## Public website and APK downloads

- Website source: [`website/`](website/)
- Vercel setup: [`docs/VERCEL_WEBSITE_DEPLOYMENT.md`](docs/VERCEL_WEBSITE_DEPLOYMENT.md)
- Vercel configuration: [`vercel.json`](vercel.json)
- APK release workflow: [`.github/workflows/publish-apk-release.yml`](.github/workflows/publish-apk-release.yml)

The website's direct APK link is updated by the `Publish APK to Website` workflow after a successful build. The public download is a debug-signed test APK, not a production Play Store build. Sideloaded APK updates require the user to open and confirm the Android package installer; Play Store distribution is required for normal managed auto-updates.
