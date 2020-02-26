/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keyRegression;

import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;

import java.math.BigInteger;

public interface IKeyRegression {

    BigInteger getKey(long id, int keyBits);

    byte[] getSeed(long id);

    byte[][] getSeeds(long from, long to);

    BigInteger[] getKeys(long from, long to, int keyBits);

    BigInteger getKeySum(long from, long to, int keyBits);

    IPRF getPRF();

}
