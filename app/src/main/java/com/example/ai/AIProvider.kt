package com.example.ai

import com.example.data.model.AiEvaluationResult
import com.example.data.model.QuestionSchema
import com.example.data.model.QuizResultSummary

interface AIProvider {
    val providerType: AIProviderType

    suspend fun evaluateAnswer(
        request: AnswerEvaluationRequest,
        config: AIConfig
    ): Result<AiEvaluationResult>

    suspend fun chatFollowUp(
        context: QuestionAiContext,
        history: List<ChatMessage>,
        userMessage: String,
        config: AIConfig
    ): Result<String>

    suspend fun analyzeQuizResult(
        summary: QuizResultSummary,
        config: AIConfig
    ): Result<AiResultAnalysis>

    suspend fun auditQuestion(
        question: QuestionSchema,
        config: AIConfig
    ): Result<QuestionAuditResult>

    suspend fun auditQuestionsBatch(
        questions: List<QuestionSchema>,
        config: AIConfig
    ): Result<List<QuestionAuditResult>> = runCatching {
        questions.map { q -> auditQuestion(q, config).getOrThrow() }
    }

    suspend fun testConnection(config: AIConfig): Result<String>
}
