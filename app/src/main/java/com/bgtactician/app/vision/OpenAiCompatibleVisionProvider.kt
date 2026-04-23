package com.bgtactician.app.vision

import android.graphics.Bitmap
import android.util.Base64
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class OpenAiCompatibleResponseMode {
    NONE,
    JSON_OBJECT,
    JSON_SCHEMA
}

data class OpenAiCompatibleVisionConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val responseMode: OpenAiCompatibleResponseMode = OpenAiCompatibleResponseMode.NONE,
    val reasoningEffort: String? = "none",
    val connectTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 45_000,
    val imageDetail: String = "auto",
    val imageMaxLongEdge: Int = 768,
    val jpegQuality: Int = 72,
    val cropTopRatio: Float = 0.42f,
    val temperature: Double = 0.0,
    val maxTokens: Int = 80,
    val fullRecognitionMaxTokens: Int = 120
)

data class OpenAiCompatibleVisionMetrics(
    val totalMillis: Long = 0,
    val imageEncodeMillis: Long = 0,
    val networkMillis: Long = 0,
    val responseDecodeMillis: Long = 0,
    val jsonExtractMillis: Long = 0,
    val resultDecodeMillis: Long = 0,
    val imageBytes: Int = 0,
    val requestBodyBytes: Int = 0,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val finishReason: String? = null
)

data class OpenAiCompatibleVisionExecution(
    val result: HeroSelectionVisionResult,
    val metrics: OpenAiCompatibleVisionMetrics
)

