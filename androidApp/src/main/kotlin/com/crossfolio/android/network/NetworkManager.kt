package com.crossfolio.android.network

import android.os.Handler
import android.os.Looper
import com.crossfolio.common.core.network.HttpRequest
import com.crossfolio.common.core.network.HttpResponse
import com.crossfolio.common.core.network.HttpTransport
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.network.NetworkResult
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class NetworkManager internal constructor(
    private val connectionFactory: (URL) -> HttpURLConnection,
) : HttpTransport {
    constructor() : this({ it.openConnection() as HttpURLConnection })

    override fun execute(request: HttpRequest, completion: (NetworkResult<HttpResponse>) -> Unit) {
        executor.execute {
            val result = try {
                val url = URL(request.url)
                require(url.protocol in listOf("http", "https") && url.host.isNotEmpty())
                val connection = connectionFactory(url)
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = request.followRedirects
                    request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    NetworkResult(HttpResponse(status, stream?.use { it.readBytes() } ?: byteArrayOf()), null)
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                NetworkResult<HttpResponse>(null, "Network request failed", NetworkFailure.TRANSPORT)
            }
            mainHandler.post { completion(result) }
        }
    }

    private companion object {
        val executor = Executors.newFixedThreadPool(4)
        val mainHandler = Handler(Looper.getMainLooper())
    }
}
