package com.crossfolio.android.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crossfolio.common.analytics.AnalyticsViewModel
import com.crossfolio.common.analytics.AssetShare
import com.crossfolio.common.analytics.PortfolioSnapshot
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

private val chartColors = listOf(
    Color(0xFF246BCE), Color(0xFF8950B8), Color(0xFF008577), Color(0xFFC16B13),
    Color(0xFFCC4378), Color(0xFF5253AB), Color(0xFF37823F), Color(0xFF886247),
)

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Аналитика", style = MaterialTheme.typography.headlineLarge)
            if (state.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Повторить") }
            }
            Text("Распределение активов", style = MaterialTheme.typography.titleLarge)
            Text("Доли по последним рыночным котировкам", color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.missingQuotes -> Text("Для расчёта долей нужны котировки всех активов. Обновите цены при подключении к интернету.")
                state.shares.isEmpty() -> Text("Нет активов с ненулевой рыночной стоимостью.")
                else -> {
                    AllocationChart(state.shares)
                    state.shares.forEachIndexed { index, share ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(10.dp).background(chartColors[index % chartColors.size], CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(share.asset.ticker, fontWeight = FontWeight.SemiBold)
                                Text(share.asset.name, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(share.percentText)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Text("Стоимость портфеля", style = MaterialTheme.typography.titleLarge)
            Text("USD · история наблюдений", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.history.isEmpty()) {
                Text("История появится после первой полной рыночной оценки портфеля.")
            } else {
                Text(state.history.last().valueUsdText, style = MaterialTheme.typography.headlineLarge)
                Text(snapshotDate(state.history.last()), style = MaterialTheme.typography.bodySmall)
                HistoryChart(state.history)
                if (state.history.size == 1) {
                    Text("Первая оценка сохранена. График появится по мере новых наблюдений.")
                }
            }
            Text(
                "Оценки сохраняются при открытии портфеля, изменении активов и обновлении котировок. " +
                    "Между наблюдениями данные не собираются. Сумма учитывает и изменение цен, " +
                    "и добавление или удаление активов — это не график доходности.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllocationChart(shares: List<AssetShare>) {
    val measurer = rememberTextMeasurer()
    val description = shares.joinToString { "${it.asset.name}, ${it.asset.ticker}: ${it.percentText}" }
    Canvas(Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription = description }) {
        val diameter = minOf(size.width, size.height)
        val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        var start = -90f
        shares.forEachIndexed { index, share ->
            val sweep = (share.fraction * 360).toFloat()
            drawArc(chartColors[index % chartColors.size], start, sweep, true, origin, Size(diameter, diameter))
            if (share.tenthsPercent >= 50) {
                val angle = (start + sweep / 2) * Math.PI / 180
                val label = measurer.measure(share.percentText, TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                val point = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (diameter * 0.32f)
                drawText(label, topLeft = point - Offset(label.size.width / 2f, label.size.height / 2f))
            }
            start += sweep
        }
    }
}

@Composable
private fun HistoryChart(points: List<PortfolioSnapshot>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maximum = points.maxBy { it.valueUsd }.let { if (it.chartValue > 0) it else null }
    val description = "От ${snapshotDate(points.first())}: ${points.first().valueUsdText}; " +
        "до ${snapshotDate(points.last())}: ${points.last().valueUsdText}. " +
        "Максимум ${maximum?.valueUsdText ?: "$0.00"}."
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(maximum?.valueUsdText ?: "$0.00", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
            val inset = 6.dp.toPx()
            val width = size.width - inset * 2
            val height = size.height - inset * 2
            val firstTime = points.first().observedAtEpochMillis
            val duration = (points.last().observedAtEpochMillis - firstTime).coerceAtLeast(1).toDouble()
            val maxValue = maximum?.chartValue ?: 1.0
            repeat(5) { index ->
                val y = inset + height * index / 4
                drawLine(gridColor, Offset(inset, y), Offset(size.width - inset, y))
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2 else inset + ((point.observedAtEpochMillis - firstTime) / duration * width).toFloat()
                val y = inset + height * (1 - point.chartValue / maxValue).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(lineColor, 3.dp.toPx(), Offset(x, y))
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        Text("$0.00", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(snapshotDate(points.first()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (points.size > 1) Text(snapshotDate(points.last()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        }
    }
}

private fun snapshotDate(point: PortfolioSnapshot): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(point.observedAtEpochMillis))
