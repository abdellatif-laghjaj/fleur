# Verification record

Verification began on 2026-11-01 using PowerShell, Gradle, and ADB. Latest checks are recorded below. The rebuilt package is `com.abdellatif.fleur`; the original package and APKs are separate.

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

- `:app:testDebugUnitTest`: **13 tests, zero failures/errors** (`unit-tests.xml`).
- Android instrumentation: **16 tests, zero failures/errors** (`android-tests.xml`; full run in `categories-device-tests.log`, final affected checks in `categories-final-device-tests.log`).
- Debug lint: **zero errors**, warnings described above. Release lint/build succeeded (`final-build.log`).
- Personal APK: `../dist/Fleur-Personal-1.1.apk`, **14,435,582 bytes**, version 1.1 (code 2), Android 10/API 29 minimum, target API 37. Includes ARM and x86 variants.
- APK SHA-256: `f0a941aa876f8bd90330bd243da7a93171ebf33e427e0b98845ac4758aaa3979`. Android APK v2 signature verified (`personal-apk-signature.txt`, `../dist/SHA256SUMS`).
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


## Performance version restored after release comparison

Restored the performance implementation from `4568fce` after the user preferred it in the release comparison. Application and Android test sources match that revision exactly. A fresh optimized release build, all 10 JVM tests and debug lint passed (`restore-performance-build.log`). The existing 13 passing Android case results apply to this restored source; Android device tests were not rerun for the restoration. The rebuilt APK is non-debuggable, its APK signature verifies, and `dist/SHA256SUMS` records both comparison builds. The standard `dist/Fleur-Personal-1.0.apk` now contains the performance version again.


## Categories and launcher branding on 2026-11-02

- New installs use Food & Drinks instead of Groceries/Restaurant/Coffee; Transportation instead of Travel/Taxi; and Communication & PC instead of Internet. Added Shopping, Health & Care, Charity and Family Support expense categories. Entertainment uses the original movie/clapperboard icon. Existing Parents income remains separate from Family Support expenses.
- SQLite database version 2 applies the same update once to existing budgets. Category merges retarget entries and recurring overrides, preserve deleted occurrences, and combine category budget amounts with checked arithmetic. Renamed custom categories and existing destinations keep their IDs, colors and icons; destination/addition names match without case sensitivity. Deleting a new default after upgrading does not cause it to return on reopening. Backup import continues to restore the categories stored in that backup.
- The category picker has 58dp icon containers (about 33dp glyphs), 13sp labels and at least 132dp cell height. Labels can wrap onto two lines. The separate icon chooser uses 40dp glyphs with at least 80dp row height.
- Generated transparent hibiscus artwork with the built-in image-generation tool, based on the user's reference. The original alpha is retained in `app/src/main/res/drawable-nodpi/fleur_launcher_art.png`; the exact prompt is in `docs/branding/app-icon-prompt.md`. Adaptive foreground and monochrome layers use a proportional 12% inset over a near-black background. Superseded density-specific launcher images were removed. `screenshots/22-launcher-icon.png` is rendered by Android's AdaptiveIconDrawable, not a mockup.
- A fresh debug/release build, all 13 JVM tests and debug lint passed (`categories-migration-build.log`). The full 16-case Android suite passed (`categories-device-tests.log`). After the final case-insensitive destination matching change, both persistence tests, reference screens and launcher rendering were rerun successfully (`categories-final-device-tests.log`); the new JVM custom-category case also covers lowercase existing names. The launcher is checked at 48, 192 and 512 pixels and includes a themed monochrome layer.
- Functional device checks use a disposable Android 16 emulator. Its read-only guest has background Google apps disabled and heads-up notifications suppressed for captures; the physical phone was not used. Early captures contained an Android System UI timeout overlay and were discarded. Gallery captures were refreshed after dismissing the pending system overlays and warming up the guest. No frame-rate claim is made from this environment.

The final reference workflow also passed at the original 1080x2400 / density 420 capture size (`categories-gallery-tests.log`); the refreshed gallery was inspected without system overlays. The optimized 1.1 APK installed over the debug build and launched successfully. Native Android UI selected Family Support, saved a 12.34 expense, and displayed it in Transactions (`categories-release-saved-transaction.xml`). `categories-release-runtime.log` contains no fatal AndroidRuntime exception. The signing certificate remains the same personal-build key. SQLite version 2 downgrades to version 1 are not supported.


