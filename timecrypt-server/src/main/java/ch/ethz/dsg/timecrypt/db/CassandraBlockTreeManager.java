/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.db;

import ch.ethz.dsg.timecrypt.index.blockindex.BlockTree;
import ch.ethz.dsg.timecrypt.index.blockindex.IBlockTreeFetcher;
import ch.ethz.dsg.timecrypt.index.blockindex.INodeManager;
import ch.ethz.dsg.timecrypt.index.blockindex.UpdateSummary;
import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;
import com.datastax.driver.core.ResultSetFuture;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;

public class CassandraBlockTreeManager implements IBlockTreeFetcher {

    private CassandraDatabaseManager man;

    private Cache<NodeKey, CacheContent<BlockNode>> blockCache;
    private Cache<TreeKey, CacheContent<BlockTree>> treeCache;

    public CassandraBlockTreeManager(CassandraDatabaseManager man, int treeCacheSize, int blockCacheSize) {
        this.man = man;
        buildBlockCache(blockCacheSize);
        buildTreeCache(treeCacheSize);
    }

    private static String deductKey(long uid, String user, long blockid) {
        return String.format("%d|%s|%d", uid, user, blockid);
    }

    private void buildBlockCache(int maxBlocks) {
        blockCache = Caffeine.newBuilder()
                .maximumSize(maxBlocks)
                .build();
    }

    private void buildTreeCache(int maxTree) {
        treeCache = Caffeine.newBuilder()
                .maximumSize(maxTree)
                .build();
    }

    public boolean treeExistsInCache(long uid, String user) {
        CacheContent<BlockTree> cacheTree = treeCache.getIfPresent(new TreeKey(user, uid));
        return cacheTree != null;
    }


    @Override
    public BlockTree createTree(long uid, String user, int k, int interval) throws Exception {
        BlockNode newRoot = new BlockNode(0, 0, interval * k, k);
        CassandraNodeManager nodeMan = new CassandraNodeManager(user, uid);
        BlockTree tree = new BlockTree(k, newRoot, nodeMan);
        ResultSetFuture resTree = man.insertTree(user, uid, newRoot, 0, k);
        //ResultSetFuture resBlock = man.insertBlock(user, uid, newRoot);
        treeCache.put(new TreeKey(user, uid), new CacheContent<BlockTree>(tree, resTree));
        return tree;
    }

    @Override
    public BlockTree fetchNewestTreeAndAwait(long uid, String user) throws Exception {
        CacheContent<BlockTree> cacheTree = treeCache.getIfPresent(new TreeKey(user, uid));
        BlockTree newest;
        if (cacheTree == null) {
            ResultSetFuture res = man.loadTree(user, uid);
            newest = man.getTree(res);
            CassandraNodeManager nodeMan = new CassandraNodeManager(user, uid);
            newest.setMan(nodeMan);
            treeCache.put(new TreeKey(user, uid), new CacheContent<BlockTree>(newest));
        } else {
            newest = cacheTree.content;
            cacheTree.awaitDBWrite();
        }
        return newest;
    }

    @Override
    public BlockTree fetchTree(long uid, String user) throws Exception {
        CacheContent<BlockTree> cacheTree = treeCache.getIfPresent(new TreeKey(user, uid));
        BlockTree newest;
        if (cacheTree == null) {
            ResultSetFuture res = man.loadTree(user, uid);
            newest = man.getTree(res);
            CassandraNodeManager nodeMan = new CassandraNodeManager(user, uid);
            newest.setMan(nodeMan);
            treeCache.put(new TreeKey(user, uid), new CacheContent<BlockTree>(newest));
        } else {
            newest = cacheTree.content;
        }
        return newest;
    }

    @Override
    public BlockTree fetchTreeMinVersion(long uid, String user, int minVersion) throws Exception {
        CacheContent<BlockTree> cacheTree = treeCache.getIfPresent(new TreeKey(user, uid));
        BlockTree newest;
        if (cacheTree == null) {
            ResultSetFuture res = man.loadTree(user, uid);
            newest = man.getTree(res);
            CassandraNodeManager nodeMan = new CassandraNodeManager(user, uid);
            newest.setMan(nodeMan);
            treeCache.put(new TreeKey(user, uid), new CacheContent<BlockTree>(newest));

        } else {
            newest = cacheTree.content;
            if (newest.root.getVersion() < minVersion) {
                treeCache.invalidate(new TreeKey(user, uid));
                ResultSetFuture res = man.loadTree(user, uid);
                newest = man.getTree(res);
                newest.setMan(new CassandraNodeManager(user, uid));
                treeCache.put(new TreeKey(user, uid), new CacheContent<BlockTree>(newest));
            }
        }
        return newest;
    }

