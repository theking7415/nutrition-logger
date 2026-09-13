# Nutrition Logger

HealthifyMe reads from Health Connect but never writes to it. This app closes
that gap for food, and adds direct water logging that never depended on
HealthifyMe in the first place.

**Food:** you screenshot HealthifyMe's daily summary, share it to this app,
check the numbers it read, and tap write. The totals land in Health Connect as
a `NutritionRecord` for that day, where Google Fit and anything else reading
Health Connect will pick them up.

**Water:** logged directly — quick-log buttons in the app, a 1×1 home screen
widget, and a Wear OS tile on the wrist — each tap writes its own
`HydrationRecord` immediately, with no HealthifyMe involvement at all. See
"Why water isn't part of the OCR path" below.

## Why daily totals and not per-meal

Nothing downstream of Health Connect displays your meal breakdown — every reader
aggregates nutrition to a daily figure before showing it. One record per day
renders identically to fifteen meal-level records, so HealthifyMe stays your
meal-level source of truth and Health Connect just gets the numbers.

## Why water isn't part of the OCR path

Early versions synced HealthifyMe's reported water total alongside food. That
was dropped: Health Connect just sums every `HydrationRecord` for the day
regardless of who wrote it, so a HealthifyMe-reported total and directly-logged
taps would stack instead of agreeing — a day where you drank 2 L would read as
4 L. Water is now exclusively the direct-logging feature's job.

## Building it

1. Open Android Studio → **Open**, and select this folder.
2. Let it sync. It will offer to create the Gradle wrapper JAR (not checked in
   here, since it is a binary) — accept.
3. Plug in your phone with USB debugging on, and hit Run.

That is the whole deployment story. Sideloaded apps do **not** need Google's
Health Connect data-type approval — that review only applies to apps published
on the Play Store. You grant the permissions yourself on-device.

