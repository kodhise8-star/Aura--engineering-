package com.example.personalassistant

import android.content.Context
import android.os.Build
import java.io.File

/** On-device GGUF inference through llama.cpp. No model API or remote AI server is used. */
object LocalQwenEngine {
    @Volatile private var loaded = false
    @Volatile private var loadedPath: String? = null

    init { System.loadLibrary("aura_native") }

    private external fun nativeLoad(path: String, contextSize: Int, threads: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeUnload()

    @Synchronized fun load(file: File): Result<Unit> = runCatching {
        require(file.exists() && file.isFile) { "Model file not found." }
        require(file.extension.equals("gguf", ignoreCase = true)) { "Choose a GGUF model file." }
        require(file.length() <= 1_000_000_000L) { "This M10 build limits models to 1 GB to reduce memory risk." }
        nativeUnload()
        loaded = false
        val ok = nativeLoad(file.absolutePath, 2048, recommendedThreads())
        check(ok) { "Qwen3 model could not load. Try a smaller Q4 GGUF model." }
        loaded = true
        loadedPath = file.absolutePath
    }

    @Synchronized fun generate(messages: List<ChatMessage>, webContext: String = ""): Result<String> = runCatching {
        check(loaded) { "Import and load a Qwen3 GGUF model first." }
        val prompt = buildString {
            append("<|im_start|>system\nYou are AURA Engineering, a mechanical-engineering-focused assistant for a learner and DIY designer. Prioritize mechanical engineering: statics and dynamics, machine design, mechanisms, shafts, gears, bearings, springs, clutches, engines, thermodynamics, fluid mechanics, heat transfer, materials, manufacturing, tolerances, metrology, CAD workflows, maintenance, and engineering project planning. Default to SI units. For calculations, state known values, assumptions, governing equations, substitutions, units, and final result; check dimensions and distinguish ideal theory from practical limits. Never invent material properties or standards. If critical data is missing, ask for it or clearly state a reasonable illustrative assumption. For safety-critical parts, rotating machinery, combustion, pressure vessels, brakes, or load-bearing structures, identify failure modes, safety factors, testing needs, and the need for qualified review before fabrication. Do not claim a design is safe or production-ready from calculations alone. Keep answers structured and concise. If web search context is supplied, use it carefully and distinguish source-backed facts from estimates.\n")
            if (webContext.isNotBlank()) append("WEB SEARCH RESULTS (may be incomplete; cite the result title in prose when useful):\n").append(webContext).append("\n")
            append("<|im_end|>\n")
            messages.takeLast(8).forEach { m ->
                append(if (m.fromUser) "<|im_start|>user\n" else "<|im_start|>assistant\n")
                append(m.text.take(if (m.fromUser) 1200 else 1800)).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n/no_think\n")
        }.take(7000)
        nativeGenerate(prompt, 192).trim().ifBlank { "The model returned an empty response. Try again with a shorter message." }
    }

    @Synchronized fun unload() { nativeUnload(); loaded = false; loadedPath = null }
    fun isLoaded() = loaded
    fun currentPath() = loadedPath
    private fun recommendedThreads(): Int = if (Build.VERSION.SDK_INT >= 26) 3 else 2
    fun modelFile(context: Context) = File(context.filesDir, "models/qwen3-local.gguf")
}
