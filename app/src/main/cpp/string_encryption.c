#include "yassertv_core.h"
#include <string.h>

void encrypt_xor(uint8_t *data, int len, uint8_t seed) {
    for (int i = 0; i < len; i++) {
        data[i] ^= (seed + (uint8_t)(i * 7));
    }
}

void decrypt_xor(uint8_t *data, int len, uint8_t seed) {
    encrypt_xor(data, len, seed);
}

int jni_decrypt_string(JNIEnv *env, jobject thiz, jbyteArray data, jbyte seed) {
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *elements = (*env)->GetByteArrayElements(env, data, NULL);
    if (!elements) return -1;

    decrypt_xor((uint8_t *)elements, (int)len, (uint8_t)seed);

    (*env)->ReleaseByteArrayElements(env, data, elements, 0);
    return len;
}
