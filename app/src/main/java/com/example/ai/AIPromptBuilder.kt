package com.example.ai

import com.example.data.model.AiEvaluationResult
import com.example.data.model.QuestionSchema
import com.example.data.model.QuestionType
import com.example.data.model.QuizResultSummary
import org.json.JSONArray
import org.json.JSONObject

object AIPromptBuilder {

    fun buildSystemInstruction(): String {
        return """
            You are the intelligent Educational AI Agent of Quiz Explore.
            You are NOT a simple string comparator. You have full intellectual authority to analyze the question context, scientific facts, mathematics, grammar, and linguistics.
            The JSON quiz data is a reference data source, NOT absolute truth. If a stored JSON answer is wrong, outdated, or flawed, identify the true answer and never penalize the student for knowing the truth.
            Always provide helpful, pedagogical explanations in clear Bengali (বাংলা).
            Output ONLY valid JSON matching the requested schema.
        """.trimIndent()
    }

    fun detectBlankPosition(questionText: String): String {
        val patterns = listOf(
            Regex("\\([a-z0-9ivx]+\\)\\s*[-—–]+", RegexOption.IGNORE_CASE),
            Regex("\\[[a-z0-9ivx]+\\]", RegexOption.IGNORE_CASE),
            Regex("_{2,}"),
            Regex("\\[\\.\\.\\.\\]"),
            Regex("—{2,}"),
            Regex("\\.\\.\\.{2,}")
        )
        for (pattern in patterns) {
            val match = pattern.find(questionText)
            if (match != null) {
                return "Blank marker '${match.value}' at character index ${match.range.first}"
            }
        }
        return "Fill-in-the-blank position in sentence"
    }

    fun extractSurroundingText(questionText: String): String {
        val patterns = listOf(
            Regex("\\([a-z0-9ivx]+\\)\\s*[-—–]+", RegexOption.IGNORE_CASE),
            Regex("\\[[a-z0-9ivx]+\\]", RegexOption.IGNORE_CASE),
            Regex("_{2,}"),
            Regex("\\[\\.\\.\\.\\]"),
            Regex("—{2,}"),
            Regex("\\.\\.\\.{2,}")
        )
        for (pattern in patterns) {
            val match = pattern.find(questionText)
            if (match != null) {
                val start = (match.range.first - 35).coerceAtLeast(0)
                val end = (match.range.last + 35).coerceAtMost(questionText.length)
                val prefix = if (start > 0) "..." else ""
                val suffix = if (end < questionText.length) "..." else ""
                return prefix + questionText.substring(start, end).trim() + suffix
            }
        }
        return questionText
    }

