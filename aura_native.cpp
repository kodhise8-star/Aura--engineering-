#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AURA", __VA_ARGS__)
static std::mutex g_mutex;
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static llama_sampler *g_sampler = nullptr;
static int32_t g_context_size = 2048;
static bool g_backend_initialized = false;

static void unload_locked() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_personalassistant_LocalQwenEngine_nativeLoad(JNIEnv *env, jobject, jstring jpath, jint contextSize, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (!g_backend_initialized) { llama_backend_init(); g_backend_initialized = true; }
    llama_model_params mp = llama_model_default_params();
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) { LOGE("Model load failed"); return JNI_FALSE; }
    llama_context_params cp = llama_context_default_params();
    g_context_size = std::max(512, std::min((int)contextSize, 4096));
    cp.n_ctx = g_context_size;
    cp.n_batch = std::min(g_context_size, 512);
    cp.n_threads = std::max(2, std::min((int)threads, 4));
    cp.n_threads_batch = cp.n_threads;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { unload_locked(); LOGE("Context init failed"); return JNI_FALSE; }
    g_sampler = llama_sampler_init_greedy();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_personalassistant_LocalQwenEngine_nativeGenerate(JNIEnv *env, jobject, jstring jprompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx || !g_sampler) return env->NewStringUTF("Load a local GGUF model first.");
    const char *raw = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(raw);
    env->ReleaseStringUTFChars(jprompt, raw);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(std::max<size_t>(prompt.size() + 16, 256));
    int n = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize((size_t)(-n));
        n = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
    }
    if (n <= 0 || n >= g_context_size) return env->NewStringUTF("Prompt is too long for the current context. Shorten the conversation and try again.");
    tokens.resize(n);
    llama_memory_clear(llama_get_memory(g_ctx), true);
    if (g_sampler) llama_sampler_free(g_sampler);
    g_sampler = llama_sampler_init_greedy();
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("Could not evaluate prompt. Try a smaller model or shorter prompt.");
    int n_past = (int)tokens.size();
    std::string output;
    std::vector<char> piece(8192);
    const int limit = std::max(16, std::min((int)maxTokens, 192));
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        llama_sampler_accept(g_sampler, token);
        int pieceLen = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, true);
        if (pieceLen > 0) output.append(piece.data(), pieceLen);
        llama_batch next = llama_batch_init(1, 0, 1);
        next.token[0] = token;
        next.pos[0] = n_past++;
        next.n_seq_id[0] = 1;
        next.seq_id[0][0] = 0;
        next.logits[0] = true;
        int decodeStatus = llama_decode(g_ctx, next);
        llama_batch_free(next);
        if (decodeStatus != 0 || n_past >= g_context_size - 1) break;
    }
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_personalassistant_LocalQwenEngine_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
}
