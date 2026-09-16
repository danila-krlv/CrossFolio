package com.crossfolio.common.core.network

fun interface ApiKeyValidation {
    // Success contains true; unsuccessful validation preserves the network failure.
    fun validateApiKey(apiKey: String, completion: (NetworkResult<Boolean>) -> Unit)
}
