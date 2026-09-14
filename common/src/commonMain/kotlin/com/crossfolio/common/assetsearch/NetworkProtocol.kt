package com.crossfolio.common.assetsearch

import com.crossfolio.common.asset.Asset

interface NetworkProtocol {
    fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit)

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
