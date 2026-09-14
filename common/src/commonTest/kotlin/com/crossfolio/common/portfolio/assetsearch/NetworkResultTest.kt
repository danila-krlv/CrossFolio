package com.crossfolio.common.portfolio.assetsearch

import com.crossfolio.common.core.asset.Asset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NetworkResultTest {
    @Test
    fun carriesEitherValueOrError() {
        val success = NetworkResult(listOf(Asset("1", "BTC")), null)
        assertEquals("1", success.value?.single()?.searchId)
        assertNull(success.error)
        val failure = NetworkResult<List<Asset>>(null, "HTTP 401")
        assertNull(failure.value)
        assertEquals("HTTP 401", failure.error)
        assertFailsWith<IllegalArgumentException> { NetworkResult<String>(null, null) }
        assertFailsWith<IllegalArgumentException> { NetworkResult("value", "error") }
    }
}
