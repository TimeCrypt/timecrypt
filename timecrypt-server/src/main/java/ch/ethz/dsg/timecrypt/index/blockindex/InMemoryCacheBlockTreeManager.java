/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex;

import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;
import ch.ethz.dsg.timecrypt.index.blockindex.node.MetaInnerNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.protobuf.InvalidProtocolBufferException;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements the tree in memory - no cassandra needed
 */
public class InMemoryCacheBlockTreeManager implements IBlockTreeFetcher {

    Map<String, BlockTree> treeMap = Collections.synchronizedMap(new HashMap<>());

    Cache<String, BlockNode> blockMap = null;


    public InMemoryCacheBlockTreeManager(int size) {
        blockMap = Caffeine.newBuilder()
                .maximumSize(size)
                .build();
    }

    public InMemoryCacheBlockTreeManager() {
        this(Integer.MAX_VALUE);
    }

    private static String deductKey(long uid, String user, long blockid) {
        return String.format("%d|%s|%d", uid, user, blockid);
    }

    private static String deductKeyTree(long uid, String user) {
        return String.format("%d|%s", uid, user);
    }

    private static byte[] encode(BlockNode node) {
        byte[] meta = node.encodeContent();
        ByteBuffer buff = ByteBuffer.allocate(meta.length + 4);
        buff.putInt(node.getVersion());
        buff.put(meta);
        return buff.array();

    }

    private static BlockNode decode(int fro, int to, byte[] content) throws InvalidProtocolBufferException {
        ByteBuffer buff = ByteBuffer.wrap(content);
        int version = buff.getInt();
        byte[] cont = new byte[content.length - 4];
        System.arraycopy(content, 4, cont, 0, cont.length);
        MetaInnerNode[] meta = BlockNode.decodeNodeContent(cont);
        return new BlockNode(version, fro, to, meta);
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
        return treeMap.get(deductKeyTree(uid, user));
    }

    @Override
    public BlockTree fetchTreeMinVersion(long uid, String user, int minVersion) throws Exception {
        return treeMap.get(deductKeyTree(uid, user));
    }

    @Override
    public BlockTree fetchTree(long uid, String user) throws Exception {
        return treeMap.get(deductKeyTree(uid, user));
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
            try {
                /*return decode(BlockIdUtil.getFrom(blockId),
                        BlockIdUtil.getTo(blockId),
                        blockMap.getIfPresent(deductKey(this.id, this.user, blockId)));*/
                return blockMap.getIfPresent(deductKey(this.id, this.user, blockId));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void pushUpdates(UpdateSummary summary) {
            BlockTree tree = treeMap.get(deductKeyTree(this.id, this.user));
            tree.root = summary.getNewRoot();
            blockMap.put(deductKey(id, user, tree.root.getId()), tree.root);
            for (BlockNode node : summary.getNewNodes()) {
                blockMap.put(deductKey(id, user, node.getId()), node);
            }
            //System.out.println(blockMap.estimatedSize());
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

