# Emulator tests

`stub/build_stubs.ps1 -Packages com.facebook.katana,com.instagram.android,...` builds stand-in apps that use the
package names of real social apps and show a long scrolling list (no content from any real service).
Install them with `adb install`, enable the FreeScroll accessibility service, then run `scenarios.ps1`.

Scenarios cover: fresh open, gate, passing it, minimise/resume from Recents and the launcher, an unanswered
stop screen, break lock, switching between two apps, screen off/on, cold restart, Back button, rotation, volume
restore, and waiting away longer than the "ask again" time.
