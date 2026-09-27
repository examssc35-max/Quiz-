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

        return """
            You are the AI Educational Agent of Quiz Explore. Evaluate the student's submitted answer with full linguistic and subject matter authority.

            QUESTION TYPE: ${if (isMcq) "Multiple Choice Question (MCQ)" else "Fill-in-the-Blank"}
            COMPLETE QUESTION / SENTENCE: "${request.questionText}"
            ${if (isMcq) "OPTIONS:\n$optionsStr" else "BLANK POSITION: Identify the blank in the sentence (e.g. '______', '(a) —', etc.)"}
            STORED JSON ANSWER: $storedJsonAnswerDisplay
            ${if (!isMcq) "ACCEPTED ANSWERS LIST: [$acceptedListStr]" else "STORED CORRECT OPTION INDEX: ${request.storedCorrectOptionIndex}"}
            STUDENT'S SUBMITTED ANSWER: "${request.userAnswer}"

            CRITICAL AGENT RULES:
            1. THE JSON IS A DATA SOURCE, NOT ABSOLUTE TRUTH:
               - Independently analyze what is actually correct according to scientific, mathematical, historical, or grammatical truth.
               - Example: If question asks "X-এর মান কত?" and JSON has 10 but mathematical truth is 12, and student wrote 12:
                 -> STUDENT IS CORRECT. jsonAnswerCorrect = false. decision = "json_error". warning = "The stored JSON answer appears to be incorrect."
               - Example: If question is "পৃথিবীর পৃষ্ঠের কত ভাগ পানি?" and student answers "৭১%" and JSON has "75%":
                 -> Recognize that student's answer ৭১% (71%) is scientifically accurate. Mark isCorrect = true, jsonAnswerCorrect = false.
            2. FULL SENTENCE CONTEXT & GRAMMAR:
               - For Fill-in-the-Blank: Does the student's answer naturally, semantically, and grammatically complete this exact sentence?
               - Singular vs. Plural (Singular / plural agreement): If singular is required (e.g. "Air is the most important (a) —"), a plural like "elements" is strictly WRONG.
               - Tense & Form: Must fit the syntax and part of speech required by surrounding words.
               - Valid alternatives: If stored answer is "harm" and student writes "damage", and "damage" naturally fits the sentence, mark isCorrect: true.
            3. NUMBERS, DIGITS & UNITS EQUIVALENCE:
               - Bengali digits ('০'..'৯') and English digits ('0'..'9') are EQUIVALENT (e.g. "৬০–৭০%" == "60-70%").
               - Number comma separators are EQUIVALENT (e.g. "১,০০০" == "1,000" == "1000").
               - Equivalent units are EQUIVALENT (e.g. "১,০০০ কেজি" == "1000 kg" == "1000 kilogram").
               - Do not mark equivalent formatting or units as wrong!
            4. MCQ EVALUATION:
               - Verify if the stored answer index actually points to the correct option.
               - If the stored index points to an incorrect option, identify the genuinely correct option index.
               - If the user selected the genuinely correct option, mark isCorrect: true, jsonAnswerCorrect: false.
            5. NEVER OVER-ACCEPT:
               - Do NOT accept an answer that changes the meaning, violates grammar/tense, uses incorrect facts, or doesn't fit sentence structure.

            BANGLA EXPLANATION MANDATE (বাংলায় বিশদ ও সহজ ব্যাখ্যা):
            Provide a natural, pedagogical explanation in clear Bengali (বাংলা):
            - If CORRECT: Explain why the user's answer fits and validates the meaning.
            - If WRONG: Explain (1) why the user's answer is wrong, (2) what the correct answer is, (3) the grammar rule or fact behind it.
            - If JSON ERROR: Clearly state "তোমার উত্তরটি সঠিক হতে পারে। JSON-এ থাকা সংরক্ষিত উত্তরটি ভুল বা অসম্পূর্ণ বলে মনে হচ্ছে।"

            RESPOND STRICTLY WITH A SINGLE VALID JSON OBJECT:
            {
              "isCorrect": boolean,
              "confidence": number between 0.0 and 1.0,
              "decision": "correct" | "wrong" | "uncertain" | "json_error",
              "userAnswer": "${request.userAnswer}",
              "jsonAnswer": "$storedJsonAnswerDisplay",
              "jsonAnswerCorrect": boolean,
              "correctAnswer": "the actually true and accurate answer",
              ${if (isMcq) "\"correctOptionIndex\": number (0-based index of genuinely correct option)," else ""}
              "matchedAnswer": "most relevant accepted answer or valid word",
              "reason": "Brief English rationale explaining the linguistic/scientific decision",
              "banglaExplanation": "বাংলায় সহজ, স্পষ্ট ও শিক্ষামূলক ব্যাখ্যা",
              "warning": "Warning message if JSON answer appears incorrect or question is ambiguous, else empty string"
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
            - Student's Submitted Answer: "${context.userAnswerText ?: context.options.getOrNull(context.userOptionIndex ?: -1) ?: "None"}"

            CONVERSATION HISTORY:
            $historyFormatted

            STUDENT'S NEW QUESTION:
            "$userMessage"

            TUTOR INSTRUCTIONS:
            1. Answer directly and concisely in natural, friendly, and pedagogical Bengali (বাংলা).
            2. Be intellectually honest: If the stored JSON answer is wrong or incomplete, acknowledge it candidly.
            3. Explain linguistic, grammatical, mathematical, or scientific concepts simply so the student truly understands.
            4. If the student asks "কেন ভুল?", "কেন সঠিক?", "সহজ করে বুঝাও", or "JSON কি ভুল?", give a crystal-clear breakdown based on the question context.
            5. Keep your response focused and conversational (2 to 5 sentences unless a deeper breakdown is requested).
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

    fun buildQuestionAuditPrompt(question: QuestionSchema): String {
        val optionsStr = question.options.mapIndexed { idx, opt ->
            "${('A' + idx)}: \"$opt\" (index $idx)"
        }.joinToString(", ")

        return """
            You are the AI Quiz Quality Auditor. Inspect this question schema for accuracy, clarity, and correctness:

            QUESTION: "${question.question}"
            TYPE: ${question.type.name}
            OPTIONS: [$optionsStr]
            STORED ANSWER: ${if (question.type == QuestionType.MCQ) "Index ${question.answer} (${question.options.getOrNull(question.answer)})" else question.fillBlankAnswer}
            ACCEPTED ANSWERS: ${question.acceptedAnswers.joinToString(", ")}

            AUDIT INSTRUCTIONS:
            - Check if the stored answer or option index is factually or mathematically wrong.
            - Check if the question is ambiguous or missing critical information.
            - For MCQ, check if multiple options are equally correct or if no option is correct.

            RESPOND WITH JSON:
            {
              "isSuspicious": boolean,
              "issueDescription": "বাংলায় বা ইংরেজিতে সমস্যার বিবরণ যদি থাকে, অন্যথায় খালি",
              "suggestedCorrectAnswer": "সঠিক প্রস্তাবিত উত্তর",
              "suggestedCorrectIndex": number or null,
              "confidence": number between 0.0 and 1.0
            }
        """.trimIndent()
    }

    fun parseEvaluationResponse(
        rawResponse: String,
        fallbackAcceptedAnswer: String = ""
    ): AiEvaluationResult {
        return AiEvaluationResult.fromJson(rawResponse, fallbackAcceptedAnswer)
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

    fun parseQuestionAudit(questionId: String, rawResponse: String): QuestionAuditResult {
        val clean = extractJsonObject(rawResponse)
        return try {
            val obj = JSONObject(clean)
            QuestionAuditResult(
                questionId = questionId,
                isSuspicious = obj.optBoolean("isSuspicious", false),
                issueDescription = obj.optString("issueDescription", "").ifBlank { null },
                suggestedCorrectAnswer = obj.optString("suggestedCorrectAnswer", "").ifBlank { null },
                suggestedCorrectIndex = if (obj.has("suggestedCorrectIndex") && !obj.isNull("suggestedCorrectIndex")) obj.optInt("suggestedCorrectIndex") else null,
                confidence = obj.optDouble("confidence", 0.9)
            )
        } catch (e: Exception) {
            QuestionAuditResult(
                questionId = questionId,
                isSuspicious = false,
                issueDescription = null,
                suggestedCorrectAnswer = null,
                suggestedCorrectIndex = null,
                confidence = 0.5
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
