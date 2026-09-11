package com.crossfolio.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crossfolio.shared.assetsearch.AssetSearchViewModel
import com.crossfolio.shared.portfolio.PortfolioCoordinator
import com.crossfolio.shared.portfolio.PortfolioRoute
import com.crossfolio.shared.portfolio.PortfolioViewModel

@Composable
fun PortfolioScreen(coordinator: PortfolioCoordinator) {
    val state by coordinator.state.collectAsState()

    when (state.currentRoute) {
        PortfolioRoute.PORTFOLIO -> PortfolioContent(coordinator.portfolioViewModel)
        PortfolioRoute.ASSET_SEARCH -> AssetSearchContent(coordinator.assetSearchViewModel)
    }
}

@Composable
private fun PortfolioContent(viewModel: PortfolioViewModel) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = state.message,
            modifier = Modifier.align(Alignment.Center),
        )
        FloatingActionButton(
            onClick = viewModel::onAssetSearch,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Text(text = "+")
        }
    }
}

@Composable
private fun AssetSearchContent(viewModel: AssetSearchViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Button(onClick = viewModel::onBack) {
            Text(text = "Назад")
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = state.message)
        }
    }
}
