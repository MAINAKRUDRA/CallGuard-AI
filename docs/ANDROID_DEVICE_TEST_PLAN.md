# CallGuard AI — Android Device Test Plan

## Purpose

Validate call-screening behavior on a physical Android handset. An emulator launch is not proof that cellular call screening works with a carrier, SIM, dialer, or OEM implementation.

## Test setup

- Android 10 (API 29) or newer phone with an active SIM.
- A second phone to place test calls.
- Debug APK produced by the GitHub Actions workflow.
- Test handset country region set correctly in CallGuard AI (for example, IN).
- Do not use real customer or sensitive contact data during QA.

Install the APK:

```bash
adb install -r app-debug.apk
adb shell monkey -p com.mainak.callguardai 1
```

Open CallGuard AI and grant the Android **Call Screening** role when prompted. Grant Contacts permission only if testing contact import or VIP ring override. Grant Phone state and Notification Policy Access only when testing the optional VIP ring override.

## Test cases

| ID | Scenario | Steps | Expected result |
|---|---|---|---|
| DEV-01 | Role onboarding | Open app; enable screening; accept system role prompt | App reports screening role enabled |
| DEV-02 | Explicit allow | Add test caller as VIP; call from that number | Call is allowed; event reason indicates explicit allow |
| DEV-03 | Explicit block | Add test caller to block-list; call from that number | Call is rejected by screening service |
| DEV-04 | VIP precedence | Add same test number to both allow and block | Call is allowed because explicit allow takes precedence |
| DEV-05 | Unknown caller | Call from a number absent from both lists | Call is allowed by safe default |
| DEV-06 | Hidden caller ID | Place a call with caller ID withheld, where carrier permits | Call is allowed; no number is inferred |
| DEV-07 | Local number format | Set region to IN; add a valid local-format Indian number; call from it | Rule and incoming number canonicalize to the same E.164 number |
| DEV-08 | International format | Add a +country-code number; call from matching international number | Rule matches independent of device region |
| DEV-09 | Contact import consent | Tap import; cancel confirmation | No contacts are imported |
| DEV-10 | Contact import | Confirm import and grant permission | Valid contact numbers appear in VIP allow-list |
| DEV-11 | Permission denial | Deny Contacts permission | Manual allow/block controls continue to work |
| DEV-12 | Activity retention | Inspect activity after calls | Events show masked numbers and reason labels |
| DEV-13 | Rule persistence | Add test rule; force-stop and reopen app | Encrypted rule remains available |
| DEV-14 | Upgrade migration | Install an older build with test rules, then update to this build | Existing rules migrate and remain usable |
| DEV-15 | No screening role | Remove role in Android settings and reopen app | UI reports role not enabled; no false success state |
| DEV-16 | VIP rings in Silent | Enable VIP ring override and grant both requested permissions; set phone to Silent; call from an explicit VIP number | Phone temporarily rings; original Silent mode is restored when the call ends |
| DEV-17 | VIP rings in Vibrate | Repeat DEV-16 with Vibrate mode | Phone temporarily rings; original Vibrate mode is restored when the call ends |
| DEV-18 | DND remains respected | Enable Do Not Disturb and call from an explicit VIP number | CallGuard does not change ringer mode or bypass DND |
| DEV-19 | Permission denial | Deny Phone state permission or Notification Policy Access during setup | VIP ring override remains off; normal call screening continues |
| DEV-20 | Ringer restoration | Enable override, receive VIP call, then end/reject the call | Original ringer mode is restored; reopen app to confirm displayed phone mode |

## Evidence to capture

For each test, record:
- Device manufacturer/model and Android version.
- SIM carrier and selected app region.
- Test ID and pass/fail.
- Timestamp and sanitized notes.
- Relevant screenshot with phone numbers hidden.

Do not attach raw call logs, phone numbers, contacts, or SIM identifiers to public issues.

## Known limits

- Android/OEM behavior and carrier restrictions can differ.
- Emergency calls, spam protection, dual-SIM routing, call waiting, and caller-ID presentation require separate testing.
- VIP ring override must be tested on each target OEM: Android ringer-mode behavior, ringtone volume, DND, and telephony state broadcasts can differ. Do not claim support for DND bypass.
- This plan does not validate recording, two-way audio capture, AI answering, or SIP/carrier forwarding.
- Do not release broadly until critical test cases pass on the target handset models.
