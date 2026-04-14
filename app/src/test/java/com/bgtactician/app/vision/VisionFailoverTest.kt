package com.bgtactician.app.vision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionFailoverTest {

    private val primary = VisionEndpoint(
        label = "主模型",
        baseUrl = "https://primary.example.com",
        apiKey = "primary-key",
        model = "primary-model"
    )

    private val backup = VisionEndpoint(
        label = "备用模型",
        baseUrl = "https://backup.example.com",
        apiKey = "backup-key",
        model = "backup-model"
    )

    @Test
    fun returnsPrimaryWhenPrimarySucceeds() = runBlocking {
        val calls = mutableListOf<String>()

        val outcome = executeVisionFailover(
            endpoints = listOf(primary, backup),
            attempt = { endpoint ->
                calls += endpoint.label
                "${endpoint.label}-ok"
            }
        )

        val success = outcome as VisionFailoverOutcome.Success
        assertEquals("主模型", success.endpoint.label)
        assertEquals("主模型-ok", success.value)
        assertTrue(success.previousFailures.isEmpty())
        assertEquals(listOf("主模型"), calls)
    }

    @Test
    fun switchesToBackupWhenPrimaryThrows() = runBlocking {
        val calls = mutableListOf<String>()

        val outcome = executeVisionFailover(
            endpoints = listOf(primary, backup),
            attempt = { endpoint ->
                calls += endpoint.label
                if (endpoint == primary) {
                    error("HTTP 503")
                }
                "${endpoint.label}-ok"
            }
        )

        val success = outcome as VisionFailoverOutcome.Success
        assertEquals("备用模型", success.endpoint.label)
        assertEquals("备用模型-ok", success.value)
        assertEquals(listOf("主模型: HTTP 503"), success.previousFailures)
        assertEquals(listOf("主模型", "备用模型"), calls)
    }

    @Test
    fun switchesToBackupWhenPrimaryValidationFails() = runBlocking {
        val outcome = executeVisionFailover(
            endpoints = listOf(primary, backup),
            attempt = { endpoint ->
                if (endpoint == primary) "invalid" else "valid"
            },
            validate = { result ->
                if (result == "invalid") "校验失败: available_tribes 去重后不是 5 个" else null
            }
        )

        val success = outcome as VisionFailoverOutcome.Success
        assertEquals("备用模型", success.endpoint.label)
        assertEquals("valid", success.value)
        assertEquals(
            listOf("主模型: 校验失败: available_tribes 去重后不是 5 个"),
            success.previousFailures
        )
    }

    @Test
    fun returnsFailureWhenAllEndpointsFail() = runBlocking {
        val outcome = executeVisionFailover(
            endpoints = listOf(primary, backup),
            attempt = { endpoint ->
                error("${endpoint.label} unavailable")
            }
        )

        val failure = outcome as VisionFailoverOutcome.Failure
        assertEquals(
            listOf(
                "主模型: 主模型 unavailable",
                "备用模型: 备用模型 unavailable"
            ),
            failure.failures
        )
    }
}
