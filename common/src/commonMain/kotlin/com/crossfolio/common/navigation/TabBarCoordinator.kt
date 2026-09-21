package com.crossfolio.common.navigation

import com.crossfolio.common.analytics.AnalyticsViewModel
import com.crossfolio.common.core.network.ApiKeyInteractor
import com.crossfolio.common.core.network.ApiKeyValidationState
import com.crossfolio.common.core.network.ApiKeyValidationStatus
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.profile.ProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab {
    PORTFOLIO,
    ANALYTICS,
    PROFILE,
}

data class TabBarState(
    val selectedTab: AppTab = AppTab.PORTFOLIO,
    val apiKeyValidation: ApiKeyValidationState = ApiKeyValidationState(),
    val alertMessage: String? = null,
)

class TabBarCoordinator(
    val portfolioCoordinator: PortfolioCoordinator = PortfolioCoordinator(),
    val analyticsViewModel: AnalyticsViewModel = AnalyticsViewModel(
        portfolioCoordinator.portfolioViewModel, portfolioCoordinator.portfolioStorage,
    ),
    val profileViewModel: ProfileViewModel = ProfileViewModel(),
    val apiKeyInteractor: ApiKeyInteractor = profileViewModel.apiKeyInteractor,
) {
    private val _state = MutableStateFlow(TabBarState())
    val state: StateFlow<TabBarState> = _state.asStateFlow()

    init {
        apiKeyInteractor.onStateChanged = ::onValidationChanged
        portfolioCoordinator.onNetworkFailure = ::onNetworkFailure
        profileViewModel.onApiKeyChanged = ::resetNavigation
        apiKeyInteractor.start()
    }

    fun resetNavigation() {
        portfolioCoordinator.resetNavigation()
        _state.value = _state.value.copy(alertMessage = null)
    }

    fun observeState(observer: (TabBarState) -> Unit): () -> Unit {
        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            state.collect { observer(it) }
        }
        return { job.cancel() }
    }

    fun selectTab(tab: AppTab) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    fun dismissAlert() {
        _state.value = _state.value.copy(alertMessage = null)
    }

    private fun onValidationChanged(validation: ApiKeyValidationState) {
        portfolioCoordinator.setSearchEnabled(validation.status == ApiKeyValidationStatus.VALID && !validation.isEditing)
        val message = when {
            validation.isEditing -> null
            validation.status == ApiKeyValidationStatus.VALID && validation.inputFailure == NetworkFailure.INVALID_KEY ->
                "Новый API-ключ недействителен. Сохранён прежний ключ."
            else -> when (validation.status) {
                ApiKeyValidationStatus.MISSING, ApiKeyValidationStatus.INVALID ->
                    "Добавьте действительный API-ключ CoinMarketCap в профиле."
                ApiKeyValidationStatus.CHECK_FAILED -> failureMessage(validation.failure, checkingKey = true)
                else -> null
            }
        }
        _state.value = _state.value.copy(apiKeyValidation = validation, alertMessage = message)
        if (_state.value.selectedTab == AppTab.ANALYTICS &&
            validation.status == ApiKeyValidationStatus.VALID && !validation.isEditing) {
            analyticsViewModel.refresh()
        }
    }

    private fun onNetworkFailure(failure: NetworkFailure?) {
        if (apiKeyInteractor.state.value.isEditing) return
        if (failure == NetworkFailure.INVALID_KEY) {
            // A catalog response for the saved key must not supersede an active key validation.
            if (apiKeyInteractor.state.value.status == ApiKeyValidationStatus.CHECKING) return
            apiKeyInteractor.rejectKey()
            portfolioCoordinator.resetNavigation()
        } else {
            _state.value = _state.value.copy(alertMessage = failureMessage(failure, checkingKey = false))
        }
    }

    private fun failureMessage(failure: NetworkFailure?, checkingKey: Boolean): String = when (failure) {
        NetworkFailure.TRANSPORT -> if (checkingKey)
            "Не удалось проверить API-ключ. Проверьте подключение к интернету."
            else "Не удалось получить данные CoinMarketCap. Проверьте подключение к интернету."
        NetworkFailure.STORAGE -> "Не удалось сохранить API-ключ. Попробуйте снова."
        NetworkFailure.INVALID_RESPONSE -> "Получен некорректный ответ CoinMarketCap. Попробуйте позже."
        else -> "Сервис CoinMarketCap временно недоступен. Попробуйте позже."
    }

    // TODO: Add nested feature navigation when detail and edit flows are implemented.
}
