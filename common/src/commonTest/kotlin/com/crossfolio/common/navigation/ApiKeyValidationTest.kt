package com.crossfolio.common.navigation

import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.core.network.ApiKeyInteractor
import com.crossfolio.common.core.network.ApiKeyValidationStatus
import com.crossfolio.common.core.network.NetworkFailure
import com.crossfolio.common.core.asset.AssetCatalog
import com.crossfolio.common.core.network.ApiKeyValidation
import com.crossfolio.common.core.network.NetworkResult
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.PortfolioRoute
import com.crossfolio.common.profile.ProfileSecureStorage
import com.crossfolio.common.profile.ProfileViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyValidationTest {
    @Test
    fun oldCatalogFailureDoesNotCancelCandidateValidation() {
        val f = Fixture()
        f.valid(0)
        val portfolio = f.tabs.portfolioCoordinator
        portfolio.openAssetSearch()
        f.profile.setCoinMarketCapApiKey("new-placeholder")
        f.network.catalogs[0](NetworkResult(null, "Invalid old key", NetworkFailure.INVALID_KEY))
        assertEquals(ApiKeyValidationStatus.CHECKING, f.interactor.state.value.status)
        assertNull(f.tabs.state.value.alertMessage)
        f.valid(1)
        assertEquals("new-placeholder", f.storage.coinMarketCapApiKey)
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
        assertTrue(portfolio.state.value.isSearchEnabled)
    }

    @Test
    fun visibleCandidateIsRetriedAfterNetworkFailure() {
        val f = Fixture()
        f.valid(0)
        f.profile.setCoinMarketCapApiKey("new-placeholder")
        f.network.validations[1](NetworkResult(null, "Offline", NetworkFailure.TRANSPORT))
        f.profile.beginApiKeyEditing("new-placeholder")
        f.profile.finishApiKeyEditing()
        assertEquals(listOf("saved-placeholder", "new-placeholder", "new-placeholder"), f.network.keys)
        f.valid(2)
        assertEquals("new-placeholder", f.storage.coinMarketCapApiKey)
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
    }

    @Test
    fun unchangedInputRetriesAfterStartupNetworkFailureOnce() {
        val f = Fixture()
        f.network.validations[0](NetworkResult(null, "Offline", NetworkFailure.TRANSPORT))
        f.profile.beginApiKeyEditing()
        f.profile.finishApiKeyEditing()
        f.profile.finishApiKeyEditing()
        assertEquals(listOf("saved-placeholder", "saved-placeholder"), f.network.keys)
        f.valid(1)
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
        assertTrue(f.tabs.portfolioCoordinator.state.value.isSearchEnabled)
        assertEquals(0, f.storage.writes)
    }

    @Test
    fun invalidCatalogKeyStillDisablesSearchWithoutActiveValidation() {
        val f = Fixture()
        f.valid(0)
        val portfolio = f.tabs.portfolioCoordinator
        portfolio.openAssetSearch()
        f.network.catalogs[0](NetworkResult(null, "Invalid key", NetworkFailure.INVALID_KEY))
        assertEquals(ApiKeyValidationStatus.INVALID, f.interactor.state.value.status)
        assertEquals(PortfolioRoute.PORTFOLIO, portfolio.state.value.currentRoute)
        assertFalse(portfolio.state.value.isSearchEnabled)
        assertEquals(1, f.network.keys.size)
    }

    @Test
    fun focusLossWithoutChangesRestartsInterruptedStartupValidation() {
        val f = Fixture()
        f.profile.beginApiKeyEditing()
        f.profile.finishApiKeyEditing()
        assertEquals(listOf("saved-placeholder", "saved-placeholder"), f.network.keys)
        f.invalid(0)
        assertNull(f.tabs.state.value.alertMessage)
        assertEquals(ApiKeyValidationStatus.CHECKING, f.interactor.state.value.status)
        f.valid(1)
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
        assertEquals(0, f.storage.writes)
    }

    @Test
    fun debounceRestartsAndSavesOnlyAfterSuccessfulValidation() {
        val f = Fixture()
        f.valid(0)
        f.profile.editCoinMarketCapApiKey("new-placeholder")
        f.timer.advance(4_999)
        assertEquals(1, f.network.keys.size)
        assertEquals("saved-placeholder", f.storage.coinMarketCapApiKey)
        f.profile.editCoinMarketCapApiKey("latest-placeholder")
        f.timer.advance(4_999)
        assertEquals(1, f.network.keys.size)
        f.timer.advance(1)
        assertEquals(listOf("saved-placeholder", "latest-placeholder"), f.network.keys)
        assertEquals(0, f.storage.writes)
        f.valid(1)
        assertEquals("latest-placeholder", f.storage.coinMarketCapApiKey)
        assertEquals(1, f.storage.writes)
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
        assertTrue(f.profile.state.value.hasApiKey)
        assertFalse(f.tabs.state.value.toString().contains("latest-placeholder"))
    }

    @Test
    fun submitAndFocusLossCommitOnceAndCancelPendingTimer() {
        val f = Fixture()
        f.valid(0)
        f.profile.beginApiKeyEditing()
        f.profile.editCoinMarketCapApiKey("new-placeholder")
        // Enter, loss of focus and disappearance all call this same action.
        f.profile.finishApiKeyEditing()
        f.profile.finishApiKeyEditing()
        f.timer.advance(5_000)
        assertEquals(2, f.network.keys.size)
        f.valid(1)
        assertEquals(1, f.storage.writes)
    }

    @Test
    fun editingSuppressesStartupAndSupersededValidationAlerts() {
        val f = Fixture()
        f.profile.beginApiKeyEditing()
        f.invalid(0)
        assertNull(f.tabs.state.value.alertMessage)
        assertEquals("saved-placeholder", f.storage.coinMarketCapApiKey)
        assertFalse(f.tabs.portfolioCoordinator.state.value.isSearchEnabled)
        f.profile.editCoinMarketCapApiKey("first-placeholder")
        f.profile.finishApiKeyEditing()
        f.profile.editCoinMarketCapApiKey("second-placeholder")
        f.invalid(1)
        assertNull(f.tabs.state.value.alertMessage)
        assertEquals(2, f.network.keys.size)
        f.timer.advance(5_000)
        f.valid(2)
        assertEquals("second-placeholder", f.storage.coinMarketCapApiKey)
    }

    @Test
    fun invalidCandidateRechecksAndKeepsValidSavedKeyWithoutWriting() {
        val f = Fixture()
        f.valid(0)
        f.profile.setCoinMarketCapApiKey("bad-placeholder")
        f.invalid(1)
        assertEquals(listOf("saved-placeholder", "bad-placeholder", "saved-placeholder"), f.network.keys)
        assertNull(f.tabs.state.value.alertMessage)
        f.valid(2)
        assertEquals("saved-placeholder", f.storage.coinMarketCapApiKey)
        assertEquals(0, f.storage.writes)
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
        assertEquals("Новый API-ключ недействителен. Сохранён прежний ключ.", f.tabs.state.value.alertMessage)
    }

    @Test
    fun invalidSavedKeyIsDeletedAndNetworkFailureDoesNotRewriteIt() {
        for (failure in listOf(NetworkFailure.INVALID_KEY, NetworkFailure.TRANSPORT)) {
            val f = Fixture()
            f.valid(0)
            f.profile.setCoinMarketCapApiKey("bad-placeholder")
            f.invalid(1)
            f.network.validations[2](NetworkResult(null, "Safe error", failure))
            if (failure == NetworkFailure.INVALID_KEY) {
                assertEquals("", f.storage.coinMarketCapApiKey)
                assertEquals(1, f.storage.writes)
                assertEquals(ApiKeyValidationStatus.MISSING, f.interactor.state.value.status)
                assertFalse(f.profile.state.value.hasApiKey)
            } else {
                assertEquals("saved-placeholder", f.storage.coinMarketCapApiKey)
                assertEquals(0, f.storage.writes)
                assertEquals(ApiKeyValidationStatus.CHECK_FAILED, f.interactor.state.value.status)
            }
            assertFalse(f.tabs.portfolioCoordinator.state.value.isSearchEnabled)
            assertTrue(f.tabs.state.value.alertMessage != null)
        }
    }

    @Test
    fun emptyInputDeletesAfterCommitAndDoesNotMakeNetworkRequest() {
        val f = Fixture()
        f.valid(0)
        f.profile.editCoinMarketCapApiKey("")
        assertEquals("saved-placeholder", f.storage.coinMarketCapApiKey)
        f.timer.advance(5_000)
        assertEquals("", f.storage.coinMarketCapApiKey)
        assertEquals(1, f.network.keys.size)
        assertEquals(ApiKeyValidationStatus.MISSING, f.interactor.state.value.status)
        assertFalse(f.profile.state.value.hasApiKey)
    }

    @Test
    fun deletingKeyFromProfileResetsOpenEditWithoutChangingSelectedTab() {
        val f = Fixture()
        f.valid(0)
        val portfolio = f.tabs.portfolioCoordinator
        portfolio.openAssetSearch()
        portfolio.assetSearchViewModel.selectAsset(Asset("1", "BTC", name = "Bitcoin"))
        assertEquals(PortfolioRoute.EDIT, portfolio.state.value.currentRoute)
        assertTrue(portfolio.editViewModel != null)

        f.tabs.selectTab(AppTab.PROFILE)
        f.profile.setCoinMarketCapApiKey("")

        assertEquals(AppTab.PROFILE, f.tabs.state.value.selectedTab)
        assertEquals(PortfolioRoute.PORTFOLIO, portfolio.state.value.currentRoute)
        assertNull(portfolio.editViewModel)
        assertFalse(portfolio.state.value.isSearchEnabled)
    }

    @Test
    fun candidateNetworkErrorRetainsStorageAndFocusAloneDoesNotRecheckValidKey() {
        val f = Fixture()
        f.valid(0)
        f.profile.beginApiKeyEditing()
        f.profile.finishApiKeyEditing()
        assertEquals(1, f.network.keys.size)
        f.profile.setCoinMarketCapApiKey("new-placeholder")
        f.network.validations[1](NetworkResult(null, "Network request failed", NetworkFailure.TRANSPORT))
        assertEquals(0, f.storage.writes)
        assertEquals(ApiKeyValidationStatus.CHECK_FAILED, f.interactor.state.value.status)
        assertEquals("Не удалось проверить API-ключ. Проверьте подключение к интернету.", f.tabs.state.value.alertMessage)
        f.profile.beginApiKeyEditing()
        assertNull(f.tabs.state.value.alertMessage)
    }

    @Test
    fun rootGatesSearchAndKeyChangeResetsCatalogPreservingSelectedTab() {
        val f = Fixture()
        val portfolio = f.tabs.portfolioCoordinator
        portfolio.openAssetSearch()
        assertEquals(0, f.network.catalogs.size)
        f.valid(0)
        portfolio.openAssetSearch()
        f.network.catalogs[0](NetworkResult(listOf(Asset("1", "BTC", name = "Bitcoin")), null))
        portfolio.assetSearchViewModel.search("btc")
        f.tabs.selectTab(AppTab.PROFILE)
        f.tabs.selectTab(AppTab.PORTFOLIO)
        portfolio.openAssetSearch()
        assertEquals(1, f.network.keys.size)
        assertEquals(1, f.network.catalogs.size)
        f.tabs.selectTab(AppTab.PROFILE)
        f.profile.setCoinMarketCapApiKey("new-placeholder")
        assertEquals("saved-placeholder", f.storage.coinMarketCapApiKey)
        f.valid(1)
        assertEquals(AppTab.PROFILE, f.tabs.state.value.selectedTab)
        assertEquals(PortfolioRoute.PORTFOLIO, portfolio.state.value.currentRoute)
        assertTrue(portfolio.assetSearchViewModel.state.value.assets.isEmpty())
        portfolio.openAssetSearch()
        assertEquals(2, f.network.catalogs.size)
    }

    @Test
    fun oldCatalogFailureCannotShowAlertDuringEditingOrAfterKeyChange() {
        val f = Fixture()
        f.valid(0)
        f.tabs.portfolioCoordinator.openAssetSearch()
        f.profile.editCoinMarketCapApiKey("new-placeholder")
        f.network.catalogs[0](NetworkResult(null, "Old error", NetworkFailure.INVALID_KEY))
        assertNull(f.tabs.state.value.alertMessage)
        f.timer.advance(5_000)
        f.valid(1)
        f.network.catalogs[0](NetworkResult(null, "Old error", NetworkFailure.INVALID_KEY))
        assertEquals(ApiKeyValidationStatus.VALID, f.interactor.state.value.status)
        assertNull(f.tabs.state.value.alertMessage)
    }

    @Test
    fun missingStartupAndStorageWriteFailureNeverEnableSearch() {
        val f = Fixture("")
        assertEquals(ApiKeyValidationStatus.MISSING, f.interactor.state.value.status)
        assertEquals(0, f.network.keys.size)
        f.storage.rejectWrites = true
        f.profile.setCoinMarketCapApiKey("new-placeholder")
        f.valid(0)
        assertEquals(ApiKeyValidationStatus.CHECK_FAILED, f.interactor.state.value.status)
        assertEquals(NetworkFailure.STORAGE, f.interactor.state.value.failure)
        assertFalse(f.tabs.portfolioCoordinator.state.value.isSearchEnabled)
        assertFalse(f.profile.state.value.hasApiKey)
    }
}

