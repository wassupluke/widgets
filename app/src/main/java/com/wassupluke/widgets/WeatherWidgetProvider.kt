package com.wassupluke.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.wassupluke.widgets.data.WeatherCache
import com.wassupluke.widgets.data.WeatherCode
import com.wassupluke.widgets.data.formatTemperature

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        renderWidgets(context)
        RefreshWeatherWorker.enqueueOnce(context)
    }

    override fun onEnabled(context: Context) {
        RefreshWeatherWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        RefreshWeatherWorker.cancelPeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            RefreshWeatherWorker.enqueueOnce(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.wassupluke.widgets.ACTION_REFRESH"

        /** Rebuilds RemoteViews for every widget instance from cached data + settings. */
        fun renderWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WeatherWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val cached = WeatherCache.load(context)
            val unit = Settings.resolvedUnit(context)

            // All values below are identical across widget instances — compute once.
            val temperatureText = cached?.let { formatTemperature(it.temperature, unit) }
                ?: context.getString(R.string.placeholder_temperature)
            val conditionText = cached?.let { WeatherCode.describe(it.weatherCode) }
                ?: context.getString(R.string.weather_unavailable)
            val conditionVisibility =
                if (Settings.showCondition(context)) View.VISIBLE else View.GONE
            // Tap launches the chosen app, or refreshes if none is set / it's uninstalled.
            val onClick = Settings.launchPackage(context)?.let { launchIntent(context, it) }
                ?: refreshIntent(context)
            val color = WidgetStyle.textColor(context)
            val temperatureSize = Settings.fontSize(context).toFloat()
            val conditionSize = temperatureSize * Settings.CONDITION_SIZE_RATIO
            val gravity = WidgetStyle.gravity(context)

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_weather)
                views.setTextViewText(R.id.temperature, temperatureText)
                views.setTextViewText(R.id.condition, conditionText)
                views.setViewVisibility(R.id.condition, conditionVisibility)
                views.setInt(R.id.widget_root, "setGravity", gravity)
                views.setTextColor(R.id.temperature, color)
                views.setTextColor(R.id.condition, color)
                views.setTextViewTextSize(R.id.temperature, TypedValue.COMPLEX_UNIT_SP, temperatureSize)
                views.setTextViewTextSize(R.id.condition, TypedValue.COMPLEX_UNIT_SP, conditionSize)
                // Click target is the text only, not the whole (mostly transparent) cell.
                views.setOnClickPendingIntent(R.id.temperature, onClick)
                views.setOnClickPendingIntent(R.id.condition, onClick)
                manager.updateAppWidget(id, views)
            }
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** PendingIntent that launches [packageName], or null if it can't be resolved. */
        private fun launchIntent(context: Context, packageName: String): PendingIntent? {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return null
            return PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