class OpenAiCompatibleVisionException(
    message: String,
    val metrics: OpenAiCompatibleVisionMetrics,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class OpenAiCompatibleVisionProvider(
    private val config: OpenAiCompatibleVisionConfig
) : HeroSelectionVisionProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "_kind"
    }

    override suspend fun analyze(request: HeroSelectionVisionRequest): HeroSelectionVisionResult {
        return analyzeDetailed(request).result
    }

    suspend fun analyzeDetailed(request: HeroSelectionVisionRequest): OpenAiCompatibleVisionExecution {
        require(config.baseUrl.isNotBlank()) { "baseUrl 不能为空" }
        require(config.apiKey.isNotBlank()) { "apiKey 不能为空" }
        require(config.model.isNotBlank()) { "model 不能为空" }
        val startedAt = System.currentTimeMillis()
        var metrics = OpenAiCompatibleVisionMetrics()

        val endpoint = buildChatCompletionsUrl(config.baseUrl)
        val prompt = HeroSelectionVisionPromptFactory.buildInstruction(
            localeHint = request.localeHint,
            recognitionScope = request.recognitionScope
        )
        val encodeStartedAt = System.currentTimeMillis()
        val encodedImage = encodeBitmapAsDataUrl(
            bitmap = request.frame.bitmap,
            recognitionScope = request.recognitionScope
        )
        metrics = metrics.copy(
            imageEncodeMillis = System.currentTimeMillis() - encodeStartedAt,
            imageBytes = encodedImage.byteSize
        )

        val maxTokens = when (request.recognitionScope) {
            VisionRecognitionScope.TRIBES_ONLY -> config.maxTokens
            VisionRecognitionScope.FULL -> maxOf(config.maxTokens, config.fullRecognitionMaxTokens)
        }

        val body = ChatCompletionsRequest(
            model = config.model,
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ChatTextPart(
                            type = "text",
                            text = prompt
                        ),
                        ChatImagePart(
                            type = "image_url",
                            imageUrl = ChatImageUrl(
                                url = encodedImage.dataUrl,
                                detail = config.imageDetail
                            )
                        )
                    )
                )
            ),
            temperature = config.temperature,
            maxTokens = maxTokens,
            reasoningEffort = config.reasoningEffort,
            responseFormat = when (config.responseMode) {
                OpenAiCompatibleResponseMode.NONE -> null
                OpenAiCompatibleResponseMode.JSON_OBJECT -> {
                    ChatResponseFormatJsonObject(type = "json_object")
                }

                OpenAiCompatibleResponseMode.JSON_SCHEMA -> {
                    ChatResponseFormatJsonSchema(
                        type = "json_schema",
                        jsonSchema = ChatJsonSchemaWrapper(
                            name = "hero_selection_vision_result",
                            strict = true,
                            schema = heroSelectionSchema()
                        )
                    )
                }
            }
        )

        val requestJson = json.encodeToString(body)
        metrics = metrics.copy(requestBodyBytes = requestJson.toByteArray(Charsets.UTF_8).size)

        val requestStartedAt = System.currentTimeMillis()
        val rawResponse = runCatching {
            postJson(endpoint, requestJson)
        }.getOrElse { error ->
            throw OpenAiCompatibleVisionException(
                message = error.message ?: "视觉请求失败",
                metrics = metrics.copy(
                    networkMillis = System.currentTimeMillis() - requestStartedAt,
                    totalMillis = System.currentTimeMillis() - startedAt
                ),
                cause = error
            )
        }
        metrics = metrics.copy(networkMillis = System.currentTimeMillis() - requestStartedAt)

        val responseDecodeStartedAt = System.currentTimeMillis()
        val completion = runCatching {
            json.decodeFromString<ChatCompletionsResponse>(rawResponse)
        }.getOrElse { error ->
            throw OpenAiCompatibleVisionException(
                message = buildString {
                    append("视觉响应解析失败")
                    append('\n')
                    append("response_preview: ")
                    append(rawResponse.preview())
                },
                metrics = metrics.copy(
                    responseDecodeMillis = System.currentTimeMillis() - responseDecodeStartedAt,
                    totalMillis = System.currentTimeMillis() - startedAt
                ),
                cause = error
            )
        }
        metrics = metrics.copy(
            responseDecodeMillis = System.currentTimeMillis() - responseDecodeStartedAt,
            promptTokens = completion.usage?.promptTokens,
            completionTokens = completion.usage?.completionTokens,
            totalTokens = completion.usage?.totalTokens,
            finishReason = completion.choices.firstOrNull()?.finishReason
        )

        val rawText = completion.firstTextContent()
            ?: throw OpenAiCompatibleVisionException(
                buildString {
                    append("视觉模型未返回可解析文本")
                    completion.choices.firstOrNull()?.finishReason?.let {
                        append('\n')
                        append("finish_reason: ")
                        append(it)
                    }
                    append('\n')
                    append("response_preview: ")
                    append(rawResponse.preview())
                },
                metrics = metrics.copy(totalMillis = System.currentTimeMillis() - startedAt)
            )

        val jsonExtractStartedAt = System.currentTimeMillis()
        val payload = runCatching {
            HeroSelectionVisionResponseParser.extractJsonPayload(rawText)
        }.getOrElse { error ->
            throw OpenAiCompatibleVisionException(
                message = buildString {
                    append("模型文本中未提取到 JSON")
                    completion.choices.firstOrNull()?.finishReason?.let {
                        append('\n')
                        append("finish_reason: ")
                        append(it)
                    }
                    append('\n')
                    append("model_text_preview: ")
                    append(rawText.preview())
                },
                metrics = metrics.copy(
                    jsonExtractMillis = System.currentTimeMillis() - jsonExtractStartedAt,
                    totalMillis = System.currentTimeMillis() - startedAt
                ),
                cause = error
            )
        }
        metrics = metrics.copy(jsonExtractMillis = System.currentTimeMillis() - jsonExtractStartedAt)

        val resultDecodeStartedAt = System.currentTimeMillis()
        val result = runCatching {
            HeroSelectionVisionJsonCodec.decode(payload)
        }.getOrElse { error ->
            throw OpenAiCompatibleVisionException(
                message = buildString {
                    append("JSON 结果解析失败")
                    append('\n')
                    append("json_preview: ")
                    append(payload.preview())
                },
                metrics = metrics.copy(
                    resultDecodeMillis = System.currentTimeMillis() - resultDecodeStartedAt,
                    totalMillis = System.currentTimeMillis() - startedAt
                ),
                cause = error
            )
        }
        metrics = metrics.copy(
            resultDecodeMillis = System.currentTimeMillis() - resultDecodeStartedAt,
            totalMillis = System.currentTimeMillis() - startedAt
        )
        return OpenAiCompatibleVisionExecution(result = result, metrics = metrics)
    }

    private fun postJson(url: String, body: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = config.connectTimeoutMillis
            readTimeout = config.readTimeoutMillis
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(
                    buildString {
                        append("视觉请求失败: HTTP ${connection.responseCode}")
                        if (errorText.isNotBlank()) {
                            append('\n')
                            append("error_preview: ")
                            append(errorText.preview())
                        }
                    }
                )
            }
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeBitmapAsDataUrl(
        bitmap: Bitmap,
        recognitionScope: VisionRecognitionScope
    ): EncodedImage {
        val cropTopRatio = when (recognitionScope) {
            VisionRecognitionScope.TRIBES_ONLY -> config.cropTopRatio
            VisionRecognitionScope.FULL -> 1f
        }
        val imageMaxLongEdge = when (recognitionScope) {
            VisionRecognitionScope.TRIBES_ONLY -> config.imageMaxLongEdge
            VisionRecognitionScope.FULL -> config.imageMaxLongEdge
        }
        val jpegQuality = when (recognitionScope) {
            VisionRecognitionScope.TRIBES_ONLY -> config.jpegQuality
            VisionRecognitionScope.FULL -> config.jpegQuality
        }

        var preparedBitmap = cropBitmapIfNeeded(bitmap, cropTopRatio)
        val resizedBitmap = resizeBitmapIfNeeded(preparedBitmap, imageMaxLongEdge)
        if (resizedBitmap !== preparedBitmap && preparedBitmap !== bitmap) {
            preparedBitmap.recycle()
        }
        preparedBitmap = resizedBitmap
        val output = ByteArrayOutputStream()
        preparedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(50, 95), output)
        val bytes = output.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (preparedBitmap !== bitmap) {
            preparedBitmap.recycle()
        }
        return EncodedImage(
            dataUrl = "data:image/jpeg;base64,$base64",
            byteSize = bytes.size
        )
    }

    private fun cropBitmapIfNeeded(bitmap: Bitmap, cropTopRatio: Float): Bitmap {
        if (cropTopRatio <= 0f || cropTopRatio >= 1f) return bitmap
        val targetHeight = (bitmap.height * cropTopRatio).toInt().coerceAtLeast(1)
        if (targetHeight >= bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, targetHeight)
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        if (maxLongEdge <= 0) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) return bitmap

        val scale = maxLongEdge.toFloat() / longEdge.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        return baseUrl.trimEnd('/') + "/chat/completions"
    }

    private fun String.preview(maxLength: Int = 1200): String {
        val normalized = replace('\n', ' ').replace('\r', ' ').trim()
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }

    private data class EncodedImage(
        val dataUrl: String,
        val byteSize: Int
    )

    private fun heroSelectionSchema(): JsonObject {
        return obj(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to obj(
                "screen_type" to obj(
                    "type" to "string",
                    "enum" to array("hero_selection", "non_target", "unknown")
                ),
                "available_tribes" to obj(
                    "type" to "array",
                    "items" to obj(
                        "type" to "string",
                        "enum" to array(
                            "Beast",
                            "Demon",
                            "Dragon",
                            "Elemental",
                            "Mech",
                            "Murloc",
                            "Naga",
                            "Pirate",
                            "Quilboar",
                            "Undead"
                        )
                    )
                ),
                "hero_options" to obj(
                    "type" to "array",
                    "items" to obj(
                        "type" to "object",
                        "additionalProperties" to false,
                        "properties" to obj(
                            "slot" to obj("type" to "integer"),
                            "name" to obj("type" to array("string", "null")),
                            "locked" to obj("type" to "boolean"),
                            "armor" to obj("type" to array("integer", "null"))
                        ),
                        "required" to array("slot", "name", "locked", "armor")
                    )
                ),
                "confidence" to obj("type" to array("number", "null")),
                "model_name" to obj("type" to array("string", "null")),
                "request_id" to obj("type" to array("string", "null")),
                "raw_summary" to obj("type" to array("string", "null"))
            ),
            "required" to array("screen_type", "available_tribes", "hero_options")
        )
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject {
        return JsonObject(
            pairs.associate { (key, value) ->
                key to value.toJsonElement()
            }
        )
    }

    private fun array(vararg values: Any?): JsonArray = JsonArray(values.map { it.toJsonElement() })

    private fun Any?.toJsonElement(): JsonElement {
        return when (this) {
            null -> JsonNull
            is JsonElement -> this
            is String -> JsonPrimitive(this)
            is Int -> JsonPrimitive(this)
            is Long -> JsonPrimitive(this)
            is Float -> JsonPrimitive(this)
            is Double -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            else -> error("Unsupported json schema value: $this")
        }
    }
}

