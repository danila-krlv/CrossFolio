package com.crossfolio.common.asset

enum class SearchPlatform {
    COIN_MARKET_CAP,
}

data class Asset(
    val searchId: String,
    val ticker: String,
    val searchPlatform: SearchPlatform = SearchPlatform.COIN_MARKET_CAP,
)
