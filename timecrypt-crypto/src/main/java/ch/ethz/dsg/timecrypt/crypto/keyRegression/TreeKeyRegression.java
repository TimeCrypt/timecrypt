/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keyRegression;

import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static java.lang.Math.min;
import static java.lang.StrictMath.max;

/**
 * Implements a TreeKeyRegression with Key Regression,
 * keys are the leaf nodes of the tree seed:
 * <p>
 * <pre>
 * RootSeed
 *
 *      - n1
 *          - k0
 *          - k1
 *      - n2
 *          - k2
 *          - k3
 * </pre>
 */
public class TreeKeyRegression implements IKeyRegression {

    public long[] powers;
    public long[] keyInterval;
    Boolean isOwner = false;
    private ArrayList<SeedNode> relevantSeeds;
    private byte[] rootSeed;
    private int depth;
    private int kFactor = 2;
    private IPRF prf;

    /**
     * Creates a tree key-regression object for an owner.
     *
     * @param prf     the regression function used to derive new seeds
     * @param depth   the depth of the tree (i.e 0 for no tree, 1 for 2 keys, 2 for 4 keys etc.)
     * @param kFactor the nonzero(!) number of children in each node (Default: 2)
     */
    public TreeKeyRegression(Boolean isOwner, IPRF prf, ArrayList<SeedNode> relevantSeeds, int depth,
                             int kFactor) {
        this.isOwner = isOwner;
        this.prf = prf;
        this.relevantSeeds = relevantSeeds;
        this.depth = depth;
        this.kFactor = kFactor;

        if (isOwner) {
            this.rootSeed = relevantSeeds.get(0).getSeed();
        }
        computePowers();
        keyInterval = new long[2];
        keyInterval[0] = getKeyInterval(relevantSeeds.get(0))[0];
        keyInterval[1] = getKeyInterval(relevantSeeds.get(relevantSeeds.size() - 1))[1];
    }

    /**
     * Creates a tree key-regression object for a receiver.
     *
     * @param prf           the prf used to derive new seeds
     * @param depth         the depth of the tree(i.e. 0 for no tree, 1 for 2 keys, 2 for 4 keys etc.
     * @param kFactor       the nonzero(!) number of childes in each node (Default: 2)
     * @param relevantSeeds the seedNodes the owner revealed to us, in order to compute our keys.
     */
    public TreeKeyRegression(IPRF prf, int depth, int kFactor, int offset, int keyLength,
                             ArrayList<SeedNode> relevantSeeds, long[] keyInterval) {
        if (kFactor == 0)
            throw new RuntimeException("kFactor is not allowed to be zero!");
        this.prf = prf;
        this.depth = depth;
        this.kFactor = kFactor;
        this.relevantSeeds = relevantSeeds;
        this.keyInterval = keyInterval;
        computePowers();
    }

    private void checkValidAccess(long id) {
        if (keyInterval[0] > id || id > keyInterval[1])
            throw new InvalidKeyDerivation("Tree does not support this access");
    }

    private long getNumberOfKeys() {
        /* compute amount of keys in whole tree */
        if (depth < 1)
            return 1L;
        return powers[0];
    }

    private void computePowers() {
        /*Computes an array storing the powers of kFactor*/
        powers = new long[depth + 1];
        long cur = 1;
        for (int i = powers.length - 1; i >= 0; i--) {
            powers[i] = cur;
            cur *= kFactor;
        }
    }

    private SeedNode getRelevantNode(long[] keyInterval, long keyNr) {
        /* Compute the relevant Node from the relevantSeeds which
           is necessary to compute a specific key */
        long keyId = keyNr;
        keyId -= keyInterval[0];
        for (SeedNode htn : relevantSeeds) {
            int currDepth = htn.getDepth();
            long amount = powers[currDepth];
            if (keyId < amount) {
                return htn;
            }
            keyId -= amount;
        }
        return relevantSeeds.get(0);
    }

    private int[] computePath(int depth, long keyId) {
        /*Computing Path from leaf up towards specific node specified by its depth*/
        int[] path = new int[this.depth - depth];
        long curId = keyId;
        long nextId;
        for (int d = this.depth; d > depth; d--) {
            nextId = curId / kFactor;
            path[d - depth - 1] = (int) curId % kFactor;
            curId = nextId;
        }
        return path;
    }

    private int[] computePathFromRoot(int depth, long nodeNr) {
        /*Compute Path from rootSeed to a specific node*/
        int[] path = new int[depth];
        long curId = nodeNr;
        long nextId;
        for (int d = depth; d > 0; d--) {
            nextId = curId / kFactor;
            path[d - 1] = (int) curId % kFactor;
            curId = nextId;
        }
        return path;
    }

    private long[] getKeyInterval(SeedNode node) {
        /*returns the interval [from, to] of keys we can compute with a certain SeedNode.*/
        int curDepth = node.getDepth();
        long curId = node.getNodeNr();
        long from = curId * powers[curDepth];
        long to = from + powers[curDepth] - 1;
        long[] interval = {from, to};
        return interval;
    }

