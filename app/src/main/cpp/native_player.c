#include "yassertv_core.h"
#include <string.h>
#include <android/log.h>

#define TAG "YasserTV-Native"

RingBuffer g_buffer = {0};

int ring_buffer_write(RingBuffer *buf, const uint8_t *src, uint32_t len) {
    if (!buf || !src || len == 0) return -1;

    uint32_t space = BUFFER_POOL_SIZE - buf->write_pos;
    if (space < len) {
        if (buf->read_pos > 0) {
            memmove(buf->data, buf->data + buf->read_pos, buf->write_pos - buf->read_pos);
            buf->write_pos -= buf->read_pos;
            buf->read_pos = 0;
        }
        space = BUFFER_POOL_SIZE - buf->write_pos;
        if (space < len) return -2;
    }

    memcpy(buf->data + buf->write_pos, src, len);
    buf->write_pos += len;
    buf->watermark = buf->write_pos - buf->read_pos;
    return 0;
}

int ring_buffer_read(RingBuffer *buf, uint8_t *dst, uint32_t len) {
    if (!buf || !dst || len == 0) return -1;

    uint32_t avail = buf->write_pos - buf->read_pos;
    if (avail < len) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Buffer underrun: need %u, have %u", len, avail);
        return -2;
    }

    memcpy(dst, buf->data + buf->read_pos, len);
    buf->read_pos += len;
    buf->watermark = buf->write_pos - buf->read_pos;
    return 0;
}

uint32_t ring_buffer_available(RingBuffer *buf) {
    return buf->write_pos - buf->read_pos;
}

void native_reset_buffer() {
    memset(&g_buffer, 0, sizeof(g_buffer));
    __android_log_print(ANDROID_LOG_INFO, TAG, "Buffer reset");
}

int native_get_reconnect_delay(int attempt) {
    int delays[] = {1000, 2000, 3000, 5000, 8000, 12000};
    int size = sizeof(delays) / sizeof(delays[0]);
    if (attempt < size) return delays[attempt];
    return 15000;
}
