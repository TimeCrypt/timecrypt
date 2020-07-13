/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC;

import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;

import java.math.BigInteger;
import java.security.SecureRandom;

public class HoMAC implements IHoMAC {

    public static final BigInteger PRIME = new BigInteger("340282366920938463463374607431768211297");

    private IKeyRegression keyRegression;
    private BigInteger prime;
    private BigInteger macKey;
    private BigInteger macKeyInv;

    public HoMAC(IKeyRegression keyRegression, BigInteger macKey, BigInteger prime) {
        this.keyRegression = keyRegression;
        this.prime = prime;
        this.macKey = macKey.mod(prime);
        this.macKeyInv = this.macKey.modInverse(prime);
    }

    public HoMAC(IKeyRegression keyRegression, SecureRandom rand, BigInteger prime) {
        this(keyRegression, new BigInteger(prime.bitLength(), rand), prime);
    }

    public HoMAC(IKeyRegression keyRegression, BigInteger macKey) {
        this(keyRegression, macKey, PRIME);
    }

    public HoMAC(IKeyRegression keyRegression, SecureRandom random) {
        this(keyRegression, random, PRIME);
    }

    @Override
    public int getNumFieldBits() {
        return prime.bitLength();
    }

    @Override
    public BigInteger getPrime() {
        return prime;
    }

    @Override
    public BigInteger getMACKey() {
        return this.macKey;
    }

    @Override
    public BigInteger getMAC(BigInteger msg, long id) {
        return getMAC(msg, keyRegression.getKey(id, getNumFieldBits()), keyRegression.getKey(id + 1, getNumFieldBits()));
    }

    @Override
    public BigInteger getMAC(BigInteger msg, BigInteger key1, BigInteger key2) {
        BigInteger key = key1.subtract(key2);
        return key.subtract(msg).multiply(macKeyInv).mod(prime);
    }

    @Override
    public boolean checkMAC(BigInteger msg, BigInteger mac, long msgID, long msgTo) {
        mac = mac.mod(PRIME);
        return checkMAC(msg, mac, keyRegression.getKey(msgID, getNumFieldBits()), keyRegression.getKey(msgTo + 1, getNumFieldBits()));
    }

    @Override
    public boolean checkMAC(BigInteger msg, BigInteger mac, BigInteger key1, BigInteger key2) {
        BigInteger key = key1.subtract(key2).mod(prime);
        BigInteger comp = mac.multiply(macKey).add(msg).mod(prime);
        return key.compareTo(comp) == 0;
    }

    @Override
    public IKeyRegression getKeyRegression() {
        return this.keyRegression;
    }

    @Override
    public BigInteger aggregateMAC(BigInteger mac1, BigInteger mac2) {
        return mac1.add(mac2).mod(prime);
    }
}
