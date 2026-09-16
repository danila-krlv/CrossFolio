package com.crossfolio.common.profile

import com.crossfolio.common.core.network.ApiKeyInteractor
import com.crossfolio.common.core.network.ApiKeyValidationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileState(
    val userName: String = "",
    val theme: AppTheme = AppTheme.SYSTEM,
    val hasApiKey: Boolean = false,
)

class ProfileViewModel internal constructor(
    private val preferencesStorage: ProfilePreferencesStorage?,
    val apiKeyInteractor: ApiKeyInteractor,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
) {
    constructor(preferencesStorage: ProfilePreferencesStorage?, apiKeyInteractor: ApiKeyInteractor) : this(
        preferencesStorage, apiKeyInteractor, { milliseconds, action ->
            val job = CoroutineScope(Dispatchers.Main.immediate).launch {
                delay(milliseconds)
                action()
            }
            val cancel: () -> Unit = { job.cancel() }
            cancel
        },
    )

    constructor() : this(null, ApiKeyInteractor())

    internal var onApiKeyChanged: () -> Unit = {}
    private var apiKeyDraft: String? = null
    private var apiKeyDirty = false
    private var cancelPending: (() -> Unit)? = null

    private val _state = MutableStateFlow(
        ProfileState(
            userName = preferencesStorage?.userName.orEmpty(),
            theme = preferencesStorage?.theme ?: AppTheme.SYSTEM,
            hasApiKey = getCoinMarketCapApiKey().isNotEmpty(),
        ),
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        apiKeyInteractor.onSavedKeyChanged = {
            _state.value = _state.value.copy(hasApiKey = getCoinMarketCapApiKey().isNotEmpty())
            onApiKeyChanged()
        }
    }

    fun setUserName(userName: String) {
        preferencesStorage?.userName = userName
        _state.value = _state.value.copy(userName = userName)
    }

    fun setTheme(theme: AppTheme) {
        preferencesStorage?.theme = theme
        _state.value = _state.value.copy(theme = theme)
    }

    fun setCoinMarketCapApiKey(apiKey: String) {
        editCoinMarketCapApiKey(apiKey)
        finishApiKeyEditing()
    }

    fun beginApiKeyEditing(apiKey: String = getCoinMarketCapApiKey()) {
        if (apiKeyInteractor.state.value.isEditing) return
        cancelTimer()
        apiKeyDraft = apiKey
        apiKeyDirty = apiKey.trim() != getCoinMarketCapApiKey()
        apiKeyInteractor.suspendValidation()
    }

    fun editCoinMarketCapApiKey(apiKey: String) {
        cancelTimer()
        apiKeyDraft = apiKey
        apiKeyDirty = true
        apiKeyInteractor.suspendValidation()
        cancelPending = schedule(5_000) { finishApiKeyEditing() }
    }

    fun finishApiKeyEditing() {
        val candidate = apiKeyDraft ?: return
        apiKeyDraft = null
        cancelTimer()
        val status = apiKeyInteractor.state.value.status
        if (!apiKeyDirty && status != ApiKeyValidationStatus.CHECKING &&
            status != ApiKeyValidationStatus.CHECK_FAILED) {
            apiKeyInteractor.endEditing()
            return
        }
        apiKeyDirty = false
        apiKeyInteractor.applyCandidate(candidate)
    }

    private fun cancelTimer() {
        cancelPending?.invoke()
        cancelPending = null
    }

    fun getCoinMarketCapApiKey(): String = apiKeyInteractor.getSavedKey()
}
