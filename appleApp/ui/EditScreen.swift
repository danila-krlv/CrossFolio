import Common
import SwiftUI

struct EditScreen: View {
    let viewModel: EditViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                viewModel.onBack()
            } label: {
                Label("Назад", systemImage: "chevron.left")
            }
            Text("Edit").font(.largeTitle)
            Text(viewModel.asset.name).font(.title2)
            Text(viewModel.asset.ticker).font(.headline)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(16)
    }
}
