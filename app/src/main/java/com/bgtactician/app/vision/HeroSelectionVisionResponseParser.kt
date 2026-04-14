package com.bgtactician.app.vision

object HeroSelectionVisionResponseParser {

    fun extractJsonPayload(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("视觉模型返回为空")
        }

        val fenced = extractFromCodeFence(trimmed)
        if (fenced != null) {
            return fenced
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        return extractFirstJsonObject(trimmed)
            ?: throw IllegalArgumentException("未找到可解析的 JSON 对象")
    }

    private fun extractFromCodeFence(text: String): String? {
        val fenceStart = text.indexOf("```")
        if (fenceStart < 0) return null

        val firstLineEnd = text.indexOf('\n', startIndex = fenceStart)
        if (firstLineEnd < 0) return null

        val fenceEnd = text.indexOf("```", startIndex = firstLineEnd + 1)
        if (fenceEnd < 0) return null

        val content = text.substring(firstLineEnd + 1, fenceEnd).trim()
        if (content.startsWith("{") && content.endsWith("}")) {
            return content
        }

        return extractFirstJsonObject(content)
    }

    private fun extractFirstJsonObject(text: String): String? {
        var start = -1
        var depth = 0
        var inString = false
        var escaping = false

        text.forEachIndexed { index, char ->
            if (escaping) {
                escaping = false
                return@forEachIndexed
            }

            when (char) {
                '\\' -> {
                    if (inString) escaping = true
                }

                '"' -> {
                    inString = !inString
                }

                '{' -> {
                    if (!inString) {
                        if (depth == 0) start = index
                        depth += 1
                    }
                }

                '}' -> {
                    if (!inString && depth > 0) {
                        depth -= 1
                        if (depth == 0 && start >= 0) {
                            return text.substring(start, index + 1)
                        }
                    }
                }
            }
        }

        return null
    }
}