    @Override
    public void invalidateCache() {
        this.treeCache.invalidateAll();
        this.blockCache.invalidateAll();
    }

    private static class NodeKey {
        private String user;
        private long uid;
        private long blockid;

        public NodeKey(String user, long uid, long blockid) {
            this.user = user;
            this.uid = uid;
            this.blockid = blockid;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NodeKey))
                return false;
            NodeKey other = (NodeKey) obj;
            return other.user.equals(this.user)
                    && other.blockid == this.blockid
                    && other.uid == this.uid;
        }

        @Override
        public int hashCode() {
            return String.format("%s%d%d", user, uid, blockid).hashCode();
        }
    }

    public static class CacheContent<T> {
        public ResultSetFuture insertSet = null;
        public T content;

        public CacheContent(T content) {
            this.content = content;
        }

        public CacheContent(T content, ResultSetFuture insertSet) {
            this.content = content;
            this.insertSet = insertSet;
        }

        public void awaitDBWrite() throws Exception {
            if (insertSet != null)
                insertSet.get();
        }
    }

    static class TreeKey {
        private String user;
        private long uid;

        public TreeKey(String user, long uid) {
            this.user = user;
            this.uid = uid;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TreeKey))
                return false;
            TreeKey other = (TreeKey) obj;
            return other.user.equals(this.user)
                    && other.uid == this.uid;
        }

        @Override
        public int hashCode() {
            return String.format("%s%d", user, uid).hashCode();
        }

    }

    public class CassandraNodeManager implements INodeManager {

        private String user;
        private long uid;

        public CassandraNodeManager(String user, long uid) {
            this.user = user;
            this.uid = uid;
        }

        private CacheContent<BlockNode> loadNodeCache(long id) throws Exception {
            CacheContent<BlockNode> result = blockCache.getIfPresent(new NodeKey(user, uid, id));
            BlockNode res;
            if (result == null) {
                ResultSetFuture futureNode = man.loadBlock(this.user, this.uid, id);
                res = man.getNode(futureNode, id);
                result = new CacheContent<BlockNode>(res);
                blockCache.put(new NodeKey(user, uid, id), result);
            }
            return result;
        }

        @Override
        public BlockNode loadNode(long id) throws Exception {
            CacheContent<BlockNode> result = loadNodeCache(id);
            return result.content;

        }

        @Override
        public void pushUpdates(UpdateSummary summary) throws Exception {
            List<BlockNode> newNodes = summary.getNewNodes();
            CassandraNodeManager nodeMan = new CassandraNodeManager(user, uid);
            BlockTree tree = new BlockTree(summary.k, summary.getNewRoot(), nodeMan);
            ResultSetFuture res = man.insertBlocks(user, uid, newNodes);
            ResultSetFuture resTree = man.insertTree(user, uid, summary.getNewRoot(), summary.version, summary.k);
            for (BlockNode n : newNodes) {
                blockCache.put(new NodeKey(user, uid, n.getId()), new CacheContent<BlockNode>(n, res));
            }
            treeCache.put(new TreeKey(user, uid), new CacheContent<BlockTree>(tree, resTree));

        }

        @Override
        public void updateToLatest(BlockTree tree) throws Exception {
            BlockTree newest = fetchNewestTreeAndAwait(uid, user);
            tree.root = newest.root;
        }

        @Override
        public void updateToLatest(BlockTree tree, int minVersion) throws Exception {
            BlockTree newest = fetchTreeMinVersion(uid, user, minVersion);
            tree.root = newest.root;
        }

        @Override
        public BlockNode loadNodeWithVersionForInsert(long blockid, int version) throws Exception {
            CacheContent<BlockNode> node = loadNodeCache(blockid);
            if (node.content.getVersion() != version) {
                blockCache.invalidate(new NodeKey(user, uid, blockid));
                node = loadNodeCache(blockid);
            } else {
                node.awaitDBWrite();
            }

            return node.content;
        }

        @Override
        public BlockNode loadNodeWithMinVersion(long blockid, int version) throws Exception {
            BlockNode node = loadNode(blockid);
            if (node.getVersion() < version) {
                blockCache.invalidate(new NodeKey(user, uid, blockid));
                node = loadNode(blockid);
            }
            return node;
        }
    }
}
