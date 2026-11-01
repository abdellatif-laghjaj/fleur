# Fleur rebuild

The target is a working Kotlin Android app in this project matching the seven supplied screenshots and the existing Android Fleur 1.1.1 behavior. All locally implemented features are available without billing, ads, or subscription prompts. The app has its own application ID, `com.abdellatif.fleur`, and does not overwrite the original.

## Reference evidence

- Screenshots supplied in the chat: summary, donut insights, accounts, category grid, transaction list, calendar, and transaction editor.
- `../decompiled/sources/j7/e.java`: original account, category, budget, recurrence, paid-status, carryover, and transaction schema.
- `../decompiled/sources/com/fleurbudget/MainActivity.java`: app entry and backup behavior.
- `recovered-strings.tsv`: complete recovered application text, including repeat options, account types, settings, and help.
- Original paper textures, fonts, and vector category/tab icons are available in `../decompiled/resources/`.

## Required behavior and acceptance checks

- [x] Reproduce paper background, script section headings, pastel category icons, dashed dividers, pink floating add button, and four-tab navigation.
- [x] Summary: period and account filters, income/expense/balance, overall/category budgets, remaining/spent switch, carryover, debt summary.
- [x] Transactions: income, expenses and transfers; create/edit/delete; date, account/category, notes and paid status; exact decimal arithmetic.
- [x] Recurrence: daily, weekly with selected days, fortnightly, monthly, quarterly, yearly, every n weeks/months, last day of month, end date; single occurrence and following-series edits/deletes.
- [x] Transactions tab: date groups, search, type/category/account/paid filters, calendar, future transactions, category/account drilldown.
- [x] Insights: expense/income toggle, striped donut, percentage/category totals and drilldown, spending over time.
- [x] Accounts: spending/savings/debt, initial balances, goals/limits, transfers, balance adjustment, hide/unhide, reorder, safe delete.
- [x] Categories: search/grid selection, custom name/icon/color/type, budgets, reorder and safe removal.
- [x] Preferences: currency, dark/system mode, accent/theme, heading fonts/size, month start, week start, period frequency, default account, carryover, decimal display, paid toggle, haptics, budget/debt visibility.
- [x] Backup/restore, CSV and PDF export through Android document picker; validate before replacing data.
- [x] Daily reminders, device-credential app lock and home-screen summary widget.
- [x] Durable local storage; no monetization or analytics SDKs; no demo transactions inserted without the user's action.
- [x] JVM tests for money, recurrence and accounting; instrumentation for persistence and UI workflows.
- [x] Build APK, install on emulator, exercise workflows, inspect actual screenshots, and check crashes/layout/accessibility.

Apple-only iCloud sync/sharing strings were bundled into the Android APK. They are not an Android service we can reproduce by connecting to the original developer's infrastructure. Android backup/restore and sharing exported files must work; no fake sync or sharing controls should be presented as working.

Implementation is complete for the local Android feature set above. Evidence and practical limits are recorded in `../qa/VERIFICATION.md`. Automated tests cover the principal editing, accounting, recurrence, storage and appearance workflows; platform integrations also receive manual emulator checks. This is a Kotlin rebuild using the recovered assets and screenshots, not the original unobfuscated source or a claim of pixel identity on every device.
