/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

import ch.ethz.dsg.timecrypt.crypto.encryption.HEAC.HEACEncryptionBI;
import ch.ethz.dsg.timecrypt.crypto.encryption.HEAC.HEACEncryptionLong;
import ch.ethz.dsg.timecrypt.crypto.encryption.MACCheckFailed;
import ch.ethz.dsg.timecrypt.crypto.encryption.TimeCryptEncryptionBIPlus;
import ch.ethz.dsg.timecrypt.crypto.encryption.TimeCryptEncryptionLongPlus;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.HoMAC;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.HomomorphicMAC;
import ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC.IHoMAC;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.TreeKeyRegressionFactory;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;
import ch.ethz.dsg.timecrypt.crypto.prf.PRFAes;
import ch.ethz.dsg.timecrypt.crypto.prf.PRFFactory;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.Assert.*;


public class TestTimeCryptCrypto {

    private static final Random rand = new Random(1);

    private static BigInteger computeRange(IHoMAC mac, BigInteger[] vals, int from, int to) {
        BigInteger res = BigInteger.ZERO;
        for (int iter = from; iter <= to; iter++) {
            res = mac.aggregateMAC(res, vals[iter]);
        }
        return res;
    }

    private static BigInteger computeRange(IHoMAC mac, long[] vals, int from, int to) {
        BigInteger res = BigInteger.ZERO;
        for (int iter = from; iter <= to; iter++) {
            res = mac.aggregateMAC(res, BigInteger.valueOf(vals[iter]));
        }
        return res;
    }

    @Test
    public void testPRFapply_shouldBeEqual() {
        IPRF aes = new PRFAes();
        IPRF aesni = PRFFactory.getDefaultPRF();
        byte[] key = new byte[16];
        byte[] val = new byte[16];
        rand.nextBytes(key);
        rand.nextBytes(val);
        assertArrayEquals(aes.apply(key, val), aesni.apply(key, val));
    }

    @Test
    public void testPRFapplyInt_shouldBeEqual() {
        IPRF aes = new PRFAes();
        IPRF aesni = PRFFactory.getDefaultPRF();
        byte[] key = new byte[16];
        int val = rand.nextInt();
        assertArrayEquals(aes.apply(key, val), aesni.apply(key, val));
    }

    @Test
    public void testPRFMultiapplyInt_shouldBeEqual() {
        IPRF aes = new PRFAes();
        IPRF aesni = PRFFactory.getDefaultPRF();
        byte[] key = new byte[16];
        int[] val = new int[16];
        for (int i = 0; i < val.length; i++) val[i] = rand.nextInt();
        assertArrayEquals(aes.muliApply(key, val), aesni.muliApply(key, val));
    }

