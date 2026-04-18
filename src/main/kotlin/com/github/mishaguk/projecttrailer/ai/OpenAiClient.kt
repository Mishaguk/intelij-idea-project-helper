package com.github.mishaguk.projecttrailer.ai

import com.github.mishaguk.projecttrailer.ProjectTrailerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service(Service.Level.APP)
class OpenAiClient {

    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun testConnection(): Result<String> {
        val key = requireKey().getOrElse { return Result.failure(it) }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("${AiConfig.BASE_URL}/models"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $key")
            .GET()
            .build()

        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            when (val code = response.statusCode()) {
                200 -> Result.success("OK")
                401 -> Result.failure(IllegalStateException("Invalid key (401)"))
                else -> Result.failure(IllegalStateException("HTTP $code"))
            }
        } catch (e: IOException) {
            Result.failure(IllegalStateException(e.message ?: "Network error"))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(IllegalStateException("Interrupted"))
        }
    }

    fun chatJson(system: String, user: String, schemaJson: String, schemaName: String = "response"): Result<String> {
        val body = """{"model":"${AiConfig.MODEL}","messages":[""" +
            """{"role":"system","content":"${jsonEscape(system)}"},""" +
            """{"role":"user","content":"${jsonEscape(user)}"}""" +
            """],"response_format":{"type":"json_schema","json_schema":{""" +
            """"name":"${jsonEscape(schemaName)}","strict":true,"schema":$schemaJson""" +
            """}}}"""
        return postChat(body)
    }

    fun chatMessages(messages: List<ChatMessage>): Result<String> {
        val messagesJson = messages.joinToString(",") {
            """{"role":"${jsonEscape(it.role)}","content":"${jsonEscape(it.content)}"}"""
        }
        val body = """{"model":"${AiConfig.MODEL}","messages":[$messagesJson]}"""
        return postChat(body)
    }

    private fun postChat(bodyJson: String): Result<String> {
        val key = requireKey().getOrElse { return Result.failure(it) }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("${AiConfig.BASE_URL}/chat/completions"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson, Charsets.UTF_8))
            .build()

        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            val code = response.statusCode()
            val respBody = response.body().orEmpty()
            if (code != 200) {
                return Result.failure(IllegalStateException("HTTP $code: ${respBody.take(200)}"))
            }
            val envelope = json.decodeFromString<ChatEnvelope>(respBody)
            val content = envelope.choices.firstOrNull()?.message?.content
                ?: return Result.failure(IllegalStateException("Empty response from model"))
            Result.success(content)
        } catch (e: IOException) {
            Result.failure(IllegalStateException(e.message ?: "Network error"))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(IllegalStateException("Interrupted"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Parse error: ${e.message}"))
        }
    }

    private fun requireKey(): Result<String> {
        val key = AiKeyProvider.getInstance().getApiKey()
            ?: return Result.failure(IllegalStateException(ProjectTrailerBundle.message("ai.test.noKey")))
        return Result.success(key)
    }

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    @Serializable
    private data class ChatEnvelope(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: Message = Message())

    @Serializable
    private data class Message(val content: String = "")

    companion object {
        fun getInstance(): OpenAiClient = service()
    }
}
