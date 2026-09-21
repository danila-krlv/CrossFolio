import Charts
import Common
import SwiftUI

struct AnalyticsScreen: View {
    let viewModel: AnalyticsViewModel
    @State private var state: AnalyticsState
    @State private var stopObserving: (() -> Void)?

    init(viewModel: AnalyticsViewModel) {
        self.viewModel = viewModel
        _state = State(initialValue: viewModel.state.value as! AnalyticsState)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                Text("Аналитика").font(.largeTitle.bold())
                if state.isLoading { ProgressView("Обновление…") }
                if let error = state.errorMessage {
                    VStack(alignment: .leading) {
                        Text(error).foregroundStyle(.secondary)
                        Button("Повторить") { viewModel.refresh() }
                    }
                }
                allocation
                Divider()
                history
            }
            .padding(24)
            .frame(maxWidth: 760, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .onAppear {
            stopObserving?()
            stopObserving = viewModel.observeState { state = $0 }
            viewModel.refresh()
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
        }
    }

    private var allocation: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Распределение активов").font(.title2.bold())
            Text("Доли по последним рыночным котировкам")
                .font(.subheadline).foregroundStyle(.secondary)
            if state.missingQuotes {
                Text("Для расчёта долей нужны котировки всех активов. Обновите цены при подключении к интернету.")
                    .foregroundStyle(.secondary)
            } else if state.shares.isEmpty {
                Text("Нет активов с ненулевой рыночной стоимостью.").foregroundStyle(.secondary)
            } else {
                Chart(Array(state.shares.enumerated()), id: \.offset) { index, share in
                    SectorMark(angle: .value("Доля", share.fraction), angularInset: 1)
                        .foregroundStyle(color(index))
                        .annotation(position: .overlay) {
                            if share.tenthsPercent >= 50 {
                                Text(share.percentText).font(.callout.bold()).foregroundStyle(.white)
                            }
                        }
                        .accessibilityLabel("\(share.asset.name), \(share.asset.ticker)")
                        .accessibilityValue(share.percentText)
                }
                .frame(height: 260)
                ForEach(Array(state.shares.enumerated()), id: \.offset) { index, share in
                    HStack(spacing: 10) {
                        Circle().fill(color(index)).frame(width: 10, height: 10)
                        Text(share.asset.ticker).fontWeight(.semibold)
                        Text(share.asset.name).foregroundStyle(.secondary)
                        Spacer(minLength: 8)
                        Text(share.percentText).monospacedDigit()
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    private var history: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Стоимость портфеля").font(.title2.bold())
            Text("USD · история наблюдений")
                .font(.subheadline).foregroundStyle(.secondary)
            if let last = state.history.last {
                Text(last.valueUsdText).font(.largeTitle.weight(.semibold)).minimumScaleFactor(0.5)
                Text(date(last), format: .dateTime.day().month().year().hour().minute())
                    .font(.caption).foregroundStyle(.secondary)
                Chart(state.history, id: \.observedAtEpochMillis) { point in
                    LineMark(x: .value("Дата", date(point)), y: .value("USD", point.chartValue))
                        .foregroundStyle(Color.accentColor)
                    PointMark(x: .value("Дата", date(point)), y: .value("USD", point.chartValue))
                        .foregroundStyle(Color.accentColor)
                        .accessibilityLabel(date(point).formatted())
                        .accessibilityValue(point.valueUsdText)
                }
                .chartYScale(domain: .automatic(includesZero: true))
                .frame(height: 240)
                if state.history.count == 1 {
                    Text("Первая оценка сохранена. График появится по мере новых наблюдений.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            } else {
                Text("История появится после первой полной рыночной оценки портфеля.")
                    .foregroundStyle(.secondary)
            }
            Text("Оценки сохраняются при открытии портфеля, изменении активов и обновлении котировок. Между наблюдениями данные не собираются. Сумма учитывает и изменение цен, и добавление или удаление активов — это не график доходности.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    private func date(_ point: PortfolioSnapshot) -> Date {
        Date(timeIntervalSince1970: Double(point.observedAtEpochMillis) / 1000)
    }

    private func color(_ index: Int) -> Color {
        let colors: [Color] = [.blue, .purple, .teal, .orange, .pink, .indigo, .green, .brown]
        return colors[index % colors.count]
    }
}
