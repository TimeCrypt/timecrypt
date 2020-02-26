/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex;

import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;

import java.util.ArrayList;
import java.util.List;

public class UpdateSummary {

    public int version;
    public int newTime;
    public int k;

    private BlockNode newRoot = null;
    private boolean rootIsCreated = false;
    private List<BlockNode> newNodes = new ArrayList<>();
    private List<Boolean> isCreated = new ArrayList<>();

    public UpdateSummary(int version, int newTime, int k) {
        this.version = version;
        this.newTime = newTime;
        this.k = k;
    }

    public void pushNewRoot(BlockNode newRoot, boolean newlyCreated) {
        this.newRoot = newRoot;
    }

    public void pushUpdate(BlockNode newNode, boolean newlyCreated) {
        newNodes.add(newNode);
        isCreated.add(newlyCreated);
    }

    public int getVersion() {
        return version;
    }

    public int getNewTime() {
        return newTime;
    }

    public BlockNode getNewRoot() {
        return newRoot;
    }

    public boolean isRootIsCreated() {
        return rootIsCreated;
    }

    public List<BlockNode> getNewNodes() {
        return newNodes;
    }

    public List<Boolean> getIsCreated() {
        return isCreated;
    }
}
