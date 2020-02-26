/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import java.util.List;

/**
 * Implements the digest aggregation tree.
 */
public interface ITree {

    void insert(int key, NodeContent[] contentData, long from, long to) throws Exception;

    List<Integer> getRange(long from, long to) throws IllegalArgumentException;

    List<Integer> getAllKeysOfChunkNodes() throws IllegalArgumentException;

    NodeContent[] getAggregation(long from, long to) throws Exception;

    NodeContent[] getAggregation(long from, long to, int[] ids) throws Exception;

    String toString();

    int getLeavesCount();
}
