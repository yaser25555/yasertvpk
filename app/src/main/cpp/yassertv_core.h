#ifndef YASSERTV_CORE_H
#define YASSERTV_CORE_H

#include <jni.h>
#include <stdint.h>

#define SEED 0x9E
#define BUFFER_POOL_SIZE (4 * 1024 * 1024)

typedef struct {
    uint8_t data[BUFFER_POOL_SIZE];
    uint32_t write_pos;
    uint32_t read_pos;
    uint32_t watermark;
    volatile int running;
} RingBuffer;

void encrypt_xor(uint8_t *data, int len, uint8_t seed);
void decrypt_xor(uint8_t *data, int len, uint8_t seed);
int ring_buffer_write(RingBuffer *buf, const uint8_t *src, uint32_t len);
int ring_buffer_read(RingBuffer *buf, uint8_t *dst, uint32_t len);
uint32_t ring_buffer_available(RingBuffer *buf);
void native_reset_buffer(void);
int native_get_reconnect_delay(int attempt);

extern RingBuffer g_buffer;

#endif
