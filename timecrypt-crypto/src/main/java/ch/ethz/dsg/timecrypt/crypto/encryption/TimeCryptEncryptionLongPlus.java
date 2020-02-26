/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption;

import ch.ethz.dsg.timecrypt.crypto.encryption.HEAC.HEACEncryptionLong;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.HoMAC;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.IHoMAC;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;

import java.math.BigInteger;

public class TimeCryptEncryptionLongPlus {

    private HEACEncryptionLong enc;
    private IHoMAC mac;
    private IKeyRegression reg;

    public TimeCryptEncryptionLongPlus(IKeyRegression reg, BigInteger macKey) {
        this.enc = new HEACEncryptionLong(reg);
        this.mac = new HoMAC(reg, macKey);
        this.reg = reg;
    }

    private TCAuthLongCiphertext encrypt(long msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        long ciphertext = enc.encrypt(msg,
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID1, true, metadataID),
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID2, true, metadataID));
        BigInteger authTag = mac.getMAC(BigInteger.valueOf(msg),
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, false, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, false, metadataID, mac.getNumFieldBits()));
        return new TCAuthLongCiphertext(ciphertext, authTag);
    }

    private long decrypt(TCAuthLongCiphertext msg, byte[] seedForID1, byte[] seedForID2, long metadataID) throws MACCheckFailed {
        long plain = enc.decryptWithKeys(msg.ciphertext,
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID1, true, metadataID),
                KeyUtil.deriveKeyLong(reg.getPRF(), seedForID2, true, metadataID));
        boolean ok = mac.checkMAC(BigInteger.valueOf(plain), msg.authCode,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, false, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, false, metadataID, mac.getNumFieldBits()));
        if (!ok) {
            throw new MACCheckFailed("Check failed", msg.authCode);
        }
        return plain;
    }

    public TCAuthLongCiphertext encryptMetadata(long msg, long timeID, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        return encrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public long decryptMetadata(TCAuthLongCiphertext msg, long timeIDFrom, long timeIDTo, long metadataID) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        return decrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public TCAuthLongCiphertext[] batchEncryptMetadata(long[] msgs, long timeID, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        TCAuthLongCiphertext[] out = new TCAuthLongCiphertext[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = encrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public long[] batchDecryptMetadata(TCAuthLongCiphertext[] msgs, long timeIDFrom, long timeIDTo, long[] metadataIDs) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        long[] out = new long[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = decrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }


    public static class TCAuthLongCiphertext {
        public long ciphertext;
        public BigInteger authCode;

        public TCAuthLongCiphertext(long ciphertext, BigInteger authCode) {
            this.ciphertext = ciphertext;
            this.authCode = authCode;
        }

        public long getCiphertext() {
            return ciphertext;
        }

        public BigInteger getAuthCode() {
            return authCode;
        }

        public TCAuthLongCiphertext add(TCAuthLongCiphertext other) {
            // Would be possible to add MAC modulo
            return new TCAuthLongCiphertext(other.ciphertext + this.ciphertext,
                    other.authCode.add(this.authCode));
        }

        public void addMerge(TCAuthLongCiphertext other) {
            // Would be possible to add MAC modulo
            this.ciphertext += other.ciphertext;
            this.authCode = this.authCode.add(other.authCode);
        }
    }
}
