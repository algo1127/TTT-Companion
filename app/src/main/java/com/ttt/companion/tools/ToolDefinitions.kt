package com.ttt.companion.tools

/**
 * Modular tool definitions to support dynamic injection (Lazy Loading).
 */
object ToolDefinitions {

    const val CORE_INSTRUCTIONS = """
        You have access to internal tools. To use one, output a single line
        in this EXACT format and nothing else on that line:
        TOOL_CALL: {"tool": "tool_name", "args": {...}}
        After a TOOL_CALL line, you may continue with a normal response
        acknowledging what you did. Only use a tool when the user clearly
        asks for one.
    """

    val TOOL_SCHEMAS = mapOf(
        "alarm" to "- set_alarm: {\"hour\": 0-23, \"minute\": 0-59, \"label\": \"string\"}",
        "timer" to "- set_timer: {\"seconds\": number, \"label\": \"string\"}",
        "torch" to "- toggle_torch: {\"on\": boolean}",
        "vibrate" to "- vibrate: {\"duration_ms\": number}",
        "apps" to "- open_app: {\"package_name\": \"string\"} (use common names like \"youtube\", \"maps\", \"gmail\", \"camera\", \"browser\")"
    )

    fun getDynamicPrompt(requestedTools: Set<String>): String {
        if (requestedTools.isEmpty()) return ""
        
        return buildString {
            append(CORE_INSTRUCTIONS.trimIndent())
            append("\nAvailable tools:\n")
            requestedTools.forEach { key ->
                TOOL_SCHEMAS[key]?.let { append(it).append("\n") }
            }
        }
    }

    @Deprecated("Use getDynamicPrompt instead", ReplaceWith("getDynamicPrompt(TOOL_SCHEMAS.keys)"))
    val SYSTEM_PROMPT_ADDITION = getDynamicPrompt(TOOL_SCHEMAS.keys)
}
