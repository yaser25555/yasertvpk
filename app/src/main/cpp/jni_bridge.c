#include "yassertv_core.h"
#include <android/log.h>
#include <string.h>
#include <stdlib.h>

#define TAG "YasserTV-JNI"

JNIEXPORT jstring JNICALL
Java_com_yassertv_core_NativeCore_decryptString(JNIEnv *env, jobject thiz,
                                                  jbyteArray encrypted, jbyte seed) {
    jsize len = (*env)->GetArrayLength(env, encrypted);
    jbyte *elements = (*env)->GetByteArrayElements(env, encrypted, NULL);
    if (!elements) return NULL;

    decrypt_xor((uint8_t *)elements, (int)len, (uint8_t)seed);

    jstring result = (*env)->NewStringUTF(env, (const char *)elements);
    (*env)->ReleaseByteArrayElements(env, encrypted, elements, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_yassertv_core_NativeCore_getReconnectDelay(JNIEnv *env, jobject thiz,
                                                      jint attempt) {
    return (jint)native_get_reconnect_delay((int)attempt);
}

JNIEXPORT void JNICALL
Java_com_yassertv_core_NativeCore_resetBuffer(JNIEnv *env, jobject thiz) {
    native_reset_buffer();
}

JNIEXPORT jint JNICALL
Java_com_yassertv_core_NativeCore_getBufferAvailable(JNIEnv *env, jobject thiz) {
    return (jint)ring_buffer_available(&g_buffer);
}

JNIEXPORT jint JNICALL
Java_com_yassertv_core_NativeCore_writeToBuffer(JNIEnv *env, jobject thiz,
                                                  jbyteArray data, jint offset, jint length) {
    jbyte *elements = (*env)->GetByteArrayElements(env, data, NULL);
    if (!elements) return -1;

    int ret = ring_buffer_write(&g_buffer, (const uint8_t *)(elements + offset), (uint32_t)length);
    (*env)->ReleaseByteArrayElements(env, data, elements, JNI_ABORT);
    return ret;
}

/* ============ NativeFFmpegPlayer JNI ============ */

typedef struct {
    char url[1024];
    volatile int is_playing;
    volatile int is_paused;
    int reconnect_attempts;
    int max_reconnect;
} PlayerContext;

JNIEXPORT jlong JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeInit(JNIEnv *env, jobject thiz,
                                                         jstring url) {
    const char *url_str = (*env)->GetStringUTFChars(env, url, NULL);
    if (!url_str) return 0;

    PlayerContext *ctx = (PlayerContext *)calloc(1, sizeof(PlayerContext));
    if (!ctx) {
        (*env)->ReleaseStringUTFChars(env, url, url_str);
        return 0;
    }

    strncpy(ctx->url, url_str, sizeof(ctx->url) - 1);
    ctx->is_playing = 1;
    ctx->max_reconnect = 10;
    ctx->reconnect_attempts = 0;

    __android_log_print(ANDROID_LOG_INFO, TAG, "FFmpegPlayer init: %s", ctx->url);
    (*env)->ReleaseStringUTFChars(env, url, url_str);
    return (jlong)ctx;
}

JNIEXPORT void JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativePlay(JNIEnv *env, jobject thiz,
                                                         jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    if (!ctx) return;
    ctx->is_paused = 0;
    ctx->is_playing = 1;
}

JNIEXPORT void JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativePause(JNIEnv *env, jobject thiz,
                                                          jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    if (!ctx) return;
    ctx->is_paused = 1;
}

JNIEXPORT void JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeStop(JNIEnv *env, jobject thiz,
                                                         jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    if (!ctx) return;
    ctx->is_playing = 0;
    ctx->is_paused = 0;
}

JNIEXPORT void JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeRelease(JNIEnv *env, jobject thiz,
                                                            jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    if (!ctx) return;
    ctx->is_playing = 0;
    free(ctx);
}

JNIEXPORT void JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeSetSurface(JNIEnv *env, jobject thiz,
                                                               jlong handle, jobject surface) {
    (void)handle; (void)surface;
}

JNIEXPORT jboolean JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeIsPlaying(JNIEnv *env, jobject thiz,
                                                              jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    if (!ctx) return JNI_FALSE;
    return ctx->is_playing && !ctx->is_paused ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeGetReconnectCount(JNIEnv *env, jobject thiz,
                                                                      jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    return ctx ? (jint)ctx->reconnect_attempts : 0;
}

JNIEXPORT void JNICALL
Java_com_yassertv_player_NativeFFmpegPlayer_nativeResetReconnect(JNIEnv *env, jobject thiz,
                                                                   jlong handle) {
    PlayerContext *ctx = (PlayerContext *)handle;
    if (ctx) ctx->reconnect_attempts = 0;
}
