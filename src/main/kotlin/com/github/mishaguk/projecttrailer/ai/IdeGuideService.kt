package com.github.mishaguk.projecttrailer.ai

import com.github.mishaguk.projecttrailer.ProjectTrailerBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Service(Service.Level.PROJECT)
class IdeGuideService(private val project: Project) {

    private val json = Json { ignoreUnknownKeys = true }

    fun generate(question: String): Result<List<IdeGuideStep>> {
        AiKeyProvider.getInstance().getApiKey()
            ?: return Result.failure(IllegalStateException(ProjectTrailerBundle.message("ai.test.noKey")))

        val client = OpenAiClient.getInstance()
        val esc: (String) -> String = { client.jsonEscapePublic(it) }

        val body = buildString {
            append("""{"model":"${AiConfig.MODEL}","messages":[""")
            append("""{"role":"system","content":"${esc(IdeGuideSchema.systemPromptFull())}"},""")
            append("""{"role":"user","content":"${esc(IdeGuideSchema.userPrompt(question))}"}""")
            append("""],"response_format":{"type":"json_schema","json_schema":{"name":"ide_guide","strict":true,"schema":""")
            append(IdeGuideSchema.JSON_SCHEMA)
            append("}}}")
        }

        val rawMsg = client.chatRawMessage(body).getOrElse { return Result.failure(it) }
        val content = json.parseToJsonElement(rawMsg).let { el ->
            el.jsonObject["content"]?.jsonPrimitive?.contentOrNull
        } ?: return Result.failure(IllegalStateException("Empty response from model"))

        val parsed = try {
            json.decodeFromString<IdeGuideResponse>(content)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Invalid JSON: ${e.message}"))
        }

        val actionManager = ActionManager.getInstance()
        val validated = parsed.steps.map { step ->
            if (step.actionId != null && actionManager.getAction(step.actionId) == null) {
                thisLogger().warn("IdeGuideService: invalid actionId '${step.actionId}', removing")
                step.copy(actionId = null, actionLabel = null)
            } else {
                step
            }
        }

        thisLogger().info("IdeGuideService: ${validated.size} steps for question: $question")
        return Result.success(validated)
    }

    companion object {
        fun getInstance(project: Project): IdeGuideService = project.service()
    }
}