    fun buildEvaluationPrompt(request: AnswerEvaluationRequest): String {
        val isMcq = request.questionType == QuestionType.MCQ
        val acceptedListStr = request.acceptedAnswers.joinToString(", ") { "\"$it\"" }
        val optionsStr = request.options.mapIndexed { idx, opt ->
            val letter = ('A' + idx).toString()
            "$letter: \"$opt\" (index $idx)"
        }.joinToString("\n")

        val storedJsonAnswerDisplay = if (isMcq) {
            val optText = request.options.getOrNull(request.storedCorrectOptionIndex) ?: "None"
            "Option ${request.storedCorrectOptionIndex} ($optText)"
        } else {
            request.storedAnswer.ifEmpty { request.acceptedAnswers.joinToString(", ") }
        }

        val blankPos = request.blankPosition ?: detectBlankPosition(request.questionText)
        val surrounding = request.surroundingText ?: extractSurroundingText(request.questionText)
        val sentenceText = request.completeSentence ?: request.questionText

        return """
            You are the "Context-Aware Educational Answer Evaluator" of Quiz Explore.
            Your role is to understand the COMPLETE question, sentence meaning, and context before evaluating the student's answer.
            You are NOT a simple string comparator or grammar-only checker. Grammar alone must NEVER determine the result.

            EVALUATION CONTEXT:
            - Question Type: ${if (isMcq) "Multiple Choice Question (MCQ)" else "Fill-in-the-Blank"}
            - Question Text: "${request.questionText}"
            - Complete Sentence: "$sentenceText"
            ${if (!isMcq) "- Blank Position: $blankPos" else ""}
            ${if (!isMcq) "- Surrounding Text: \"$surrounding\"" else ""}
            ${if (!isMcq) "- Note for Blank: The blank is part of a complete sentence. Evaluate what word/phrase belongs in this exact position." else ""}
            - Subject / Category: "${request.subject ?: request.category ?: "General"}"
            - Source / Textbook Context: "${request.sourceContext ?: request.quizTitle ?: "Textbook Educational Context"}"
            ${if (request.explanation != null) "- Stored Explanation: \"${request.explanation}\"" else ""}
            ${if (isMcq) "- Options:\n$optionsStr" else ""}
            - Stored Reference Answer: "$storedJsonAnswerDisplay"
            ${if (!isMcq) "- Accepted Answers List: [$acceptedListStr]" else "- Stored Correct Option Index: ${request.storedCorrectOptionIndex}"}
            - Student's Submitted Answer: "${request.userAnswer}"

            MANDATORY EVALUATION PIPELINE & PRINCIPLES:
            1. MULTI-DIMENSIONAL CONTEXTUAL EVALUATION:
               Evaluate ALL of these factors:
               (a) Exact correctness
               (b) Meaning & semantic relationship
               (c) Semantic fit in the sentence
               (d) Context & collocation (natural word combinations)
               (e) Grammatical role, part of speech, and agreement (e.g. Singular / plural agreement)
               (f) Natural English usage
               (g) Sentence meaning
               (h) Source / textbook context
               (i) Whether it is a valid alternative answer
               CRITICAL: Do NOT give grammar the highest priority by default. Grammar is only ONE factor.

            2. DO NOT AUTOMATICALLY TRUST THE STORED ANSWER:
               The JSON answer is a reference answer, not unquestionable truth.
               If the student's answer is clearly valid in the context of the sentence even though it differs from the stored answer:
               -> mark isCorrect = true
               -> evaluationType = "semantic_match" or "source_context_match"
               -> explain why it is valid
               Do NOT force the student to match the stored answer word-for-word.

            3. INDEPENDENTLY VERIFY THE QUESTION:
               Independently analyze: "What answer best completes this sentence and preserves the intended meaning?"
               Do NOT simply state "The stored answer is correct because it is the stored answer."

            4. NEVER INVENT AN UNWARRANTED CORRECT ANSWER:
               Never hallucinate certainty. If there is genuine ambiguity, indicate needsReview = true and explain cautiously.

            5. CONFIDENCE SCORING:
               - 0.95 - 1.00: Very high confidence
               - 0.85 - 0.94: High confidence
               - 0.70 - 0.84: Moderate confidence
               - Below 0.70: Uncertain (must set needsReview = true; do NOT confidently tell the student they are wrong)

            6. SIMPLE, STUDENT-FRIENDLY BANGLA EXPLANATION MANDATE (বাংলায় সহজ ও স্পষ্ট ব্যাখ্যা):
               Write explanations in simple, natural, student-friendly Bengali.
               DO NOT use complicated vocabulary, unnecessary technical grammar terminology, or confusing reasoning.

               IF THE ANSWER IS CORRECT:
               Explain simply why the answer fits the sentence and makes sense.
               Example: "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “${request.userAnswer}” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"

               IF THE ANSWER IS WRONG (MANDATORY 4-PART STRUCTURE):
               The explanation MUST answer these four questions clearly:
               1. তোমার উত্তর কেন ভুল? (Why is the student's word inappropriate in this sentence?)
               2. সঠিক উত্তর কী? (What is the correct answer?)
               3. সঠিক উত্তরটি বাক্যে কেন বসে? (Why does the correct word belong in this sentence?)
               4. তোমার উত্তরের সঙ্গে সঠিক উত্তরের অর্থগত পার্থক্য কী? (What is the meaning difference between the two?)

               Follow this exact pedagogical style:
               "তোমার উত্তর “sustainability” এখানে উপযুক্ত নয়, কারণ বাক্যটি ব্যবহৃত জিনিসের স্থায়িত্ব এবং প্রয়োজনীয়তা নিয়ে প্রশ্ন করার কথা বলছে। “durability” শব্দটি কোনো জিনিস কতটা দীর্ঘসময় টিকে থাকে—এই অর্থ প্রকাশ করে। তাই এই বাক্যে “durability” বেশি উপযুক্ত।"

            RESPOND STRICTLY WITH A SINGLE VALID JSON OBJECT MATCHING THIS SCHEMA:
            {
              "isCorrect": boolean,
              "confidence": number between 0.0 and 1.0,
              "evaluationType": "exact_match" | "normalized_match" | "semantic_match" | "grammar_and_semantic_match" | "source_context_match" | "incorrect" | "ambiguous" | "ai_unavailable",
              "matchedAnswer": "string (the closest accepted answer or the student's accepted alternative)",
              "verifiedAnswer": "string (the genuine verified correct answer)",
              "needsReview": boolean,
              "explanation_bn": "বাংলায় সহজ, স্পষ্ট ও শিক্ষামূলক ব্যাখ্যা"
            }
        """.trimIndent()
    }

    fun buildFollowUpChatPrompt(
        context: QuestionAiContext,
        history: List<ChatMessage>,
        userMessage: String
    ): String {
        val optionsList = context.options.mapIndexed { idx, opt ->
            val letter = ('A' + idx).toString()
            "$letter: \"$opt\" (index $idx)"
        }.joinToString("\n")

        val historyFormatted = history.takeLast(10).joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") "Student" else "AI Tutor"
            "$speaker: ${msg.content}"
        }

