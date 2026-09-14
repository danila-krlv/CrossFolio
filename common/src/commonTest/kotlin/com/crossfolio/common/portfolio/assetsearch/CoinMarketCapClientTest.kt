package com.crossfolio.common.portfolio.assetsearch

import com.crossfolio.common.core.network.CoinMarketCapClient
import com.crossfolio.common.core.network.HttpRequest
import com.crossfolio.common.core.network.HttpResponse
import com.crossfolio.common.core.network.HttpTransport
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoinMarketCapClientTest {
    @Test
    fun buildsCatalogRequestAndDecodesStableIdentities() {
        val transport = FakeTransport()
        var key = "old-placeholder"
        val client = CoinMarketCapClient(transport) { key }
        key = " current-placeholder "
        client.fetchMap {
            assertNull(it.error)
            assertEquals(listOf("1", "2"), it.value?.map { asset -> asset.searchId })
            assertEquals(listOf("BTC", "BTC"), it.value?.map { asset -> asset.ticker })
            assertEquals(1, it.value?.first()?.rank)
            assertNull(it.value?.last()?.rank)
        }
        val request = transport.requests.single()
        assertEquals("https://pro-api.coinmarketcap.com/v1/cryptocurrency/map?start=1&limit=1000", request.url)
        assertEquals("current-placeholder", request.headers["X-CMC_PRO_API_KEY"])
        assertFalse(request.followRedirects)
        transport.complete(200, """{"status":{"error_code":0},"data":[
            {"id":1,"symbol":"BTC","name":"Bitcoin","slug":"bitcoin","rank":1},
            {"id":2,"symbol":"BTC","name":"Other Bitcoin","slug":"other","rank":null}]}""")
    }

    @Test
    fun validatesKeysBeforeTransportAndHandlesProviderFailure() {
        val transport = FakeTransport()
        for (key in listOf("", "   ", "placeholder\nkey")) {
            CoinMarketCapClient(transport) { key }.fetchMap {
                assertEquals(NetworkFailure.INVALID_KEY, it.failure)
                assertNull(it.value)
            }
        }
        CoinMarketCapClient(transport) { error("private-placeholder") }.fetchMap {
            assertEquals("API key is unavailable", it.error)
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun classifiesErrorsWithoutExposingResponseDetails() {
        val fixtures = listOf(
            Triple(200, "invalid private-placeholder", NetworkFailure.INVALID_RESPONSE),
            Triple(200, """{"status":{"error_code":0}}""", NetworkFailure.INVALID_RESPONSE),
            Triple(401, "invalid private-placeholder", NetworkFailure.INVALID_KEY),
            Triple(429, "{}", NetworkFailure.HTTP),
            Triple(200, """{"status":{"error_code":1001,"error_message":"private-placeholder"}}""", NetworkFailure.INVALID_KEY),
            Triple(403, """{"status":{"error_code":"1007"}}""", NetworkFailure.INVALID_KEY),
            Triple(200, """{"status":{"error_code":5000}}""", NetworkFailure.API),
        )
        for ((status, body, failure) in fixtures) {
            val transport = FakeTransport()
            CoinMarketCapClient(transport) { "placeholder" }.fetchMap {
                assertEquals(failure, it.failure)
                assertNull(it.value)
                assertFalse(it.error.orEmpty().contains("private-placeholder"))
            }
            transport.complete(status, body)
        }
    }

    @Test
    fun changedKeyMakesPendingResponseStale() {
        val transport = FakeTransport()
        var key = "first-placeholder"
        CoinMarketCapClient(transport) { key }.fetchMap {
            assertEquals(NetworkFailure.STALE_RESPONSE, it.failure)
            assertNull(it.value)
        }
        key = "second-placeholder"
        transport.complete(401, """{"status":{"error_code":1001}}""")
    }

    @Test
    fun metadataQuotesAndPublicImagesUseSharedClient() {
        val transport = FakeTransport()
        val client = CoinMarketCapClient(transport) { "placeholder" }
        client.fetchLogoURL("1") { assertEquals("https://example.com/logo", it.value) }
        transport.complete(200, """{"status":{"error_code":0},"data":{"1":{"logo":"https://example.com/logo"}}}""")
        client.fetchPriceArray("1,2", listOf("1", "2")) { assertEquals(mapOf("1" to 12.5, "2" to 20.0), it.value) }
        assertTrue(transport.requests.last().url.contains("id=1%2C2&convert=USD"))
        transport.complete(200, """{"status":{"error_code":0},"data":{"1":{"quote":{"USD":{"price":12.5}}},"2":{"quote":{"USD":{"price":20}}}}}""")
        client.fetchLogoURL("missing") { assertEquals(NetworkFailure.INVALID_RESPONSE, it.failure) }
        transport.complete(200, """{"status":{"error_code":0},"data":{}}""")
        CoinMarketCapClient(transport) { error("Image must not read key") }.fetchImg("https://example.com/logo") {
            assertTrue(it.value!!.contentEquals(byteArrayOf(0, -1, 127)))
        }
        assertTrue(transport.requests.last().headers.isEmpty())
        assertTrue(transport.requests.last().followRedirects)
        transport.callback(NetworkResult(HttpResponse(200, byteArrayOf(0, -1, 127)), null))
        client.fetchMap { assertEquals(NetworkFailure.TRANSPORT, it.failure) }
        transport.callback(NetworkResult(null, "Network request failed", NetworkFailure.TRANSPORT))
    }
}

private class FakeTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    lateinit var callback: (NetworkResult<HttpResponse>) -> Unit
    override fun execute(request: HttpRequest, completion: (NetworkResult<HttpResponse>) -> Unit) {
        requests += request
        callback = completion
    }
    fun complete(status: Int, body: String) {
        callback(NetworkResult(HttpResponse(status, body.encodeToByteArray()), null))
    }
}
