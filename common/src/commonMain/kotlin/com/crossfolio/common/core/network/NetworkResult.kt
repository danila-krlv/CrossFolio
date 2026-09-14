package com.crossfolio.common.core.network

enum class NetworkFailure {
    INVALID_KEY,
    HTTP,
    API,
    TRANSPORT,
    INVALID_RESPONSE,
    STALE_RESPONSE,
    STORAGE,
}

class NetworkResult<T : Any>(
    val value: T?,
    val error: String?,
    val failure: NetworkFailure? = null,
) {
    init {
        require((value != null) != (error != null)) {
            "Network result must contain either a value or an error"
        }
    }
}
