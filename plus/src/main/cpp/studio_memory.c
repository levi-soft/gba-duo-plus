#include <jni.h>
#include <stdlib.h>
#include "studio_memory_range.h"
#include "bus.h"

extern const void* gbaduo_rom(size_t* size);

JNIEXPORT jlong JNICALL
Java_com_khumam_gbaduo_debug_StudioMemory_romSize(JNIEnv* env, jobject thiz) {
    size_t size = 0;
    gbaduo_rom(&size);
    return (jlong)size;
}

/* Called only by the opted-in Studio service, between emulated frames. */
JNIEXPORT jbyteArray JNICALL
Java_com_khumam_gbaduo_debug_StudioMemory_read(JNIEnv* env, jobject thiz, jlong address, jint length) {
    size_t rom_size = 0;
    const unsigned char* rom = gbaduo_rom(&rom_size);
    if (!rom || gbaduo_bus_platform() != GBADUO_BUS_GBA) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), "No GBA game loaded");
        return NULL;
    }
    if (address < 0 || length < 1 || !plus_studio_range_valid((uint64_t)address, (uint64_t)length, rom_size)) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), "Invalid Studio inspection range");
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, length);
    if (!result) return NULL;
    if (address >= 0x08000000) {
        (*env)->SetByteArrayRegion(env, result, 0, length, (const jbyte*)(rom + (size_t)(address - 0x08000000)));
        return result;
    }
    jbyte* bytes = malloc((size_t)length);
    if (!bytes) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), "Studio inspection allocation failed");
        return NULL;
    }
    for (jint i = 0; i < length; ++i) bytes[i] = (jbyte)gbaduo_bus_read8((uint32_t)(address + i));
    (*env)->SetByteArrayRegion(env, result, 0, length, bytes);
    free(bytes);
    return result;
}
