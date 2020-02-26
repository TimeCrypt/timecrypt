/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.encryption;

import java.math.BigInteger;

public class MACCheckFailed extends Exception {
    private BigInteger tag;
    private BigInteger match;

    public MACCheckFailed(String message, BigInteger tag) {
        super(message);
        this.tag = tag;
    }
}
