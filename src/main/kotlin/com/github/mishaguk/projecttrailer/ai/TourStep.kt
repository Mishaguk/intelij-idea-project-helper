package com.github.mishaguk.projecttrailer.ai

import kotlinx.serialization.Serializable

@Serializable
data class TourStep(
    val path: String,
    val title: String,
    val explanation: String,
)

@Serializable
internal data class TourResponse(val steps: List<TourStep>)
