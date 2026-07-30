package com.wassupluke.widgets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RefreshResult {
    data class Success(val data: WeatherData) : RefreshResult
    data object NoPermission : RefreshResult
    data object NoLocation : RefreshResult
    data object NetworkError : RefreshResult
}

class WeatherRepository(
    private val locationProvider: LocationProvider,
    private val httpClient: HttpClient
) {
    suspend fun refresh(): RefreshResult {
        val location = when (val l = locationProvider.getLocation()) {
            is LocationResult.Available -> l
            LocationResult.PermissionMissing -> return RefreshResult.NoPermission
            LocationResult.Unavailable -> return RefreshResult.NoLocation
        }

        val url = OpenMeteo.buildUrl(location.latitude, location.longitude)
        val body = withContext(Dispatchers.IO) { httpClient.get(url) }
            ?: return RefreshResult.NetworkError

        return runCatching { OpenMeteo.parse(body) }
            .fold(
                onSuccess = { RefreshResult.Success(it) },
                onFailure = { RefreshResult.NetworkError }
            )
    }
}
