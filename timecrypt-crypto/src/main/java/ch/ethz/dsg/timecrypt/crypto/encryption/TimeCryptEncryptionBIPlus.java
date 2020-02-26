/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption;

import ch.ethz.dsg.timecrypt.crypto.encryption.HEAC.HEACEncryptionBI;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.HoMAC;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.IHoMAC;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;

import java.math.BigInteger;

public class TimeCryptEncryptionBIPlus {
    private HEACEncryptionBI enc;
    private IHoMAC mac;
    private IKeyRegression reg;

    public TimeCryptEncryptionBIPlus(IKeyRegression reg, BigInteger macKey, int numBits) {
        this.enc = new HEACEncryptionBI(reg, numBits);
        this.mac = new HoMAC(reg, macKey);
        this.reg = reg;
    }

    public TimeCryptEncryptionBIPlus(IKeyRegression reg, BigInteger macKey) {
        this.enc = new HEACEncryptionBI(reg, HoMAC.PRIME);
        this.mac = new HoMAC(reg, macKey);
        this.reg = reg;
    }

    private TCAuthBICiphertext encrypt(BigInteger msg, byte[] seedForID1, byte[] seedForID2, long metadataID) {
        BigInteger ciphertext = enc.encrypt(msg,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, mac.getNumFieldBits()));
        BigInteger authTag = mac.getMAC(ciphertext,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, false, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, false, metadataID, mac.getNumFieldBits()));
        return new TCAuthBICiphertext(ciphertext, authTag);
    }

    private BigInteger decrypt(TCAuthBICiphertext msg, byte[] seedForID1, byte[] seedForID2, long metadataID) throws MACCheckFailed {
        checkMAC(msg.ciphertext, msg.authCode, seedForID1, seedForID2, metadataID);
        return enc.decrypt(msg.ciphertext,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, mac.getNumFieldBits()));
    }

    private long decryptLong(TCAuthBICiphertext msg, byte[] seedForID1, byte[] seedForID2, long metadataID) throws MACCheckFailed {
        checkMAC(msg.ciphertext, msg.authCode, seedForID1, seedForID2, metadataID);
        return enc.decryptLong(msg.ciphertext,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, mac.getNumFieldBits()));
    }

    public TCAuthBICiphertext encryptMetadata(BigInteger msg, long timeID, long metadataID) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        return encrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public TCAuthBICiphertext[] batchEncryptMetadata(BigInteger[] msgs, long timeID, long[] metadataIDs) {
        byte[] seedForID1 = reg.getSeed(timeID);
        byte[] seedForID2 = reg.getSeed(timeID + 1);
        TCAuthBICiphertext[] out = new TCAuthBICiphertext[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = encrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    private void checkMAC(BigInteger ciph, BigInteger auth, byte[] seedForID1, byte[] seedForID2, long metadataID) throws MACCheckFailed {
        boolean ok = mac.checkMAC(ciph, auth,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, false, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, false, metadataID, mac.getNumFieldBits()));
        if (!ok) {
            throw new MACCheckFailed("Check failed", auth);
        }
    }

    public BigInteger decryptMetadata(TCAuthBICiphertext msg, long timeIDFrom, long timeIDTo, long metadataID) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        return decrypt(msg, seedForID1, seedForID2, metadataID);
    }

    public BigInteger[] batchDecryptMetadata(TCAuthBICiphertext[] msgs, long timeIDFrom, long timeIDTo, long[] metadataIDs) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        BigInteger[] out = new BigInteger[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = decrypt(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public long decryptMetadataLong(TCAuthBICiphertext msg, long timeIDFrom, long timeIDTo, long metadataID) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        checkMAC(msg.ciphertext, msg.authCode, seedForID1, seedForID2, metadataID);
        return decryptLong(msg, seedForID1, seedForID2, metadataID);
    }

    public long[] batchDecryptMetadataLong(TCAuthBICiphertext[] msgs, long timeIDFrom, long timeIDTo, long[] metadataIDs) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        long[] out = new long[msgs.length];
        for (int idx = 0; idx < msgs.length; idx++) {
            out[idx] = decryptLong(msgs[idx], seedForID1, seedForID2, metadataIDs[idx]);
        }
        return out;
    }

    public int decryptMetadataInt(TCAuthBICiphertext msg, long timeIDFrom, long timeIDTo, long metadataID) throws MACCheckFailed {
        byte[] seedForID1 = reg.getSeed(timeIDFrom);
        byte[] seedForID2 = reg.getSeed(timeIDTo + 1);
        checkMAC(msg.ciphertext, msg.authCode, seedForID1, seedForID2, metadataID);
        return enc.decryptInt(msg.ciphertext,
                KeyUtil.deriveKey(reg.getPRF(), seedForID1, true, metadataID, mac.getNumFieldBits()),
                KeyUtil.deriveKey(reg.getPRF(), seedForID2, true, metadataID, mac.getNumFieldBits()));
    }


    public static class TCAuthBICiphertext {
        public BigInteger ciphertext;
        public BigInteger authCode;

        public TCAuthBICiphertext(BigInteger ciphertext, BigInteger authCode) {
            this.ciphertext = ciphertext;
            this.authCode = authCode;
        }

        public BigInteger getCiphertext() {
            return ciphertext;
        }

        public BigInteger getAuthCode() {
            return authCode;
        }

        public TCAuthBICiphertext add(TCAuthBICiphertext other) {
            // Would be possible to add MAC and ENC modulo
            return new TCAuthBICiphertext(other.ciphertext.add(this.ciphertext),
                    other.authCode.add(this.authCode));
        }

        public void addMerge(TCAuthBICiphertext other) {
            // Would be possible to add MAC and ENC modulo
            this.ciphertext = this.ciphertext.add(other.ciphertext);
            this.authCode = this.authCode.add(other.authCode);
        }
    }
}
