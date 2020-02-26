//
// Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
// Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
//

#include <jni.h>
#include <memory.h>
#include <openssl/conf.h>
#include <openssl/evp.h>
#include <openssl/err.h>
#include <openssl/aes.h>

#define AES_KEY_BYTES 16
#define CIPHERTEXT_LEN 16
#define GCM_IV 12
#define AAD_LOCAL 4
#define STS_OK 1
#define STS_ERR 0

typedef unsigned char byte;

jbyteArray as_byte_array(JNIEnv *env, unsigned char *buf, int len) {
    jbyteArray array = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, array, 0, len, (jbyte *) buf);
    return array;
}

int gcm_encrypt(unsigned char *plaintext, int plaintext_len,
                unsigned char *aad, int aad_len,
                unsigned char *key,
                unsigned char *iv, int iv_len,
                unsigned char *ciphertext,
                unsigned char *tag)
{
    EVP_CIPHER_CTX *ctx;

    int len;

    int ciphertext_len;


    /* Create and initialise the context */
    if(!(ctx = EVP_CIPHER_CTX_new()))
        return STS_ERR;

    /* Initialise the encryption operation. */
    if(1 != EVP_EncryptInit_ex(ctx, EVP_aes_128_gcm(), NULL, NULL, NULL))
       return STS_ERR;

    /*
     * Set IV length if default 12 bytes (96 bits) is not appropriate
     */
    if(1 != EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv_len, NULL))
        return STS_ERR;

    /* Initialise key and IV */
    if(1 != EVP_EncryptInit_ex(ctx, NULL, NULL, key, iv))
        return STS_ERR;

    /*
     * Provide any AAD data. This can be called zero or more times as
     * required
     */
    if(1 != EVP_EncryptUpdate(ctx, NULL, &len, aad, aad_len))
        return STS_ERR;

    /*
     * Provide the message to be encrypted, and obtain the encrypted output.
     * EVP_EncryptUpdate can be called multiple times if necessary
     */
    if(1 != EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, plaintext_len))
        return STS_ERR;
    ciphertext_len = len;

    /*
     * Finalise the encryption. Normally ciphertext bytes may be written at
     * this stage, but this does not occur in GCM mode
     */
    if(1 != EVP_EncryptFinal_ex(ctx, ciphertext + len, &len))
        return STS_ERR;;
    ciphertext_len += len;

    /* Get the tag */
    if(1 != EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag))
        return STS_ERR;

    /* Clean up */
    EVP_CIPHER_CTX_free(ctx);

    return ciphertext_len;
}

int gcm_decrypt(unsigned char *ciphertext, int ciphertext_len,
                unsigned char *aad, int aad_len,
                unsigned char *tag,
                unsigned char *key,
                unsigned char *iv, int iv_len,
                unsigned char *plaintext)
{
    EVP_CIPHER_CTX *ctx;
    int len;
    int plaintext_len;
    int ret;

    /* Create and initialise the context */
    if(!(ctx = EVP_CIPHER_CTX_new()))
        return STS_ERR;;

    /* Initialise the decryption operation. */
    if(!EVP_DecryptInit_ex(ctx, EVP_aes_128_gcm(), NULL, NULL, NULL))
        return STS_ERR;

    /* Set IV length. Not necessary if this is 12 bytes (96 bits) */
    if(!EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv_len, NULL))
        return STS_ERR;

    /* Initialise key and IV */
    if(!EVP_DecryptInit_ex(ctx, NULL, NULL, key, iv))
        return STS_ERR;

    /*
     * Provide any AAD data. This can be called zero or more times as
     * required
     */
    if(!EVP_DecryptUpdate(ctx, NULL, &len, aad, aad_len))
        return STS_ERR;

    /*
     * Provide the message to be decrypted, and obtain the plaintext output.
     * EVP_DecryptUpdate can be called multiple times if necessary
     */
    if(!EVP_DecryptUpdate(ctx, plaintext, &len, ciphertext, ciphertext_len))
        return STS_ERR;
    plaintext_len = len;

    /* Set expected tag value. Works in OpenSSL 1.0.1d and later */
    if(!EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, 16, tag))
        return STS_ERR;

    /*
     * Finalise the decryption. A positive return value indicates success,
     * anything else is a failure - the plaintext is not trustworthy.
     */
    ret = EVP_DecryptFinal_ex(ctx, plaintext + len, &len);

    /* Clean up */
    EVP_CIPHER_CTX_free(ctx);

    if(ret > 0) {
        /* Success */
        plaintext_len += len;
        return plaintext_len;
    } else {
        /* Verify failed */
        return -1;
    }
}

/*int encrypt(byte *plaintext, byte *key, byte *ciphertext) {
    EVP_CIPHER_CTX *ctx;
    int len = 0;

    if(!(ctx = EVP_CIPHER_CTX_new())) return STS_ERR;

    if(STS_OK != EVP_EncryptInit_ex(ctx, EVP_aes_128_ecb(), NULL, key, NULL)) return STS_ERR;

    if(STS_OK != EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, AES_KEY_BYTES)) return STS_ERR;

    if (len != AES_KEY_BYTES) return STS_ERR;

    if(STS_OK != EVP_EncryptFinal_ex(ctx, ciphertext + len, &len)) return STS_ERR;

    if (len != AES_KEY_BYTES) return STS_ERR;

    EVP_CIPHER_CTX_free(ctx);
    return STS_OK;
}*/

