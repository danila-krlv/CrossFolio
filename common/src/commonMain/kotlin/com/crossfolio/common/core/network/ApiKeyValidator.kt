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

class ApiKeyValidator(
    private val validate: (String, (NetworkResult<Boolean>) -> Unit) -> Unit,
) {
    constructor(validation: ApiKeyValidation) : this(validation::validateApiKey)

    fun check(apiKey: String, completion: (NetworkResult<Boolean>) -> Unit) {
        validate(apiKey, completion)
    }
}
