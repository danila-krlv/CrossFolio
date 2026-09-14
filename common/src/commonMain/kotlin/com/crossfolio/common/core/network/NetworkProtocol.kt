package com.crossfolio.common.core.network

import com.crossfolio.common.core.asset.AssetCatalog

interface NetworkProtocol : AssetCatalog {
    // Success contains true; unsuccessful validation preserves the network failure.
    fun validateApiKey(apiKey: String, completion: (NetworkResult<Boolean>) -> Unit)

    fun validateApiKey(completion: (NetworkResult<Boolean>) -> Unit)

    fun fetchLogoURL(id: String, completion: (NetworkResult<String>) -> Unit)

    fun fetchLogoUrlArray(
        idString: String,
        idArray: List<String>,
        completion: (NetworkResult<Map<String, String>>) -> Unit,
    )

    fun fetchImg(url: String, completion: (NetworkResult<ByteArray>) -> Unit)

    fun fetchPriceArray(
        idString: String,
        idArray: List<String>,
        completion: (NetworkResult<Map<String, Double>>) -> Unit,
    )
}
