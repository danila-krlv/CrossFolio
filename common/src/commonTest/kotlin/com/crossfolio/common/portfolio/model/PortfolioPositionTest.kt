package com.crossfolio.common.portfolio.model

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.portfolio.edit.AssetFieldRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortfolioPositionTest {
    private fun addition(quantity: String, time: Long = 1000) = PortfolioOperation(
        id = "addition", direction = PortfolioOperationDirection.ADDITION,
        quantity = DecimalValue.parse(quantity), occurredAtEpochMillis = time,
        acquisitionPrice = AcquisitionPrice(DecimalValue("0.000012"), AcquisitionPriceSource.MARKET),
    )

    @Test
    fun exactDecimalsAndHistoryPreserveZeroPosition() {
        assertEquals(DecimalValue("0.3"), DecimalValue("0.1").add(DecimalValue("0.2")))
        assertEquals(DecimalValue("999.99"), DecimalValue("1000").subtract(DecimalValue("0.01")))
        assertEquals(DecimalValue("1000"), DecimalValue("999.99").add(DecimalValue("0.01")))
        assertEquals(DecimalValue("1.2"), DecimalValue.parse("001.200"))
        val position = PortfolioPosition(Asset("1", "BTC")).record(addition("0.00000001"), 1000)
        val reduction = PortfolioOperation("reduction", PortfolioOperationDirection.REDUCTION,
            DecimalValue("0.00000001"), 500, commissionUsd = DecimalValue("0.01"))
        val empty = position.record(reduction, 1000)
        assertEquals(DecimalValue.ZERO, empty.quantity)
        assertEquals(2, empty.operations.size)
        assertEquals(DecimalValue("0.000012"), empty.operations.first().acquisitionPrice?.usd)
    }

    @Test
    fun inputPolicyRejectsFutureDatesExcessPrecisionAndOverdraft() {
        val position = PortfolioPosition(Asset("1", "BTC"))
        assertFailsWith<IllegalArgumentException> { position.record(addition("1", 1001), 1000) }
        assertFailsWith<IllegalArgumentException> { position.record(addition("0.000000001"), 1000) }
        assertFailsWith<IllegalArgumentException> { position.record(addition("10000000.01"), 1000) }
        assertFailsWith<IllegalArgumentException> {
            position.record(addition("1").copy(acquisitionPrice =
                AcquisitionPrice(DecimalValue("0.001"), AcquisitionPriceSource.MANUAL)), 1000)
        }
        assertFailsWith<IllegalArgumentException> {
            position.record(addition("1").copy(commissionUsd = DecimalValue("0.001")), 1000)
        }
        assertFailsWith<IllegalArgumentException> {
            position.record(PortfolioOperation("reduction", PortfolioOperationDirection.REDUCTION,
                DecimalValue("1"), 1000), 1000)
        }
        assertFailsWith<IllegalArgumentException> { addition("0") }
        assertFailsWith<IllegalArgumentException> {
            AssetFieldRules(quantityFractionDigits = 0).validate(addition("0.1"), DecimalValue.ZERO, 1000)
        }
        assertEquals(DecimalValue("10000000"), position.record(addition("10000000"), 1000).quantity)
    }
}
