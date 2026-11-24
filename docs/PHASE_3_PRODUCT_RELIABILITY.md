# Phase 3 — Product reliability and usability

Phase 3 is development-only. Continue on `phase-2-security-hardening` and the existing draft PR. Do not merge, tag, publish, or update the public APK unless the owner explicitly authorizes release.

## Guardrails

- Preserve allow-first behavior: uncertain callers and evaluation failures must not be blocked.
- Do not add SMS, call-log, microphone, overlay, or broad storage permissions without a separately reviewed requirement.
- Keep caller numbers, contact names, and device data out of logs and test fixtures.
- Do not claim cross-OEM support based only on emulator tests.
- Keep CI debug artifacts separate from signed production release artifacts.

## 3A — Accessibility and interface foundations

- [x] Mark section titles as accessibility headings on Android 9+.
- [x] Announce screening status and activity updates as polite live-region changes.
- [x] Add meaningful accessibility descriptions to phone-number and region inputs.
- [x] Make the saved-rules activation area keyboard-focusable with a minimum 48 dp height.
- [x] Reflow dashboard statistics into a two-column rules row and a full-width activity row; allow cards to grow with text scaling.
- [x] Give both preference switches explicit accessibility descriptions and 48 dp minimum height.
- [x] Add Espresso instrumentation coverage for primary dashboard controls and preference-switch accessibility labels.
- [ ] Verify TalkBack navigation and announcements on a physical device.
- [ ] Verify font scaling at 1.0x, 1.3x, and 2.0x without clipped controls.
- [ ] Verify minimum touch targets and contrast across all interactive controls.

## 3B — Layout and device compatibility

- [ ] Check compact phones, large phones, tablets, portrait, and landscape.
- [ ] Check Android 10 through Android 16 where available.
- [ ] Verify system font/display scaling and edge-to-edge/inset behavior.
- [ ] Confirm permission prompts and role selection return to a correct, refreshed state.
- [ ] Test permission revocation from Android Settings while the app is backgrounded.

## 3C — Lifecycle and workflow regression

- [ ] Test process death and relaunch with biometric lock enabled and disabled.
- [ ] Test reboot, app update, and app data/Keystore mismatch recovery.
- [ ] Verify call-screening role loss and re-grant behavior.
- [ ] Verify Contacts and Notification Policy Access revocation while VIP override is enabled.
- [x] Add initial automated UI checks for dashboard summary controls and accessibility labels.
- [x] Add interaction tests for allow/block creation, normalized persistence, invalid-number rejection, and allow-rule removal.
- [ ] Add automated checks for block-rule removal, region settings, history clearing, and biometric gate states where the Android test environment supports them.

## Current implementation notes

The first accessibility pass is committed in `MainActivity.kt`: section titles expose heading semantics on API 28+, status/history changes use polite accessibility live regions, phone-number and region inputs have explicit descriptions, and the saved-rules activation area is keyboard-focusable with a 48 dp minimum height. The dashboard summary now uses a more flexible two-column layout, the stat cards can grow vertically with larger text, and preference switches expose explicit accessibility descriptions.

`RuleManagementInstrumentedTest.kt` adds Espresso coverage for adding an allow rule, adding a block rule, checking normalized encrypted persistence, rejecting an invalid number, and removing an allow rule through the management dialog. These tests are added but are not considered passing until the current commit's emulator CI job completes.

These source changes are not yet a claim of full accessibility conformance. TalkBack, large-font, touch-target, and OEM validation remain open until tested on actual devices.

## Validation record

| Check | Status | Evidence |
|---|---|---|
| JVM unit tests | Passed on Phase 3 docs snapshot | CI run 45496191687; current head requires a new run after interaction-test commits |
| Debug APK assembly | In progress on Phase 3 docs snapshot | CI run 45496191687 |
| API 35 emulator smoke | In progress on Phase 3 docs snapshot | CI run 45496191687 |
| Dashboard Espresso UI checks | Added; awaiting current CI result | `DashboardAccessibilityInstrumentedTest.kt` |
| Rule interaction Espresso checks | Added; awaiting current CI result | `RuleManagementInstrumentedTest.kt` |
| TalkBack / font scaling | Not tested | Physical device required |
| OEM call screening / VIP ring override | Not tested | Physical SIM/device tests required |

Do not close a validation item without current evidence for the exact commit under test.