@Serializable
private data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null,
    @SerialName("response_format")
    val responseFormat: ChatResponseFormat? = null
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: List<ChatContentPart>
)

@Serializable
private sealed interface ChatContentPart {
    val type: String
}

@Serializable
private data class ChatTextPart(
    override val type: String,
    val text: String
) : ChatContentPart

@Serializable
private data class ChatImagePart(
    override val type: String,
    @SerialName("image_url")
    val imageUrl: ChatImageUrl
) : ChatContentPart

@Serializable
private data class ChatImageUrl(
    val url: String,
    val detail: String? = null
)

@Serializable
private sealed interface ChatResponseFormat {
    val type: String
}

@Serializable
private data class ChatResponseFormatJsonObject(
    override val type: String
) : ChatResponseFormat

@Serializable
private data class ChatResponseFormatJsonSchema(
    override val type: String,
    @SerialName("json_schema")
    val jsonSchema: ChatJsonSchemaWrapper
) : ChatResponseFormat

@Serializable
private data class ChatJsonSchemaWrapper(
    val name: String,
    val strict: Boolean = true,
    val schema: JsonObject
)

@Serializable
private data class ChatCompletionsResponse(
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null
) {
    fun firstTextContent(): String? {
        val message = choices.firstOrNull()?.message ?: return null
        if (!message.content.isNullOrBlank()) return message.content
        return message.parts
            ?.mapNotNull { part -> part.text }
            ?.firstOrNull { it.isNotBlank() }
    }
}

@Serializable
private data class ChatChoice(
    val message: ChatResponseMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
private data class ChatUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
private data class ChatResponseMessage(
    val content: String? = null,
    @SerialName("content_parts")
    val parts: List<ChatResponsePart>? = null
)

@Serializable
private data class ChatResponsePart(
    val type: String? = null,
    val text: String? = null
)
