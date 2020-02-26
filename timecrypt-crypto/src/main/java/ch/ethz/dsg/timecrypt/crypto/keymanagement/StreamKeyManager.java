/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keymanagement;


import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.TreeKeyRegressionFactory;

import java.math.BigInteger;

/**
 * Class for handling all the keys associated with a stream as well as receiving them.
 */
public class StreamKeyManager {

    private final byte[] chunkKeystreamSeedNode;
    private final byte[] metadataEncryptionKey;
    private final byte[] macKey;
    private final byte[] sharingKeystreamMasterKey;
    private final int chunkKeystreamDepth;

    public StreamKeyManager(byte[] streamMasterKey, int chunkKeystreamDepth) {
        this.chunkKeystreamDepth = chunkKeystreamDepth;
        IKeyRegression keyDerivationTree = TreeKeyRegressionFactory.getNewDefaultKeyRegression(streamMasterKey, 2);

        chunkKeystreamSeedNode = keyDerivationTree.getSeed(0);
        metadataEncryptionKey = keyDerivationTree.getSeed(1);
        macKey = keyDerivationTree.getSeed(2);
        sharingKeystreamMasterKey = keyDerivationTree.getSeed(3);
    }

    public byte[] getMetadataEncryptionKey() {
        return metadataEncryptionKey;
    }

    public BigInteger getMacKeyAsBigInteger() {
        return new BigInteger(macKey);
    }

    public byte[] getMacKey() {
        return macKey;
    }

    public IKeyRegression getChunkKeyRegression() {
        return TreeKeyRegressionFactory.getNewDefaultKeyRegression(chunkKeystreamSeedNode, chunkKeystreamDepth);
    }

    public IKeyRegression getSharingKeyRegression(int precision, int depth) {
        // TODO: create sharing keyRegression from the precision
        // prf.apply(key, precision)
        return TreeKeyRegressionFactory.getNewDefaultKeyRegression(sharingKeystreamMasterKey, depth);
    }
}