Requires Android 8.0+ (Health Connect's floor). On Android 14+ Health Connect is
part of the system; on Android 13 and below install it from the Play Store first.

## Using it

**Food — fast path:** on HealthifyMe's daily overview, tap its own **share**
button and pick Nutrition Logger from the share sheet. The app registers as a
share target for `image/*`, so it appears there alongside WhatsApp and the
rest — no need to screenshot manually, and no need to open the app first. OCR
runs on arrival, fields get pre-filled, you check them and write.

**Food — manual path:** open the app, pick the date, type the numbers in. The
screenshot step is a convenience, not a requirement.

The date defaults to today; tap the date to jump straight to a day (photos
carry no date of their own, and aren't always shared same-day), or use
Prev/Next to step one day at a time. Each day shows what Health Connect
already holds for it, so you can tell a missed day from a synced one.

**Water:** tap one of the quick-log buttons (250 / 500 / 750 ml) or enter a
custom amount, any time — it isn't tied to the date selector above, it always
logs "now". The home screen widget does the same for a default 250 ml tap
without opening the app. On the watch, tapping the tile (or the button in the
watch app) logs the same way from the wrist.

## The Wear OS tile

The watch has no working third-party Health Connect access of its own, so the
tile doesn't write anything locally — it relays the tap to the phone over the
Wearable Data Layer API, and the phone (which already holds
`WRITE_HYDRATION`) makes the actual `HealthConnectManager.logHydration()`
call. The `:wear` module has no Health Connect dependency at all as a result.

Tapping the tile launches a translucent, instantly-finishing activity
(Wear Tiles can only launch an intent or reload the tile from a click, not
run arbitrary code directly), which sends the amount to the phone; the
phone's `PhoneRelayListenerService` receives it, logs it, and updates the
home screen widget so both surfaces stay in sync regardless of which device
you tapped on.

## The two things that make food-sync safe to re-run

**Deterministic record IDs.** Every day writes with
`clientRecordId = nutrilogger-nutrition-YYYY-MM-DD`. Health Connect treats a
repeat insert with the same client record ID as an *update*, so syncing the
same day twice — or deliberately re-uploading an old photo — corrects it
rather than doubling it. Screenshot at lunch and again at dinner, or upload a
week late — the day ends up right either way.

**Blank means blank.** An empty field is omitted from the record entirely. It is
never written as zero, because a zero would tell every weekly average that you
genuinely ate no fibre that day.

Water logging works differently on purpose: each tap gets its own fresh ID, so
repeated taps *add up* over the day instead of overwriting each other.

## How the screenshot gets read

HealthifyMe's summary card has two properties that drive the parser:

**Every value is a `consumed / goal` pair** — `1,674 / 2,000 Cal`,
`90.7 g/150.0 g`. The parser matches the pair as a unit and takes the left-hand
number. That is much safer than "first number on the line", because each row also
carries a percentage (`60%`) that would otherwise win whenever OCR merges it in.

**It is a column layout.** ML Kit groups text by proximity, so the label column
(Proteins / Fats / Carbs / Fibre) and the value column usually come back as two
separate blocks — the label and its number are not adjacent in ML Kit's own
ordering at all. `reconstructRows()` throws that ordering away and regroups every
recognised line by vertical position, rebuilding `Proteins  90.7 g/150.0 g  60%`
as one row. If even that fails, a positional fallback maps four gram-pairs onto
the four macros in HealthifyMe's fixed on-screen order — but only when *no* label
matched, so a partial match is never silently overwritten by a guess.

Both paths, plus the merged-percentage and missing-space cases, are pinned in
`app/src/test/java/com/nutritionlogger/ocr/ScreenshotParserTest.kt`. Run them with
`./gradlew testDebugUnitTest` — pure JVM, no device.

## When the OCR guesses wrong

It will, sometimes — HealthifyMe puts the eaten value and the goal value next to
each other and OCR cannot tell them apart. Two mitigations are built in:

- Every value lands in an editable field. Nothing is written without your
  confirmation.
- **Show OCR text** dumps the raw recognised lines. That tells you exactly what
  the recogniser saw.

If a field is consistently wrong, the fix is in `ocr/ScreenshotParser.kt`: the
label regexes (`ENERGY`, `PROTEIN`, `FAT`, `CARBS`, `FIBER`) and the `EXCLUDE`
regex at the top of the file are the only things worth touching. Paste the
offending row into `ScreenshotParserTest` first — it reproduces in seconds on
the JVM, which beats reinstalling the app to find out.

## Layout

| File | Role |
| --- | --- |
| `health/HealthConnectManager.kt` | All Health Connect I/O: nutrition upsert, additive hydration logging, read-back |
| `health/DailyTotals.kt` | The day model; nullable fields throughout |
| `ocr/ScreenshotParser.kt` | ML Kit OCR + the keyword extraction to tune |
| `ui/SyncViewModel.kt` | State, OCR orchestration, food write, water quick-log |
| `ui/SyncScreen.kt` | The single Compose screen |
| `MainActivity.kt` | Share-intent handling, permission + photo launchers |
| `PermissionsRationaleActivity.kt` | Required by Health Connect |
| `widget/WaterQuickLogWidget.kt`, `widget/LogWaterAction.kt`, `widget/WaterQuickLogWidgetReceiver.kt` | 1×1 home screen widget (Jetpack Glance) — tap logs 250 ml with no activity launch |
| `relay/PhoneRelayListenerService.kt` | Receives a relayed water amount from the watch and calls `logHydration()` |
| `wear/tile/WaterTileService.kt` | The watch tile — a single button |
| `wear/TrampolineActivity.kt` | Translucent, instantly-finishing activity; the only way a tile click can trigger code |
| `wear/relay/PhoneRelayClient.kt` | Sends the tapped amount to the phone over the Wearable Data Layer API |
| `wear/MainActivity.kt` | Manual test screen on the watch, same relay call as the tile |

## Version pins to know about

`:app`'s `connect-client` is pinned to `1.1.0-alpha07` because the `Metadata`
constructor became private in later releases. If you bump it, the only thing
that breaks is `metadataFor()` at the bottom of `HealthConnectManager.kt`, and
the replacement is written out in the comment above it.

`:wear` has no Health Connect dependency at all (see "The Wear OS tile"
above), so it tracks nothing related to that pin — its own dependency
versions (AGP, compileSdk, etc.) are independent of `:app`'s.
