package com.wassupluke.widgets

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wassupluke.widgets.data.FrameworkLocationProvider
import com.wassupluke.widgets.data.RefreshResult
import com.wassupluke.widgets.data.UrlHttpClient
import com.wassupluke.widgets.data.WeatherCache
import com.wassupluke.widgets.data.WeatherRepository
import java.util.concurrent.TimeUnit

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
        val result = repository.refresh()
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
        private const val PERIODIC = "weather_refresh_periodic"

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWeatherWorker>(
                30, TimeUnit.MINUTES
            ).setConstraints(networkConstraint).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }

        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWeatherWorker>()
                .setConstraints(networkConstraint)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
