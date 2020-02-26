/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keyRegression;

import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Implements a tree key regression, keys are the leaf nodes of the tree
 * <pre>
 * seed
 *
 *      - n1
 *          - k0
 *          - k1
 *      - n2
 *          - k2
 *          - k3
 * </pre>
 */
public class OldTreeKeyDerivation implements IKeyRegression {

    private IPRF prf;
    private byte[] rootSeed;

    private int depth = 0;
    private int kFactor = 2;
    private int offset = 0;

    private long[] powers;

    /**
     * Creates a tree key-regression object
     *
     * @param regFunc  the regression function used to derive new seeds
     * @param rootSeed the root secret
     * @param depth    the depth of the tree (i.e 0 for no tree, 1 for 2 keys, 2 for 4 keys etc)
     * @param kFactor  the number of childes in each node (Default: 2)
     * @param offset   if the key identifiers have an offset (Default 0)
     */
    public OldTreeKeyDerivation(IPRF regFunc, byte[] rootSeed, int depth, int kFactor, int offset) {
        this.prf = regFunc;
        this.rootSeed = rootSeed;
        this.depth = depth;
        this.kFactor = kFactor;
        this.offset = offset;
        computePowers();

    }

    public long getNumberOfKeys() {
        if (depth < 1)
            return 1L;
        return powers[0];
    }

    private void computePowers() {
        powers = new long[depth];
        long cur = 1;
        for (int iter = powers.length; iter > 0; iter--) {
            cur *= kFactor;
            powers[iter - 1] = cur;
        }
    }

    private int[] computePath(long keyId) {
        if (powers.length < 1 || powers[0] < keyId || keyId < 0)
            throw new RuntimeException(String.format("%d is not in the tree", keyId));
        int[] path = new int[powers.length];
        long curid = keyId;
        for (int i = 0; i < powers.length - 1; i++) {
            path[i] = (int) (curid / powers[i + 1]);
            curid = (curid % powers[i + 1]);
        }
        assert (curid >= 0 && curid < kFactor);
        path[path.length - 1] = (int) curid;
        return path;
    }

    /**
     * Returns the key with the given identifier
     *
     * @param id      the key identifier
     * @param keyBits the key size in number of bits
     * @return the corresponding key
     */
    public BigInteger getKey(long id, int keyBits) {
        return KeyUtil.deriveKey(prf, getSeed(id), keyBits);
    }

    public byte[] getSeed(long id) {
        byte[] cur = rootSeed;
        if (depth > 0) {
            int[] path = computePath(id - offset);
            cur = prf.muliApply(rootSeed, path);
        }
        return cur;
    }

    @Override
    public byte[][] getSeeds(long from, long to) {
        if (from == to) {
            return new byte[][]{getSeed(from)};
        }

        byte[][] result = new byte[(int) (to - from + 1)][];
        int[] pathFrom = computePath(from - offset);
        int[] pathTo = computePath(to - offset);
        assert (pathFrom.length == pathTo.length);

        ArrayList<byte[]> nextSeeds, previousSeeds = new ArrayList<byte[]>();
        previousSeeds.add(rootSeed);

        for (int it = 0; it < pathFrom.length; it++) {
            nextSeeds = new ArrayList<byte[]>(previousSeeds.size() * kFactor);
            if (previousSeeds.size() == 1) {
                for (int k = pathFrom[it]; k <= pathTo[it]; k++) {
                    nextSeeds.add(prf.apply(previousSeeds.get(0), k));
                }
            } else {
                for (int iter = 0; iter < previousSeeds.size(); iter++) {
                    int fromK = 0;
                    int toK = kFactor - 1;
                    if (iter == 0) {
                        fromK = pathFrom[it];
                    } else if (iter == previousSeeds.size() - 1) {
                        toK = pathTo[it];
                    }
                    for (int k = fromK; k <= toK; k++) {
                        nextSeeds.add(prf.apply(previousSeeds.get(iter), k));
                    }
                }
            }
            previousSeeds = nextSeeds;
        }

        assert (previousSeeds.size() == result.length);
        int id = 0;
        for (byte[] seed : previousSeeds) {
            result[id++] = seed;
        }
        return result;
    }

    /**
     * Returns the keys in the given range (Inclusive)
     *
     * @param from    the first key identifier in the range
     * @param to      the last key identifier in the range
     * @param keyBits he key sizes in number of bits
     * @return the key range
     */
    public BigInteger[] getKeys(long from, long to, int keyBits) {
        if (from == to) {
            return new BigInteger[]{getKey(from, keyBits)};
        }

        BigInteger[] result = new BigInteger[(int) (to - from + 1)];
        int[] pathFrom = computePath(from - offset);
        int[] pathTo = computePath(to - offset);
        assert (pathFrom.length == pathTo.length);

        ArrayList<byte[]> nextSeeds, previousSeeds = new ArrayList<byte[]>();
        previousSeeds.add(rootSeed);

        for (int it = 0; it < pathFrom.length; it++) {
            nextSeeds = new ArrayList<byte[]>(previousSeeds.size() * kFactor);
            if (previousSeeds.size() == 1) {
                for (int k = pathFrom[it]; k <= pathTo[it]; k++) {
                    nextSeeds.add(prf.apply(previousSeeds.get(0), k));
                }
            } else {
                for (int iter = 0; iter < previousSeeds.size(); iter++) {
                    int fromK = 0;
                    int toK = kFactor - 1;
                    if (iter == 0) {
                        fromK = pathFrom[it];
                    } else if (iter == previousSeeds.size() - 1) {
                        toK = pathTo[it];
                    }
                    for (int k = fromK; k <= toK; k++) {
                        nextSeeds.add(prf.apply(previousSeeds.get(iter), k));
                    }
                }
            }
            previousSeeds = nextSeeds;
        }

        assert (previousSeeds.size() == result.length);

        for (int it = 0; it < previousSeeds.size(); it++) {
            result[it] = KeyUtil.deriveKey(prf, previousSeeds.get(it), keyBits);
        }

        return result;
    }


    public BigInteger getKeySum(long from, long to, int keyBits) {
        BigInteger res = BigInteger.ZERO;
        for (BigInteger tmp : getKeys(from, to, keyBits)) {
            res = res.add(tmp);
        }
        return res;
    }

    public IPRF getPRF() {
        return this.prf;
    }
}
