package com.crossfolio.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.crossfolio.common.profile.AppTheme
import com.crossfolio.common.profile.ProfileViewModel

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.userName,
            onValueChange = viewModel::setUserName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Имя пользователя") },
            singleLine = true,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Тема")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppTheme.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = state.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppTheme.entries.size,
                        ),
                        label = { Text(theme.title) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.coinMarketCapApiKey,
            onValueChange = viewModel::setCoinMarketCapApiKey,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("CoinMarketCap API-ключ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}

private val AppTheme.title: String
    get() = when (this) {
        AppTheme.SYSTEM -> "Системная"
        AppTheme.LIGHT -> "Светлая"
        AppTheme.DARK -> "Тёмная"
    }
