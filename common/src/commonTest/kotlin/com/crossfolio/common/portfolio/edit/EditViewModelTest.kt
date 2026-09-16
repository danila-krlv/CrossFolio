package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.decimal.DecimalValue
import com.crossfolio.common.portfolio.model.PortfolioOperationRules
import com.crossfolio.common.portfolio.model.AcquisitionPriceSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditViewModelTest {
    private fun model() = EditViewModel(Asset("1", "BTC"), {}, nowEpochMillis = { 120_000L })

    @Test
    fun formAndPositionUseTheSameCustomOperationLimits() {
        val rules = AssetFieldRules(PortfolioOperationRules(
            quantityFractionDigits = 0, maximumInputValue = DecimalValue("2"),
        ))
        val model = EditViewModel(Asset("1", "BTC"), {}, rules, nowEpochMillis = { 120_000L })
        model.setQuantity("1.1")
        assertTrue(model.state.value.quantity.isError)
        model.setQuantity("3")
        assertFalse(model.state.value.canSave)
        model.setQuantity("2")
        assertEquals(DecimalValue("2"), model.state.value.position?.quantity)
        model.setPrice("3")
        assertTrue(model.state.value.price.isError)
        model.setPrice("2")
        model.setCommission("3")
        assertTrue(model.state.value.commission.isError)
        model.setCommission("2")
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun touchedFieldsAndPreparedPositionFollowFormValidity() {
        val model = model()
        assertFalse(model.state.value.canSave)
        assertFalse(model.state.value.quantity.isError)
        model.setQuantity("0")
        assertTrue(model.state.value.quantity.isError)
        model.setQuantity("1,25")
        val position = requireNotNull(model.state.value.position)
        assertEquals(DecimalValue("1.25"), position.quantity)
        assertEquals(DecimalValue("100"), position.operations.single().acquisitionPrice?.usd)
        assertEquals(AcquisitionPriceSource.MARKET, position.operations.single().acquisitionPrice?.source)
        val id = position.operations.single().id
        model.setPrice("123,45")
        assertEquals(AcquisitionPriceSource.MANUAL,
            model.state.value.position?.operations?.single()?.acquisitionPrice?.source)
        assertEquals(id, model.state.value.position?.operations?.single()?.id)
        model.setPrice("0")
        assertTrue(model.state.value.price.isError)
        assertNull(model.state.value.position)
        model.setPrice("")
        model.setCommission("0,001")
        assertTrue(model.state.value.commission.isError)
        model.setCommission("")
        assertEquals(DecimalValue.ZERO, model.state.value.position?.operations?.single()?.commissionUsd)
        model.setQuantity("")
        assertTrue(model.state.value.quantity.isError)
        assertFalse(model.state.value.canSave)
    }

    @Test
    fun futureDateAndExcessPrecisionDisableSave() {
        val model = model()
        model.setQuantity("0.000000001")
        assertFalse(model.state.value.canSave)
        model.setQuantity("10000000.01")
        assertFalse(model.state.value.canSave)
        model.setQuantity("1")
        model.setDate(180_000)
        assertFalse(model.state.value.canSave)
        assertTrue(model.state.value.dateError != null)
        model.setDate(60_000)
        assertTrue(model.state.value.canSave)
        model.save()
        assertTrue(model.state.value.canSave)
    }
}
