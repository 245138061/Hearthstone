package com.bgtactician.app.vision

data class VisionEndpoint(
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String
)

sealed interface VisionFailoverOutcome<out T> {
    data class Success<T>(
        val endpoint: VisionEndpoint,
        val value: T,
        val previousFailures: List<String>
    ) : VisionFailoverOutcome<T>

    data class Failure(
        val failures: List<String>
    ) : VisionFailoverOutcome<Nothing>
}

suspend fun <T> executeVisionFailover(
    endpoints: List<VisionEndpoint>,
    attempt: suspend (VisionEndpoint) -> T,
    validate: (T) -> String? = { null }
): VisionFailoverOutcome<T> {
    val failures = mutableListOf<String>()

    for (endpoint in endpoints) {
        val value = runCatching {
            attempt(endpoint)
        }.getOrElse { error ->
            failures += "${endpoint.label}: ${error.message?.lineSequence()?.firstOrNull().orEmpty()}"
                .trimEnd(':', ' ')
            continue
        }

        val validationError = validate(value)
        if (validationError != null) {
            failures += "${endpoint.label}: $validationError"
            continue
        }

        return VisionFailoverOutcome.Success(
            endpoint = endpoint,
            value = value,
            previousFailures = failures.toList()
        )
    }

    return VisionFailoverOutcome.Failure(failures = failures.toList())
}
