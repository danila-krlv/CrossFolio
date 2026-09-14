package com.crossfolio.common.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ApiKeyValidationStatus {
    UNCHECKED,
    MISSING,
    CHECKING,
    VALID,
    INVALID,
    CHECK_FAILED,
}

data class ApiKeyValidationState(
    val status: ApiKeyValidationStatus = ApiKeyValidationStatus.UNCHECKED,
    val failure: NetworkFailure? = null,
)

class ApiKeyValidator(
    private val validate: ((NetworkResult<Boolean>) -> Unit) -> Unit,
) {
    constructor(networkManager: NetworkProtocol) : this(networkManager::validateApiKey)

    private val _state = MutableStateFlow(ApiKeyValidationState())
    val state: StateFlow<ApiKeyValidationState> = _state.asStateFlow()
    private var generation = 0
    internal var onStateChanged: (ApiKeyValidationState) -> Unit = {}

    // Called on the main thread, as are network completions and ViewModel actions.
    fun check(hasApiKey: Boolean) {
        val request = ++generation
        if (!hasApiKey) {
            publish(ApiKeyValidationState(ApiKeyValidationStatus.MISSING))
            return
        }
        publish(ApiKeyValidationState(ApiKeyValidationStatus.CHECKING))
        validate { result ->
            if (request != generation) return@validate
            val status = when {
                result.value == true -> ApiKeyValidationStatus.VALID
                result.failure == NetworkFailure.STALE_RESPONSE -> ApiKeyValidationStatus.UNCHECKED
                result.failure == NetworkFailure.INVALID_KEY || result.value == false -> ApiKeyValidationStatus.INVALID
                else -> ApiKeyValidationStatus.CHECK_FAILED
            }
            publish(ApiKeyValidationState(status, result.failure))
        }
    }

    internal fun rejectKey() {
        generation++
        publish(ApiKeyValidationState(ApiKeyValidationStatus.INVALID, NetworkFailure.INVALID_KEY))
    }

    private fun publish(state: ApiKeyValidationState) {
        _state.value = state
        onStateChanged(state)
    }
}
