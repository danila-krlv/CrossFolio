import Shared
import SwiftUI

struct AnalyticsScreen: View {
    let viewModel: AnalyticsViewModel

    var body: some View {
        Text(state.message)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var state: AnalyticsState {
        viewModel.state.value as! AnalyticsState
    }
}
