package com.crossfolio.common.portfolio.assetsearch

class NetworkResult<T : Any>(
    val value: T?,
    val error: String?,
) {
    init {
        require((value != null) != (error != null)) {
            "Network result must contain either a value or an error"
        }
    }
}
