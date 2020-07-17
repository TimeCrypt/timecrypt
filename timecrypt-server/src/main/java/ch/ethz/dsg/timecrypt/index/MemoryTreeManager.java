/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

import ch.ethz.dsg.timecrypt.index.blockindex.IBlockTreeFetcher;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptTreeException;

import java.util.*;

public class MemoryTreeManager implements ITreeManager {

    private IBlockTreeFetcher treeFetcher;
    private int k = 64;
    private Map<String, Set<Long>> userToStreams = Collections.synchronizedMap(new HashMap<String, Set<Long>>());
    private Map<String, UserStreamTree> userAndUidToTree = Collections.synchronizedMap(new HashMap<String, UserStreamTree>());

    public MemoryTreeManager(IBlockTreeFetcher fetcher, int k) {
        this.treeFetcher = fetcher;
        this.k = k;
    }

    public MemoryTreeManager(IBlockTreeFetcher fetcher) {
        this(fetcher, 64);
    }

    @Override
    public UserStreamTree createTree(long uid, String user) throws TimeCryptTreeException {
        Set<Long> cur = userToStreams.get(user);
        if (cur == null) {
            cur = new HashSet<Long>();
            userToStreams.put(user, cur);
        } else {
            if (cur.contains(uid))
                throw new TimeCryptTreeException("Tree already exists", 1);
        }

        UserStreamTree tree = null;
        try {
            tree = new UserStreamTree(user, uid, treeFetcher.createTree(uid, user, k, 1));
        } catch (Exception e) {
            throw new TimeCryptTreeException(e.getMessage(), 1);
        }

        cur.add(uid);
        userAndUidToTree.put(String.format("%s%d", user, uid), tree);
        return tree;
    }

    @Override
    public boolean deleteTree(long uid, String user) throws TimeCryptTreeException {
        Set<Long> cur = userToStreams.get(user);
        if (cur == null) {
            return true;
        }
        cur.remove(uid);
        userAndUidToTree.remove(String.format("%s%d", user, uid));
        return true;
    }

    public UserStreamTree getTreeForUser(long uid, String user) throws TimeCryptTreeException {
        UserStreamTree tree = userAndUidToTree.get(String.format("%s%d", user, uid));
        if (tree == null)
            throw new TimeCryptTreeException("Tree does not exists", 1);
        return tree;
    }

    @Override
    public UserStreamTree getTreeForUser(long uid, String user, int minVersion) throws TimeCryptTreeException {
        return getTreeForUser(uid, user);
    }

    @Override
    public void invalidateCache() {

    }
}
