package com.crossfolio.common.portfolio.edit

import com.crossfolio.common.core.asset.Asset

class EditViewModel(
    val asset: Asset,
    private val onBackRequested: () -> Unit,
) {
    fun onBack() {
        onBackRequested()
    }
}
