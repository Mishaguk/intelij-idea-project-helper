package com.github.mishaguk.projecttrailer.ai

import kotlinx.serialization.Serializable

@Serializable
data class IdeGuideStep(
    val title: String,
    val instruction: String,
    val actionId: String? = null,
    val actionLabel: String? = null,
)

@Serializable
internal data class IdeGuideResponse(val steps: List<IdeGuideStep>)
