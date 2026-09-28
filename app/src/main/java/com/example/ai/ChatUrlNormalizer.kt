package com.example.ai

/**
 * Normalizes OpenAI-compatible base URLs to ensure the final Chat Completions endpoint
 * is constructed strictly once without path duplication, trailing slashes, or missing /v1 paths.
 */
object ChatUrlNormalizer {

    fun normalize(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""

        // 1. Ensure scheme (default to https:// if missing)
        val scheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> "https://"
            trimmed.startsWith("http://", ignoreCase = true) -> "http://"
            else -> {
                trimmed = "https://$trimmed"
                "https://"
            }
        }
        var rest = trimmed.substring(scheme.length)

        // 2. Collapse consecutive duplicate slashes (e.g. "v1//chat" -> "v1/chat")
        rest = rest.replace(Regex("/{2,}"), "/")

        // 3. Trim trailing slashes
        rest = rest.trimEnd('/')

        // 4. Strip any duplicated or trailing /chat/completions or /completions or /chat
        var changed = true
        while (changed) {
            changed = false
            if (rest.endsWith("/chat/completions", ignoreCase = true)) {
                rest = rest.substring(0, rest.length - "/chat/completions".length).trimEnd('/')
                changed = true
            } else if (rest.endsWith("/completions", ignoreCase = true)) {
                rest = rest.substring(0, rest.length - "/completions".length).trimEnd('/')
                changed = true
            } else if (rest.endsWith("/chat", ignoreCase = true)) {
                rest = rest.substring(0, rest.length - "/chat".length).trimEnd('/')
                changed = true
            }
        }

        // 5. Handle path anomalies like /chat/completions/v1
        if (rest.endsWith("/chat/completions/v1", ignoreCase = true)) {
            val baseBefore = rest.substring(0, rest.length - "/chat/completions/v1".length).trimEnd('/')
            rest = "$baseBefore/v1"
        }

        // 6. Deduplicate repeated /v1 segments (e.g. /v1/v1 -> /v1)
        rest = rest.replace(Regex("(/v1)+(/)?$"), "/v1")
        rest = rest.replace(Regex("(/v1){2,}"), "/v1")

        // 7. For Hugging Face router, ensure /v1 is present
        if (rest.contains("router.huggingface.co", ignoreCase = true) && !rest.endsWith("/v1", ignoreCase = true)) {
            rest = "$rest/v1"
        }

        // 8. Append /chat/completions exactly once
        return "$scheme$rest/chat/completions"
    }
}
