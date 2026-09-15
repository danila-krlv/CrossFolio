package com.crossfolio.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.crossfolio.common.core.network.ApiKeyValidationStatus
import com.crossfolio.common.profile.AppTheme
import com.crossfolio.common.profile.ProfileViewModel

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsState()
    var apiKey by remember(viewModel) { mutableStateOf(viewModel.getCoinMarketCapApiKey()) }
    val keyValidation by viewModel.apiKeyInteractor.state.collectAsState()
    LaunchedEffect(keyValidation.status, keyValidation.isEditing) {
        if (!keyValidation.isEditing && keyValidation.status in listOf(
                ApiKeyValidationStatus.VALID, ApiKeyValidationStatus.MISSING)) {
            apiKey = viewModel.getCoinMarketCapApiKey()
        }
    }

    val focusManager = LocalFocusManager.current
    DisposableEffect(viewModel) {
        onDispose { viewModel.finishApiKeyEditing() }
    }

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
            value = apiKey,
            onValueChange = {
                apiKey = it
                viewModel.editCoinMarketCapApiKey(it)
            },
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) viewModel.beginApiKeyEditing() else viewModel.finishApiKeyEditing()
                }
                .onKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                        viewModel.finishApiKeyEditing()
                        focusManager.clearFocus()
                        true
                    } else false
                },
            label = { Text("CoinMarketCap API-ключ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                viewModel.finishApiKeyEditing()
                focusManager.clearFocus()
            }),
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