## White launcher background on 2026-11-02

Changed the adaptive icon background from near-black to white. The generated transparent flower PNG is byte-for-byte unchanged. Debug, test and optimized release APK builds passed (`white-icon-build.log`), and the existing launcher renderer test passed at 48, 192 and 512 pixels (`white-icon-device-test.log`). The refreshed README icon preview was inspected and its exposed background is opaque white. The APK signature and checksum were verified. Broader app tests and physical-phone checks were not repeated for this resource-only change.


## CSV import and export, version 1.3 on 2026-11-07

- Settings now has Export CSV and Import CSV. JSON backup/restore and PDF export controls were removed, along with the unused PDF writer. Internal state encoding remains for SQLite and the private pre-import recovery copy.
- CSV import accepts Fleur and Cashew headers, validates the entire file before replacing anything, previews account/transaction counts and the balance through today, and replaces accounts, transactions and category budgets only after confirmation. Appearance and security settings are retained. Import errors leave the saved budget intact, and failed saves allow retrying the confirmation.
- Amounts use exact hundredths. The parser handles UTF-8 BOM, multiline fields, doubled quotes, CRLF/LF, and empty fields. It rejects malformed quoting, mismatched columns, invalid dates, fractional cents, contradictory income signs, mixed currencies, unsupported transaction types, partial payments and oversized input.
- CSV export includes history through today, opening-balance rows, account types, transfers, paid status and complete notes. Formula-like text is escaped and decoded on import. Duplicate account names are rejected before export because CSV account names must identify accounts unambiguously.
- CSV is a transaction snapshot. Imported rows do not generate recurring charges. Repeat descriptions are preserved in notes. Future schedules, goals, budgets and repeat exceptions are not restored from CSV.
- All 20 JVM cases passed. The full Android run passed 19 of 20 cases; the supplied-file check initially tried to read Downloads directly and hit scoped-storage EACCES. Both CSV persistence cases passed after moving that private test input into the test app's own external folder. `android-tests.xml` records the 20 latest passing cases across those runs. Logs are `csv-final-build.log`, `csv-device-tests.log` and `csv-file-device-tests.log`.
- The supplied seven-row Cashew CSV reconciled exactly and survived SQLite save/reopen in a temporary test database. The original file, its notes, personal balances, device dumps and private APK copies are excluded from Git.
- Native Android document-picker checks in the optimized release imported a synthetic two-transaction Cashew file, confirmed the preview and exported a CSV containing both transactions and its opening balance. An independent decimal calculation matched the sample balance exactly. `screenshots/30-csv-import-preview.png` contains only synthetic data. The final build changes only singular/plural wording in that preview, with a successful release build and lint rerun in `csv-release-final.log`.
- Lint reports no errors. Existing dependency/vector/widget warnings and the incompatible legacy lifecycle custom lint checks remain. The new date formatter reads the current locale rather than storing it in a static field.

The signed release is `dist/Fleur-Personal-1.3.apk`, versionCode 4, 14,468,354 bytes, SHA-256 `213f52635439068b558a5f1ba6a33f2e698e70429b8e316cb0a864e9e5ea4117`. Its APK v2 signature verifies. The same APK was installed with `adb install -r` on the connected OnePlus CPH2747; a pulled installed APK has the identical SHA-256, and its package flags do not include DEBUGGABLE. The initial CSV import was superseded by the user's request for direct entry. A new spending account was created through the native UI, the imported account and its seven records were deleted, and six dated expenses were recreated manually. The correction income category was removed. The account opening balance was set to match the user's newly supplied current balance, without creating any income/adjustment transaction. Monthly frequency, the overall limit and three category limits were entered and read back from the app. All six expense amounts and the final balance were verified, including after a full app restart. Personal amounts, notes and screenshots remain local and excluded from Git. The temporary screen-awake helper was stopped, and the phone was left on Home.

