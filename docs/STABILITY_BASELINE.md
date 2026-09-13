# Phase 0 — Stability baseline

Branch: `arena/01a0990a-mustfa`
Release: https://github.com/hesham717/Mustfa/releases/tag/build-5

## Root cause of the startup crash

`MainActivity` inflates `com.leinardi.android.speeddial.SpeedDialView` from
`main_toolbar.xml`. SpeedDial 3.3.0 depends on `material >= 1.5`, whose
components enforce `Theme.MaterialComponents` and throw during inflation
because every activity theme in this app is `Theme.AppCompat.*`. The fix is to
stay on **speed-dial 3.2.0** (material 1.3.0), and this is now:

* pinned and documented in `app/build.gradle` (`fabSpeedDialVersion`);
* the `material` / `appcompat` versions we declare match what speed-dial 3.2.0
  brings transitively (1.3.0), so the dependency graph is deterministic;
* guarded by `DependencyPinsTest` so a future bump fails CI instead of the app.

## Other stability work in this phase

| Item | Change |
|---|---|
| Dependency conflicts | Removed duplicate `jcifs-ng` declaration; material/appcompat pinned to 1.3.0 |
| ProGuard (release) | Keep rules for SpeedDial, `FloatingActionButton`, `CrashReporter`; `-dontwarn` for ExoPlayer optional modules |
| Shell helper | `Shell.Builder.setShell()` restored (unit tests failed to compile); `IOException` from a missing `sh`/`su` no longer interrupts the calling thread |
| CI | `arena-apk-release.yml` now runs unit tests, builds **debug and release**, uploads the test report artifact and publishes both APKs |

## Verified

* `:app:testFdroidDebugUnitTest` — `PlayerGestureTrackerTest` (15) + `DependencyPinsTest` (2): all green
* `:app:assembleFdroidDebug` — OK
* `:app:assembleFdroidRelease` (minified, signed with `release.jks`) — OK
* Manual: Android 10 device, debug APK launches without crash (user report)

## Frozen artefacts

| APK | Package | Size |
|---|---|---|
| `Mustfa-debug.apk` | `com.droidtechlab.filemanager.debug` | 16.5 MB |
| `Mustfa-release.apk` | `com.droidtechlab.filemanager` | 10.1 MB |

Use `build-5` as the reference point for regression when starting Phase 1.
