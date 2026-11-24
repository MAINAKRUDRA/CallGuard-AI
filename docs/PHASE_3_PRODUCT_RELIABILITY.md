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

- [x] Add an automated landscape-orientation dashboard smoke test.
- [x] Add an automated 2.0x system-font test for reachability of primary actions.
- [ ] Check compact phones (320–360 dp), large phones, tablets, portrait, and landscape on hardware/emulators.
- [ ] Check Android 10 through Android 16 where available.
- [ ] Verify system font/display scaling at 1.0x, 1.3x, and 2.0x.
- [ ] Verify edge-to-edge/inset behavior on Android 15/16 devices.
- [ ] Confirm permission prompts and role selection return to a correct, refreshed state.
- [x] Add instrumentation regressions for Contacts and Phone state permission revocation; verify VIP ring override fails closed on next Activity resume and can be explicitly re-enabled after access is restored.
- [x] Add instrumentation coverage for Notification Policy Access revocation and recovery.
- [ ] Verify Notification Policy Access revocation while VIP override is enabled on a physical device.

## 3C — Lifecycle and workflow regression

- [x] Add biometric gate persistence coverage across Activity recreation.
- [ ] Verify biometric prompt behavior after process death, actual device reboot, lock-screen credential changes, and biometric enrollment changes.
- [ ] Test app update and app data/Keystore mismatch recovery on hardware.
- [ ] Verify call-screening role loss and re-grant behavior on hardware.
- [x] Add history clearing UI regression; verify the activity list clears and allow/block rules remain intact.
- [x] Add encrypted history retention/cap/clear regression coverage in storage instrumentation tests.
- [x] Add automated region settings validation and persistence checks.
- [x] Add interaction tests for allow/block creation, normalized persistence, invalid-number rejection, and allow/block removal.
- [ ] Exercise revocation of every runtime/special access (Contacts, Phone state, Notification Policy Access, Call Screening role) and verify recovery without reinstalling.

## 3D — Physical manufacturer validation (not executable in hosted CI)

Run the full matrix with a real SIM and at least one current device from each available family. Record model, Android version, security patch, build SHA, result, and evidence for every row.

| Manufacturer / software | Required checks | Status |
|---|---|---|
| Google Pixel / AOSP | Call screening role, incoming calls, reboot, biometric gate, TalkBack, font scaling | Not run — physical device required |
| Samsung / One UI | Role priority, battery/background restrictions, DND/ringer restore, permission revocation | Not run — physical device required |
| Xiaomi / Redmi / POCO / HyperOS or MIUI | Autostart/background restrictions, call screening delivery, DND/ringer restore | Not run — physical device required |
| Motorola / near-stock Android | Call screening, process recreation, ringer restore | Not run — physical device required |
| OnePlus / OPPO / ColorOS/OxygenOS | Background restrictions, role handling, call delivery, restore behavior | Not run — physical device required |

For every device, test:
1. Set CallGuard as the Android Call Screening app; verify Android's role confirmation and role status.
2. Receive allowed, blocked, unknown, private, malformed, and saved-contact calls with screen on/off.
3. Verify a VIP call can temporarily override Silent/Vibrate/DND only when opted in and permissions are granted.
4. Change ringer/DND manually during a call; confirm CallGuard does not overwrite the newer user choice.
5. Revoke Contacts and Phone state permissions in Settings; verify VIP override turns off and app remains usable.
6. Revoke Notification Policy Access and the Call Screening role; verify clear status and recovery after re-grant.
7. Force-stop, relaunch, reboot, update, and test biometric lock and encrypted history preservation.
8. Run TalkBack and 1.0x / 1.3x / 2.0x font scaling; inspect portrait, landscape, compact display, and touch targets.

Do not mark a manufacturer row complete without physical evidence. Hosted Android emulators cannot validate OEM telephony stacks, vendor battery managers, SIM/carrier behavior, or OEM-specific DND restrictions.

## Current implementation notes

The accessibility pass in `MainActivity.kt` marks section titles as headings on API 28+, uses polite live regions for status/history, labels phone-number and region inputs, and provides explicit switch descriptions and 48 dp minimum touch heights. Dashboard statistic cards reflow into a two-column rules row plus a full-width activity row and can grow vertically with larger text.

`RuleManagementInstrumentedTest.kt` covers allow/block creation, normalized persistence, invalid-number rejection, allow removal, block removal, and region validation. `SecureStorageInstrumentedTest.kt` covers encrypted history retention, the 1,000-event cap, clearing, missing-key preservation, and legacy migration. `LifecycleAndRecoveryInstrumentedTest.kt` adds Contacts / Phone state / Notification Policy Access revocation and recovery, biometric lock persistence across Activity recreation, history-clear UI behavior while preserving rules, and landscape / 2.0x font-scale dashboard smoke tests. Process-death and reboot checks remain manual because terminating or rebooting the target device from its own instrumentation process is not a reliable substitute for an independent device-level test.

These are test definitions, not passing results. They must pass in CI on the exact commit. Physical TalkBack, large-font, reboot, telephony, DND, and OEM validation remain open.

## Validation record

| Check | Status | Evidence |
|---|---|---|
| JVM unit tests | Awaiting latest head CI | Latest test additions require a completed run |
| Debug APK assembly | Awaiting latest head CI | Latest test additions require a completed run |
| API 35 emulator smoke | Awaiting latest head CI | Latest test additions require a completed run |
| Dashboard and rule Espresso checks | Added; awaiting CI | `DashboardAccessibilityInstrumentedTest.kt`, `RuleManagementInstrumentedTest.kt` |
| Contacts / Phone state / DND access revocation and recovery; biometric Activity recreation; landscape; 2.0x font scale | Added; awaiting CI | `LifecycleAndRecoveryInstrumentedTest.kt` |
| History retention, cap, missing key, migration, clear | Added; awaiting CI | `SecureStorageInstrumentedTest.kt` |
| TalkBack / font scaling / compact-screen validation | Not verified | Physical device required |
| Reboot / SIM telephony / OEM call screening and VIP restore | Not verified | Physical device required |

Do not close a validation item without current evidence for the exact commit under test.