    private byte[][] getNodeSeeds(SeedNode node, long from, long to) {
        int[] pathFrom = computePath(node.getDepth(), from);
        int[] pathTo = computePath(node.getDepth(), to);

        byte[][] result = new byte[(int) (to - from + 1)][];

        ArrayList<byte[]> previousSeeds = new ArrayList<byte[]>();
        ArrayList<byte[]> nextSeeds;
        previousSeeds.add(node.getSeed());

        for (int i = 0; i < this.depth - node.getDepth(); i++) {
            nextSeeds = new ArrayList<byte[]>(previousSeeds.size() * kFactor);

            for (int j = 0; j < previousSeeds.size(); j++) {
                int fromk = 0;
                int tok = kFactor - 1;
                if (j == 0)
                    fromk = pathFrom[i];
                if (j == previousSeeds.size() - 1)
                    tok = pathTo[i];
                for (int k = fromk; k <= tok; k++) {
                    nextSeeds.add(prf.apply(previousSeeds.get(j), k));
                }
            }
            previousSeeds = nextSeeds;
        }

        for (int i = 0; i < previousSeeds.size(); i++) {
            result[i] = previousSeeds.get(i);
        }
        return result;
    }

    private TreeKeyRegressionNode reveal(int depth, long nodeNr) {
        byte[] cur = rootSeed;
        if (depth == 0) {
            return new TreeKeyRegressionNode(cur, 0, 0);
        }
        int[] path = computePathFromRoot(depth, nodeNr);
        cur = prf.muliApply(rootSeed, path);
        return new TreeKeyRegressionNode(cur, depth, nodeNr);
    }

    public ArrayList<SeedNode> sortNodeArray(ArrayList<SeedNode> list) {
        Collections.sort(list, new Comparator<SeedNode>() {
            @Override
            public int compare(SeedNode node1, SeedNode node2) {
                long[] interval1 = getKeyInterval(node1);
                long[] interval2 = getKeyInterval(node2);
                return Long.compare(interval1[0], interval2[0]);
            }
        });
        return list;
    }

    @Override
    public BigInteger getKey(long id, int keyBits) {
        return KeyUtil.deriveKey(prf, getSeed(id), keyBits);
    }

    /**
     * Returns the key with the given identifier
     *
     * @param id the key identifier
     * @return the corresponding key
     */
    @Override
    public byte[] getSeed(long id) throws InvalidKeyDerivation {
        checkValidAccess(id);
        SeedNode seedNode = getRelevantNode(keyInterval, id);
        int curDepth = seedNode.getDepth();
        byte[] curSeed = seedNode.getSeed();

        if (curDepth != depth) {
            /*if seedNode is not already a leaf.*/
            int[] path = computePath(curDepth, id);
            curSeed = prf.muliApply(curSeed, path);
        }
        /*Derive Key from leaf seed value*/
        return curSeed;
    }

    @Override
    public BigInteger[] getKeys(long from, long to, int keyBits) {
        byte[][] seeds = getSeeds(from, to);
        BigInteger[] result = new BigInteger[seeds.length];
        for (int it = 0; it < seeds.length; it++) {
            result[it] = KeyUtil.deriveKey(prf, seeds[it], keyBits);
        }
        return result;
    }

    @Override
    public BigInteger getKeySum(long from, long to, int keyBits) {
        return null;
    }

    @Override
    public IPRF getPRF() {
        return this.prf;
    }

    /**
     * Returns the keys in the given range (Inclusive)
     *
     * @param fromValue the first key identifier in the range
     * @param toValue   the last key identifier in the range
     * @return the key range
     */
    @Override
    public byte[][] getSeeds(long fromValue, long toValue) throws InvalidKeyDerivation {
        checkValidAccess(fromValue);
        checkValidAccess(toValue);
        long from = fromValue;
        long to = toValue;
        long curId = 0; /* describes how many keys we already have */
        byte[][] result = new byte[(int) (to - from + 1)][];

        for (SeedNode node : relevantSeeds) {
            long[] nodeKeyInterval = getKeyInterval(node);
            long curFrom = nodeKeyInterval[0];
            long curTo = nodeKeyInterval[1];

            if (from <= curTo && to >= curFrom) {
                /* node is relevant for at least one key */
                curFrom = max(from, curFrom);
                curTo = min(to, curTo);
                long amountOfKeys = curTo - curFrom + 1; /*nr of  desired keys we get from current node*/
                byte[][] keys = getNodeSeeds(node, curFrom, curTo);

                for (long i = curId; i < curId + amountOfKeys; i++) {
                    result[(int) i] = keys[(int) (i - curId)];
                }
                curId += amountOfKeys;
            }
        }
        return result;
    }

    /**
     * Reveal the relevant SeedNodes to allow computation
     * of the keys from Interval [from, to].
     *
     * @param from specifies start of Interval of keys we would like to reveal.
     * @param to   specifies end of Interval.
     * @return ArrayList of HashTreeNodes.
     */
    public ArrayList<SeedNode> revealSeeds(long from, long to) {
        assert isOwner;
        ArrayList<SeedNode> seedNodes = new ArrayList<SeedNode>();
        if (from > to || rootSeed == null)
            throw new RuntimeException(String.format("%d is not smaller than %d", from, to));
        for (int d = depth; d >= 0; d--) {
            for (int i = 0; i < kFactor - 1; i++) {
                if (from == to) {
                    TreeKeyRegressionNode node = reveal(d, from);
                    if (!seedNodes.contains(node))
                        seedNodes.add(node);
                }
                if (from % kFactor != 0) {
                    TreeKeyRegressionNode node = reveal(d, from);
                    if (!seedNodes.contains(node))
                        seedNodes.add(node);
                    from++;
                }
                if (to % kFactor != kFactor - 1) {
                    TreeKeyRegressionNode node = reveal(d, to);
                    if (!seedNodes.contains(node))
                        seedNodes.add(node);
                    to--;
                }
                if (from > to) {
                    break;
                }
            }
            if (from > to) {
                break;
            }
            from /= kFactor;
            to /= kFactor;
        }
        sortNodeArray(seedNodes);
        return seedNodes;
    }
}