    @Test
    public void testTreeKeyRegression_shouldMatch() {
        int depth = 15;
        IPRF aes = new PRFAes();
        IPRF aesni = PRFFactory.getDefaultPRF();
        IKeyRegression regAES = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aes, depth);
        IKeyRegression regFAES = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(aesni, depth);
        for (int i = 0; i < 1000; i++) {
            BigInteger sAES = regAES.getKey(i, 64);
            BigInteger sAESF = regFAES.getKey(i, 64);
            assertEquals(0, sAES.compareTo(sAESF));
        }
    }

    @Test
    public void testTreeKeyRegressionGetKeys_shouldMatchWithSingleKey() {
        int depth = 15;
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, depth);
        BigInteger[] range = reg.getKeys(0, 1 << depth - 1, 64);
        for (int iter = 0; iter < range.length; iter++) {
            assertEquals(0, range[iter].compareTo(reg.getKey(iter, 64)));
        }
    }

    @Test
    public void testHEACEncryptionEncryptDecrypt_sumShouldMatchDecryption() {
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        HEACEncryptionBI cat = new HEACEncryptionBI(reg, 64);
        for (int i = 0; i < 10; i++) {
            int m0 = rand.nextInt() / 4, m1 = rand.nextInt() / 4;
            BigInteger c1, c2;
            c1 = cat.encrypt(BigInteger.valueOf(m0), 0);
            c2 = cat.encrypt(BigInteger.valueOf(m1), 1);
            int res = cat.decryptInt(c1.add(c2), 0, 1);
            assertEquals(res, m0 + m1);
        }
    }

    @Test
    public void testHEACEncryptionEncryptDecryptLargeSum_sumShouldMatchDecryption() {
        int depth = 20;
        int iter = 1000;
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, depth);
        HEACEncryptionBI cat = new HEACEncryptionBI(reg, 64);
        BigInteger sumPlain = BigInteger.ZERO, sumCi = BigInteger.ZERO;
        BigInteger[] numbers = new BigInteger[iter];
        BigInteger[] ciphertexts = new BigInteger[iter];
        for (int i = 0; i < iter; i++) {
            long val = rand.nextLong() / (iter * 2);
            numbers[i] = BigInteger.valueOf(val);
            ciphertexts[i] = cat.encrypt(numbers[i], i);
        }

        for (int i = 0; i < iter; i++) {
            sumPlain = sumPlain.add(numbers[i]);
            sumCi = sumCi.add(ciphertexts[i]).mod(cat.getM());
            BigInteger decryptedVal = BigInteger.valueOf(cat.decryptLong(sumCi, 0, i));
            assertTrue(sumPlain.compareTo(decryptedVal) == 0);
            assertEquals(decryptedVal, sumPlain);
        }

    }

    @Test
    public void testHEACLongEncryptionEncryptDecrypt_sumShouldMatchDecryption() {
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        HEACEncryptionLong cat = new HEACEncryptionLong(reg);
        for (int i = 0; i < 10; i++) {
            long m0 = rand.nextLong() / 4, m1 = rand.nextLong() / 4;
            long c1, c2;
            c1 = cat.encrypt(m0, 0);
            c2 = cat.encrypt(m1, 1);
            long res = cat.decrypt(c1 + c2, 0, 1);
            assertEquals(res, m0 + m1);
        }
    }

    @Test
    public void testHEACLongEncryptionEncryptDecryptLargeSum_sumShouldMatchDecryption() {
        int depth = 20;
        int iter = 1000;
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, depth);
        HEACEncryptionLong cat = new HEACEncryptionLong(reg);
        long sumPlain = 0, sumCi = 0;
        long[] numbers = new long[iter];
        long[] ciphertexts = new long[iter];
        for (int i = 0; i < iter; i++) {
            numbers[i] = rand.nextLong() / (iter * 2);
            ciphertexts[i] = cat.encrypt(numbers[i], i);
        }

        for (int i = 0; i < iter; i++) {
            sumPlain = sumPlain + numbers[i];
            sumCi = sumCi + ciphertexts[i];
            long decryptedVal = cat.decrypt(sumCi, 0, i);
            assertEquals(sumPlain, decryptedVal);
        }

    }

    @Test
    public void testHomorphicMacBasic_validCheck() {
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        HomomorphicMAC mac = new HomomorphicMAC(reg, macKey);

        BigInteger msg0 = BigInteger.valueOf(10);
        BigInteger msg1 = BigInteger.valueOf(12);

        BigInteger mac0 = mac.getMAC(msg0, 2);
        BigInteger mac1 = mac.getMAC(msg1, 3);

        BigInteger aggrMac = mac.aggregateMAC(mac0, mac1);

        assertTrue(mac.checkMAC(msg0.add(msg1), aggrMac, 2, 3));
    }

    @Test
    public void testHomorphicMacBasic_invalidCheck() {
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        IHoMAC mac = new HomomorphicMAC(reg, macKey);

        BigInteger msg0 = BigInteger.valueOf(10);
        BigInteger msg1 = BigInteger.valueOf(12);

        BigInteger mac0 = mac.getMAC(msg0, 2);
        BigInteger mac1 = mac.getMAC(msg1, 3);

        BigInteger aggrMac = mac.aggregateMAC(mac0, mac1).add(BigInteger.ONE);

        assertFalse(mac.checkMAC(msg0.add(msg1), aggrMac, 2, 3));
    }

    @Test
    public void testHomorphicMacSums_validCheck() {
        int numMessages = 100;
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        HomomorphicMAC mac = new HomomorphicMAC(reg, macKey);


        BigInteger[] msgs = new BigInteger[numMessages];
        BigInteger[] macs = new BigInteger[numMessages];
        for (int i = 0; i < numMessages; i++) {
            msgs[i] = BigInteger.valueOf(rand.nextInt());
            macs[i] = mac.getMAC(msgs[i], i);
        }

        for (int i = 0; i < numMessages; i++) {
            for (int j = i; j < numMessages; j++) {
                BigInteger aggrMac = computeRange(mac, macs, i, j);
                BigInteger aggrMsg = computeRange(mac, msgs, i, j);
                assertTrue(mac.checkMAC(aggrMsg, aggrMac, i, j));
            }
        }
    }

    @Test
    public void testHoMacBasic_validCheck() {
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        IHoMAC mac = new HoMAC(reg, macKey);

        BigInteger msg0 = BigInteger.valueOf(10);
        BigInteger msg1 = BigInteger.valueOf(12);

        BigInteger mac0 = mac.getMAC(msg0, 2);
        BigInteger mac1 = mac.getMAC(msg1, 3);

        BigInteger aggrMac = mac.aggregateMAC(mac0, mac1);

        assertTrue(mac.checkMAC(msg0.add(msg1), aggrMac, 2, 3));
    }

    @Test
    public void testHoMacBasic_invalidCheck() {
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        IHoMAC mac = new HoMAC(reg, macKey);

        BigInteger msg0 = BigInteger.valueOf(10);
        BigInteger msg1 = BigInteger.valueOf(12);

        BigInteger mac0 = mac.getMAC(msg0, 2);
        BigInteger mac1 = mac.getMAC(msg1, 3);

        BigInteger aggrMac = mac.aggregateMAC(mac0, mac1).add(BigInteger.ONE);

        assertFalse(mac.checkMAC(msg0.add(msg1), aggrMac, 2, 3));
    }

    @Test
    public void testHoMac_validCheck() {
        int numMessages = 100;
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        IHoMAC mac = new HoMAC(reg, macKey);


        BigInteger[] msgs = new BigInteger[numMessages];
        BigInteger[] macs = new BigInteger[numMessages];
        for (int i = 0; i < numMessages; i++) {
            msgs[i] = BigInteger.valueOf(rand.nextInt());
            macs[i] = mac.getMAC(msgs[i], i);
        }

        for (int i = 0; i < numMessages; i++) {
            for (int j = i; j < numMessages; j++) {
                BigInteger aggrMac = computeRange(mac, macs, i, j);
                BigInteger aggrMsg = computeRange(mac, msgs, i, j);
                assertTrue(mac.checkMAC(aggrMsg, aggrMac, i, j));
            }
        }
    }

    @Test
    public void testTimeCryptEncryptionLongPlus_validCheckandDecryptedMatch() throws MACCheckFailed {
        int numMessages = 100;
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);

        TimeCryptEncryptionLongPlus enc = new TimeCryptEncryptionLongPlus(reg, macKey);

        long[] msgs = new long[numMessages];
        TimeCryptEncryptionLongPlus.TCAuthLongCiphertext[] encs =
                new TimeCryptEncryptionLongPlus.TCAuthLongCiphertext[numMessages];
        for (int i = 0; i < numMessages; i++) {
            msgs[i] = rand.nextInt();
            encs[i] = enc.encryptMetadata(msgs[i], i, 0);
        }

        long aggrMSG = 0;
        TimeCryptEncryptionLongPlus.TCAuthLongCiphertext merge =
                new TimeCryptEncryptionLongPlus.TCAuthLongCiphertext(0, BigInteger.ZERO);
        for (int i = 0; i < msgs.length; i++) {
            aggrMSG += msgs[i];
            merge.addMerge(encs[i]);
            long res = enc.decryptMetadata(merge, 0, i, 0);
            assertEquals(aggrMSG, res);
        }
    }

    @Test(expected = MACCheckFailed.class)
    public void testTimeCryptEncryptionLong_macCheckShouldFail() throws MACCheckFailed {
        BigInteger macKey = new BigInteger(128, rand).mod(HomomorphicMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);
        TimeCryptEncryptionLongPlus enc = new TimeCryptEncryptionLongPlus(reg, macKey);
        TimeCryptEncryptionLongPlus.TCAuthLongCiphertext ciph = enc.encryptMetadata(1, 0, 0);
        ciph.ciphertext += 1;
        TimeCryptEncryptionLongPlus.TCAuthLongCiphertext merge =
                new TimeCryptEncryptionLongPlus.TCAuthLongCiphertext(0, BigInteger.ZERO);
        enc.decryptMetadata(ciph, 0, 0, 0);
    }

    @Test
    public void testTimeCryptEncryptionBi_validCheckandDecryptedMatch() throws MACCheckFailed {
        int numMessages = 100;
        BigInteger macKey = new BigInteger(128, rand).mod(HoMAC.PRIME);
        IPRF func = PRFFactory.getDefaultPRF();
        IKeyRegression reg = TreeKeyRegressionFactory.getNewDefaultTESTKeyRegression(func, 20);

        TimeCryptEncryptionBIPlus enc = new TimeCryptEncryptionBIPlus(reg, macKey);

        long[] msgs = new long[numMessages];
        TimeCryptEncryptionBIPlus.TCAuthBICiphertext[] encs =
                new TimeCryptEncryptionBIPlus.TCAuthBICiphertext[numMessages];
        for (int i = 0; i < numMessages; i++) {
            msgs[i] = rand.nextInt();
            encs[i] = enc.encryptMetadata(BigInteger.valueOf(msgs[i]), i, 0);
        }

        long aggrMSG = 0;
        TimeCryptEncryptionBIPlus.TCAuthBICiphertext merge =
                new TimeCryptEncryptionBIPlus.TCAuthBICiphertext(BigInteger.ZERO, BigInteger.ZERO);
        for (int i = 0; i < msgs.length; i++) {
            aggrMSG += msgs[i];
            merge.addMerge(encs[i]);
            long res = enc.decryptMetadataLong(merge, 0, i, 0);
            assertEquals(aggrMSG, res);
        }
    }
}
