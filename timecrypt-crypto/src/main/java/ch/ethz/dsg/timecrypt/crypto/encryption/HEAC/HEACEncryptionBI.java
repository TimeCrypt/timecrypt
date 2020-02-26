/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption.HEAC;

import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;

import java.math.BigInteger;

/**
 * Implements the TimeCrypts additive homomorphic scheme
 * Idea:
 * c_0 = m_0 + k_0 - k_1 mod M
 * c_1 = m_1 + k_1 - k_2 mod M
 * c_2 = m_2 + k_2 - k_3 mod M
 */
public class HEACEncryptionBI {

    private IKeyRegression keyRegression;
    private BigInteger M;

    /**
     * New Castelluccia scheme
     *
     * @param keyRegression a key regression object for deriving the keys
     * @param m             the prime for the modulo computation Z_m
     */
    public HEACEncryptionBI(IKeyRegression keyRegression, BigInteger m) {
        this.keyRegression = keyRegression;
        M = m;
    }

    /**
     * New Castelluccia scheme with ranomd modulo prime
     *
     * @param keyRegression a key regression object for deriving the keys
     * @param mBits         the number of bits of the modulo prime
     */
    public HEACEncryptionBI(IKeyRegression keyRegression, int mBits) {
        this.keyRegression = keyRegression;
        M = BigInteger.ONE.shiftLeft(mBits);
    }

    public BigInteger getM() {
        return this.M;
    }

    public IKeyRegression getKeyRegression() {
        return keyRegression;
    }

    public int getNumMBits() {
        return this.M.bitLength() - 1;
    }

    public BigInteger encrypt(BigInteger msg, BigInteger key1, BigInteger key2) {
        return msg.add(key1).subtract(key2).mod(M);
    }

    public BigInteger encrypt(BigInteger msg, long id) {
        return encrypt(msg, keyRegression.getKey(id, getNumMBits()), keyRegression.getKey(id + 1, getNumMBits()));
    }


    private BigInteger adaptForNegNumber(BigInteger num, BigInteger maxPosNum) {
        if (num.compareTo(maxPosNum) > 0)
            return num.subtract(M);
        return num;
    }

    public BigInteger decrypt(BigInteger ciphertext, BigInteger key1, BigInteger key2) {
        return ciphertext.subtract(key1).add(key2).mod(M);
    }

    public long decryptLong(BigInteger ciphertext, BigInteger key1, BigInteger key2) {
        BigInteger plain = decrypt(ciphertext, key1, key2);
        return adaptForNegNumber(plain, BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public int decryptInt(BigInteger ciphertext, BigInteger key1, BigInteger key2) {
        BigInteger plain = decrypt(ciphertext, key1, key2);
        return adaptForNegNumber(plain, BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    public BigInteger decrypt(BigInteger ciphertext, long msgID) {
        return decrypt(ciphertext, keyRegression.getKey(msgID, getNumMBits()), keyRegression.getKey(msgID + 1, getNumMBits()));
    }

    public long decryptLong(BigInteger ciphertext, long msgID) {
        BigInteger plain = decrypt(ciphertext, msgID);
        return adaptForNegNumber(plain, BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public int decryptInt(BigInteger ciphertext, long msgID) {
        BigInteger plain = decrypt(ciphertext, msgID);
        return adaptForNegNumber(plain, BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    public BigInteger decrypt(BigInteger ciphertext, long msgFrom, long msgTo) {
        return (ciphertext.subtract(keyRegression.getKey(msgFrom, getNumMBits())).add(keyRegression.getKey(msgTo + 1, getNumMBits()))).mod(M);
    }

    public long decryptLong(BigInteger ciphertext, long msgID, long msgTo) {
        BigInteger plain = decrypt(ciphertext, msgID, msgTo);
        return adaptToLong(plain);
    }

    public int decryptInt(BigInteger ciphertext, long msgID, long msgTo) {
        BigInteger plain = decrypt(ciphertext, msgID, msgTo);
        return adaptForNegNumber(plain, BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }

    public BigInteger add(BigInteger c1, BigInteger c2) {
        return c1.add(c2).mod(M);
    }

    public long adaptToLong(BigInteger value) {
        return adaptForNegNumber(value, BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

}
