# Adding a New Widget

## Checklist

Every widget requires:

- [ ] `widget/<Name>Widget.kt` — `GlanceAppWidget` subclass with UI
- [ ] `widget/<Name>WidgetReceiver.kt` — `GlanceAppWidgetReceiver` subclass
- [ ] `res/xml/<name>_widget_info.xml` — unique provider metadata
- [ ] `AndroidManifest.xml` — `<receiver>` entry with `<meta-data>` pointing to the info XML
- [ ] `WeatherDataStore.kt` — tap-package key (and any cache keys)
- [ ] `SettingsViewModel.kt` — state field, setter, propagate appearance changes
- [ ] `SettingsScreen.kt` — settings UI section
- [ ] `res/values/strings.xml` — label, description, settings title

---

## 1. Widget UI — `<Name>Widget.kt`

Extend `GlanceAppWidget` and implement `provideGlance`. Glance uses its own composables (`Box`, `Column`, `Row`, `Text`, `Image`) — not standard Compose — because widgets are backed by `RemoteViews`.

```kotlin
class <Name>Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs by context.dataStore.data.collectAsState(initial = emptyPreferences())
                // read DataStore keys, resolve colors, build tap action
                <Name>WidgetContent(...)
            }
        }
    }
}

@Composable
private fun <Name>WidgetContent(...) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Apply .clickable() to the content element (Text/Row/Image),
        // NOT the outer fillMaxSize Box — see layout isolation note below.
        Text(
            text = ...,
            modifier = GlanceModifier.clickable(tapAction),
            style = TextStyle(...)
        )
    }
}
```

**Layout isolation (critical):** Never share a `@Composable` root scaffold across multiple `GlanceAppWidget` subclasses. Glance can assign the same layout resource ID to widgets that share composable functions, causing one widget's content to appear on another. Each widget's Content composable must own its full `Box(fillMaxSize)` scaffold independently.

**Tap action:** Use `getLaunchIntentForPackage`, not `queryIntentActivities(MATCH_DEFAULT_ONLY)` — clock and alarm apps often lack `CATEGORY_DEFAULT` and won't appear in intent queries.

```kotlin
val tapPackage = prefs[WeatherDataStore.<NAME>_WIDGET_TAP_PACKAGE]
val tapAction = if (!tapPackage.isNullOrEmpty()) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(tapPackage)
    if (launchIntent?.component != null) actionStartActivity(launchIntent.component!!)
    else actionStartActivity<MainActivity>()
} else {
    actionStartActivity<MainActivity>()
}
```

Shared DataStore keys for appearance:
- `WIDGET_TEXT_COLOR` + `WIDGET_DYNAMIC_COLOR` + `resolveDynamicColor()` — color
- `FONT_SIZE` / `DEFAULT_FONT_SIZE` — font size
- Widget-specific: `<NAME>_WIDGET_TAP_PACKAGE`

---

## 2. Receiver — `<Name>WidgetReceiver.kt`

Extend `GlanceAppWidgetReceiver`. The base class handles `ACTION_APPWIDGET_UPDATE` (including calling `goAsync()` internally). Override `onReceive` for any domain-specific system broadcasts.

```kotlin
class <Name>WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = <Name>Widget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            /* domain broadcasts */ -> updateContent(context, intent.action ?: "")
        }
    }

    private fun updateContent(context: Context, action: String) {
        // APPWIDGET_UPDATE: super already called goAsync() — don't call it again.
        val pendingResult = if (action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) goAsync() else null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // read data, write to DataStore, call <Name>Widget().updateAll(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
```

---

## 3. Widget Provider Metadata — `res/xml/<name>_widget_info.xml`

Each widget needs its own file. Glance handles the initial layout internally — no `android:initialLayout` required.

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="80dp"
    android:minHeight="80dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:updatePeriodMillis="0"
    android:description="@string/widget_<name>_description"
    android:resizeMode="horizontal|vertical" />
```

`updatePeriodMillis="0"` — updates are driven by WorkManager and system broadcasts, not the OS polling mechanism.

---

## 4. Manifest Entry — `AndroidManifest.xml`

Each widget requires a separate `<receiver>` entry with a unique `<meta-data>` reference.

```xml
<receiver
    android:name=".widget.<Name>WidgetReceiver"
    android:exported="true"
    android:label="@string/widget_<name>_label">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
        <!-- add domain-specific broadcasts here, e.g.: -->
        <!-- <action android:name="android.app.action.NEXT_ALARM_CLOCK_CHANGED" /> -->
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/<name>_widget_info" />
</receiver>
```

Add any required permissions (e.g. `RECEIVE_BOOT_COMPLETED`) if new broadcasts are needed.

---

## 5. DataStore Keys — `WeatherDataStore.kt`

```kotlin
val <NAME>_WIDGET_TAP_PACKAGE = stringPreferencesKey("<name>_widget_tap_package")
val <NAME>_DATA = stringPreferencesKey("<name>_data")  // if caching display text
```

---

## 6. SettingsViewModel — `SettingsViewModel.kt`

Add to `SettingsUiState`:
```kotlin
val <name>WidgetTapPackage: String = ""
```

Add to `prefsToUiState`:
```kotlin
<name>WidgetTapPackage = prefs[WeatherDataStore.<NAME>_WIDGET_TAP_PACKAGE] ?: ""
```

Add setter:
```kotlin
fun set<Name>WidgetTapPackage(pkg: String) {
    viewModelScope.launch(dispatcher) {
        context.dataStore.edit { it[WeatherDataStore.<NAME>_WIDGET_TAP_PACKAGE] = pkg }
        <Name>Widget().updateAll(context)
    }
}
```

Also add `<Name>Widget().updateAll(context)` to `setFontSize`, `setWidgetTextColor`, and `setWidgetDynamicColor` so shared appearance settings propagate to the new widget.

---

## 7. Settings UI — `SettingsScreen.kt`

Add `onSet<Name>WidgetTapPackage: (String) -> Unit` to `SettingsScreenContent`. Add a section after the existing widget sections, using the `settings_<name>_tap_title` string and the existing app-picker pattern.

---

## 8. String Resources — `res/values/strings.xml`

Follow the `<where>_<what>` convention:

```xml
<!-- Widget picker -->
<string name="widget_<name>_label">Human Name</string>
<string name="widget_<name>_description">Shows X on your home screen</string>

<!-- Settings -->
<string name="settings_<name>_tap_title"><Name> widget tap action</string>
```

Shared strings (`settings_tap_none_label`, `settings_tap_app_missing_label`, appearance keys) are reused — don't duplicate them.
