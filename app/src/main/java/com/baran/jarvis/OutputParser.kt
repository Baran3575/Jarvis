package com.baran.jarvis

import org.json.JSONObject
import java.util.regex.Pattern

object OutputParser {

    private val ANSI = Pattern.compile("\\u001b\\[[0-9;]*[A-Za-z]")

    /**
     * opencode `run --format json` emits one JSON object per line. We collect
     * the assistant text from every `type:"text"` event. If the JSON stream is
     * empty (a known opencode regression in some environments), fall back to
     * the raw stdout with ANSI codes stripped.
     */
    fun parse(raw: String): String {
        val texts = mutableListOf<String>()
        for (line in raw.split("\n")) {
            val t = line.trim()
            if (!t.startsWith("{")) continue
            try {
                val json = JSONObject(t)
                if (json.optString("type") == "text") {
                    val part = json.optJSONObject("part")
                    val txt = part?.optString("text")
                    if (!txt.isNullOrBlank()) texts.add(txt)
                }
            } catch (_: Exception) {
                // ignore malformed lines
            }
        }
        val joined = texts.joinToString("")
        return if (joined.isNotBlank()) joined else stripAnsi(raw)
    }

    private fun stripAnsi(s: String): String = ANSI.matcher(s).replaceAll("").trim()
}
