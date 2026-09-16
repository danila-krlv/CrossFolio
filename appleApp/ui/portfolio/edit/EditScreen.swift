import Common
import SwiftUI

struct EditScreen: View {
    let viewModel: EditViewModel
    @State private var state: EditState
    @State private var stopObserving: (() -> Void)?
    private enum Field: Hashable { case quantity, price, commission }
    @FocusState private var focusedField: Field?

    init(viewModel: EditViewModel) {
        self.viewModel = viewModel
        _state = State(initialValue: viewModel.state.value as! EditState)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(viewModel.asset.name).font(.title2)
                        Text(viewModel.asset.ticker).font(.headline).foregroundStyle(.secondary)
                    }
                    decimalField("Количество (\(viewModel.asset.ticker))", field: state.quantity,
                                 id: .quantity,
                                 onChange: { viewModel.setQuantity(text: $0) })
                    VStack(alignment: .leading, spacing: 6) {
                        DatePicker("Дата и время", selection: eventDate, in: ...Date(),
                                   displayedComponents: [.date, .hourAndMinute])
                        if state.hasEditedDate, let error = state.dateError {
                            Text(error).font(.caption).foregroundStyle(.red)
                        }
                    }
                    decimalField("Цена приобретения, USD", field: state.price,
                                 id: .price,
                                 hint: marketPriceHint,
                                 onChange: { viewModel.setPrice(text: $0) })
                    decimalField("Комиссия, USD", field: state.commission,
                                 id: .commission,
                                 hint: "Необязательно — по умолчанию 0 USD",
                                 onChange: { viewModel.setCommission(text: $0) })
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Добавить актив")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { viewModel.onBack() } label: {
                        Label("Назад", systemImage: "chevron.left")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { viewModel.save() }.disabled(!state.canSave)
                }
                #if os(iOS)
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") { focusedField = nil }
                }
                #endif
            }
        }
        .onAppear {
            stopObserving?()
            stopObserving = viewModel.observeState { state = $0 }
        }
        .onDisappear {
            stopObserving?()
            stopObserving = nil
        }
    }

    private var marketPriceHint: String {
        if let price = state.marketPriceUsdText {
            return "Необязательно — используется \(price) USD"
        }
        return state.isMarketPriceLoading
            ? "Загружаем котировку… Можно указать цену вручную"
            : "Котировка недоступна — укажите цену вручную"
    }

    private var eventDate: Binding<Date> {
        Binding(
            get: { Date(timeIntervalSince1970: Double(state.occurredAtEpochMillis) / 1000) },
            set: { viewModel.setDate(epochMillis: Int64($0.timeIntervalSince1970 * 1000)) }
        )
    }

    private func decimalField(
        _ label: String, field: EditFieldState, id: Field, hint: String? = nil,
        onChange: @escaping (String) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.subheadline)
            TextField(label, text: Binding(get: { field.text }, set: onChange))
                .textFieldStyle(.plain)
                .focused($focusedField, equals: id)
                #if os(iOS)
                .keyboardType(.decimalPad)
                #endif
                .padding(12)
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(field.isError ? Color.red : Color.secondary.opacity(0.4), lineWidth: 1)
                }
                .accessibilityLabel(label)
            if field.isError, let error = field.error {
                Text(error).font(.caption).foregroundStyle(.red)
            } else if let hint {
                Text(hint).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}
