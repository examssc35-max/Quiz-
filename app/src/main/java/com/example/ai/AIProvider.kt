package com.example.ai

import com.example.data.model.AiEvaluationResult

interface AIProvider {
    val providerType: AIProviderType

    suspend fun evaluateAnswer(
        request: AnswerEvaluationRequest,
        config: AIConfig
    ): Result<AiEvaluationResult>

    suspend fun testConnection(config: AIConfig): Result<String>
}
