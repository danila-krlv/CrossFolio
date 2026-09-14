package com.crossfolio.common.core.asset

import com.crossfolio.common.core.network.NetworkResult

interface AssetCatalog {
    fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit)
}
