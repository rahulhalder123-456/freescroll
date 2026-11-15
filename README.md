# FreeScroll

Counters the 10 attention traps of social media: infinite scroll, red badges, streaks, anticipation, filtered reality, customized feeds, artificial urgency, likes and followers, simplicity, and notification buzz.

- `android/` – Android app. Logic in Rust (`android/core`), thin Java shell (`android/app`). Uses an Accessibility Service to show an intention gate and a scroll stopping point over social apps and social sites in browsers, and a notification listener to silence social buzz. No internet permission; nothing leaves the phone.
- `extension/` – Chrome/Edge extension for desktop web (also usable in Firefox for Android).

## Install the Android APK
Download `FreeScroll.apk` from the Releases page. It is signed with a debug key, so Google Play Protect may block it. See Releases notes for workarounds, or build it yourself with `android/build.ps1` (needs Rust + Android SDK/NDK + JDK).

Status: early test build. The Rust core is unit-tested; on-device behaviour is still being verified.
