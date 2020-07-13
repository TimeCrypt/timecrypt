/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keymanagement;


import ch.ethz.dsg.timecrypt.crypto.keyRegression.IKeyRegression;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.SeedNode;
import ch.ethz.dsg.timecrypt.crypto.keyRegression.TreeKeyRegressionFactory;

import java.math.BigInteger;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for handling all the keys associated with a stream as well as receiving them.
 */
public class StreamKeyManager {

    private final IKeyRegression treeKeyRegression;
    private final byte[] macKey;
    private final byte[] sharingKeystreamMasterKey;
    private boolean isMaster;

    public StreamKeyManager(byte[] streamMasterKey, int numKeysDepth) {
        IKeyRegression keyDerivationTree = TreeKeyRegressionFactory.getNewDefaultKeyRegression(streamMasterKey, 2);
        byte[] metadataEncryptionKey = keyDerivationTree.getSeed(1);
        treeKeyRegression = TreeKeyRegressionFactory.getNewDefaultKeyRegression(metadataEncryptionKey, numKeysDepth);
        macKey = keyDerivationTree.getSeed(2);
        sharingKeystreamMasterKey = keyDerivationTree.getSeed(3);
        isMaster = true;
    }

    public StreamKeyManager(ArrayList<SeedNode> nodes, byte[] macKey, int numKeysDepth) {
        this.treeKeyRegression = TreeKeyRegressionFactory.getNewDefaultKeyRegression(nodes, numKeysDepth);
        this.macKey = macKey;
        sharingKeystreamMasterKey = null;
        isMaster = false;
    }

    public BigInteger getMacKeyAsBigInteger() {
        return new BigInteger(macKey);
    }

    public IKeyRegression getTreeKeyRegression() {
        return treeKeyRegression;
    }

    public byte[] getChunkEncryptionKey(long chunkId) {
        return KeyUtil.deriveCombinedKey(treeKeyRegression.getPRF(),
                treeKeyRegression.getSeed(chunkId),
                treeKeyRegression.getSeed(chunkId + 1));
    }

    public byte[] getChunkEncryptionKey(long chunkId, CachedKeys keys) {
        if (!keys.containsKeys()) {
            keys.setK1(treeKeyRegression.getSeed(chunkId));
            keys.setK2(treeKeyRegression.getSeed(chunkId + 1));
        }
        return KeyUtil.deriveCombinedKey(treeKeyRegression.getPRF(), keys.k1, keys.k2);
    }

    public IKeyRegression getSharingKeyRegression(int precision, int depth) {
        if (isMaster) {
            byte[] precisionMasterSecret = this.treeKeyRegression.getPRF().apply(sharingKeystreamMasterKey, precision);
            return TreeKeyRegressionFactory.getNewDefaultKeyRegression(precisionMasterSecret, depth);
        } else {
            throw new RuntimeException("Non-owner is not able to share");
        }
    }

    public boolean isMaster() {
        return this.isMaster;
    }


}
