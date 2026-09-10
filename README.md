# OTPless Headless Android Demo

A minimal reference app for integrating the [OTPless Headless SDK](https://otpless.com/docs/frontend-sdks/app-sdks/android/headless/intro)
on Android — phone number → OTP (or silent network auth) → verified token, with no
OTPless-hosted UI. It exists to be read end-to-end, not shipped: every SDK call site
is small enough to copy into a real app.

## What it demonstrates

- **Headless OTP auth**: enter a phone number, receive an OTP via SMS, verify it —
  see [`HomeScreen.kt`](app/src/main/java/com/otpless/demo/otplessheadlessdemo/HomeScreen.kt)
  and [`OTPScreen.kt`](app/src/main/java/com/otpless/demo/otplessheadlessdemo/OTPScreen.kt).
- **Silent network auth (SNA)**: when the carrier supports it, OTPless verifies over
  the data connection with no OTP at all — same `INITIATE`/`VERIFY` callback flow,
  just a different `authType`.
- **Every SDK callback surfaced live**: an in-app event log (tap "Event log" at the
  bottom of any screen) mirrors every `OtplessResponse` the SDK delivers, with the
  full JSON payload — see [`OtplessLogger.kt`](app/src/main/java/com/otpless/demo/otplessheadlessdemo/OtplessLogger.kt).
  The same lines also go to Logcat under tag `OTPLESS`.
- **Logout and re-test**: after a successful login, "Logout & test again" calls
  `OtplessSDK.cleanup()` and re-initializes the SDK, so you can run the flow
  repeatedly without reinstalling — see [`OtplessManager.kt`](app/src/main/java/com/otpless/demo/otplessheadlessdemo/OtplessManager.kt).

## Requirements

- Android Studio (recent stable) with an SDK Platform 36 installed
- `minSdk 23`, `compileSdk`/`targetSdk 36` — the SDK's own `androidx.core-ktx`
  transitive dependency requires 36
- A real device or emulator with a SIM/network for OTP delivery and SNA to work
- An OTPless App ID — [sign up](https://otpless.com/) and create one in the dashboard

## Setup

1. Open the project in Android Studio (or build from the CLI, see below).
2. Set your App ID in
   [`OtplessManager.kt`](app/src/main/java/com/otpless/demo/otplessheadlessdemo/OtplessManager.kt):
   ```kotlin
   const val APP_ID = "YOUR_APP_ID"
   ```
3. Update the deep-link scheme in
   [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) to match, lowercased:
   ```xml
   android:scheme="otpless.<your_app_id_lowercase>"
   ```
4. Run on a device or emulator.

## Build & run from the CLI

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.otpless.demo.otplessheadlessdemo/.MainActivity
```

## Project layout

| File | Role |
|---|---|
| `MainActivity.kt` | Hosts the fragment container + the event log console; owns SDK init on app start and forwards deep-link intents via `onNewIntent`. |
| `HomeScreen.kt` | Phone number entry; handles `INITIATE`/`VERIFY`/`DELIVERY_STATUS`/`ONETAP` for both the OTP and SNA paths. |
| `OTPScreen.kt` | OTP entry and verification. |
| `SuccessScreen.kt` | Shown on `ONETAP`; displays the token and drives logout. |
| `OtplessManager.kt` | Single place owning the App ID and the init/logout lifecycle. |
| `OtplessLogger.kt` | Mirrors every callback to Logcat and to the on-screen event log. |

## Network security config

SNA needs plaintext HTTP to a handful of carrier endpoints (Jio, Airtel, Vi,
Sekura). The manifest points `android:networkSecurityConfig` at
`@xml/otpless_network_security_config`, which resolves from the SDK's own AAR —
there's no local copy to maintain in this app.

## Notes

- The demo hardcodes `+91` as the phone country code — adjust in `HomeScreen.kt`
  and `OTPScreen.kt` for other countries.
- Error codes surfaced in `handleInitiateError`/`handleVerifyError` are logged via
  `println` as placeholders; a real integration should react to them (retry,
  fallback channel, etc.) per the
  [OTPless error reference](https://otpless.com/docs/frontend-sdks/app-sdks/android/headless/intro).
