package com.crossfolio.android.ui.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crossfolio.android.ui.portfolio.assetsearch.AssetSearchScreen
import com.crossfolio.android.ui.portfolio.edit.EditScreen
import com.crossfolio.common.portfolio.PortfolioCoordinator
import com.crossfolio.common.portfolio.PortfolioRoute
import com.crossfolio.common.portfolio.overview.PortfolioViewModel
import com.crossfolio.common.portfolio.overview.PortfolioRowState

@Composable
fun PortfolioScreen(coordinator: PortfolioCoordinator) {
    val state by coordinator.state.collectAsState()

    when (state.currentRoute) {
        PortfolioRoute.PORTFOLIO -> PortfolioContent(coordinator.portfolioViewModel, state.isSearchEnabled)
        PortfolioRoute.ASSET_SEARCH -> AssetSearchScreen(coordinator.assetSearchViewModel)
        PortfolioRoute.EDIT -> coordinator.editViewModel?.let { EditScreen(it) }
    }
}

@Composable
private fun PortfolioContent(viewModel: PortfolioViewModel, isSearchEnabled: Boolean) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, isSearchEnabled) { viewModel.loadPositions() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Стоимость портфеля",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "\$${state.totalValueUsdText}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()

            when {
                state.isLoading && state.positions.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.storageFailure != null && state.positions.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Не удалось загрузить портфель")
                    Button(onClick = viewModel::loadPositions) { Text("Повторить") }
                }
                state.rows.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Добавьте первый актив",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(
                        items = state.rows,
                        key = { "${it.position.asset.searchPlatform.name}:${it.position.asset.searchId}" },
                    ) { row ->
                        PortfolioRow(row, viewModel)
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
        FilledIconButton(
            enabled = isSearchEnabled,
            onClick = viewModel::onAssetSearch,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp),
        ) {
            Text(text = "+", modifier = Modifier.semantics { contentDescription = "Добавить актив" })
        }
    }
}

@Composable
private fun PortfolioRow(row: PortfolioRowState, viewModel: PortfolioViewModel) {
    val position = row.position
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssetLogo(viewModel.logoUrl(position.asset), viewModel::loadImage)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(position.asset.ticker, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${position.quantity.value} ${position.asset.ticker}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "\$${row.valueUsdText}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
