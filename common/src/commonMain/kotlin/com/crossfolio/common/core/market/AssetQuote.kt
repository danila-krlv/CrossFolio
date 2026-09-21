package com.crossfolio.common.core.market

import com.crossfolio.common.core.asset.AssetIdentity
import com.crossfolio.common.core.decimal.DecimalValue

data class AssetQuote(
    val assetIdentity: AssetIdentity,
    val priceUsd: DecimalValue,
    val receivedAtEpochMillis: Long,
) {
    init {
        require(!priceUsd.isZero) { "Market price must be positive" }
    }
}