        return """
            You are the conversational AI Educational Tutor of Quiz Explore. The student is asking follow-up questions about this specific quiz question:

            QUESTION CONTEXT:
            - Quiz Title: "${context.quizTitle}"
            - Question Type: ${context.type.name}
            - Question Text: "${context.questionText}"
            ${if (context.options.isNotEmpty()) "- Options:\n$optionsList" else ""}
            - Stored Answer: "${context.storedAnswer}"
            ${if (context.acceptedAnswers.isNotEmpty()) "- Accepted Answers: ${context.acceptedAnswers.joinToString(", ")}" else ""}
            ${if (context.explanation != null) "- Stored Explanation: \"${context.explanation}\"" else ""}
            ${if (context.wasCorrected) "- AI VERIFIED CORRECTION:\n  Original Answer: \"${context.originalAnswerDisplay.orEmpty()}\"\n  AI Verified Answer: \"${context.verifiedAnswerDisplay.orEmpty()}\"\n  Correction Reason: \"${context.correctionReason.orEmpty()}\"\n  Confidence: ${context.auditConfidence ?: 0.95}" else ""}
            - Student's Submitted Answer: "${context.userAnswerText ?: context.options.getOrNull(context.userOptionIndex ?: -1) ?: "None"}"

            CONVERSATION HISTORY:
            $historyFormatted

            STUDENT'S NEW QUESTION:
            "$userMessage"

            TUTOR INSTRUCTIONS:
            1. Answer directly and concisely in natural, friendly, and pedagogical Bengali (বাংলা).
            2. Be intellectually honest: If the stored JSON answer was wrong, acknowledge it candidly and explain why the verified answer is correct.
            3. Explain linguistic, grammatical, mathematical, or scientific concepts simply so the student truly understands.
            4. If the student asks "কেন পরিবর্তন করা হয়েছে?" or "Why was this corrected?", clearly detail why the original answer was flawed, cite grammatical/factual rules, and show why the corrected answer fits.
            5. If the student asks "Are you sure?" ("তুমি কি নিশ্চিত?"), re-evaluate the full question context objectively with rigorous logic rather than merely repeating past statements.
            6. Keep your response focused and conversational (2 to 5 sentences unless a deeper breakdown is requested).
        """.trimIndent()
    }

    fun buildResultAnalysisPrompt(summary: QuizResultSummary): String {
        val reviewItemsSummary = summary.reviewItems.take(15).mapIndexed { idx, item ->
            "Q${idx + 1}: \"${item.questionText}\" | User: \"${item.userAnswerText.orEmpty()}\" | Correct: \"${item.correctAnswerText}\" | WasCorrect: ${item.isCorrect}"
        }.joinToString("\n")

        return """
            You are the AI Learning Coach of Quiz Explore. Analyze the student's completed quiz attempt and generate an insightful performance review in Bengali.

            QUIZ ATTEMPT DATA:
            - Title: "${summary.quizTitle}"
            - Mode: ${summary.mode.name}
            - Score: ${summary.score} / ${summary.maxScore}
            - Correct: ${summary.correctCount}, Wrong: ${summary.wrongCount}, Unanswered: ${summary.unansweredCount}
            - Accuracy: ${summary.accuracy}%

            SAMPLE QUESTION REVIEWS:
            $reviewItemsSummary

            GENERATE STRUCTURED JSON:
            {
              "overallSummary": "বাংলায় সামগ্রিক পারফরম্যান্সের উৎসাহজনক ও বাস্তবসম্মত সারসংক্ষেপ",
              "strengths": ["বিষয় ১ বা দক্ষতার দিক", "বিষয় ২"],
              "weakAreas": ["যেসব বিষয়ে আরও মনোযোগ দরকার"],
              "recommendations": ["১ বা ২টি কার্যকর পরামর্শ"],
              "mistakeBreakdown": "প্রধান ভুলের ধরন এবং তা সংশোধনের উপায় বাংলায় সহজ ব্যাখ্যা"
            }
        """.trimIndent()
    }

    fun buildQuestionAuditPrompt(question: QuestionSchema, contextHint: String = ""): String {
        val optionsFormatted = if (question.type == QuestionType.MCQ) {
            question.options.mapIndexed { idx, opt ->
                "  $idx (${('A' + idx)}): \"$opt\""
            }.joinToString("\n")
        } else {
            "  (None - Fill in the blank)"
        }

        val storedAnswerDesc = if (question.type == QuestionType.MCQ) {
            "Index ${question.answer} (${question.options.getOrNull(question.answer) ?: "INVALID INDEX"})"
        } else {
            "Primary: \"${question.fillBlankAnswer}\", Accepted: ${question.acceptedAnswers.joinToString(", ") { "\"$it\"" }}"
        }

        return """
            You are the Chief AI Quiz Quality Auditor & Fact-Checking Engine.
            Your task is to independently solve and audit this quiz question. DO NOT assume the stored answer is correct.

            QUESTION: "${question.question}"
            TYPE: ${question.type.name}
            OPTIONS:
            $optionsFormatted
            STORED ANSWER IN QUIZ JSON: $storedAnswerDesc
            EXISTING EXPLANATION: "${question.explanation ?: "None"}"
            ${if (contextHint.isNotBlank()) "ADDITIONAL CONTEXT: $contextHint" else ""}

            CRITICAL AUDITING RULES:
            1. INDEPENDENTLY SOLVE the question first using factual knowledge, mathematics, grammar, and context.
            2. COMPARE your independent solution with the STORED ANSWER:
               - For MCQ: Determine which option index (0-based) is factually and contextually correct. If the stored index is wrong, set "verified_answer" to the correct index and "answer_changed": true.
               - For FILL_BLANK: Determine the exact correct word/phrase. Set "verified_answer" to the best answer, and list all genuinely valid alternatives in "accepted_answers".
            3. OPTIONS CHECK: Check if any option has an obvious factual error, duplicate, or corruption. If options are okay, keep "corrected_options": [].
            4. EXPLANATION ALIGNMENT: If the answer is changed or the existing explanation conflicts with the correct answer, provide a corrected, coherent explanation in "corrected_explanation".
            5. AMBIGUITY & CONFIDENCE:
               - If the question is ambiguous or lacks necessary info, set "needs_review": true and confidence < 0.75.
               - If highly certain (>= 0.90), set "confidence": 0.90 to 1.0.
               - If moderately certain (0.75 - 0.89), set "needs_review": true.

            OUTPUT FORMAT:
            Respond STRICTLY with a single JSON object. No markdown code blocks, no explanations outside JSON.
            For MCQ:
            {
              "is_valid": true,
              "needs_review": false,
              "question": "${question.question.replace("\"", "\\\"")}",
              "question_type": "mcq",
              "original_answer": ${question.answer},
              "verified_answer": 0,
              "answer_changed": false,
              "confidence": 0.98,
              "reason": "<clear explanation of why this answer is correct and why stored answer was right/wrong>",
              "corrected_options": [],
              "corrected_explanation": "<concise explanation consistent with the verified answer>"
            }

            For FILL_BLANK:
            {
              "is_valid": true,
              "needs_review": false,
              "question_type": "fill_blank",
              "original_answer": "${question.fillBlankAnswer.replace("\"", "\\\"")}",
              "verified_answer": "<exact correct word or phrase>",
              "accepted_answers": ["<primary correct>", "<valid alternative>"],
              "answer_changed": false,
              "confidence": 0.98,
              "reason": "<clear explanation of grammar/context/fact>",
              "corrected_explanation": "<concise explanation consistent with the verified answer>"
            }
        """.trimIndent()
    }

    /**
     * Requirement 12: Batch Cloud AI Requests
     * Formats multiple questions into ONE single compact request to conserve AI credits.
     */
    fun buildBatchQuestionAuditPrompt(questions: List<QuestionSchema>, contextHint: String = ""): String {
        val itemsFormatted = questions.mapIndexed { idx, q ->
            val optStr = if (q.type == QuestionType.MCQ) {
                q.options.mapIndexed { i, o -> "      $i: \"$o\"" }.joinToString("\n")
            } else {
                "      (None - Fill in the blank)"
            }
            val storedAns = if (q.type == QuestionType.MCQ) {
                "Index ${q.answer} (${q.options.getOrNull(q.answer) ?: "INVALID"})"
            } else {
                "\"${q.fillBlankAnswer}\", Accepted: ${q.acceptedAnswers.joinToString(", ") { "\"$it\"" }}"
            }
            """
            ITEM ${idx + 1}:
            - question_id: "${q.id}"
            - type: "${q.type.name.lowercase()}"
            - question: "${q.question.replace("\"", "\\\"")}"
            - options:
$optStr
            - stored_answer: $storedAns
            - explanation: "${q.explanation ?: "None"}"
            """.trimIndent()
        }.joinToString("\n\n")

        return """
            You are the Chief AI Educational Quiz Quality Auditor.
            Audit the following ${questions.size} questions in a SINGLE batch.
            Evaluate each independently. Check if the stored answer is factually and contextually correct.
            If confident (>= 0.90) that stored answer is wrong, provide verified_answer and set "answer_changed": true.
            If ambiguous, set "needs_review": true.

            QUESTIONS TO AUDIT:
            $itemsFormatted

            ${if (contextHint.isNotBlank()) "CONTEXT: $contextHint" else ""}

            OUTPUT FORMAT:
            Respond STRICTLY with a valid JSON array of objects, one for each question, matching this schema:
            [
              {
                "question_id": "string (matching id)",
                "is_valid": true,
                "needs_review": false,
                "question_type": "mcq" | "fill_blank",
                "original_answer": 0 or "text",
                "verified_answer": 0 or "text",
                "accepted_answers": ["..."],
                "answer_changed": false,
                "confidence": 0.98,
                "reason": "Clear explanation",
                "corrected_options": [],
                "corrected_explanation": "Concise verified explanation"
              }
            ]
        """.trimIndent()
    }

    /**
     * Requirement 2: Inspect questions deterministically BEFORE sending to AI.
     * Identifies whether a question genuinely has structural/semantic uncertainties.
     */
    fun isQuestionSuspiciousLocally(question: QuestionSchema): Pair<Boolean, String?> {
        if (question.type == QuestionType.MCQ) {
            if (question.options.isEmpty() || question.options.size < 2) {
                return Pair(true, "MCQ has fewer than 2 options")
            }
            if (question.answer !in question.options.indices) {
                return Pair(true, "Answer index ${question.answer} is out of bounds (options size: ${question.options.size})")
            }
            val duplicates = question.options.groupBy { it.trim().lowercase() }.filter { it.value.size > 1 }
            if (duplicates.isNotEmpty()) {
                return Pair(true, "Duplicate options detected: ${duplicates.keys.joinToString(", ")}")
            }
            val expl = question.explanation.orEmpty()
            if (expl.isNotBlank()) {
                val explicitOptionMention = Regex("(?i)\\boption\\s+([A-D])\\b").find(expl)
                if (explicitOptionMention != null) {
                    val letter = explicitOptionMention.groupValues[1].uppercase()[0]
                    val mentionedIndex = letter - 'A'
                    if (mentionedIndex in question.options.indices && mentionedIndex != question.answer) {
                        return Pair(true, "Explanation mentions Option $letter but stored answer is Option ${('A' + question.answer)}")
                    }
                }
            }
        } else {
            // Fill blank
            if (question.fillBlankAnswer.isBlank() && question.acceptedAnswers.all { it.isBlank() }) {
                return Pair(true, "Fill-in-the-blank answer is not configured")
            }
            val blankPos = detectBlankPosition(question.question)
            if (blankPos == "Fill-in-the-blank position in sentence" &&
                !question.question.contains("_") && !question.question.contains("—") && !question.question.contains("...")) {
                return Pair(true, "Question text has no clear blank indicator")
            }
        }
        return Pair(false, null)
    }

    fun parseBatchQuestionAuditResponse(
        rawResponse: String,
        originalQuestions: List<QuestionSchema>
    ): List<QuestionAuditResult> {
        val clean = extractJsonArrayOrObject(rawResponse)
        val resultsMap = mutableMapOf<String, QuestionAuditResult>()

        try {
            val jsonArray = if (clean.startsWith("[")) {
                JSONArray(clean)
            } else {
                val obj = JSONObject(clean)
                obj.optJSONArray("questions") ?: obj.optJSONArray("results") ?: JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val qId = item.optString("question_id", item.optString("id", "")).trim()
                val origQ = originalQuestions.find { it.id == qId } ?: originalQuestions.getOrNull(i)
                if (origQ != null) {
                    resultsMap[origQ.id] = parseQuestionAudit(origQ, item.toString())
                }
            }
        } catch (e: Exception) {
            // gracefully handled
        }

        // Return results for each original question in order
        return originalQuestions.map { q ->
            resultsMap[q.id] ?: generateLocalDeterministicAudit(q)
        }
    }

    fun generateLocalDeterministicAudit(question: QuestionSchema): QuestionAuditResult {
        val (isSuspicious, issue) = isQuestionSuspiciousLocally(question)
        return if (question.type == QuestionType.MCQ) {
            val optText = question.options.getOrNull(question.answer).orEmpty()
            QuestionAuditResult(
                questionId = question.id,
                isValid = !isSuspicious,
                needsReview = isSuspicious,
                questionText = question.question,
                questionType = QuestionType.MCQ,
                originalAnswerIndex = question.answer,
                verifiedAnswerIndex = question.answer,
                originalAnswerText = optText,
                verifiedAnswerText = optText,
                acceptedAnswers = emptyList(),
                answerChanged = false,
                confidence = if (isSuspicious) 0.60 else 1.0,
                reason = issue ?: "Locally verified clean question structure without AI call",
                correctedOptions = emptyList(),
                correctedExplanation = question.explanation
            )
        } else {
            val text = question.fillBlankAnswer.ifBlank { question.acceptedAnswers.firstOrNull().orEmpty() }
            QuestionAuditResult(
                questionId = question.id,
                isValid = !isSuspicious,
                needsReview = isSuspicious,
                questionText = question.question,
                questionType = QuestionType.FILL_BLANK,
                originalAnswerIndex = null,
                verifiedAnswerIndex = null,
                originalAnswerText = text,
                verifiedAnswerText = text,
                acceptedAnswers = question.acceptedAnswers,
                answerChanged = false,
                confidence = if (isSuspicious) 0.60 else 1.0,
                reason = issue ?: "Locally verified clean question structure without AI call",
                correctedOptions = emptyList(),
                correctedExplanation = question.explanation
            )
        }
    }

    private fun extractJsonArrayOrObject(raw: String): String {
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val firstBracket = cleaned.indexOf('[')
        val firstBrace = cleaned.indexOf('{')
        if (firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace)) {
            val lastBracket = cleaned.lastIndexOf(']')
            if (lastBracket != -1 && lastBracket > firstBracket) {
                return cleaned.substring(firstBracket, lastBracket + 1)
            }
        }
        return extractJsonObject(raw)
    }

    fun parseEvaluationResponse(
        rawResponse: String,
        fallbackAcceptedAnswer: String = "",
        userAnswer: String = "",
        acceptedAnswers: List<String> = emptyList()
    ): AiEvaluationResult {
        val parsed = AiEvaluationResult.fromJson(rawResponse, fallbackAcceptedAnswer, userAnswer)
        return validateAndHardenEvaluation(
            parsedResult = parsed,
            userAnswer = userAnswer,
            acceptedAnswers = acceptedAnswers,
            fallbackAnswer = fallbackAcceptedAnswer
        )
    }

    /**
     * Step 5: Strict Response Validation & SAFETY OVERRIDE
     * Enforces the critical law:
     * If the student's answer is an exact or normalized match to any accepted answer,
     * the answer MUST be marked CORRECT with confidence 1.0.
     * AI must NEVER override an exact verified match and mark it wrong.
     */
    fun validateAndHardenEvaluation(
        parsedResult: AiEvaluationResult,
        userAnswer: String,
        acceptedAnswers: List<String>,
        fallbackAnswer: String = ""
    ): AiEvaluationResult {
        val trimmedUser = userAnswer.trim()
        val allAccepted = acceptedAnswers.filter { it.isNotBlank() }.ifEmpty {
            if (fallbackAnswer.isNotBlank()) listOf(fallbackAnswer) else emptyList()
        }

        // 1. CRITICAL SAFETY OVERRIDE:
        // Even if AI returned isCorrect = false, if student answer matches accepted answers locally,
        // AI is strictly overridden. Student is CORRECT!
        val (isExactOrNorm, matched) = SmartNormalizer.checkExactOrNormalizedMatch(trimmedUser, allAccepted)
        if (isExactOrNorm && matched != null) {
            val evalType = if (trimmedUser.equals(matched, ignoreCase = false)) "exact_match" else "normalized_match"
            val expl = if (parsedResult.isCorrect && parsedResult.explanation_bn.isNotBlank()) {
                parsedResult.explanation_bn
            } else {
                "তোমার উত্তরটি সঠিক। বাক্যের এই স্থানে “$matched” শব্দটিই সঠিকভাবে বসে এবং বাক্যের অর্থ ঠিক থাকে।"
            }
            return parsedResult.copy(
                isCorrect = true,
                confidence = 1.0,
                evaluationType = evalType,
                matchedAnswer = matched,
                verifiedAnswer = matched,
                needsReview = false,
                explanation_bn = expl,
                banglaExplanation = expl,
                decision = "correct",
                jsonAnswerCorrect = true,
                warning = null
            )
        }

        // 2. Confidence validation
        val confidence = parsedResult.confidence.coerceIn(0.0, 1.0)
        val needsReview = parsedResult.needsReview || confidence < 0.70

        // 3. Fallback & Contradiction Validation for Bangla Explanation (Requirement 17)
        val verified = parsedResult.verifiedAnswer?.ifBlank { null }
            ?: parsedResult.matchedAnswer?.ifBlank { null }
            ?: allAccepted.firstOrNull().orEmpty()

        var explanationBn = if (parsedResult.explanation_bn.isNotBlank()) {
            parsedResult.explanation_bn
        } else if (parsedResult.banglaExplanation.isNotBlank()) {
            parsedResult.banglaExplanation
        } else {
            if (parsedResult.isCorrect) {
                "তোমার উত্তরটি সঠিক। বাক্যের অর্থ ও প্রসঙ্গের সাথে এটি সংগতিপূর্ণ।"
            } else {
                val correctDisplay = allAccepted.joinToString(", ").ifEmpty { verified }
                "তোমার উত্তর “$trimmedUser” এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও প্রসঙ্গ অনুযায়ী এখানে সঠিক উত্তর: “$correctDisplay”।"
            }
        }

        // Validate agreement: Explanation must agree with isCorrect (Requirement 17)
        if (parsedResult.isCorrect) {
            if (explanationBn.contains("ভুল") || explanationBn.contains("উপযুক্ত নয়") || explanationBn.contains("উপযুক্ত নয়") || explanationBn.contains("সঠিক উত্তর নয়")) {
                explanationBn = "তোমার উত্তরটি সঠিক। বাক্যের অর্থ ও প্রসঙ্গের সাথে এটি সংগতিপূর্ণ।"
            }
        } else {
            if (explanationBn.contains("তোমার উত্তরটি সঠিক") || explanationBn.contains("সঠিক উত্তর দিয়েছ") || explanationBn.contains("সঠিক উত্তর দিয়েছ")) {
                val correctDisplay = allAccepted.joinToString(", ").ifEmpty { verified }
                explanationBn = "তোমার উত্তর “$trimmedUser” এই বাক্যে উপযুক্ত নয়। বাক্যের অর্থ ও প্রসঙ্গ অনুযায়ী এখানে সঠিক উত্তর: “$correctDisplay”।"
            }
        }

        // 4. Ensure valid evaluationType
        val evalType = when (parsedResult.evaluationType.lowercase().trim()) {
            "exact_match", "normalized_match", "semantic_match",
            "grammar_and_semantic_match", "source_context_match",
            "incorrect", "ambiguous", "ai_unavailable" -> parsedResult.evaluationType
            else -> if (parsedResult.isCorrect) "semantic_match" else "incorrect"
        }

        return parsedResult.copy(
            confidence = confidence,
            needsReview = needsReview,
            evaluationType = evalType,
            verifiedAnswer = verified,
            matchedAnswer = parsedResult.matchedAnswer?.ifBlank { verified } ?: verified,
            explanation_bn = explanationBn,
            banglaExplanation = explanationBn
        )
    }

    fun parseResultAnalysis(rawResponse: String): AiResultAnalysis {
        val clean = extractJsonObject(rawResponse)
        return try {
            val obj = JSONObject(clean)
            val overallSummary = obj.optString("overallSummary", "তোমার কুইজের ফলাফল সফলভাবে বিশ্লেষিত হয়েছে।")
            val strengths = jsonArrayToStringList(obj.optJSONArray("strengths"))
            val weakAreas = jsonArrayToStringList(obj.optJSONArray("weakAreas"))
            val recommendations = jsonArrayToStringList(obj.optJSONArray("recommendations"))
            val mistakeBreakdown = obj.optString("mistakeBreakdown", "ভুল প্রশ্নগুলো পর্যালোচনা করে পরবর্তী পরীক্ষার প্রস্তুতি নাও।")

            AiResultAnalysis(
                overallSummary = overallSummary,
                strengths = strengths.ifEmpty { listOf("নিয়মিত অনুশীলন এবং কুইজে অংশগ্রহণ") },
                weakAreas = weakAreas,
                recommendations = recommendations.ifEmpty { listOf("ভুল হওয়া প্রশ্নগুলো পুনরায় অনুশীলন করুন") },
                mistakeBreakdown = mistakeBreakdown
            )
        } catch (e: Exception) {
            AiResultAnalysis(
                overallSummary = "তোমার কুইজ সমাপ্ত হয়েছে। নিয়মিত অনুশীলনের মাধ্যমে দক্ষতা বৃদ্ধি করুন।",
                strengths = listOf("কুইজে সময়মতো অংশগ্রহণ"),
                weakAreas = emptyList(),
                recommendations = listOf("ভুল উত্তরগুলো পুনরায় রিভিউ করুন"),
                mistakeBreakdown = ""
            )
        }
    }

    fun parseQuestionAudit(
        question: QuestionSchema,
        rawResponse: String
    ): QuestionAuditResult {
        val clean = extractJsonObject(rawResponse)
        return try {
            val obj = JSONObject(clean)
            val isValid = obj.optBoolean("is_valid", true)
            var needsReview = obj.optBoolean("needs_review", false)
            val confidence = obj.optDouble("confidence", 0.95).coerceIn(0.0, 1.0)
            val reason = obj.optString("reason", "").ifBlank {
                obj.optString("issueDescription", "")
            }
            val correctedExplanation = obj.optString("corrected_explanation", "").ifBlank {
                obj.optString("explanation", "").ifBlank { null }
            }

            if (question.type == QuestionType.MCQ) {
                val verifiedIdx = when {
                    obj.has("verified_answer") && obj.get("verified_answer") is Number -> obj.optInt("verified_answer")
                    obj.has("verified_answer_index") -> obj.optInt("verified_answer_index")
                    obj.has("suggestedCorrectIndex") && !obj.isNull("suggestedCorrectIndex") -> obj.optInt("suggestedCorrectIndex")
                    else -> question.answer
                }

                // VALIDATION (Requirement 13):
                // Answer index must exist within option bounds
                val finalVerifiedIdx: Int?
                val answerChanged: Boolean

                if (verifiedIdx in question.options.indices) {
                    finalVerifiedIdx = verifiedIdx
                    answerChanged = verifiedIdx != question.answer
                } else {
                    // Invalid index returned by AI! Do NOT modify question (Requirement 13)
                    finalVerifiedIdx = null
                    answerChanged = false
                    needsReview = true
                }

                // Confidence threshold enforcement (Requirement 8):
                val safeCorrection = confidence >= 0.90 && !needsReview
                val finalNeedsReview = if (confidence < 0.90) true else needsReview

                // Corrected options validation
                val corrOptionsList = mutableListOf<String>()
                if (obj.has("corrected_options")) {
                    val arr = obj.optJSONArray("corrected_options")
                    if (arr != null && arr.length() >= 2) {
                        for (i in 0 until arr.length()) corrOptionsList.add(arr.getString(i).trim())
                    }
                }

                val origText = question.options.getOrNull(question.answer).orEmpty()
                val verText = question.options.getOrNull(finalVerifiedIdx ?: question.answer).orEmpty()

                QuestionAuditResult(
                    questionId = question.id,
                    isValid = isValid,
                    needsReview = finalNeedsReview,
                    questionText = question.question,
                    questionType = QuestionType.MCQ,
                    originalAnswerIndex = question.answer,
                    verifiedAnswerIndex = if (safeCorrection) finalVerifiedIdx else null,
                    originalAnswerText = origText,
                    verifiedAnswerText = verText,
                    acceptedAnswers = emptyList(),
                    answerChanged = answerChanged && safeCorrection,
                    confidence = confidence,
                    reason = reason,
                    correctedOptions = corrOptionsList,
                    correctedExplanation = correctedExplanation
                )
            } else {
                // FILL_BLANK
                val origAnswer = question.fillBlankAnswer
                val rawVerified = when {
                    obj.has("verified_answer") -> obj.optString("verified_answer")
                    obj.has("suggestedCorrectAnswer") -> obj.optString("suggestedCorrectAnswer")
                    else -> origAnswer
                }.trim()

                val acceptedList = mutableListOf<String>()
                if (obj.has("accepted_answers")) {
                    val arr = obj.optJSONArray("accepted_answers")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val itm = arr.optString(i, "").trim()
                            if (itm.isNotBlank() && itm !in acceptedList) acceptedList.add(itm)
                        }
                    }
                }
                if (rawVerified.isNotBlank() && rawVerified !in acceptedList) {
                    acceptedList.add(0, rawVerified)
                }

                val answerChanged = rawVerified.isNotBlank() && !rawVerified.equals(origAnswer, ignoreCase = true)
                val safeCorrection = confidence >= 0.90 && !needsReview && rawVerified.isNotBlank()
                val finalNeedsReview = if (confidence < 0.90) true else needsReview

                QuestionAuditResult(
                    questionId = question.id,
                    isValid = isValid,
                    needsReview = finalNeedsReview,
                    questionText = question.question,
                    questionType = QuestionType.FILL_BLANK,
                    originalAnswerIndex = null,
                    verifiedAnswerIndex = null,
                    originalAnswerText = origAnswer,
                    verifiedAnswerText = if (safeCorrection) rawVerified else origAnswer,
                    acceptedAnswers = if (safeCorrection && acceptedList.isNotEmpty()) acceptedList else question.acceptedAnswers,
                    answerChanged = answerChanged && safeCorrection,
                    confidence = confidence,
                    reason = reason,
                    correctedOptions = emptyList(),
                    correctedExplanation = correctedExplanation
                )
            }
        } catch (e: Exception) {
            QuestionAuditResult(
                questionId = question.id,
                isValid = true,
                needsReview = true,
                questionText = question.question,
                questionType = question.type,
                originalAnswerIndex = if (question.type == QuestionType.MCQ) question.answer else null,
                verifiedAnswerIndex = null,
                originalAnswerText = if (question.type == QuestionType.MCQ) question.options.getOrNull(question.answer) else question.fillBlankAnswer,
                verifiedAnswerText = null,
                acceptedAnswers = question.acceptedAnswers,
                answerChanged = false,
                confidence = 0.5,
                reason = "AI verification output could not be parsed. Original quiz answer preserved.",
                correctedExplanation = null
            )
        }
    }

    fun parseQuestionAudit(questionId: String, rawResponse: String): QuestionAuditResult {
        val clean = extractJsonObject(rawResponse)
        return try {
            val obj = JSONObject(clean)
            val suggestedIdx = if (obj.has("suggestedCorrectIndex") && !obj.isNull("suggestedCorrectIndex")) {
                obj.optInt("suggestedCorrectIndex")
            } else if (obj.has("verified_answer") && obj.get("verified_answer") is Number) {
                obj.optInt("verified_answer")
            } else null
            val suggestedAns = obj.optString("suggestedCorrectAnswer", "").ifBlank {
                obj.optString("verified_answer", "").ifBlank { null }
            }
            val reason = obj.optString("reason", "").ifBlank {
                obj.optString("issueDescription", "").ifBlank { null }
            }
            val answerChanged = obj.optBoolean("answer_changed", suggestedIdx != null || suggestedAns != null)
            val needsReview = obj.optBoolean("needs_review", false)

            QuestionAuditResult(
                questionId = questionId,
                isValid = obj.optBoolean("is_valid", true),
                needsReview = needsReview,
                verifiedAnswerIndex = suggestedIdx,
                verifiedAnswerText = suggestedAns,
                answerChanged = answerChanged,
                confidence = obj.optDouble("confidence", 0.9),
                reason = reason.orEmpty(),
                correctedExplanation = obj.optString("corrected_explanation", "").ifBlank { null }
            )
        } catch (e: Exception) {
            QuestionAuditResult(
                questionId = questionId,
                isValid = true,
                needsReview = true,
                answerChanged = false,
                confidence = 0.5,
                reason = "Parsing failed"
            )
        }
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i, "").trim()
            if (item.isNotEmpty()) {
                list.add(item)
            }
        }
        return list
    }

    private fun extractJsonObject(text: String): String {
        val clean = text.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return clean.substring(firstBrace, lastBrace + 1)
        }
        return clean
    }
}