private class Fixture(initialKey: String = "saved-placeholder") {
    val storage = FakeStorage(initialKey)
    val network = FakeNetwork()
    val timer = ManualTimer()
    val interactor = ApiKeyInteractor(storage, network)
    val profile = ProfileViewModel(null, interactor, timer::schedule)
    val tabs = TabBarCoordinator(PortfolioCoordinator(network), profileViewModel = profile, apiKeyInteractor = interactor)
    fun valid(index: Int) { network.validations[index](NetworkResult(true, null)) }
    fun invalid(index: Int) { network.validations[index](NetworkResult(null, "Invalid key", NetworkFailure.INVALID_KEY)) }
}

private class FakeStorage(initial: String) : ProfileSecureStorage {
    var writes = 0
    var rejectWrites = false
    override var coinMarketCapApiKey = initial
        set(value) { writes++; if (!rejectWrites) field = value }
}

private class ManualTimer {
    private var now = 0L
    private data class Task(val due: Long, val action: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()
    fun schedule(delay: Long, action: () -> Unit): () -> Unit {
        val task = Task(now + delay, action)
        tasks += task
        return { task.cancelled = true }
    }
    fun advance(milliseconds: Long) {
        now += milliseconds
        val due = tasks.filter { !it.cancelled && it.due <= now }
        tasks.removeAll(due)
        due.forEach { it.action() }
    }
}

private class FakeNetwork : AssetCatalog, ApiKeyValidation {
    val keys = mutableListOf<String>()
    val validations = mutableListOf<(NetworkResult<Boolean>) -> Unit>()
    val catalogs = mutableListOf<(NetworkResult<List<Asset>>) -> Unit>()
    override fun validateApiKey(apiKey: String, completion: (NetworkResult<Boolean>) -> Unit) {
        keys += apiKey
        validations += completion
    }
    override fun fetchMap(completion: (NetworkResult<List<Asset>>) -> Unit) { catalogs += completion }
}