int encrypt(byte *plaintext, byte *key, byte *ciphertext) {
    AES_KEY key_aes;
    AES_set_encrypt_key(key, 128, &key_aes);
    AES_encrypt(plaintext, ciphertext, &key_aes);
    return STS_OK;
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesOpenSSL_encrypt(JNIEnv *env, jobject javaThis,
                                                                           jbyteArray key_oct, jbyteArray to_encrypt) {
    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    byte out[AES_KEY_BYTES];
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

    if (STS_OK != encrypt(data, seed, out)) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Error AES encryption");
    }

    return as_byte_array(env, out, AES_KEY_BYTES);
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesOpenSSL_encryptgcm(JNIEnv *env, jobject javaThis,
                                                                           jbyteArray key_oct, jbyteArray aad, jbyteArray to_encrypt, jbyteArray iv) {
    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    byte iv_local[GCM_IV];
    byte aad_local[AAD_LOCAL];
    byte out[AES_KEY_BYTES + AES_KEY_BYTES];
    int len_key = (*env)->GetArrayLength(env, key_oct);
    int len_cont = (*env)->GetArrayLength(env, to_encrypt);
    int len_iv = (*env)->GetArrayLength(env, iv);
    int len_aad = (*env)->GetArrayLength(env, aad);

    if(len_key!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Key is not 16 bytes long");
    }

    if(len_cont!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Content is not 16 bytes long");
    }

    if(len_iv!=GCM_IV) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "IV is not 12 bytes long");
    }

    if(len_aad!=AAD_LOCAL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "ADD is not 4 bytes long");
    }


    (*env)->GetByteArrayRegion(env, key_oct, 0, AES_KEY_BYTES, (jbyte *) seed);
    (*env)->GetByteArrayRegion(env, to_encrypt, 0, AES_KEY_BYTES, (jbyte *) data);
    (*env)->GetByteArrayRegion(env, iv, 0, GCM_IV, (jbyte *) iv_local);
    (*env)->GetByteArrayRegion(env, aad, 0, AAD_LOCAL, (jbyte *) aad_local);

    if (AES_KEY_BYTES != gcm_encrypt(data, AES_KEY_BYTES, aad_local, AAD_LOCAL, seed, iv_local, GCM_IV, out, out + AES_KEY_BYTES)) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Error AES encryption");
    }

    return as_byte_array(env, out, AES_KEY_BYTES*2);
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesOpenSSL_decryptgcm(JNIEnv *env, jobject javaThis,
                                                                           jbyteArray key_oct, jbyteArray aad, jbyteArray ciphertext, jbyteArray iv) {
    byte data[AES_KEY_BYTES*2];
    byte seed[AES_KEY_BYTES];
    byte iv_local[GCM_IV];
    byte aad_local[AAD_LOCAL];
    byte out[AES_KEY_BYTES];
    int len_key = (*env)->GetArrayLength(env, key_oct);
    int len_data = (*env)->GetArrayLength(env, ciphertext);
    int len_iv = (*env)->GetArrayLength(env, iv);
    int len_aad = (*env)->GetArrayLength(env, aad);
    int err = STS_ERR;

    if(len_key!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Key is not 16 bytes long");
    }

    if(len_data!=AES_KEY_BYTES*2) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Ciphertext is not 16 bytes long");
    }

    if(len_iv!=GCM_IV) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "IV is not 12 bytes long");
    }

    if(len_aad!=AAD_LOCAL) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "ADD is not 4 bytes long");
    }


    (*env)->GetByteArrayRegion(env, key_oct, 0, AES_KEY_BYTES, (jbyte *) seed);
    (*env)->GetByteArrayRegion(env, ciphertext, 0, AES_KEY_BYTES*2, (jbyte *) data);
    (*env)->GetByteArrayRegion(env, iv, 0, GCM_IV, (jbyte *) iv_local);
    (*env)->GetByteArrayRegion(env, aad, 0, AAD_LOCAL, (jbyte *) aad_local);

    err = gcm_decrypt(data, AES_KEY_BYTES, aad_local, AAD_LOCAL, data + AES_KEY_BYTES, seed, iv_local, GCM_IV, out);
    if (-1 == err) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Authentication failed");
    }

    if (STS_ERR == err) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Decryption failed");
    }

    return as_byte_array(env, out, AES_KEY_BYTES);
}


jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesOpenSSL_apply(JNIEnv *env,
                                                                         jobject javaThis, jbyteArray key_oct, jint k) {
    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    byte out[AES_KEY_BYTES];

    int len_key = (*env)->GetArrayLength(env, key_oct);

    if(len_key!=AES_KEY_BYTES) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Key is not 16 bytes long");
    }

    (*env)->GetByteArrayRegion(env, key_oct, 0, AES_KEY_BYTES, (jbyte *) seed);
    memset(data, 0, AES_KEY_BYTES);
    for (int shift = 0; shift < 4; shift++) {
        data[15 - shift] = (byte) (k >> (shift * 8)) & 0xFF;
    }

    if (STS_OK != encrypt(data, seed, out)){
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Error AES encryption");
    }

    return as_byte_array(env, out, AES_KEY_BYTES);
}

jbyteArray Java_ch_ethz_dsg_timecrypt_crypto_prf_PRFAesOpenSSL_multiapply(JNIEnv *env,
                                                                              jobject javaThis, jbyteArray key_oct, jintArray k_path) {
    byte data[AES_KEY_BYTES];
    byte seed[AES_KEY_BYTES];
    byte out[AES_KEY_BYTES];

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

        if (STS_OK != encrypt(data, seed, out)) {
            (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "Error AES encryption");
        }
        memcpy(seed, out, AES_KEY_BYTES);
    }
    (*env)->ReleaseIntArrayElements(env, k_path, k_path_elements, 0);

    return as_byte_array(env, seed, AES_KEY_BYTES);
}

