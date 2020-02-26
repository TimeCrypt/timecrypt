/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption.HEAC;

import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;

public class HEACEncryptionLong {

    private IKeyRegression keyRegression;


    public HEACEncryptionLong(IKeyRegression keyRegression) {
        this.keyRegression = keyRegression;
    }


    public int getNumMBits() {
        return 64;
    }

    private long deriveKey(long id) {
        return KeyUtil.deriveKeyLong(keyRegression.getPRF(), keyRegression.getSeed(id));
    }

    public long encrypt(long msg, long key1, long key2) {
        return msg + key1 - key2;
    }

    public long encrypt(long msg, long id) {
        return encrypt(msg, deriveKey(id), deriveKey(id + 1));
    }

    public long decrypt(long ciphertext, long msgID) {
        return ciphertext - deriveKey(msgID) + deriveKey(msgID + 1);
    }

    public long decryptWithKeys(long ciphertext, long key1, long key2) {
        return ciphertext - key1 + key2;
    }

    public long decrypt(long ciphertext, long msgFrom, long msgTo) {
        return decryptWithKeys(ciphertext, deriveKey(msgFrom), deriveKey(msgTo + 1));
    }

}
