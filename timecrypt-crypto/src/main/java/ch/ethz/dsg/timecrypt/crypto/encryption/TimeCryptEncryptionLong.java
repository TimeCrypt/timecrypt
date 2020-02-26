/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption;


import ch.ethz.dsg.timecrypt.crypto.encryption.HEAC.HEACEncryptionLong;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;

public class TimeCryptEncryptionLong {

    private HEACEncryptionLong enc;
    private IKeyRegression reg;

    public TimeCryptEncryptionLong(IKeyRegression reg) {
        this.enc = new HEACEncryptionLong(reg);
        this.reg = reg;
    }

    private long encrypt(long msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        return enc.encrypt(msg,
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID1, true, metadataID),
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID2, true, metadataID));
    }

    private long decrypt(long msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        return enc.decryptWithKeys(msg,
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID1, true, metadataID),
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID2, true, metadataID));
    }

    public long[] batchEncryptMetadata(long[] msgs, long timeID, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        long[] out = new long[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = encrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public long[] batchDecrypttMetadata(long[] msgs, long timeID, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        long[] out = new long[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = decrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public long encryptMetadata(long msg, long timeID, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        return encrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public long decryptMetadata(long msg, long timeIDFrom, long timeIDTo, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        return decrypt(msg, seedForID1, seedForID2, metadataID);
    }

}
