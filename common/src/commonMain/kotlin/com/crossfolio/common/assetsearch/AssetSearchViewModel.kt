package com.crossfolio.common.assetsearch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AssetSearchState(
    val message: String = "hello AssetSearchViewModel",
)

class AssetSearchViewModel(
    private val onBackRequested: () -> Unit,
) {
    private val _state = MutableStateFlow(AssetSearchState())
    val state: StateFlow<AssetSearchState> = _state.asStateFlow()

    fun onBack() {
        onBackRequested()
    }

    // TODO: Add asset catalog, search query and asset selection actions.
}
