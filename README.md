# CallGuard AI

Android-first call screening and personal call management.

> **Current scope:** Phase 1 MVP — local incoming-call screening, contacts/VIP allow-list, explicit allow/block rules, explainable decisions, and a private activity log. The AI receptionist is a separate later phase that requires a supported carrier-forwarding or SIP/VoIP audio route.

## Product principles

- **Allow-first safety:** uncertain callers are allowed by default.
- **VIP protection:** explicit allow/VIP rules take priority over block rules.
- **Explainability:** every screening decision records a rule/reason.
- **Local-first privacy:** screening policy and event data stay on-device in the MVP.
- **Reversible decisions:** users can undo a block and allow a number.
- **No false AI claims:** ordinary Android apps do not receive general cellular call audio. Live AI answering is not part of this MVP.

## MVP feature plan

1. Android role onboarding for `ROLE_CALL_SCREENING`.
2. Kotlin `CallScreeningService` with a bounded deterministic policy.
3. Number normalization and explicit allow/block rules.
4. Contact/VIP allow-list (only after the user grants the required permission).
5. Local call decision history and reason labels.
6. Manual override and tests for policy priority and safe fallback.

## Technical baseline

- Native Android / Kotlin
- Gradle Kotlin DSL
- Room for persistent local data (introduced with the storage implementation)
- Android Telecom `CallScreeningService`
- Android Keystore for sensitive local data if/when encrypted fields are persisted

## Build

Open the `android/` project in Android Studio with a compatible Android SDK installed. The first implementation targets Android API 35 and a minimum API level of 26. Generate the Gradle wrapper from a local Gradle installation if it is not already present:

```bash
gradle wrapper
./gradlew :app:assembleDebug
```

## Important platform limitations

- The screening service must respond within Android's documented callback deadline. Keep the decision path local and bounded; persistence must not delay the Telecom response.
- Restricted/private caller presentations may not expose a number to the screening callback.
- DND, silent-mode, physical alert switches, and VIP alert overrides vary by Android version and OEM. They are not promised by this MVP.
- A third-party app cannot generally capture both sides of an ordinary cellular call. AI receptionist functionality requires a separately validated telephony bridge.

## Security

Never commit API keys, signing credentials, real call records, contact exports, or production secrets. Do not put privileged backend tokens in the APK. Use synthetic data in tests and demos.

## Status

Repository initialized. Implementation and device verification are in progress; no production-readiness or device-compatibility claim is made yet.
