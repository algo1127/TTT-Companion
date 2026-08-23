package com.ttt.companion.tools

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

data class ParsedToolCall(
    val tool: String,
    val args: JsonObject
)

data class ToolParseResult(
    val toolCall: ParsedToolCall?,
    val cleanedResponse: String // response text with the TOOL_CALL line removed
)

object ToolCallParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val TOOL_CALL_START_REGEX = Regex("""TOOL_CALL:\s*\{""", RegexOption.IGNORE_CASE)

    /**
     * Scans [rawResponse] for a TOOL_CALL line.
     * Returns the parsed tool call (if any) and the response with that
     * line stripped out, so it's never shown/spoken to the user.
     */
    fun parse(rawResponse: String): ToolParseResult {
        val match = TOOL_CALL_START_REGEX.find(rawResponse)
            ?: return ToolParseResult(null, rawResponse)

        val jsonStartIndex = match.range.last // index of the '{'

        var depth = 0
        var inString = false
        var escaped = false
        var jsonEndIndex = -1
        for (i in jsonStartIndex until rawResponse.length) {
            val c = rawResponse[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }

            if (!inString) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            jsonEndIndex = i
                            break
                        }
                    }
                }
            }
        }

        if (jsonEndIndex == -1) {
            Log.e("ToolCallParser", "Incomplete JSON in TOOL_CALL starting at $jsonStartIndex")
            return ToolParseResult(null, rawResponse)
        }

        val jsonStr = rawResponse.substring(jsonStartIndex, jsonEndIndex + 1)
        val fullMatchText = rawResponse.substring(match.range.first, jsonEndIndex + 1)

        val parsed = try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val tool = obj["tool"]?.jsonPrimitive?.content
            val args = obj["args"]?.jsonObject

            if (tool != null && args != null) {
                ParsedToolCall(tool, args)
            } else {
                Log.w("ToolCallParser", "Parsed JSON but missing 'tool' or 'args': $jsonStr")
                null
            }
        } catch (e: Exception) {
            Log.e("ToolCallParser", "Failed to parse tool JSON: $jsonStr", e)
            null
        }

        // Remove the TOOL_CALL block from what gets shown/spoken
        val cleaned = rawResponse.replace(fullMatchText, "").trim()

        return ToolParseResult(parsed, cleaned)
    }
}