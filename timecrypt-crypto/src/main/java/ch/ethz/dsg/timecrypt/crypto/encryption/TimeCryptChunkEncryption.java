/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class TimeCryptChunkEncryption {

    private static final int NONCE_SIZE = 12;

    private static SecureRandom randomSecureRandom = new SecureRandom();

    public static byte[] encryptAESGcm(byte[] key, byte[] data) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            ShortBufferException, IllegalBlockSizeException {
        return encryptAESGcm(key,data, data.length);
    }

    public static byte[] encryptAESGcm(byte[] key, byte[] data, int lenData) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            ShortBufferException, IllegalBlockSizeException {
        SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] ivBytes = new byte[NONCE_SIZE];
        randomSecureRandom.nextBytes(ivBytes);

        GCMParameterSpec gcm = new GCMParameterSpec(cipher.getBlockSize() * 8, ivBytes);
        byte[] finalResult = new byte[ivBytes.length + gcm.getTLen() / 8 + lenData];

        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, gcm);
        System.arraycopy(ivBytes, 0, finalResult, 0, ivBytes.length);
        cipher.doFinal(data, 0, lenData, finalResult, ivBytes.length);
        return finalResult;
    }

    public static byte[] decryptAESGcm(byte[] key, byte[] encData) throws InvalidKeyException, BadPaddingException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException {
        SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = null;
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] ivByte = new byte[NONCE_SIZE];
            System.arraycopy(encData, 0, ivByte, 0, ivByte.length);

            GCMParameterSpec gcmParams = new GCMParameterSpec(cipher.getBlockSize() * 8, ivByte);
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, gcmParams);
            return cipher.doFinal(encData, ivByte.length, encData.length - ivByte.length);
    }

}
