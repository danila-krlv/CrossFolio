package com.crossfolio.android.network

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.crossfolio.common.asset.Asset
import com.crossfolio.common.assetsearch.NetworkProtocol
import com.crossfolio.common.assetsearch.NetworkResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

class NetworkManager(
    private val apiKeyProvider: () -> String,
) : NetworkProtocol {
    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) {
        request("v1/cryptocurrency/map", mapOf("start" to "1", "limit" to "1000"), {
            val coins = it.getJSONArray("data")
            List(coins.length()) { index ->
                val coin = coins.getJSONObject(index)
                Asset(
                    searchId = coin.getString("id"),
                    ticker = coin.getString("symbol"),
                    name = coin.getString("name"),
                    slug = coin.getString("slug"),
                    rank = if (coin.isNull("rank")) null else coin.getInt("rank"),
                )
            }
        }, completion)
    }

    override fun fetchLogoURL(id: String, completion: (NetworkResult<String>) -> Unit) {
        fetchLogoUrlArray(id, listOf(id)) { result ->
            completion(NetworkResult(value = result.value?.get(id), error = result.error))
        }
    }

    override fun fetchLogoUrlArray(
        idString: String,
        idArray: List<String>,
        completion: (NetworkResult<Map<String, String>>) -> Unit,
    ) {
        request("v2/cryptocurrency/info", mapOf("id" to idString, "aux" to "logo"), {
            val metadata = it.getJSONObject("data")
            idArray.associateWith { id -> metadata.getJSONObject(id).getString("logo") }
        }, completion)
    }

    override fun fetchImg(url: String, completion: (NetworkResult<ByteArray>) -> Unit) {
        execute(completion) { download(URL(url), null) }
    }

    override fun fetchPriceArray(
        idString: String,
        idArray: List<String>,
        completion: (NetworkResult<Map<String, Double>>) -> Unit,
    ) {
        request("v2/cryptocurrency/quotes/latest", mapOf("id" to idString, "convert" to "USD"), {
            val quotes = it.getJSONObject("data")
            idArray.associateWith { id ->
                quotes.getJSONObject(id).getJSONObject("quote").getJSONObject("USD")
                    .getDouble("price").also { price ->
                        require(price.isFinite()) { "Invalid price" }
                    }
            }
        }, completion)
    }

    private fun <T : Any> request(
        path: String,
        parameters: Map<String, String>,
        transform: (JSONObject) -> T,
        completion: (NetworkResult<T>) -> Unit,
    ) {
        // Read the current profile key on the caller's thread before starting background work.
        val apiKey = try {
            apiKeyProvider().trim().also { require(it.isNotEmpty()) { "API key is missing" } }
        } catch (_: Exception) {
            mainHandler.post { completion(NetworkResult(null, "API key is unavailable")) }
            return
        }
        execute(completion) {
            val uri = Uri.parse("https://pro-api.coinmarketcap.com/$path").buildUpon()
            parameters.forEach { (key, value) -> uri.appendQueryParameter(key, value) }
            val payload = JSONObject(download(URL(uri.build().toString()), apiKey).toString(Charsets.UTF_8))
            checkApiStatus(payload)
            transform(payload)
        }
    }

    private fun download(url: URL, apiKey: String?): ByteArray {
        require(url.protocol in listOf("http", "https") && url.host.isNotEmpty()) { "Invalid URL" }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            // Do not forward the API key to a redirected host.
            connection.instanceFollowRedirects = apiKey == null
            if (apiKey != null) {
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("X-CMC_PRO_API_KEY", apiKey)
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }
                val payload = body?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (apiKey != null && payload != null) checkApiStatus(payload)
                throw SafeNetworkException("HTTP $status: Request failed")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun checkApiStatus(payload: JSONObject) {
        val status = payload.getJSONObject("status")
        val code = status.getInt("error_code")
        if (code != 0) {
            throw SafeNetworkException("CoinMarketCap error $code: API request rejected")
        }
    }

    private fun <T : Any> execute(completion: (NetworkResult<T>) -> Unit, block: () -> T) {
        executor.execute {
            val result = try {
                NetworkResult(block(), null)
            } catch (error: Exception) {
                // Transport/decoder messages can include request details; expose only safe errors.
                val message = if (error is SafeNetworkException) error.message!! else
                    "Network request or response decoding failed"
                NetworkResult<T>(null, message)
            }
            mainHandler.post { completion(result) }
        }
    }

    private class SafeNetworkException(message: String) : IOException(message)

    private companion object {
        val executor = Executors.newFixedThreadPool(4)
        val mainHandler = Handler(Looper.getMainLooper())
    }
}
