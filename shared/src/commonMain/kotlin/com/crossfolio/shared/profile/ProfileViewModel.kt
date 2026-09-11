package com.crossfolio.shared.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileState(
    val message: String = "hello ProfileViewModel",
)

class ProfileViewModel {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    // TODO: Add profile settings and actions when persistence is implemented.
}
