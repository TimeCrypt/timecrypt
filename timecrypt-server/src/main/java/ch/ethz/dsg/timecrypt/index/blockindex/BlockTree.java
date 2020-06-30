/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex;

import ch.ethz.dsg.timecrypt.index.ITree;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockIdUtil;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;
import ch.ethz.dsg.timecrypt.index.blockindex.node.MetaInnerNode;
import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class BlockTree implements ITree {

    private static final int RETRY_AWAIT_TIME = 1;
    private static final int MAX_RETRY = 3;


    public int k;

    public int interval = 1;

    public BlockNode root;
    public INodeManager man;

    public BlockTree(int k, BlockNode root, INodeManager man) {
        this.k = k;
        this.root = root;
        this.man = man;
    }

    public BlockTree(int k, BlockNode root) {
        this.k = k;
        this.root = root;
        this.man = null;
    }

    public void setRoot(BlockNode root) {
        this.root = root;
    }

    public void setMan(INodeManager man) {
        this.man = man;
    }

    public void updateToLatest() throws Exception {
        this.man.updateToLatest(this);
    }

    public void buildFrom(List<NodeContent[]> contents) {
        // TODO
        ArrayList<BlockNode> upperLeveL = new ArrayList<>();
        ArrayList<BlockNode> lowerLevel = new ArrayList<>();

        BlockNode cur = new BlockNode(contents.size(), 0, k * interval, k);
        int curFrom = 0;
        for (NodeContent[] cont : contents) {
            if (!cur.hasSpace()) {
                lowerLevel.add(cur);
                cur = new BlockNode(contents.size(), cur.to, cur.to + (k * interval), k);
                cur.insert(new MetaInnerNode(cont), curFrom, curFrom + interval);
            }
            cur.insert(new MetaInnerNode(cont), curFrom, curFrom + interval);
        }
        lowerLevel.add(cur);

        while (lowerLevel.size() > 1) {
            cur = new BlockNode(contents.size(), lowerLevel.get(0).from, lowerLevel.get(0).to * k * interval, k);
            for (BlockNode blockNode : lowerLevel) {
                if (!cur.hasSpace()) {
                    lowerLevel.add(cur);
                    cur = new BlockNode(contents.size(), cur.to, cur.to + (k * interval), k);
                    //cur.insert(new MetaInnerNode(cont), curFrom, curFrom + interval);
                }
                //cur.insert(new MetaInnerNode(cont), curFrom, curFrom + interval);
            }
        }
    }

    @Override
    public void insert(int key, NodeContent[] contentData, long fromL, long toL) throws Exception {
        if (fromL != root.getVersion())
            throw new RuntimeException("Not in order Insert!");
        int from = (int) fromL, to = (int) toL;
        int maxRetry = 5;
        MetaInnerNode meta = new MetaInnerNode(contentData);
        assert (to - from == interval);

        updateToNeededVersion(from, maxRetry);

        BlockNode curNode = root;

        ArrayList<BlockNode> nodePath = new ArrayList<>();
        boolean placeFound = false;
        int newVersion = curNode.getVersion() + 1;
        UpdateSummary summary = new UpdateSummary(newVersion, to, k);


        // do we require a new root?
        if (curNode.containsInterval(from, to)) {
            if (curNode.isLeaf(interval)) {
                curNode.insert(meta, from, to);
                summary.pushNewRoot(curNode, false);
                //done
                curNode.setNewVersion(newVersion);
                this.man.pushUpdates(summary);
                return;
            } else {
                summary.pushNewRoot(curNode, false);
            }
        } else {
            //create new root
            BlockNode newRoot = new BlockNode(newVersion, curNode.from, curNode.to * k, k);
            newRoot.insert(new MetaInnerNode(curNode.getAggregatedNodeContent()), curNode.from, curNode.to);
            summary.pushNewRoot(newRoot, true);
            summary.pushUpdate(curNode, false);
            curNode = newRoot;
        }
        nodePath.add(curNode);

        // find the place in the tree
        while (!placeFound) {

            if (curNode.hasMetaForInterval(from, to)) {
                // load existing child content
                long blockid = curNode.getPointerMetaInfoIndexForLeaf(from, to);
                curNode = man.loadNodeWithVersionForInsert(blockid, newVersion - 1);
                summary.pushUpdate(curNode, false);
            } else {
                // create new content
                BlockNode newNode = curNode.createChildNodeForInterval(from, k);
                summary.pushUpdate(newNode, true);
                curNode = newNode;
            }

            nodePath.add(curNode);

            if (curNode.isLeaf(interval)) {
                //found leaf content
                placeFound = true;
                curNode.insert(meta, from, to);
            }
        }

        // update ranges;
        for (int i = nodePath.size() - 2; i >= 0; i--) {
            BlockNode upperNode = nodePath.get(i);
            BlockNode lowerNode = nodePath.get(i + 1);

            upperNode.insert(new MetaInnerNode(lowerNode.getAggregatedNodeContent()), lowerNode.from, lowerNode.to);
            upperNode.setNewVersion(newVersion);
            lowerNode.setNewVersion(newVersion);
        }
        this.man.pushUpdates(summary);
    }

    @Override
    public int getLastWrittenChunk() {
        return root.getVersion() - 1;
    }

    @Override
    public List<Integer> getRange(long from, long to) throws IllegalArgumentException {
        return null;
    }

    @Override
    public List<Integer> getAllKeysOfChunkNodes() throws IllegalArgumentException {
        return null;
    }

    @Override
    public NodeContent[] getAggregation(long fromL, long toL) throws Exception {
        int from = (int) fromL, to = (int) toL;
        if (to <= from) {
            throw new IllegalArgumentException();
        }

        updateToNeededVersion(to, MAX_RETRY);

        NodeContent[] res = null;

        Queue<BlockNode> queue = new ArrayDeque<>();
        queue.add(root);

        while (queue.size() != 0) {
            BlockNode current = queue.poll();

            for (MetaInnerNode meta : current.getMetaFullyContained(from, to)) {
                if (res == null) {
                    res = meta.getContentCopy();
                } else {
                    for (int i = 0; i < res.length; i++) {
                        res[i].mergeOther(meta.metaInformation[i]);
                    }
                }
            }

            int indLeft = current.getLeftNodeDepper(from);
            addNotContainedNodes(queue, current, indLeft, to);

            int indRight = current.getRigthNodeDepper(to);
            if (indRight != indLeft)
                addNotContainedNodes(queue, current, indRight, to);
        }

        return res;
    }

    private void updateToNeededVersion(int to, int maxRetry) throws InterruptedException {
        for (int count = 0; root.getVersion() < to && count < maxRetry; count++) {
            if (count > 1)
                Thread.sleep(RETRY_AWAIT_TIME);
            try {
                this.man.updateToLatest(this, to);
            } catch (Exception e) {
                continue;
            }
        }
    }

    private void addNotContainedNodes(Queue<BlockNode> queue, BlockNode current, int indRight, int to) throws Exception {
        int maxRetry = 3;
        if (indRight != -1) {
            if (current.metaInnerNode[indRight] != null) {
                BlockNode node = null;
                long id = current.getPointerMetaInfoIndex(indRight);
                //TODO Check consistency
                int minVersion = (BlockIdUtil.getTo(id) < to) ? BlockIdUtil.getTo(id) : to;
                for (int count = 0; node == null || (node.getVersion() < minVersion || count > maxRetry); count++) {
                    if (count > 1)
                        Thread.sleep(RETRY_AWAIT_TIME);
                    try {
                        node = man.loadNodeWithMinVersion(id, minVersion);
                    } catch (Exception e) {
                        continue;
                    }

                }
                node = man.loadNodeWithMinVersion(id, minVersion);
                queue.add(node);
            }
        }
    }

    @Override
    public NodeContent[] getAggregation(long fromL, long toL, int[] ids) throws Exception {
        int from = (int) fromL, to = (int) toL;
        if (to <= from) {
            throw new IllegalArgumentException("From (" + from + ") has to be greater then to (" + to + ")");
        }

        updateToNeededVersion(to, MAX_RETRY);

        NodeContent[] res = null;

        Queue<BlockNode> queue = new ArrayDeque<>();
        queue.add(root);

        while (queue.size() != 0) {
            BlockNode current = queue.poll();

            for (MetaInnerNode meta : current.getMetaFullyContained(from, to)) {
                if (res == null) {
                    res = meta.getContentCopy();
                } else {
                    for (int i : ids) {
                        res[i].mergeOther(meta.metaInformation[i]);
                    }
                }
            }

            int indLeft = current.getLeftNodeDepper(from);
            addNotContainedNodes(queue, current, indLeft, to);

            int indRight = current.getRigthNodeDepper(to);
            if (indRight != indLeft)
                addNotContainedNodes(queue, current, indRight, to);
        }

        return res;
    }

    @Override
    public int getLeavesCount() {
        return this.root.getVersion();
    }

    private StringBuilder toStringHelper(BlockNode n, String ind) throws Exception {
        StringBuilder s = new StringBuilder();
        s.append(ind).append(n.toString()).append("\n");
        if (n.isLeaf(interval))
            return s;
        for (int i = 0; i < n.metaInnerNode.length; i++) {
            if (n.metaInnerNode[i] == null)
                break;
            BlockNode child = man.loadNode(n.getPointerMetaInfoIndex(i));
            s.append(toStringHelper(child, ind + "  "));
        }
        return s;
    }

    @Override
    public String toString() {
        try {
            return toStringHelper(this.root, "").toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "ERROR";
    }
}
