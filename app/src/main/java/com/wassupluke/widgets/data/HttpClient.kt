package com.wassupluke.widgets.data

import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP GET abstraction so the repository can be tested with a fake. */
interface HttpClient {
    /** Returns the response body, or null on any failure. */
    fun get(url: String): String?
}

class UrlHttpClient : HttpClient {
    override fun get(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
