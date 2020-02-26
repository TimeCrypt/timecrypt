/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keyRegression;

import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;
import ch.ethz.dsg.timecrypt.crypto.sharing.IEnvelopeHandler;

import java.math.BigInteger;

public class ResolutionBasedRegression implements IKeyRegression {

    private IKeyRegression keyDer;
    private int keyJumpSize;
    private IEnvelopeHandler handler;

    public ResolutionBasedRegression(IKeyRegression reg, int keyJumpSize, IEnvelopeHandler handler) {
        this.keyDer = reg;
        this.keyJumpSize = keyJumpSize;
        this.handler = handler;
    }

    @Override
    public BigInteger getKey(long id, int keyBits) throws InvalidKeyDerivation {
        return KeyUtil.deriveKey(keyDer.getPRF(), getSeed(id), keyBits);
    }

    @Override
    public byte[] getSeed(long id) throws InvalidKeyDerivation {
        if (id % keyJumpSize != 0)
            throw new InvalidKeyDerivation("Resolution not supported");
        long resolutionID = id / keyJumpSize;
        byte[] seed = keyDer.getSeed(resolutionID);
        return handler.fetchAndDecryptEnvelopeWithID(id, seed);
    }

    @Override
    public byte[][] getSeeds(long from, long to) throws InvalidKeyDerivation {
        throw new InvalidKeyDerivation("Resolution not supported");
    }

    @Override
    public BigInteger[] getKeys(long from, long to, int keyBits) {
        throw new InvalidKeyDerivation("Resolution not supported");
    }

    @Override
    public BigInteger getKeySum(long from, long to, int keyBits) {
        throw new InvalidKeyDerivation("Resolution not supported");
    }

    @Override
    public IPRF getPRF() {
        return keyDer.getPRF();
    }
}
