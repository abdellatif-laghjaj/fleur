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
- Android instrumentation: **13 tests, zero outstanding failures/errors** (`android-tests.xml`; 12 passed in `perf-final-device-tests.log`, the corrected remaining test passed in `perf-scroll-final.log`).
- Debug lint: **zero errors**, warnings described above. Release lint/build succeeded (`final-build.log`).
- Personal APK: `../dist/Fleur-Personal-1.0.apk`, **14,108,424 bytes**, version 1.0 (code 1), Android 10/API 29 minimum, target API 37. Includes ARM and x86 variants.
- APK SHA-256: `43062b0ac1801713637955257477ca8e4418cd4a35f5b39180564600a8ed7d70`. Android APK v2 signature verified (`personal-apk-signature.txt`, `../dist/SHA256SUMS`).
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


## UI refinement on 2026-11-01

- Reused the original `circlebg_hq` / `circlebg_hq_dark` paint assets, tinted and layered twice as in the recovered implementation. Category and account badges, color-picker swatches, the selected-color previews, date badges and the add button now use their irregular watercolor edge and pigment texture.
- Shared centered alerts have rounded corners, centered copy, a thin divider and equal-width Cancel / confirmation buttons. Destructive confirmations retain their data-loss warning. Name/balance inputs, the color picker and restore confirmation share this style in both themes.
- Choice lists and account actions use bottom action sheets with a separate Cancel section. Account actions support editing, exact balance adjustment, hiding and confirmed deletion; the last account cannot be deleted.
- Bottom navigation has no ripple or press haptic; the selected icon and label still update. An image comparison of the active tab before/during a held press checks that no press overlay appears.
- `FleurTheme` also themes the activity-level restore confirmation without painting over the underlying screen.

Build, all 10 JVM tests and lint passed in `ui-final-build.log`. All 10 Android tests passed in `ui-device-tests.log`; `android-tests.xml` records their completed runner statuses. This includes balance validation, update/hide/edit/delete actions, cancellation, last-account protection, and the pixel comparison for a held navigation tab.

The emulator displayed a System UI timeout overlay during its initial boot. After dismissing it, the three screenshot-producing workflows were rerun and passed (`ui-screenshot-tests.log`). The refreshed images were inspected without the system overlay:

- `screenshots/11-account-actions-light.png` and `12-account-actions-dark.png`: account action sheets.
- `screenshots/13-delete-account-dark.png`: centered destructive confirmation.
- `screenshots/14-color-picker-dark.png` and `15-color-picker-light.png`: original watercolor swatches.
- `screenshots/16-restore-dark.png`: the activity-level restore alert in the installed optimized APK.
- `screenshots/17-release-main-dark.png`: the updated installed app.

The optimized APK installed and launched, switched to dark mode through the new choice sheet, opened Android's document picker and displayed the themed restore confirmation. Cancel returned to the budget without restoring data. `ui-release-runtime.log` contains no AndroidRuntime errors. The attached physical phone was not used for these checks.


## Input styling on 2026-11-01

- Replaced Material outlined text fields with one shared `BasicTextField` component: soft translucent rounded backgrounds, no outline or floating label, and separate labels where needed. Name fields are compact and centered; numeric, budget and search inputs use the same surface treatment.
- Text selection handles, selection highlight and carets follow the chosen accent. Fields retain accessible descriptions, numeric keyboards, validation and existing save behavior. The keyboard Done action clears focus.
- Debug/release builds, all 10 JVM tests and debug lint passed (`input-build-final.log`). All 11 Android tests passed (`input-device-tests.log`), including a new light/dark account-name editing test that checks compact width, explicit saving and unchanged balance. Existing workflows exercise search, categories, savings, budgets and recurrence.
- `screenshots/18-account-input-light.png` and `19-account-input-dark.png` show the new name field in both themes; the README gallery includes both. The screenshot-producing workflows were refreshed from the successful full test run.
- The emulator displayed Process system and System UI timeout overlays during cold boot before this app was installed. Both were dismissed; the final screenshots were inspected without those overlays. Physical-phone input behavior was not checked.
- The optimized release APK installed successfully. Native Android input typed `Main` into the account-name field, Save persisted it, and the account balance remained $0.00 (`input-release-typed.xml`, `input-release-saved.xml`). `screenshots/20-account-input-release.png` shows the actual release field and accent caret; `input-release-runtime.log` contains no fatal AndroidRuntime exception. The APK v2 signature and distribution checksum were verified.


## Navigation and scrolling performance on 2026-11-01

The reported phone installation came from Android Studio Run. That normally uses the debug variant; performance comparisons should use the R8-optimized release APK. The recovered original APK also contains Compose runtime/UI libraries, so the available evidence does not point to a different UI framework as the explanation. Its complete rendering/caching behavior has not been profiled against this rebuild.

- Removed the bottom-tab `Crossfade`, which kept outgoing/incoming pages composed and blended during each switch. Only the selected page is now composed.
- Added a saveable state holder for tabs. Insights retains its Income/Expense selection and lists retain their positions. The transaction-list reset no longer runs on every tab re-entry.
- Period occurrences and current/carryover/debt balances are calculated once per budget/range/date on `Dispatchers.Default` and shared by pages. The UI shows a loading indicator while a new snapshot is calculated; it never combines an old snapshot with a changed budget or period. Tab selection does not restart those calculations.
- Decoded paper/watercolor painters are shared through the theme and inherited by nested editor themes. List rows no longer each decode the same PNG as they enter composition. The original pixels, two-pass watercolor tint and light/dark variants are retained.
- Added tests for immediate page replacement with the animation clock stopped after one frame, and for preserved Insights selection/transaction scroll using 60 entries.

The first instrumentation attempt on an Android 16 emulator configured with 1536 MB RAM terminated before tests completed. The device log records ANRs in System UI, the keyboard, Google services and the test app, with high CPU/I/O pressure (`perf-emulator-pressure.log`). No performance numbers are inferred from that run. Physical-phone frame timing, comparison with the original, startup profiles and a broad hardware benchmark remain unmeasured.

Build, all 10 JVM tests and lint completed successfully in `perf-checked-build.log`. The full Android run passed 12 of 13 cases, including immediate first-frame replacement and all existing workflows. The new scroll/selection test initially looked for `Income` rather than the button's uppercase `INCOME`; after fixing the selector and waiting for page readiness, that case passed in `perf-scroll-final.log`. `android-tests.xml` contains the 13 latest passing case results across those two runs. Production code was unchanged during the test-selector reruns.

The successful functional retry used a disposable Android 16 emulator at 720x1600, density 320 and 2048 MB RAM, with background Google services disabled only in its read-only guest overlay. A pending System UI timeout overlay was dismissed before release visual checks. Screenshots from the earlier instrumentation pass contained that overlay and did not replace the README gallery. These checks establish behavior, not a frame-rate comparison with the original on the user's phone.

The optimized release APK installed successfully. Native Android UI checks switched tabs, selected Income in Insights, left and returned with that selection preserved, opened the account editor/color palette, and returned Home. The clean palette capture (`perf-release-palette.png`) was inspected and retains distinct watercolor tints and paper texture. `perf-release-runtime.log` contains no fatal AndroidRuntime exception. The release APK v2 signature and distribution checksum were verified.
