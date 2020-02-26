/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption.hoMAC;

import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;

import java.math.BigInteger;

public interface IHoMAC {
    int getNumFieldBits();

    BigInteger getPrime();

    BigInteger getMACKey();

    BigInteger getMAC(BigInteger msg, long id);

    BigInteger getMAC(BigInteger msg, BigInteger key1, BigInteger key2);

    boolean checkMAC(BigInteger msg, BigInteger mac, long msgID, long msgTo);

    boolean checkMAC(BigInteger msg, BigInteger mac, BigInteger key1, BigInteger key2);

    IKeyRegression getKeyRegression();

    BigInteger aggregateMAC(BigInteger mac1, BigInteger mac2);
}
