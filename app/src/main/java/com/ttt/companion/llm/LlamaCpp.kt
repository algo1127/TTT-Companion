package com.ttt.companion.llm

import android.content.Context

object LlamaCpp {
    init {
        // The library name is "rnllama" based on the logs (librnllama_x86_64.so)
        System.loadLibrary("rnllama")
    }

    // These signatures MUST match the native exports in the library
    // The library uses a class-based JNI structure (org.nehuatl.llamacpp.LlamaContext)
    // but since we are calling it from our own object, the native side won't find the methods
    // unless we match the exact package and class name expected by the library's JNI.
}