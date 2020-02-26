/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex;

import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;

import java.util.HashMap;

public class DebugBlockTreeManager implements IBlockTreeFetcher {

    HashMap<String, BlockTree> treeMap = new HashMap<>();

    HashMap<String, BlockNode> blockMap = new HashMap<>();

    private static String deductKey(long uid, String user, long blockid) {
        return String.format("%d|%s|%d", uid, user, blockid);
    }

    private static String deductKeyTree(long uid, String user) {
        return String.format("%d|%s", uid, user);
    }


    @Override
    public BlockTree createTree(long uid, String user, int k, int interval) {
        BlockNode newRoot = new BlockNode(0, 0, interval * k, k);
        BlockTree tree = new BlockTree(k, newRoot, new DebufgNodeManager(user, uid));
        treeMap.put(deductKeyTree(uid, user), tree);
        blockMap.put(deductKey(uid, user, newRoot.getId()), newRoot);
        return tree;
    }

    @Override
    public BlockTree fetchNewestTreeAndAwait(long uid, String user) {
        return null;
    }

    @Override
    public BlockTree fetchTreeMinVersion(long uid, String user, int minVersion) throws Exception {
        return null;
    }

    @Override
    public BlockTree fetchTree(long uid, String user) throws Exception {
        return null;
    }

    @Override
    public void invalidateCache() {

    }

    public class DebufgNodeManager implements INodeManager {

        BlockTree tree;
        private String user;
        private long id;

        public DebufgNodeManager(String user, long id) {
            this.user = user;
            this.id = id;
        }

        @Override
        public BlockNode loadNode(long blockId) {
            return blockMap.get(deductKey(this.id, this.user, blockId));
        }

        @Override
        public void pushUpdates(UpdateSummary summary) {
            BlockTree tree = treeMap.get(deductKeyTree(this.id, this.user));
            tree.root = summary.getNewRoot();
            blockMap.put(deductKey(id, user, tree.root.getId()), tree.root);
            for (BlockNode node : summary.getNewNodes()) {
                blockMap.put(deductKey(id, user, node.getId()), node);
            }
        }

        @Override
        public void updateToLatest(BlockTree tree) {
            BlockTree actualTree = treeMap.get(deductKeyTree(this.id, this.user));
            tree.root = actualTree.root;
        }

        @Override
        public BlockNode loadNodeWithMinVersion(long blockid, int version) throws Exception {
            return loadNode(blockid);
        }

        @Override
        public BlockNode loadNodeWithVersionForInsert(long blockid, int version) throws Exception {
            return loadNode(blockid);
        }

        @Override
        public void updateToLatest(BlockTree tree, int minVersion) throws Exception {
            this.updateToLatest(tree);
        }
    }
}
