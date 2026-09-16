package com.crossfolio.common.core.network

enum class ApiKeyValidationStatus {
    UNCHECKED,
    MISSING,
    CHECKING,
    VALID,
    INVALID,
    CHECK_FAILED,
}

data class ApiKeyValidationState(
    val status: ApiKeyValidationStatus = ApiKeyValidationStatus.UNCHECKED,
    val failure: NetworkFailure? = null,
    val isEditing: Boolean = false,
    val inputFailure: NetworkFailure? = null,
)
