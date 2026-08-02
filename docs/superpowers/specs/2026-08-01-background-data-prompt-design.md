# First-run background-data heads-up

## Problem

The weather widget refreshes in the background via a ~30-min `AlarmManager` heartbeat that
enqueues `RefreshWeatherWorker`. On a metered connection (cellular), if the user has
"Background data" turned **off** for the app, the OS applies the `REJECT_METERED_BACKGROUND`
network policy: the worker still runs but its network is denied, so `refresh()` returns
`NetworkError` almost instantly and the widget silently goes stale. It only updates when the
app is opened (foreground is exempt from the metered-background block). This is invisible to
the user and no amount of refresh-scheduling work can fix it — it is a device setting.

There is a parallel, already-shipped case: without "Allow all the time" background location,
background refreshes can't get a fresh fix. `MainActivity.ensureBackgroundLocation()` already
warns about that once, after location permission is granted.

## Goal

At first install, after location permission is granted, proactively tell the user about **both**
settings that keep the widget updating in the background, so they know what to check if updates
stop. Purely informational and one-time — never nag.

## Non-goals

- **No failure-detection notification.** A worker-side notification that fires when a refresh
  fails due to the restriction was considered but is out of scope here (it would require the
  `POST_NOTIFICATIONS` runtime permission). This spec adds no new permissions.
- **No detection gating.** The dialog is shown unconditionally (once), not gated on
  `getRestrictBackgroundStatus()`. At first install the user is typically on Wi-Fi, where that
  API reports "not restricted" even when the toggle is off — a detection gate would wrongly skip
  the message.

## Behavior

After location permission is granted, show up to two one-time dialogs **in sequence, never
overlapping**:

1. **Dialog 1** — reworded existing "Allow all the time" background-location dialog.
2. **Dialog 2** — new "Allow background data" dialog.

On dismiss of dialog 1 (via button, back press, or outside tap), dialog 2 is considered. Each
dialog is gated by its own "already asked" flag, so once dismissed it never reappears (the user
manages the settings from the OS afterward). Dialog 2 must also show when dialog 1 is **skipped**
— i.e. background location already granted, already asked, or API < 29 (where the location
dialog does not apply). Background-data restriction applies on all API levels, so dialog 2 is not
version-gated.

### Copy

| Dialog | Title | Message | Positive | Negative |
|-|-|-|-|-|
| 1 (location) | Allow location all the time | The weather widget may not update to your location properly if background location permission is not granted all the time; it is recommended to turn this setting on. | Open Settings | Not now |
| 2 (data) | Allow background data | The weather widget may stop updating if background mobile data is not allowed; it is recommended to turn this setting on. | Open Settings | Not now |

Dialog 1's title is unchanged from today; its message is reworded to the above.

### Button actions

- **Dialog 1 → Open Settings:** unchanged — `ACTION_APPLICATION_DETAILS_SETTINGS` for the app.
- **Dialog 2 → Open Settings:** launch `Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS`
  with a `package:<packageName>` URI (opens straight to the unrestricted-data toggle). If it does
  not resolve on a given OEM, fall back to `ACTION_APPLICATION_DETAILS_SETTINGS` (the same screen
  dialog 1 uses). Verified to resolve on the target Pixel / Android 17.
- **Both → Not now:** dismiss only. The "already asked" flag is set when the dialog is shown, not
  on a particular button, so any dismissal is terminal.

## Components

### `res/values/strings.xml`
- Reword `background_location_message` to dialog 1's new text.
- Change `background_location_dismiss` from "Dismiss" to "Not now".
- Add `background_data_title` = "Allow background data".
- Add `background_data_message` = dialog 2's text.
- Reuse the existing `background_location_open_settings` ("Open Settings") label for both positive
  buttons.

### `Settings.kt`
- Add `backgroundDataAsked(context): Boolean` / `setBackgroundDataAsked(context)`, mirroring the
  existing `backgroundLocationAsked` / `setBackgroundLocationAsked` pair (same SharedPreferences
  pattern).

### `MainActivity.kt`
Introduce a small coordinator that owns the sequencing so the two prompts never overlap and
dialog 2 always gets its chance:

```
private fun runOnboardingPrompts() {
    if (shouldShowBackgroundLocationDialog()) {
        showBackgroundLocationDialog(onDismiss = ::maybePromptBackgroundData)
    } else {
        maybePromptBackgroundData()
    }
}
```

- `shouldShowBackgroundLocationDialog()` encapsulates the current early-return checks in
  `ensureBackgroundLocation()` (API >= 29, foreground granted, background not yet granted, not yet
  asked).
- `showBackgroundLocationDialog(onDismiss)` shows dialog 1 (setting the asked flag), and attaches
  `setOnDismissListener { onDismiss() }` so chaining covers button, back, and outside-tap
  dismissals.
- `maybePromptBackgroundData()` is guarded by `Settings.backgroundDataAsked`; if not yet asked, it
  sets the flag and shows dialog 2 with the fallback-aware Open Settings action.

The two existing call sites that currently invoke `ensureBackgroundLocation()` — the
location-permission-granted callback and the `onCreate` "already has permission" branch — call
`runOnboardingPrompts()` instead. `ensureBackgroundLocation()` is refactored into the two helper
pieces above (no behavior change for the location dialog itself beyond the copy edit and the added
dismiss chaining).

## Error handling

- The background-data settings intent may not resolve on some OEMs. Guard with a
  `resolveActivity`/try-catch and fall back to `ACTION_APPLICATION_DETAILS_SETTINGS`.
- No permissions are added, so no permission-denial paths.

## Testing / verification

No data-layer change, so this is not JVM-unit-testable; verification is manual on-device plus the
existing gates:

1. Fresh install (or clear the two "asked" flags) → grant location → **dialog 1** appears →
   dismiss → **dialog 2** appears.
2. Dialog 2 → Open Settings lands on the app's unrestricted / background-data screen.
3. Reopen the app → neither dialog reappears (flags honored).
4. On a build path where the location dialog is skipped (background location already granted),
   dialog 2 still appears once.
5. `./gradlew lintDebug` and `./gradlew :app:testDebugUnitTest` remain green.
