package com.wassupluke.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wassupluke.widgets.data.FrameworkLocationProvider
import com.wassupluke.widgets.data.RefreshResult
import com.wassupluke.widgets.data.UrlHttpClient
import com.wassupluke.widgets.data.WeatherCache
import com.wassupluke.widgets.data.WeatherRepository

class RefreshWeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = WeatherRepository(
            locationProvider = FrameworkLocationProvider(applicationContext),
            httpClient = UrlHttpClient()
        )
        // Weather is fetched in Celsius; the display unit is applied at render time.
        val startedAt = System.currentTimeMillis()
        Debug.log("refresh start (attempt ${runAttemptCount + 1})")
        val result = repository.refresh()
        val elapsed = System.currentTimeMillis() - startedAt
        when (result) {
            is RefreshResult.Success ->
                Debug.log("refresh ok in ${elapsed}ms: ${result.data.temperature}C code=${result.data.weatherCode}")
            else -> Debug.warn("refresh failed in ${elapsed}ms: $result")
        }
        if (result is RefreshResult.Success) {
            WeatherCache.save(applicationContext, result.data)
        }
        // Re-render on everything except a pure network error, where the cached
        // value is already on screen and unchanged.
        if (result !is RefreshResult.NetworkError) {
            WeatherWidgetProvider.renderWidgets(applicationContext)
        }

        return when (result) {
            is RefreshResult.Success -> Result.success()
            RefreshResult.NoPermission -> Result.failure()
            RefreshResult.NoLocation, RefreshResult.NetworkError -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "weather_refresh"

        fun enqueueOnce(context: Context) {
            Debug.log("refresh enqueued")
            val request = OneTimeWorkRequestBuilder<RefreshWeatherWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            // Unique + REPLACE coalesces a burst of triggers (permission grant, onResume,
            // heartbeat, taps) into a single refresh so the widget doesn't re-render repeatedly.
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
