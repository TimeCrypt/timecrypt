//
// Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
// Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
//

#include <jni.h>
#include <memory.h>
#include "aes.h"

#define AES_KEY_BYTES 16

typedef unsigned char byte;

jbyteArray as_byte_array(JNIEnv *env, unsigned char *buf, int len) {
    jbyteArray array = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, array, 0, len, (jbyte *) buf);
    return array;
}

unsigned char *as_unsigned_char_array(JNIEnv *env, jbyteArray array, int *len) {
    *len = (*env)->GetArrayLength(env, array);
    unsigned char *buf = (unsigned char *) malloc((size_t) *len);
    (*env)->GetByteArrayRegion(env, array, 0, *len, (jbyte *) buf);
    return buf;
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesNi_encrypt(JNIEnv *env, jobject javaThis,
                                                                           jbyteArray key_oct, jbyteArray to_encrypt) {

    AES_KEY key[1];
    jbyteArray res;

    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    int len_key = (*env)->GetArrayLength(env, key_oct);
    int len_cont = (*env)->GetArrayLength(env, to_encrypt);

    if(len_key!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Key is not 16 bytes long");
    }

    if(len_cont!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Content is not 16 bytes long");
    }

    (*env)->GetByteArrayRegion(env, key_oct, 0, AES_KEY_BYTES, (jbyte *) seed);
    (*env)->GetByteArrayRegion(env, to_encrypt, 0, AES_KEY_BYTES, (jbyte *) data);


    AES_128_Key_Expansion(seed, key);
    AES_ecb_encrypt_blk((block *) data, key);

    res = as_byte_array(env, data, AES_KEY_BYTES);
    //(*env)->ReleaseByteArrayElements(env, key_oct, (jbyte *) seed, 0);
    //(*env)->ReleaseByteArrayElements(env, to_encrypt, (jbyte *) data, 0);
    return res;
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesNi_apply(JNIEnv *env,
                                            jobject javaThis, jbyteArray key_oct, jint k) {
    AES_KEY key[1];
    jbyteArray res;

    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    int len_key = (*env)->GetArrayLength(env, key_oct);

    if(len_key!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Key is not 16 bytes long");
    }

    if(k>255) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "k does not fit in one byte");
    }

    (*env)->GetByteArrayRegion(env, key_oct, 0, AES_KEY_BYTES, (jbyte *) seed);
    memset(data, 0, AES_KEY_BYTES);
    for (int shift = 0; shift < 4; shift++) {
        data[15 - shift] = (byte) (k >> (shift * 8)) & 0xFF;
    }


    AES_128_Key_Expansion(seed, key);
    AES_ecb_encrypt_blk((block *) data, key);

    res = as_byte_array(env, data, AES_KEY_BYTES);
    //(*env)->ReleaseByteArrayElements(env, key_oct, (jbyte *) seed, 0);
    return res;
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesNi_multiapply(JNIEnv *env,
                                              jobject javaThis, jbyteArray key_oct, jintArray k_path) {
    AES_KEY key[1];
    jbyteArray res;

    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    int len_key = (*env)->GetArrayLength(env, key_oct);

    if(len_key!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Key is not 16 bytes long");
    }


    (*env)->GetByteArrayRegion(env, key_oct, 0, AES_KEY_BYTES, (jbyte *) seed);
    memset(data, 0, AES_KEY_BYTES);

    jsize k_path_len = (*env)->GetArrayLength(env, k_path);
    jint *k_path_elements = (*env)->GetIntArrayElements(env, k_path, 0);
    for (int i=0; i<(int) k_path_len; i++) {
        memset(data, 0, AES_KEY_BYTES);
        for (int shift = 0; shift < 4; shift++) {
            data[15 - shift] = (byte) (k_path_elements[i] >> (shift * 8)) & 0xFF;
        }
        AES_128_Key_Expansion(seed, key);
        AES_ecb_encrypt_blk((block *) data, key);
        memcpy(seed, data, AES_KEY_BYTES);
    }
    (*env)->ReleaseIntArrayElements(env, k_path, k_path_elements, 0);

    res = as_byte_array(env, seed, AES_KEY_BYTES);
    //(*env)->ReleaseByteArrayElements(env, key_oct, (jbyte *) seed, 0);
    return res;
}

