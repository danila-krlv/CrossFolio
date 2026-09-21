package com.crossfolio.android.ui.portfolio.edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crossfolio.common.portfolio.edit.EditFieldState
import com.crossfolio.common.portfolio.edit.EditViewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(viewModel: EditViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    BackHandler { if (!state.isSaving) viewModel.onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить актив") },
                navigationIcon = { TextButton(onClick = viewModel::onBack, enabled = !state.isSaving) { Text("Назад") } },
                actions = { TextButton(onClick = viewModel::save, enabled = state.canSave && !state.isSaving) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(viewModel.asset.name, style = MaterialTheme.typography.headlineSmall)
            Text(viewModel.asset.ticker, style = MaterialTheme.typography.titleMedium)
            DecimalField("Количество (${viewModel.asset.ticker})", state.quantity, viewModel::setQuantity, enabled = !state.isSaving)
            Column {
                Text("Дата и время", style = MaterialTheme.typography.labelLarge)
                TextButton(enabled = !state.isSaving, onClick = {
                    val selected = Calendar.getInstance().apply { timeInMillis = state.occurredAtEpochMillis }
                    DatePickerDialog(context, { _, year, month, day ->
                        selected.set(year, month, day)
                        viewModel.setDate(selected.timeInMillis)
                    }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                        selected.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }) { Text(DateFormat.getDateInstance().format(Date(state.occurredAtEpochMillis))) }
                TextButton(enabled = !state.isSaving, onClick = {
                    val selected = Calendar.getInstance().apply { timeInMillis = state.occurredAtEpochMillis }
                    TimePickerDialog(context, { _, hour, minute ->
                        selected.set(Calendar.HOUR_OF_DAY, hour)
                        selected.set(Calendar.MINUTE, minute)
                        viewModel.setDate(selected.timeInMillis)
                    }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE),
                        android.text.format.DateFormat.is24HourFormat(context)).show()
                }) { Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.occurredAtEpochMillis))) }
                if (state.hasEditedDate && state.dateError != null) {
                    Text(state.dateError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
            DecimalField("Цена приобретения, USD", state.price, viewModel::setPrice,
                when {
                    state.marketPriceUsdText != null ->
                        "Необязательно — используется ${state.marketPriceUsdText} USD"
                    state.isMarketPriceLoading -> "Загружаем котировку… Можно указать цену вручную"
                    else -> "Котировка недоступна — укажите цену вручную"
                }, enabled = !state.isSaving)
            DecimalField("Комиссия, USD", state.commission, viewModel::setCommission,
                "Необязательно — по умолчанию 0 USD", enabled = !state.isSaving)
            if (state.isSaving) CircularProgressIndicator()
            if (state.storageFailure != null) {
                Text("Не удалось сохранить операцию. Попробуйте сохранить ещё раз.",
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DecimalField(
    label: String,
    field: EditFieldState,
    onChange: (String) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = field.text, onValueChange = onChange,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, isError = field.isError, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = {
            if (field.isError) Text(field.error.orEmpty()) else if (hint != null) Text(hint)
        },
    )
}
