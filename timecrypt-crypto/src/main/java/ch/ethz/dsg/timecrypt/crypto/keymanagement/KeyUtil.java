/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keymanagement;

import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Util for key derivations for metadata encrytion and metadata MAC keys.
 */
public class KeyUtil {

    private static final byte[] macDefault = new byte[16];


    public static byte[] createInputForEncKeyDerivation(long id) {
        byte[] encDefault = new byte[16];
        for (int i = 0; i < encDefault.length / 2; i++) {
            encDefault[i] |= 0xFF;
        }
        for (int shift = 0; shift < 8; shift++) {
            encDefault[15 - shift] = (byte) ((id >> (shift * 8)) & 0xFF);
        }
        return encDefault;
    }

    public static byte[] createInputForMacKeyDerivation(long id) {
        byte[] macDefault = new byte[16];
        for (int i = macDefault.length / 2; i < macDefault.length; i++) {
            macDefault[i] |= 0xFF;
        }
        for (int shift = 0; shift < 8; shift++) {
            macDefault[7 - shift] = (byte) ((id >> (shift * 8)) & 0xFF);
        }
        return macDefault;
    }

    public static BigInteger deriveKey(IPRF prf, byte[] seed, boolean forEnc, long metaID, int bits) {
        if (forEnc)
            return deriveKey(prf, seed, createInputForEncKeyDerivation(metaID), bits);
        return deriveKey(prf, seed, createInputForMacKeyDerivation(metaID), bits);
    }

    public static long deriveKeyLong(IPRF prf, byte[] seed, boolean forEnc, long metaID) {
        if (forEnc)
            return deriveKeyLong(prf, seed, createInputForEncKeyDerivation(metaID));
        return deriveKeyLong(prf, seed, createInputForMacKeyDerivation(metaID));
    }

    public static BigInteger deriveKey(IPRF prf, byte[] seed, byte[] input, int bits) {
        byte[] key = prf.apply(seed, input);
        if ((key.length * 8) % bits != 0) {
            throw new IllegalArgumentException("Key cannot be created with that seed");
        }
        int numPartitions = key.length * 8 / bits;

        BigInteger curInt;
        byte[] partition = new byte[bits / 8];
        if (numPartitions < 2) {
            return new BigInteger(1, key);
        } else {
            System.arraycopy(key, 0, partition, 0, partition.length);
            curInt = new BigInteger(1, partition);
        }
        for (int i = 1; i < numPartitions; i++) {
            partition = new byte[bits / 8];
            System.arraycopy(key, i * partition.length, partition, 0, partition.length);
            curInt = curInt.xor(new BigInteger(1, partition));
        }
        return curInt;
    }

    public static BigInteger deriveKey(IPRF prf, byte[] seed, int bits) {
        byte[] tmp = new byte[16];
        for (int i = 0; i < tmp.length; i++)
            tmp[i] = (byte) (tmp[i] | 0xFF);
        return deriveKey(prf, seed, tmp, bits);
    }

    private static long bytesToLong(byte[] in) {
        ByteBuffer buffer = ByteBuffer.allocate(in.length);
        buffer.put(in);
        buffer.flip();
        return buffer.getLong();
    }

    public static long deriveKeyLong(IPRF prf, byte[] seed) {
        byte[] tmp = new byte[16];
        for (int i = 0; i < tmp.length; i++)
            tmp[i] = (byte) (tmp[i] | 0xFF);
        return deriveKeyLong(prf, seed, tmp);
    }

    public static long deriveKeyLong(IPRF prf, byte[] seed, byte[] input) {
        byte[] key = prf.apply(seed, input);
        byte[] out = new byte[8];
        System.arraycopy(key, 0, out, 0, out.length);
        for (int i = out.length; i < key.length; i++) {
            out[i - out.length] ^= key[i];
        }
        return bytesToLong(out);
    }

    public BigInteger generateMACKey(int numBits, BigInteger fieldPrime, SecureRandom random) {
        return new BigInteger(numBits, random).mod(fieldPrime);
    }

    public byte[] generateKey(int numBytes, SecureRandom random) {
        byte[] key = new byte[numBytes];
        random.nextBytes(key);
        return key;
    }
}
