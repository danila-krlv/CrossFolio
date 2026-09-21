package com.crossfolio.common.core.asset

/** Tickers and names may change; provider identity determines the coin. */
data class AssetIdentity(val searchId: String, val searchPlatform: SearchPlatform) {
    init {
        require(searchId.isNotBlank()) { "Asset ID must not be blank" }
    }
}
