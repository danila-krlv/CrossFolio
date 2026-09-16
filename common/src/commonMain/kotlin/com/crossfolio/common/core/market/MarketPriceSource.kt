package com.crossfolio.common.core.market

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.core.network.NetworkResult

interface MarketPriceSource {
    fun fetchMarketPrice(asset: Asset, completion: (NetworkResult<DecimalValue>) -> Unit)
}
