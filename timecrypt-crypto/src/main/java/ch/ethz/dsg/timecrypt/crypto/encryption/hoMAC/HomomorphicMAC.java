/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC;

import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;

import java.math.BigInteger;

public class HomomorphicMAC implements IHoMAC {

    public static final BigInteger PRIME = new BigInteger("340282366920938463463374607431768211297");

    private IKeyRegression keyRegression;
    private BigInteger prime;
    private BigInteger macKey;

    public HomomorphicMAC(IKeyRegression keyRegression, BigInteger macKey, BigInteger prime) {
        this.keyRegression = keyRegression;
        this.prime = prime;
        this.macKey = macKey;
    }

    public HomomorphicMAC(IKeyRegression keyRegression, BigInteger macKey) {
        this(keyRegression, macKey, PRIME);
    }

    public int getNumFieldBits() {
        return prime.bitLength();
    }

    public BigInteger getPrime() {
        return prime;
    }

    @Override
    public BigInteger getMACKey() {
        return this.macKey;
    }

    public BigInteger getMAC(BigInteger msg, long id) {
        return getMAC(msg, keyRegression.getKey(id, getNumFieldBits()), keyRegression.getKey(id + 1, getNumFieldBits()));
    }

    @Override
    public BigInteger getMAC(BigInteger msg, BigInteger key1, BigInteger key2) {
        BigInteger mul = msg.multiply(macKey).mod(prime);
        return mul.add(key1).subtract(key2).mod(prime);
    }

    public boolean checkMAC(BigInteger msg, BigInteger mac, long msgID, long msgTo) {
        return checkMAC(msg, mac, keyRegression.getKey(msgID, getNumFieldBits()), keyRegression.getKey(msgTo + 1, getNumFieldBits()));
    }

    @Override
    public boolean checkMAC(BigInteger msg, BigInteger mac, BigInteger key1, BigInteger key2) {
        BigInteger tmpMac = mac;
        if (mac.compareTo(PRIME) >= 0)
            tmpMac = mac.mod(PRIME);
        BigInteger mul = msg.multiply(macKey).mod(prime);
        BigInteger tmp = mul.add(key1).subtract(key2).mod(prime);
        return tmpMac.compareTo(tmp) == 0;
    }

    @Override
    public IKeyRegression getKeyRegression() {
        return this.keyRegression;
    }

    public BigInteger aggregateMAC(BigInteger mac1, BigInteger mac2) {
        return mac1.add(mac2).mod(prime);
    }
}
