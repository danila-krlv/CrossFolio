package com.crossfolio.common.core.network

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.asset.AssetCatalog
import com.crossfolio.common.core.asset.SearchPlatform
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.market.MarketPriceSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class CoinMarketCapClient(
    private val transport: HttpTransport,
    private val apiKeyProvider: () -> String,
) : AssetCatalog, ApiKeyValidation, MarketPriceSource {
    override fun validateApiKey(apiKey: String, completion: (NetworkResult<Boolean>) -> Unit) {
        request("v1/key/info", { payload ->
            payload.getValue("data").jsonObject
            true
        }, completion, apiKey)
    }

    // Invoke from the main thread, matching ViewModel actions and transport callbacks.
    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) {
        request("v1/cryptocurrency/map?start=1&limit=1000", { payload ->
            payload.getValue("data").jsonArray.map { element ->
                val coin = element.jsonObject
                Asset(
                    searchId = coin.getValue("id").jsonPrimitive.long.also { require(it > 0) }.toString(),
                    ticker = coin.string("symbol"),
                    name = coin.string("name"),
                    slug = coin.string("slug"),
                    rank = coin["rank"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int,
                )
            }
        }, completion)
    }

    fun fetchPriceArray(idString: String, idArray: List<String>,
        completion: (NetworkResult<Map<String, Double>>) -> Unit) {
        request("v2/cryptocurrency/quotes/latest?id=${encode(idString)}&convert=USD", { payload ->
            val data = payload.getValue("data").jsonObject
            idArray.associateWith {
                data.getValue(it).jsonObject.getValue("quote").jsonObject.getValue("USD")
                    .jsonObject.getValue("price").jsonPrimitive.double.also { price -> require(price.isFinite()) }
            }
        }, completion)
    }

    override fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit) {
        val id = asset.searchId
        if (asset.searchPlatform != SearchPlatform.COIN_MARKET_CAP ||
            id.isEmpty() || !id.all { it in '0'..'9' } || id.all { it == '0' }) {
            completion(invalidResponse())
            return
        }
        request("v2/cryptocurrency/quotes/latest?id=${encode(id)}&convert=USD", { payload ->
            val price = payload.getValue("data").jsonObject.getValue(id).jsonObject
                .getValue("quote").jsonObject.getValue("USD").jsonObject
                .getValue("price").jsonPrimitive
            parseJsonDecimal(price.content).also { require(!it.isZero) }
        }, completion)
    }

    fun logoUrl(asset: Asset): String? {
        val id = asset.searchId
        if (asset.searchPlatform != SearchPlatform.COIN_MARKET_CAP ||
            id.isEmpty() || !id.all { it in '0'..'9' }) return null
        return "https://s2.coinmarketcap.com/static/img/coins/64x64/$id.png"
    }

    fun fetchImg(url: String, completion: (NetworkResult<ByteArray>) -> Unit) {
        transport.execute(HttpRequest(url)) { result ->
            val response = result.value
            when {
                response == null -> completion(NetworkResult(null, result.error, result.failure))
                response.statusCode !in 200..299 -> completion(httpFailure(response.statusCode))
                else -> completion(NetworkResult(response.body, null))
            }
        }
    }

    private fun <T : Any> request(path: String, decode: (JsonObject) -> T,
        completion: (NetworkResult<T>) -> Unit, candidateKey: String? = null) {
        val originalKey = candidateKey ?: runCatching { apiKeyProvider() }.getOrNull()
        val key = originalKey?.trim().orEmpty()
        if (key.isEmpty() || key.any { it.isWhitespace() }) {
            completion(NetworkResult(null, "API key is unavailable", NetworkFailure.INVALID_KEY))
            return
        }
        transport.execute(HttpRequest("https://pro-api.coinmarketcap.com/$path",
            mapOf("Accept" to "application/json", "X-CMC_PRO_API_KEY" to key), false)) { result ->
            if (candidateKey == null && originalKey != runCatching { apiKeyProvider() }.getOrNull()) {
                completion(NetworkResult(null, "Response is outdated", NetworkFailure.STALE_RESPONSE))
                return@execute
            }
            val response = result.value
            if (response == null) {
                completion(NetworkResult(null, result.error, result.failure))
                return@execute
            }
            val payload = runCatching { Json.parseToJsonElement(response.body.decodeToString()).jsonObject }.getOrNull()
            val code = runCatching { payload?.get("status")?.jsonObject?.get("error_code")?.jsonPrimitive?.int }.getOrNull()
            val output = when {
                code != null && code != 0 -> NetworkResult<T>(null,
                    "CoinMarketCap error $code: API request rejected",
                    if (code in listOf(1001, 1002, 1005, 1007)) NetworkFailure.INVALID_KEY else NetworkFailure.API)
                response.statusCode !in 200..299 -> httpFailure(response.statusCode)
                payload == null || code == null -> invalidResponse()
                else -> runCatching { NetworkResult(decode(payload), null) }.getOrElse { invalidResponse() }
            }
            completion(output)
        }
    }

    private fun <T : Any> httpFailure(code: Int) = NetworkResult<T>(null, "HTTP $code: Request failed",
        if (code == 401) NetworkFailure.INVALID_KEY else NetworkFailure.HTTP)

    private fun <T : Any> invalidResponse() =
        NetworkResult<T>(null, "Invalid response or missing data", NetworkFailure.INVALID_RESPONSE)

    private fun JsonObject.string(name: String): String {
        val value = getValue(name).jsonPrimitive
        require(value.isString)
        return value.content
    }

    private fun parseJsonDecimal(value: String): DecimalValue {
        val match = Regex("([0-9]+)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?").matchEntire(value)
            ?: error("Invalid decimal format")
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        val exponent = match.groupValues[3].ifEmpty { "0" }.toIntOrNull()
            ?: error("Decimal exponent is out of range")
        require(exponent in -1_000..1_000) { "Decimal exponent is out of range" }
        val digits = whole + fraction
        val decimalIndex = whole.length + exponent
        val plain = when {
            decimalIndex <= 0 -> "0." + "0".repeat(-decimalIndex) + digits
            decimalIndex >= digits.length -> digits + "0".repeat(decimalIndex - digits.length)
            else -> digits.substring(0, decimalIndex) + "." + digits.substring(decimalIndex)
        }
        return DecimalValue.parse(plain)
    }

    private fun encode(value: String): String = value.encodeToByteArray().joinToString("") { byte ->
        val char = (byte.toInt() and 255).toChar()
        if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-._~") char.toString()
        else "%" + (byte.toInt() and 255).toString(16).uppercase().padStart(2, '0')
    }
}
