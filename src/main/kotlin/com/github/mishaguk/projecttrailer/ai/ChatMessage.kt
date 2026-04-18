package com.github.mishaguk.projecttrailer.ai

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}
