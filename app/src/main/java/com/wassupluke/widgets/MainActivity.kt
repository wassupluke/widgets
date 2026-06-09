package com.wassupluke.widgets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            updateStatus(granted)
            if (granted) {
                RefreshWeatherWorker.schedulePeriodic(this)
                RefreshWeatherWorker.enqueueOnce(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge is on by default at targetSdk 36; pad the content so it
        // clears the status and navigation bars.
        val base = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(base + bars.left, base + bars.top, base + bars.right, base + bars.bottom)
            insets
        }

        status = findViewById(R.id.status)

        val toggle = findViewById<MaterialSwitch>(R.id.switch_show_condition)
        toggle.isChecked = Settings.showCondition(this)
        toggle.setOnCheckedChangeListener { _, checked ->
            Settings.setShowCondition(this, checked)
            WeatherWidgetProvider.renderWidgets(this)
        }

        val unitGroup = findViewById<RadioGroup>(R.id.unit_group)
        unitGroup.check(
            when (Settings.unitMode(this)) {
                Settings.UnitMode.AUTO -> R.id.unit_auto
                Settings.UnitMode.CELSIUS -> R.id.unit_celsius
                Settings.UnitMode.FAHRENHEIT -> R.id.unit_fahrenheit
            }
        )
        unitGroup.setOnCheckedChangeListener { _, checkedId ->
            Settings.setUnitMode(
                this,
                when (checkedId) {
                    R.id.unit_celsius -> Settings.UnitMode.CELSIUS
                    R.id.unit_fahrenheit -> Settings.UnitMode.FAHRENHEIT
                    else -> Settings.UnitMode.AUTO
                }
            )
            // Cached temperature is in Celsius, so a unit change re-renders instantly
            // with no refetch.
            WeatherWidgetProvider.renderWidgets(this)
        }

        val dynamicColor = findViewById<MaterialSwitch>(R.id.switch_dynamic_color)
        dynamicColor.isChecked = Settings.dynamicColor(this)
        dynamicColor.setOnCheckedChangeListener { _, checked ->
            Settings.setDynamicColor(this, checked)
            renderAllWidgets()
        }

        val fontSizeValue = findViewById<TextView>(R.id.font_size_value)
        val fontSizeBar = findViewById<SeekBar>(R.id.font_size_seekbar)
        fontSizeBar.min = Settings.FONT_SIZE_MIN
        fontSizeBar.max = Settings.FONT_SIZE_MAX
        fontSizeBar.progress = Settings.fontSize(this)
        fontSizeValue.text = getString(R.string.font_size_value, Settings.fontSize(this))
        fontSizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                fontSizeValue.text = getString(R.string.font_size_value, progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            // Persist + re-render once on release, not on every drag tick.
            override fun onStopTrackingTouch(bar: SeekBar) {
                Settings.setFontSize(this@MainActivity, bar.progress)
                renderAllWidgets()
            }
        })

        val alignGroup = findViewById<MaterialButtonToggleGroup>(R.id.align_group)
        alignGroup.check(
            when (Settings.textAlign(this)) {
                Settings.TextAlign.START -> R.id.align_start
                Settings.TextAlign.CENTER -> R.id.align_center
                Settings.TextAlign.END -> R.id.align_end
            }
        )
        alignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            Settings.setTextAlign(
                this,
                when (checkedId) {
                    R.id.align_center -> Settings.TextAlign.CENTER
                    R.id.align_end -> Settings.TextAlign.END
                    else -> Settings.TextAlign.START
                }
            )
            renderAllWidgets()
        }

        bindTapButton(
            buttonId = R.id.tap_action_button,
            defaultLabelRes = R.string.tap_action_refresh,
            titleRes = R.string.settings_tap_action,
            currentLabel = { Settings.launchLabel(this) },
            clear = { Settings.clearLaunchPackage(this) },
            set = { pkg, label -> Settings.setLaunchPackage(this, pkg, label) },
            render = { WeatherWidgetProvider.renderWidgets(this) },
        )
        bindTapButton(
            buttonId = R.id.alarm_tap_action_button,
            defaultLabelRes = R.string.alarm_tap_default,
            titleRes = R.string.settings_alarm_tap_action,
            currentLabel = { Settings.alarmLaunchLabel(this) },
            clear = { Settings.clearAlarmLaunchPackage(this) },
            set = { pkg, label -> Settings.setAlarmLaunchPackage(this, pkg, label) },
            render = { AlarmWidgetProvider.renderAlarmWidgets(this) },
        )

        if (!hasPermission()) {
            requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    /** Re-render both widgets — used by the appearance settings they share. */
    private fun renderAllWidgets() {
        WeatherWidgetProvider.renderWidgets(this)
        AlarmWidgetProvider.renderAlarmWidgets(this)
    }

    /** Binds a tap-action button: shows its current target and opens the app picker. */
    private fun bindTapButton(
        buttonId: Int,
        defaultLabelRes: Int,
        titleRes: Int,
        currentLabel: () -> String?,
        clear: () -> Unit,
        set: (packageName: String, label: String) -> Unit,
        render: () -> Unit,
    ) {
        val button = findViewById<Button>(buttonId)
        fun labelText() = currentLabel()?.let { getString(R.string.tap_action_launch, it) }
            ?: getString(defaultLabelRes)
        button.text = labelText()
        button.setOnClickListener {
            showAppPicker(titleRes, defaultLabelRes) { pkg, label ->
                if (pkg == null) clear() else set(pkg, label!!)
                render()
                button.text = labelText()
            }
        }
    }

    /**
     * Shows an app chooser whose first item is the "default" action (index 0 -> null
     * package), followed by all launchable apps. [onPick] receives the package + label,
     * or (null, null) for the default.
     */
    private fun showAppPicker(
        titleRes: Int,
        defaultLabelRes: Int,
        onPick: (packageName: String?, label: String?) -> Unit
    ) {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }

        val items = (listOf(getString(defaultLabelRes)) + apps.map { it.second })
            .toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setItems(items) { _, which ->
                if (which == 0) onPick(null, null)
                else apps[which - 1].let { onPick(it.first, it.second) }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateStatus(hasPermission())
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun updateStatus(granted: Boolean) {
        status.setText(
            if (granted) R.string.permission_granted else R.string.permission_denied
        )
    }
}
