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
import java.util.concurrent.Executor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class NetworkManager internal constructor(
    private val executor: Executor = apiExecutor,
    private val connectionFactory: (URL) -> HttpURLConnection,
) : HttpTransport {
    constructor() : this(connectionFactory = { it.openConnection() as HttpURLConnection })

    override fun execute(request: HttpRequest, completion: (NetworkResult<HttpResponse>) -> Unit) {
        val work = Runnable {
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
        try {
            executor.execute(work)
        } catch (_: RejectedExecutionException) {
            mainHandler.post {
                completion(NetworkResult(null, "Network queue is full", NetworkFailure.TRANSPORT))
            }
        }
    }

    internal companion object {
        private val apiExecutor = Executors.newFixedThreadPool(4)
        // Bound obsolete image work; API requests use their own workers.
        private val imageExecutor = ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
        )
        fun forImages(connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) =
            NetworkManager(imageExecutor, connectionFactory)

        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
