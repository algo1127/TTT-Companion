package com.ttt.companion.tools

/**
 * Plain-text tool descriptions injected into the system prompt.
 * Small models follow this pattern reliably — much simpler than
 * native function-calling APIs which not all GGUF models support well.
 */
object ToolDefinitions {

    val SYSTEM_PROMPT_ADDITION = """
        You have access to these tools. To use one, output a single line
        in this EXACT format and nothing else on that line:
        TOOL_CALL: {"tool": "tool_name", "args": {...}}
        Available tools:
        - set_alarm: {"hour": 0-23, "minute": 0-59, "label": "string"}
        - set_timer: {"seconds": number, "label": "string"}
        - toggle_torch: {"on": boolean}
        - vibrate: {"duration_ms": number}
        - open_app: {"package_name": "string"} (use common names like "youtube", "maps", "gmail", "camera", "browser")
        After a TOOL_CALL line, you may continue with a normal response
        acknowledging what you did. Only use a tool when the user clearly
        asks for one — for normal conversation, never output TOOL_CALL.
        Example:
        User: wake me up at 7am for the gym
        You: TOOL_CALL: {"tool": "set_alarm", "args": {"hour": 7, "minute": 0, "label": "gym"}}
        Sure thing, I've set an alarm for 7 AM. Don't snooze it too much!
    """.trimIndent()
}