/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.prf;

public interface IPRF {
    byte[] apply(byte[] prfKey, byte[] input);

    byte[] apply(byte[] prfKey, int input);

    byte[] muliApply(byte[] prfKey, int[] inputs);
}
