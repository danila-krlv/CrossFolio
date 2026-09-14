package com.crossfolio.android.ui.portfolio.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crossfolio.common.portfolio.edit.EditViewModel

@Composable
fun EditScreen(viewModel: EditViewModel) {
    BackHandler(onBack = viewModel::onBack)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = viewModel::onBack) { Text("Назад") }
        Text("Edit", style = MaterialTheme.typography.headlineMedium)
        Text(viewModel.asset.name, style = MaterialTheme.typography.titleLarge)
        Text(viewModel.asset.ticker, style = MaterialTheme.typography.bodyLarge)
    }
}
