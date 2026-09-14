package com.crossfolio.common.core.network

import com.crossfolio.common.profile.ProfileSecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ApiKeyManager(
    private val secureStorage: ProfileSecureStorage?,
    private val validator: ApiKeyValidator,
) {
    constructor() : this(null, ApiKeyValidator { _, completion ->
        completion(NetworkResult(null, "Network manager is unavailable", NetworkFailure.TRANSPORT))
    })

    private val _state = MutableStateFlow(ApiKeyValidationState())
    val state: StateFlow<ApiKeyValidationState> = _state.asStateFlow()
    private var inMemoryKey = ""
    private var generation = 0
    internal var onStateChanged: (ApiKeyValidationState) -> Unit = {}
    internal var onSavedKeyChanged: () -> Unit = {}

    fun getSavedKey(): String = secureStorage?.coinMarketCapApiKey ?: inMemoryKey

    fun observeState(observer: (ApiKeyValidationState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            state.collect { observer(it) }
        }
        return { job.cancel() }
    }

    fun start() {
        validateSaved(invalidate())
    }

    internal fun suspendValidation() {
        invalidate()
        publish(_state.value.copy(isEditing = true, inputFailure = null))
    }

    internal fun endEditing() {
        publish(_state.value.copy(isEditing = false))
    }

    fun applyCandidate(apiKey: String) {
        val candidate = apiKey.trim()
        val request = invalidate()
        if (candidate.isEmpty()) {
            // TODO: Ask for confirmation before deleting a saved API key through an empty field.
            if (save("")) publish(ApiKeyValidationState(ApiKeyValidationStatus.MISSING))
            return
        }
        if (candidate == getSavedKey() && _state.value.status == ApiKeyValidationStatus.VALID) {
            publish(ApiKeyValidationState(ApiKeyValidationStatus.VALID))
            return
        }
        publish(ApiKeyValidationState(ApiKeyValidationStatus.CHECKING))
        validator.check(candidate) { result ->
            if (request != generation) return@check
            when {
                result.value == true -> {
                    if (save(candidate)) publish(ApiKeyValidationState(ApiKeyValidationStatus.VALID))
                }
                result.failure == NetworkFailure.INVALID_KEY || result.value == false ->
                    validateSaved(request, NetworkFailure.INVALID_KEY)
                else -> publish(ApiKeyValidationState(ApiKeyValidationStatus.CHECK_FAILED,
                    result.failure, inputFailure = result.failure))
            }
        }
    }

    internal fun rejectKey() {
        invalidate()
        publish(ApiKeyValidationState(ApiKeyValidationStatus.INVALID, NetworkFailure.INVALID_KEY,
            isEditing = _state.value.isEditing))
    }

    private fun validateSaved(request: Int, inputFailure: NetworkFailure? = null) {
        val key = getSavedKey()
        if (key.isEmpty()) {
            publish(ApiKeyValidationState(ApiKeyValidationStatus.MISSING, inputFailure = inputFailure))
            return
        }
        publish(ApiKeyValidationState(ApiKeyValidationStatus.CHECKING))
        validator.check(key) { result ->
            if (request != generation) return@check
            when {
                result.value == true -> publish(ApiKeyValidationState(ApiKeyValidationStatus.VALID,
                    inputFailure = inputFailure))
                result.failure == NetworkFailure.INVALID_KEY || result.value == false -> {
                    if (save("")) publish(ApiKeyValidationState(ApiKeyValidationStatus.MISSING,
                        inputFailure = NetworkFailure.INVALID_KEY))
                }
                else -> publish(ApiKeyValidationState(ApiKeyValidationStatus.CHECK_FAILED,
                    result.failure, inputFailure = inputFailure))
            }
        }
    }

    private fun save(key: String): Boolean {
        val previous = getSavedKey()
        if (previous == key) return true
        val saved = runCatching {
            if (secureStorage != null) secureStorage.coinMarketCapApiKey = key else inMemoryKey = key
            getSavedKey() == key
        }.getOrDefault(false)
        if (getSavedKey() != previous) onSavedKeyChanged()
        if (!saved) publish(ApiKeyValidationState(ApiKeyValidationStatus.CHECK_FAILED, NetworkFailure.STORAGE))
        return saved
    }

    private fun invalidate(): Int {
        generation++
        return generation
    }

    private fun publish(state: ApiKeyValidationState) {
        _state.value = state
        onStateChanged(state)
    }
}
