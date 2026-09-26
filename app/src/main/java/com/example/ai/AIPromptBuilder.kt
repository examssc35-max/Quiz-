package com.example.ai

import com.example.data.model.AiEvaluationResult
import org.json.JSONObject

object AIPromptBuilder {

    fun buildSystemInstruction(): String {
        return """
            You are a senior academic linguist and educational evaluator.
            Your role is to strictly and accurately evaluate a student's answer for fill-in-the-blank questions in both English and Bengali (বাংলা).
            You evaluate whether the student's answer correctly, naturally, grammatically, and semantically fits the exact blank in the context of the sentence.
            You must output ONLY valid JSON without markdown wrapping or commentary.
        """.trimIndent()
    }

    fun buildEvaluationPrompt(request: AnswerEvaluationRequest): String {
        val acceptedListStr = request.acceptedAnswers.joinToString(", ") { "\"$it\"" }
        val isBengaliQuestion = request.questionText.any { it in '\u0980'..'\u09FF' } ||
                request.acceptedAnswers.any { ans -> ans.any { it in '\u0980'..'\u09FF' } }

        return """
            You are an expert educational quiz evaluator. Evaluate the student's submitted answer for the following question:

            SENTENCE / QUESTION: "${request.questionText}"
            BLANK CONTEXT: Identify the blank position in the sentence (e.g. '______', '___', '[...]', or markers like '(a) —', '(b) —', '(c) —').
            STORED ACCEPTED ANSWER(S): [$acceptedListStr]
            STUDENT SUBMITTED ANSWER: "${request.userAnswer}"
            PRIMARY LANGUAGE: ${if (isBengaliQuestion) "Bengali (বাংলা)" else "English"}

            CORE EVALUATION PRINCIPLE:
            Judge the STUDENT'S ANSWER against the ACTUAL SENTENCE, not just string equality with the stored answer.
            Question to answer: "Does the student's answer correctly, naturally, and grammatically fit this exact blank in this exact sentence?"

            MUST EVALUATE CAREFULLY:
            1. Sentence meaning and overall context.
            2. Grammar & syntax required by the surrounding words.
            3. Part of speech (noun, verb, adjective, adverb, preposition) required by the blank.
            4. Tense and verb form (past, present, participle, gerund, 3rd person singular, etc.).
            5. Singular / plural agreement:
               - If the sentence syntax requires a singular noun, a plural noun is strictly WRONG.
               - Example: "Air is the most important (a) — of human environment." Stored: "element", User: "elements" -> MUST be marked isCorrect: false because singular is required!
            6. Mathematical & numerical equivalence:
               - Bengali digits and English digits are equivalent: e.g. "৬০–৭০%" and "60-70%" are EQUIVALENT (isCorrect: true).
               - Commas and formatting differences in numbers (e.g. "1,000" and "1000", "১,০০০" and "1000") are EQUIVALENT.
            7. Unit equivalence:
               - Equivalent units in context: e.g. "১,০০০ কেজি" and "1000 kg" are EQUIVALENT (isCorrect: true).
            8. Genuinely valid alternative words in context:
               - Example: "These materials cause (d) — to the environment." Stored: "harm", User: "damage" -> ACCEPT as isCorrect: true if "damage" naturally fits the sentence.
               - If multiple stored answers exist like ["management", "disposal"], both are valid.
            9. NEVER OVER-ACCEPT:
               - Do NOT accept a synonym if it violates grammar, alters the sentence meaning, or changes part of speech.
               - Do NOT accept incorrect facts, wrong units, or words that do not fit the sentence structure.

            BANGLA EXPLANATION MANDATE (বাংলায় বিশদ ও সহজ ব্যাখ্যা):
            Provide a clear, natural, pedagogical explanation in Bengali (বাংলা).
            Do NOT output generic phrases like "তোমার উত্তর সঠিক উত্তরের সাথে মেলেনি।"
            - If CORRECT:
              Explain why the user's answer fits the sentence and meaning.
              Example: "তোমার উত্তরটি সঠিক। 'damage' শব্দটি এই বাক্যে অর্থ ও grammar অনুযায়ী যথাযথভাবে বসে।"
            - If WRONG:
              1. Explain why the user's answer is wrong (grammar rule, singular/plural, tense, or meaning error).
              2. Explain why the accepted answer is correct.
              3. Explain the sentence meaning and grammar rule in simple Bengali.
              Example: "তোমার উত্তর ‘elements’ এখানে ঠিক নয়, কারণ ‘the most important’ এর পরে এই বাক্যে singular noun দরকার। তাই ‘element’ সঠিক।"

            OUTPUT FORMAT:
            Respond ONLY with a single valid JSON object with these exact keys:
            {
              "isCorrect": boolean,
              "confidence": number between 0.0 and 1.0,
              "matchedAnswer": "stored accepted answer or validated alternative",
              "banglaExplanation": "বাংলায় সহজ, স্পষ্ট ও শিক্ষামূলক ব্যাখ্যা",
              "reason": "Brief English rationale explaining the linguistic decision"
            }
        """.trimIndent()
    }

    fun parseEvaluationResponse(
        rawResponse: String,
        fallbackAcceptedAnswer: String = ""
    ): AiEvaluationResult {
        val clean = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStr = extractJsonObject(clean)
        val obj = JSONObject(jsonStr)

        val isCorrect = obj.optBoolean("isCorrect", false)
        val confidence = obj.optDouble("confidence", if (isCorrect) 0.95 else 0.85)
        val matched = obj.optString("matchedAnswer", fallbackAcceptedAnswer)
        val banglaExplanation = obj.optString("banglaExplanation", "")
        val reason = obj.optString("reason", "")

        return AiEvaluationResult(
            isCorrect = isCorrect,
            confidence = confidence,
            reason = reason,
            banglaExplanation = banglaExplanation,
            matchedAnswer = matched.ifBlank { fallbackAcceptedAnswer }
        )
    }

    private fun extractJsonObject(text: String): String {
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        return text
    }
}