The branch also includes the earlier paper/theme and Insights work already in the working tree: white switch thumbs with 48dp targets, softer neutral dark paper, inline category transaction expansion/editing, date/amount labels and corrected aggregation for daily bars, and collapsible budget outlooks. Forecast tests cover daily pace, scheduled-expense minimums, past/future periods, one-day ranges and leap months. The existing app workflow tests cover expansion/collapse in both themes, transaction editing and switch persistence. No physical-phone frame-rate benchmark was performed. The generated README cover and exact built-in image-generation prompt are saved under `docs/branding/`.

The two screenshot-producing workflows passed again in `csv-gallery-refresh.log`, but Android System UI timeout overlays spoiled that refresh. Those captures were rejected and are not published. Existing gallery images from earlier releases are retained and labelled accordingly; the new CSV settings and native import-preview captures were inspected without overlays and contain only synthetic data.


## UI reference audit, version 1.4 on 2026-11-07

| Requirement | Current evidence |
| --- | --- |
| Cover matches the new flower identity | README references the inspected red-hibiscus cover on white paper. Original generated asset and exact prompt are in `docs/branding/`. |
| White Settings switch thumbs in dark mode | `FleurSwitchAppearanceTest` renders the real component in both themes, checked and unchecked, verifies toggle semantics and samples near-white, opaque thumb pixels. The custom switch no longer draws a Material press ripple over its white thumb. |
| Inline category transactions | The expanded Expense and Income fixtures show related dated transactions, support collapse and open a transaction editor. `23-insights-expanded-dark.png` and `31-insights-income-dark.png` are native app-content captures. |
| Softer dark paper | The theme uses neutral `#30302F` paper with a lighter texture blend. The inspected native capture has background RGB 43,43,42, compared with 26,27,28 in the earlier dark gallery. |
| Understandable daily bars | `24-daily-spending-dark.png` shows date endpoints, an amount scale and a one-day-total explanation. Long ranges combine days and scale against the combined values; JVM coverage checks complete coverage and unchanged totals. |
| Collapsible category budget outlook | `25-budget-outlook-dark.png` and `26-budget-outlook-light.png` show recorded spending, forecast, budget line, expected remaining amount and selected period end. Existing JVM tests cover scheduled-expense minimums, past/future periods and leap/one-day periods. |

The latest reference correction removes disclosure arrows and hides the category-header divider while expanded; the divider appears after the final transaction instead. Empty-note rows no longer repeat the account name, and the vertical guide aligns with the category icon. The full row remains tappable and exposes Expanded/Collapsed accessibility state. The same expanded-group divider treatment applies to Home budget outlooks.

All 20 JVM tests, debug/test/release builds and debug lint passed (`insights-final-build.log`). Two focused Android cases passed together (`insights-final-device.log`), covering Income/Expense expansion, transaction editing, light/dark budget outlooks, switch persistence and white switch rendering in both states/themes. The other Android case results in `android-tests.xml` are retained from the previously documented runs, bringing the latest case ledger to 21; the entire 21-case suite was not rerun for this UI-only change.

The first build was stopped before lint completed and was rerun successfully. Capture checks caught an activity-window capture being used for a dialog and a pointer click racing a moving lazy row. Main-page examples now capture native activity content, frame the category group explicitly, and wait for semantic expansion before scrolling. Dialog screenshots use the existing full-window capture path; rejected dialog-content images are not published. The first strict switch pixel checks exposed the Material press layer tint; disabling that indication resolved the interaction mismatch. Pixel checks allow small capture rounding while rejecting colored or grey thumbs.

The final optimized release is `dist/Fleur-Personal-1.4.apk`, versionCode 5, 14,468,350 bytes, SHA-256 `2e3bd45db07b4f69f36b3e3ea29dc5594adc4cb0a84c9060dfcd1cca268e8f2f`. The APK v2 signature verifies. It was installed on the connected OnePlus using `adb install -r`; the pulled installed APK has the identical hash. The expanded Food & Drinks group was inspected on the physical phone, including both related transactions and the footer-only separator. The saved balance was read back unchanged after the layout update. No financial model, database migration or personal transaction/budget data was changed by this release. Personal device screenshots stay local and excluded from Git. No physical frame-rate benchmark was performed.
