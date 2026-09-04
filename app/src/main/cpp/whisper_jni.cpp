// JNI bridge between the Kotlin dictation pipeline and whisper.cpp.
//
// One long-lived whisper_context is created when the dictation service starts
// and reused for every utterance, so the ~180 MiB of weights are mapped once
// and each transcription only pays for the encode/decode passes.

#include <jni.h>
#include <android/log.h>

#include <cstring>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "aivoice-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

void log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    int priority;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: priority = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  priority = ANDROID_LOG_WARN;  break;
        default:                   priority = ANDROID_LOG_DEBUG; break;
    }
    __android_log_write(priority, "whisper.cpp", text);
}

// Trims the leading space whisper puts in front of every segment and collapses
// the blank-audio markers it emits when the user taps stop without speaking.
std::string clean_segment(const char *text) {
    std::string s(text == nullptr ? "" : text);
    const char *noise[] = {"[BLANK_AUDIO]", "(blank audio)", "[SILENCE]", "[MUSIC]", "(music)"};
    for (const char *marker : noise) {
        size_t pos;
        while ((pos = s.find(marker)) != std::string::npos) {
            s.erase(pos, strlen(marker));
        }
    }
    size_t begin = s.find_first_not_of(" \t\n\r");
    if (begin == std::string::npos) return "";
    size_t end = s.find_last_not_of(" \t\n\r");
    return s.substr(begin, end - begin + 1);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_aivoice_flow_whisper_WhisperNative_initContext(
        JNIEnv *env, jobject /*thiz*/, jstring model_path) {
    static bool log_installed = false;
    if (!log_installed) {
        whisper_log_set(log_callback, nullptr);
        log_installed = true;
    }

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;    // no GPU backend is compiled in for Android
    cparams.flash_attn = false;

    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    if (ctx == nullptr) {
        LOGE("failed to load model from %s", path);
    } else {
        LOGI("loaded model from %s", path);
    }
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_aivoice_flow_whisper_WhisperNative_freeContext(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

JNIEXPORT jstring JNICALL
Java_com_aivoice_flow_whisper_WhisperNative_transcribe(
        JNIEnv *env, jobject /*thiz*/, jlong ptr, jfloatArray audio,
        jint n_threads, jstring language, jstring prompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    const jsize n_samples = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.n_threads         = n_threads;
    wparams.translate         = false;
    wparams.no_context        = true;   // each utterance is independent
    wparams.no_timestamps     = true;
    wparams.single_segment    = false;
    wparams.print_special     = false;
    wparams.print_progress    = false;
    wparams.print_realtime    = false;
    wparams.print_timestamps  = false;
    wparams.suppress_blank    = true;
    wparams.suppress_nst      = true;   // drop "(coughs)"-style non-speech tokens
    wparams.temperature       = 0.0f;
    wparams.temperature_inc   = 0.2f;   // still allow the usual fallback ladder
    wparams.greedy.best_of    = 1;
    wparams.beam_search.beam_size = -1;

    const char *lang = nullptr;
    if (language != nullptr) {
        lang = env->GetStringUTFChars(language, nullptr);
        wparams.language = lang;
        wparams.detect_language = (strcmp(lang, "auto") == 0);
    }
    const char *initial_prompt = nullptr;
    if (prompt != nullptr) {
        initial_prompt = env->GetStringUTFChars(prompt, nullptr);
        if (initial_prompt[0] != '\0') {
            wparams.initial_prompt = initial_prompt;
        }
    }

    std::string result;
    if (whisper_full(ctx, wparams, samples, n_samples) != 0) {
        LOGE("whisper_full failed");
    } else {
        const int n_segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < n_segments; ++i) {
            const std::string segment = clean_segment(whisper_full_get_segment_text(ctx, i));
            if (segment.empty()) continue;
            if (!result.empty()) result += ' ';
            result += segment;
        }
    }

    if (lang != nullptr) env->ReleaseStringUTFChars(language, lang);
    if (initial_prompt != nullptr) env->ReleaseStringUTFChars(prompt, initial_prompt);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_aivoice_flow_whisper_WhisperNative_detectedLanguage(
        JNIEnv *env, jobject /*thiz*/, jlong ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return env->NewStringUTF("");
    const int id = whisper_full_lang_id(ctx);
    const char *str = id >= 0 ? whisper_lang_str(id) : "";
    return env->NewStringUTF(str == nullptr ? "" : str);
}

JNIEXPORT jstring JNICALL
Java_com_aivoice_flow_whisper_WhisperNative_systemInfo(
        JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(whisper_print_system_info());
}

} // extern "C"
