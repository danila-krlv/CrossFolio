package com.crossfolio.android.network

import android.os.Looper
import com.crossfolio.common.portfolio.assetsearch.NetworkResult
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object NetworkManagerChecks {
    fun run(): Int {
        catalogUsesCurrentKeyAndStableIDs()
        metadataAndQuotesUseCMCIDs()
        imagesDoNotReadOrTransmitKey()
        failuresDoNotExposeServerOrTransportDetails()
        missingKeyAndInvalidURLDoNotOpenConnections()
        return 5
    }

    private fun catalogUsesCurrentKeyAndStableIDs() {
        var key = "old-placeholder"
        val connection = FixtureConnection(
            body = """{"status":{"error_code":0},"data":[
                {"id":1,"symbol":"ETH","name":"Ethereum","slug":"ethereum","rank":2},
                {"id":2,"symbol":"ETH","name":"Other Ethereum","slug":"other","rank":null}]}""",
        )
        var requestedURL: URL? = null
        val manager = NetworkManager({ key }, { requestedURL = it; connection })
        key = "test-placeholder"
        val result = awaitResult(manager::fetchMap)
        check(result.error == null)
        check(result.value?.map { it.searchId } == listOf("1", "2"))
        check(result.value?.first()?.name == "Ethereum")
        check(result.value?.first()?.slug == "ethereum")
        check(result.value?.first()?.rank == 2)
        check(result.value?.last()?.rank == null)
        val url = checkNotNull(requestedURL)
        check(url.host == "pro-api.coinmarketcap.com")
        check(url.query.contains("start=1"))
        check(url.query.contains("limit=1000"))
        check(connection.getRequestProperty("X-CMC_PRO_API_KEY") == "test-placeholder")
        check(!connection.instanceFollowRedirects)
        check(connection.connectTimeout == 15_000 && connection.readTimeout == 30_000)
        check(connection.disconnected)
    }

    private fun metadataAndQuotesUseCMCIDs() {
        val metadata = FixtureConnection(body =
            """{"status":{"error_code":0},"data":{"1":{"logo":"https://example.com/1.png"}}}""")
        val manager = NetworkManager({ "test-placeholder" }, { metadata })
        val logo = awaitResult<String> { manager.fetchLogoURL("1", it) }
        check(logo.value == "https://example.com/1.png")
        val missing = awaitResult<Map<String, String>> {
            manager.fetchLogoUrlArray("1", listOf("missing"), it)
        }
        check(missing.value == null && missing.error != null)

        val prices = FixtureConnection(body = """{"status":{"error_code":0},"data":{
            "1":{"quote":{"USD":{"price":12.5}}},"2":{"quote":{"USD":{"price":20}}}}}""")
        var query: String? = null
        val quotes = NetworkManager({ "test-placeholder" }, { query = it.query; prices })
        val result = awaitResult<Map<String, Double>> {
            quotes.fetchPriceArray("1,2", listOf("1", "2"), it)
        }
        check(result.value == mapOf("1" to 12.5, "2" to 20.0))
        val parameters = checkNotNull(query)
        check(parameters.contains("id=1%2C2"))
        check(parameters.contains("convert=USD"))
    }

    private fun imagesDoNotReadOrTransmitKey() {
        val connection = FixtureConnection(bytes = byteArrayOf(0, -1, 127))
        val manager = NetworkManager({ error("Image must not read API key") }, { connection })
        val result = awaitResult<ByteArray> { manager.fetchImg("https://example.com/1.png", it) }
        check(result.value?.contentEquals(byteArrayOf(0, -1, 127)) == true)
        check(connection.getRequestProperty("X-CMC_PRO_API_KEY") == null)
        check(connection.instanceFollowRedirects)
        check(connection.disconnected)
    }

    private fun failuresDoNotExposeServerOrTransportDetails() {
        val fixtures = listOf(
            FixtureConnection(401, """{"status":{"error_code":1001,
                "error_message":"test-placeholder partial test-place"}}""") to
                "CoinMarketCap error 1001: API request rejected",
            FixtureConnection(429, """{"error":"test-placeholder"}""") to "HTTP 429: Request failed",
            FixtureConnection(body = "invalid test-placeholder") to
                "Network request or response decoding failed",
            FixtureConnection(body = """{"status":{"error_code":0}}""") to
                "Network request or response decoding failed",
            FixtureConnection(failure = IOException("test-placeholder")) to
                "Network request or response decoding failed",
        )
        for ((connection, expected) in fixtures) {
            val result = awaitResult(NetworkManager({ "test-placeholder" }, { connection })::fetchMap)
            check(result.value == null && result.error == expected)
            check(connection.disconnected)
        }
    }

    private fun missingKeyAndInvalidURLDoNotOpenConnections() {
        val manager = NetworkManager({ "" }, { error("Connection must not be opened") })
        check(awaitResult(manager::fetchMap).error == "API key is unavailable")
        val invalid = awaitResult<ByteArray> { manager.fetchImg("file:///image", it) }
        check(invalid.value == null && invalid.error != null)
    }

    private fun <T : Any> awaitResult(action: ((NetworkResult<T>) -> Unit) -> Unit): NetworkResult<T> {
        val latch = CountDownLatch(1)
        var result: NetworkResult<T>? = null
        var onMainThread = false
        action {
            onMainThread = Looper.myLooper() == Looper.getMainLooper()
            result = it
            latch.countDown()
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Network completion timed out" }
        check(onMainThread) { "Completion must run on main thread" }
        return checkNotNull(result)
    }
}

private class FixtureConnection(
    private val status: Int = 200,
    body: String = "",
    private val bytes: ByteArray = body.toByteArray(),
    private val failure: IOException? = null,
) : HttpURLConnection(URL("https://example.com")) {
    var disconnected = false

    override fun getResponseCode(): Int {
        failure?.let { throw it }
        return status
    }

    override fun getInputStream() = ByteArrayInputStream(bytes)
    override fun getErrorStream() = ByteArrayInputStream(bytes)
    override fun connect() {}
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
}
