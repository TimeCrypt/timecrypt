/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto.keyRegression;

public interface SeedNode {

    /**
     * @return
     */
    long getNodeNr();

    /**
     * @return
     */
    byte[] getSeed();

    /**
     * @return
     */
    int getDepth();

    void printNode();

    /**
     * @param node
     * @return
     */
    int compareTo(SeedNode node);
}