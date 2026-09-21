package com.crossfolio.android.ui.portfolio.assetsearch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.crossfolio.android.ui.portfolio.AssetLogo
import com.crossfolio.common.core.asset.Asset
import com.crossfolio.common.portfolio.assetsearch.AssetSearchViewModel

@Composable
fun AssetSearchScreen(viewModel: AssetSearchViewModel) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = viewModel::onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = viewModel::onBack) { Text("Назад") }
        OutlinedTextField(
            value = state.searchText,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Тикер или название") },
            placeholder = { Text("BTC или Bitcoin") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Не удалось загрузить каталог.")
                Button(onClick = { viewModel.loadCatalog(false) }) { Text("Повторить") }
            }
            state.assets.isEmpty() -> Text("Монеты не найдены")
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.assets, key = { "${it.searchPlatform.name}:${it.searchId}" }) { asset ->
                    AssetRow(asset, state.logoUrls[asset.searchId], viewModel)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: Asset, logoUrl: String?, viewModel: AssetSearchViewModel) {
    LaunchedEffect(asset.searchId) { viewModel.loadLogo(asset) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClickLabel = "Открыть ${asset.name}") { viewModel.selectAsset(asset) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssetLogo(logoUrl, viewModel::loadImage)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(asset.ticker, style = MaterialTheme.typography.titleMedium)
            Text(asset.name, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
