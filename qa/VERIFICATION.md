# Verification record

Verified on 2026-11-01 using PowerShell, Gradle, and ADB. The rebuilt package is `com.abdellatif.fleur`; the original package and APKs are separate.

Test reports and screenshots are committed. Raw device dumps, command logs, sample exports, and built APKs remain local and are ignored by Git.

## Automated coverage

- JVM: exact decimal parsing and invalid input, leap/month-end recurrence, custom intervals/weekdays, anchored following-series edits, moved/deleted occurrences, deletion without resurrecting an exception, account transfer conservation, continuous custom month boundaries, and stable reorder identities.
- Android persistence: close/reopen SQLite, complete JSON round trip, fractional/negative money and broken references rejected before replacing data, multiline CSV quoting, native PDF generation, and long-note PDF pagination.
- Android UI: income/expense creation, search, calendar, editing, paid status, deletion, savings account/goal creation, transfer, long-press reorder, custom category name/icon/color, monthly series creation/deletion, budget editing, debt-to-cash transfer totals, and light/dark reference screens.
- Android widget: bind a real AppWidgetHost, render a debt-to-cash transfer balance, and confirm amounts/details are hidden when App Lock is enabled. The test removes its own widget host afterward.

The tests use temporary databases; populated screenshots never seed the personal budget. The widget test supplies a state directly to the widget renderer and does not write its fixture to the database.

## Android platform checks

- Android's real document picker created JSON, CSV, and PDF in Downloads. Their pulled copies are `picker-backup.json`, `picker-transactions.csv`, and `picker-report.pdf`.
- Selecting the JSON backup displayed the replace-data confirmation with account/transaction counts. Confirming restore created `files/before-restore.json` in private app storage.
- The notification permission dialog appeared. Allowing it registered the daily `com.abdellatif.fleur.REMIND` alarm at the selected local time with a 15-minute delivery window, checked using `dumpsys alarm`.
- Device credential confirmation opened Android's PIN screen and accepted the emulator's temporary test PIN. The correct PIN opened the budget and Settings. Returning from the launcher after leaving Settings displayed only the locked screen, checked from actual MainActivity UI dumps (`pin-result.xml`, `unlocked-settings.xml`, `confirmed-relock.xml`).

## Screenshots

`qa/screenshots/` contains nine full-resolution captures of the running Compose UI:

1. Home with budgets and category spending.
2. Insights with striped donut and percentage totals.
3. Grouped transactions.
4. Calendar transactions.
5. Spending, savings, wedding goal and debt accounts.
6. Category selection grid.
7. Transaction editor.
8. Dark settings.
9. Dark home.

The fixture includes future entries: period totals include those entries while account balances only include paid entries through today. This is deliberate and separately asserted in tests. These screenshots use an instrumentation activity; Android system-bar appearance is verified separately in the actual MainActivity.

## Practical limits

- This is a native Kotlin rebuild using recovered fonts, paper textures and vector icons. The seven supplied screens guide layout; pixel identity and frame-rate parity with the original on every phone are not claimed.
- Android API 29+ is supported. The final acceptance device is the headless Pixel 7 emulator on Android 16. Earlier workflow checks also ran on an Android 10 device; there is no broad hardware or accessibility-device certification.
- Lint reports no errors. It retains warnings for original complex vector paths, small reference typography, unused starter resources, widget size hints on older Android versions, and an obsolete lint check in the existing lifecycle dependency.
- Reminders use inexact battery-friendly alarms. Alarm registration and notification permission are checked; no overnight delivery test is claimed.
- App Lock uses the existing device PIN/pattern/password. It secures the UI and screenshots, and masks widget amounts; JSON exports are ordinary unencrypted files chosen by the user.
- Backup/restore uses this app's own versioned `fleur-personal` JSON format. Importing the original developer's backup format and Apple iCloud/shared budgets are not implemented.
- Both debug and optimized personal APKs use the local Android debug signing key. Publishing and release-key management are outside this personal build.

## Issues found during verification

- A debug input-dispatch ANR occurred while the emulator and R8 compilation ran together. The Android report recorded CPU and I/O pressure. This is retained in `last-anr.txt` and `anr-details.txt`; performance parity with a real phone has not been established.
- The starter project's experimental R8 `packageScope` configuration produced a release-only Kotlin superclass access error during startup. It was replaced with whole-app optimization and a keep rule for the reflected AndroidViewModel constructor. See [Android's guidance on optimization scope](https://developer.android.com/topic/performance/app-optimization/optimize-specified-packages).
- App Lock now hides the budget on `onPause`, and a restore confirmation waits for authentication when returning from the system picker.

## Final results

- `:app:testDebugUnitTest`: **10 tests, zero failures/errors** (`unit-tests.xml`).
- `:app:connectedDebugAndroidTest`: **8 tests, zero failures/errors** (`android-tests.xml`, `acceptance.log`).
- Debug lint: **zero errors**, warnings described above. Release lint/build succeeded (`final-build.log`).
- Personal APK: `../dist/Fleur-Personal-1.0.apk`, **13,811,576 bytes**, version 1.0 (code 1), Android 10/API 29 minimum, target API 37. Includes ARM and x86 variants.
- APK SHA-256: `341d2e114e7304bad2c046116ccc32385bb6ce700ace6cddacd1e7712347b086`. Android APK v2 signature verified (`personal-apk-signature.txt`, `../dist/SHA256SUMS`).
- Final optimized APK installed over the debug build without losing the saved budget/app-lock preference, started, and unlocked using the device PIN (`release-fixed-start.xml`, `release-fixed-home.xml`).
- With App Lock enabled, selecting a JSON backup returned to the locked screen without displaying its restore confirmation. Entering the correct device PIN revealed the confirmation; restore completed and persisted after background/reopen (`release-restore-locked.xml`, `release-restore-authenticated.xml`, `release-restored-reopen.xml`).
- Final optimized release created an exact 12.34 expense, persisted it, showed it in Transactions, deleted it with confirmation, and returned to an empty budget (`release-crud-created.xml`, `release-crud-list.xml`, `release-crud-clean.xml`).
- No AndroidRuntime errors or ActivityManager errors were emitted during the final release smoke run (`release-runtime.log`). No new ANR occurred in this run. This does not establish frame-rate or startup parity on physical hardware.
- `screenshots/10-release-main.png` shows the actual release MainActivity and system bars. The temporary emulator PIN was removed. Test fixtures were not packaged in the APK.

Reproduce from PowerShell in `fleur/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest
```

Run device tests and release compilation separately when the workstation is under memory/CPU pressure.
