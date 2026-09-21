package com.crossfolio.common.portfolio.model

import com.crossfolio.common.core.decimal.DecimalValue

enum class AcquisitionPriceSource { MANUAL, MARKET }

data class AcquisitionPrice(val usd: DecimalValue, val source: AcquisitionPriceSource) {
    init {
        require(!usd.isZero) { "Acquisition price must be positive" }
    }
}
