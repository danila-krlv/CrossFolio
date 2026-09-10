import Shared
import SwiftUI

struct ContentView: View {
    private let viewModel: HelloViewModel

    init(viewModel: HelloViewModel = HelloViewModel()) {
        self.viewModel = viewModel
    }

    var body: some View {
        Text(viewModel.greeting)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    ContentView()
}
