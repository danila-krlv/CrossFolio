package com.crossfolio.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.crossfolio.common.asset.Asset
import com.crossfolio.common.assetsearch.AssetSearchViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
fun AssetSearchScreen(viewModel: AssetSearchViewModel) {
    val state by viewModel.state.collectAsState()

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
                Text("Не удалось загрузить каталог. Проверьте API-ключ в профиле и подключение к интернету.")
                Button(onClick = viewModel::loadCatalog) { Text("Повторить") }
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
    LaunchedEffect(asset.searchId) { viewModel.loadLogo(asset.searchId) }
    val image by produceState<ImageBitmap?>(null, logoUrl, viewModel) {
        value = null
        val url = logoUrl ?: return@produceState
        val bytes = suspendCancellableCoroutine<ByteArray?> { continuation ->
            viewModel.loadImage(url) { result ->
                if (continuation.isActive) continuation.resume(result.value)
            }
        } ?: return@produceState
        value = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            val bitmap = image
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text("◉", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(asset.ticker, style = MaterialTheme.typography.titleMedium)
            Text(asset.name, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
