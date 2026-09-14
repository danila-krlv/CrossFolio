package com.crossfolio.common.edit

import com.crossfolio.common.asset.Asset

class EditViewModel(
    val asset: Asset,
    private val onBackRequested: () -> Unit,
) {
    fun onBack() {
        onBackRequested()
    }
}
