/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keyRegression;

import static java.lang.StrictMath.max;

/**
 * Each Node is a Triple (seed, depth, nodeNr), where seed is the seed value at
 * a specific depth and nodeNr in a TreeKeyRegression. NodeNr starts from 0 at each
 * depth and increases from left to right on the same layer of the Tree.
 */

public class TreeKeyRegressionNode implements SeedNode {

    private byte[] seed;
    private int depth;
    private long nodeNr;

    /**
     * Creates a TreeKeyRegressionNode object
     *
     * @param seed   the seed value at this node
     * @param depth  the depth of the node in the tree
     * @param nodeNr nr of this node from left to right at same depth
     */
    public TreeKeyRegressionNode(byte[] seed, int depth, long nodeNr) {
        this.seed = seed;
        this.depth = depth;
        this.nodeNr = nodeNr;
    }

    public byte[] getSeed() {
        return seed;
    }

    public int getDepth() {
        return depth;
    }

    public long getNodeNr() {
        return nodeNr;
    }

    public void printNode() {
        System.out.println("[Seed: " + seed.toString() + ", Depth: " + depth + ", NodeNr: " + nodeNr + "]");
    }

    public int compareTo(SeedNode node) {
        if (this.depth == node.getDepth() && this.nodeNr == node.getNodeNr())
            return 0;
        long factor1 = this.getNodeNr() / max(1, this.getDepth());
        long factor2 = node.getNodeNr() / max(1, node.getDepth());
        if (factor1 < factor2)
            return -1;
        else
            return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TreeKeyRegressionNode))
            return false;
        SeedNode node = (SeedNode) obj;
        return this.depth == node.getDepth() && this.nodeNr == node.getNodeNr();
    }
}
