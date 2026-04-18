package com.github.mishaguk.projecttrailer.ai

import com.github.mishaguk.projecttrailer.ProjectTrailerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.json.Json
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class TourService(private val project: Project) {

    private val json = Json { ignoreUnknownKeys = true }

    fun generate(): Result<List<TourStep>> {
        AiKeyProvider.getInstance().getApiKey()
            ?: return Result.failure(IllegalStateException(ProjectTrailerBundle.message("ai.test.noKey")))

        val structure = ProjectStructureScanner.scan(project)
        println("===== TourService: structure to send (${structure.length} chars) =====")
        println(structure)
        println("===== end =====")
        thisLogger().info("Project structure to send (${structure.length} chars):\n$structure")

        val raw = OpenAiClient.getInstance().chatJson(
            system = TourSchema.SYSTEM_PROMPT,
            user = TourSchema.userPrompt(structure),
            schemaJson = TourSchema.JSON_SCHEMA,
            schemaName = "tour",
        ).getOrElse { return Result.failure(it) }

        val parsed = try {
            json.decodeFromString<TourResponse>(raw)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Model returned invalid JSON: ${e.message}"))
        }

        val validated = parsed.steps.filter { pathExists(it.path) }
        thisLogger().info("Tour steps: ${parsed.steps.size} total, ${validated.size} with existing paths")
        return Result.success(validated)
    }

    private fun pathExists(relPath: String): Boolean {
        val base = project.basePath ?: return false
        val normalized = relPath.trimStart('/', '\\')
        val absolute = Path.of(base, normalized).toString()
        return LocalFileSystem.getInstance().findFileByPath(absolute.replace('\\', '/')) != null
    }

    companion object {
        fun getInstance(project: Project): TourService = project.service()
    }
}
