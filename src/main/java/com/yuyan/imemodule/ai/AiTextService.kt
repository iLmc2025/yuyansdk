package com.yuyan.imemodule.ai

import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.prefs.behavior.AiAssistRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 文本服务（OpenAI 兼容优先）。
 *
 * 支持：
 * 1) mock：本地模拟返回（便于调试）；
 * 2) http：请求 OpenAI 兼容接口，默认使用 /v1/chat/completions。
 */
class AiTextService private constructor() {

    enum class AssistMode(val prompt: String) {
        Continue("请基于用户输入自然续写，保持语义连贯，输出不超过 60 字。"),
        Polish("请将用户输入润色成更通顺、自然、简洁的中文。"),
        Expand("请在不改变原意的前提下扩写内容，使表达更完整。"),
        Formal("请改写为正式、礼貌、可用于工作沟通的表达。");

        companion object {
            fun fromRaw(raw: String): AssistMode = entries.firstOrNull { it.name == raw } ?: Continue
        }
    }

    data class CompletionRequest(
        val textBeforeCursor: String,
        val mode: AssistMode,
        val maxTokens: Int = 160,
    )

    data class CompletionResult(
        val ok: Boolean,
        val text: String,
        val errorMessage: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun complete(request: CompletionRequest): CompletionResult {
        val prefs = AppPrefs.getInstance().input
        if (!prefs.aiAssistEnabled.getValue()) {
            return CompletionResult(false, "", "AI功能未开启")
        }
        if (request.textBeforeCursor.isBlank()) {
            return CompletionResult(false, "", "光标前无可处理内容")
        }

        return if (prefs.aiUseMock.getValue()) mockComplete(request) else httpComplete(request)
    }

    private fun mockComplete(request: CompletionRequest): CompletionResult {
        val role = AppPrefs.getInstance().input.aiAssistRole.getValue()
        val source = request.textBeforeCursor.takeLast(80)
        val suffix = when (request.mode) {
            AssistMode.Continue -> "，这是续写示例。"
            AssistMode.Polish -> "（已润色）"
            AssistMode.Expand -> "，补充一点细节后会更完整。"
            AssistMode.Formal -> "，烦请您知悉并处理，谢谢。"
        }
        return CompletionResult(ok = true, text = "【${role.name}】" + source + suffix)
    }

    private fun httpComplete(request: CompletionRequest): CompletionResult {
        val prefs = AppPrefs.getInstance().input
        val endpoint = normalizeEndpoint(prefs.aiEndpoint.getValue().trim())
        val apiKey = prefs.aiApiKey.getValue().trim()
        val model = prefs.aiModel.getValue().trim().ifBlank { "gpt-4o-mini" }

        if (endpoint.isBlank()) return CompletionResult(false, "", "AI接口地址为空")

        return try {
            val role = prefs.aiAssistRole.getValue()
            val requestBody = buildOpenAiRequestBody(request, model, role)
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 10000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            }

            connection.outputStream.use { out ->
                out.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                CompletionResult(false, "", "AI服务异常($code)")
            } else {
                parseOpenAiResponse(responseText)
                    ?.let { CompletionResult(true, it) }
                    ?: CompletionResult(false, "", "AI返回为空")
            }
        } catch (e: Exception) {
            CompletionResult(false, "", e.message ?: "网络请求失败")
        }
    }

    private fun normalizeEndpoint(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.endsWith("/chat/completions")) raw else raw.trimEnd('/') + "/v1/chat/completions"
    }

    private fun buildOpenAiRequestBody(request: CompletionRequest, model: String, role: AiAssistRole): String {
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", JsonPrimitive(0.7))
            put("max_tokens", request.maxTokens)
            put("messages", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", buildSystemPrompt(request.mode.prompt, role))
                    }
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", request.textBeforeCursor.takeLast(300))
                    }
                )
            })
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun parseOpenAiResponse(raw: String): String? {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val content = root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
            if (!content.isNullOrBlank()) return content

            // 兼容某些简化网关：{ "text": "..." }
            root["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: AiTextService? = null

        fun get(): AiTextService {
            return instance ?: synchronized(this) {
                instance ?: AiTextService().also { instance = it }
            }
        }
    }
}
