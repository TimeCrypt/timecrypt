/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.prf;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class PRFAes implements IPRF {

    private Cipher cipher;

    public PRFAes (){
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    private byte[] AESBlockEncrypt(byte[] key, byte[] value) {
        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            return cipher.doFinal(value);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    @Override
    public byte[] apply(byte[] prfKey, byte[] input) {
        return AESBlockEncrypt(prfKey, input);
    }

    @Override
    public byte[] apply(byte[] prfKey, int input) {
        byte[] data = new byte[16];
        for (int shift = 0; shift < 4; shift++) {
            data[15 - shift] = (byte) ((input >> (shift * 8)) & 0xFF);
        }
        return apply(prfKey, data);
    }

    @Override
    public byte[] muliApply(byte[] prfKey, int[] inputs) {
        byte[] cur = prfKey;
        for (int k_iter : inputs) {
            cur = apply(cur, k_iter);
        }
        return cur;
    }
}
