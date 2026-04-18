package com.github.mishaguk.projecttrailer.ai

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ChatService(private val project: Project) {

    private val history: MutableList<ChatMessage> = mutableListOf()

    @Synchronized
    fun ask(userText: String): Result<String> {
        if (history.isEmpty()) {
            val structure = ProjectStructureScanner.scan(project)
            history += ChatMessage(ChatMessage.SYSTEM, ChatPrompts.systemPromptWithStructure(structure))
            thisLogger().info("ChatService: initialized with structure (${structure.length} chars)")
        }
        history += ChatMessage(ChatMessage.USER, userText)
        trim()

        val reply = OpenAiClient.getInstance().chatMessages(history)
            .getOrElse { return Result.failure(it) }

        history += ChatMessage(ChatMessage.ASSISTANT, reply)
        trim()
        thisLogger().info("ChatService: history size=${history.size}")
        return Result.success(reply)
    }

    @Synchronized
    fun reset() {
        history.clear()
        thisLogger().info("ChatService: reset")
    }

    @Synchronized
    fun transcript(): List<ChatMessage> =
        history.filter { it.role != ChatMessage.SYSTEM }.toList()

    private fun trim() {
        if (history.isEmpty()) return
        val system = history.first()
        val rest = history.drop(1)
        if (rest.size <= MAX_MESSAGES) return
        val kept = rest.takeLast(MAX_MESSAGES)
        history.clear()
        history += system
        history += kept
    }

    companion object {
        private const val MAX_MESSAGES = 10
        fun getInstance(project: Project): ChatService = project.service()
    }
}
