package com.yuyan.imemodule.ai

import android.content.Context
import com.yuyan.imemodule.prefs.AppPrefs
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI文本能力服务：输入上下文，返回建议文本。
 *
 * 当前实现提供两种模式：
 * 1. mock 模式（默认）：离线拼接文本，便于调试 UI 与流程；
 * 2. HTTP 模式：调用可配置的 AI 网关。
 */
class AiTextService private constructor() {

    data class CompletionRequest(
        val textBeforeCursor: String,
        val instruction: String = "",
        val maxTokens: Int = 128,
    )

    data class CompletionResult(
        val ok: Boolean,
        val text: String,
        val errorMessage: String? = null,
    )

    fun complete(request: CompletionRequest): CompletionResult {
        val prefs = AppPrefs.getInstance().input
        if (!prefs.aiAssistEnabled.getValue()) {
            return CompletionResult(false, "", "AI功能未开启")
        }
        return if (prefs.aiUseMock.getValue()) {
            mockComplete(request)
        } else {
            httpComplete(request)
        }
    }

    private fun mockComplete(request: CompletionRequest): CompletionResult {
        val prefix = request.textBeforeCursor.takeLast(80)
        val instructionPrefix = request.instruction.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
        return CompletionResult(
            ok = true,
            text = "${instructionPrefix}${prefix}，这是AI续写示例。"
        )
    }

    private fun httpComplete(request: CompletionRequest): CompletionResult {
        val prefs = AppPrefs.getInstance().input
        val endpoint = prefs.aiEndpoint.getValue().trim()
        val apiKey = prefs.aiApiKey.getValue().trim()
        if (endpoint.isBlank()) return CompletionResult(false, "", "AI接口地址为空")

        return try {
            val body = buildRequestJson(request)
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 5000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
            connection.outputStream.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                CompletionResult(false, "", "AI服务异常($code)")
            } else {
                val parsed = parseResponseText(responseText)
                if (parsed.isBlank()) CompletionResult(false, "", "AI返回为空")
                else CompletionResult(true, parsed)
            }
        } catch (e: Exception) {
            CompletionResult(false, "", e.message ?: "网络请求失败")
        }
    }

    /**
     * 兼容简易网关：
     * 请求：{"prompt":"...","instruction":"...","max_tokens":128}
     * 响应：{"text":"..."}
     */
    private fun buildRequestJson(request: CompletionRequest): String {
        fun String.escapeJson() = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val prompt = request.textBeforeCursor.escapeJson()
        val instruction = request.instruction.escapeJson()
        return "{\"prompt\":\"$prompt\",\"instruction\":\"$instruction\",\"max_tokens\":${request.maxTokens}}"
    }

    private fun parseResponseText(raw: String): String {
        val matcher = Regex("\"text\"\\s*:\\s*\"(.*?)\"", setOf(RegexOption.DOT_MATCHES_ALL)).find(raw)
        return matcher?.groupValues?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.trim()
            .orEmpty()
    }

    companion object {
        @Volatile
        private var instance: AiTextService? = null

        fun get(context: Context): AiTextService {
            return instance ?: synchronized(this) {
                instance ?: AiTextService().also { instance = it }
            }
        }
    }
}
