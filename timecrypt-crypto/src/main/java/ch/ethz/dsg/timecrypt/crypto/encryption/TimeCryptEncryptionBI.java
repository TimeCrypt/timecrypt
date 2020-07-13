/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption;

import ch.ethz.dsg.timecrypt.crypto.encryption.HEAC.HEACEncryptionBI;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.CachedKeys;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;

import java.math.BigInteger;

public class TimeCryptEncryptionBI {
    private HEACEncryptionBI enc;
    private IKeyRegression reg;

    public TimeCryptEncryptionBI(IKeyRegression reg, int numBits) {
        this.enc = new HEACEncryptionBI(reg, numBits);
        this.reg = reg;
    }

    public TimeCryptEncryptionBI(IKeyRegression reg) {
        this(reg, 128);
    }

    private BigInteger encrypt(BigInteger msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        return enc.encrypt(msg,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, enc.getNumMBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, enc.getNumMBits()));
    }

    private BigInteger decrypt(BigInteger msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        return enc.decrypt(msg,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, enc.getNumMBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, enc.getNumMBits()));
    }

    private long decryptLong(BigInteger msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        return enc.decryptLong(msg,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, enc.getNumMBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, enc.getNumMBits()));
    }

    public BigInteger encryptMetadata(BigInteger msg, long timeID, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        return encrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public BigInteger encryptMetadata(BigInteger msg, long timeID, long metadataID, CachedKeys keys) {
        if (!keys.containsKeys()) {
            keys.setK1(reg.getSeed(timeID));
            keys.setK2(reg.getSeed(timeID + 1));
        }
        return encrypt(msg, keys.getK1(), keys.getK2(), metadataID);
    }

    public BigInteger decryptMetadata(BigInteger msg, long timeIDFrom, long timeIDTo, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        return decrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public BigInteger[] batchEncryptMetadata(BigInteger[] msgs, long timeID, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        BigInteger[] out = new BigInteger[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = encrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public BigInteger[] batchDecryptMetadata(BigInteger[] msgs, long timeIDFrom, long timeIDTo, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        BigInteger[] out = new BigInteger[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = decrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public long decryptMetadataLong(BigInteger msg, long timeIDFrom, long timeIDTo, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        return decryptLong(msg, seedForID1, seedForID2, metadataID);
    }

    public long decryptMetadataLong(BigInteger msg, long timeIDFrom, long timeIDTo, long metadataID, CachedKeys cachedKeys) {
        if (!cachedKeys.containsKeys()) {
            cachedKeys.setK1(reg.getSeed(timeIDFrom));
            cachedKeys.setK2(reg.getSeed(timeIDTo + 1));
        }
        return decryptLong(msg, cachedKeys.getK1(), cachedKeys.getK2(), metadataID);
    }

    public long[] batchDecryptMetadataLong(BigInteger[] msgs, long timeIDFrom, long timeIDTo, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        long[] out = new long[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = decryptLong(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public int decryptMetadataInt(BigInteger msg, long timeIDFrom, long timeIDTo, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        return enc.decryptInt(msg,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, enc.getNumMBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, enc.getNumMBits()));
    }
}
