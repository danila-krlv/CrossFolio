import Common
import SwiftUI

struct ProfileScreen: View {
    let viewModel: ProfileViewModel

    var body: some View {
        Text(state.message)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var state: ProfileState {
        viewModel.state.value as! ProfileState
    }
}
