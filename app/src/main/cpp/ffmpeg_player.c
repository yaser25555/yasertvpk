#include "yassertv_core.h"
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#define TAG "FFmpegPlayer"

// FFmpeg forward declarations (linked dynamically)
typedef struct AVFormatContext AVFormatContext;
typedef struct AVCodecContext AVCodecContext;
typedef struct AVCodec AVCodec;
typedef struct AVFrame AVFrame;
typedef struct AVPacket AVPacket;
typedef struct AVIOContext AVIOContext;
typedef struct SwrContext SwrContext;

typedef struct {
    AVFormatContext *fmt_ctx;
    AVCodecContext *video_dec_ctx;
    AVCodecContext *audio_dec_ctx;
    int video_stream_idx;
    int audio_stream_idx;
    int video_frame_count;
    int audio_frame_count;
    volatile int is_playing;
    volatile int is_paused;
    pthread_t decode_thread;
    RingBuffer *buffer;
    int reconnect_attempts;
    int max_reconnect;
    char url[1024];
} FFmpegPlayerContext;

static FFmpegPlayerContext player = {0};

int ffmpeg_player_init(const char *url) {
    memset(&player, 0, sizeof(player));
    strncpy(player.url, url, sizeof(player.url) - 1);
    player.video_stream_idx = -1;
    player.audio_stream_idx = -1;
    player.max_reconnect = 10;
    player.is_playing = 1;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Initialized with URL: %s", url);
    return 0;
}

void ffmpeg_player_stop() {
    player.is_playing = 0;
    player.is_paused = 0;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Player stopped");
}

int ffmpeg_player_is_playing() {
    return player.is_playing && !player.is_paused;
}

void ffmpeg_player_pause() {
    player.is_paused = 1;
}

void ffmpeg_player_resume() {
    player.is_paused = 0;
}

int ffmpeg_player_get_reconnect_count() {
    return player.reconnect_attempts;
}

void ffmpeg_player_reset_reconnect() {
    player.reconnect_attempts = 0;
}
