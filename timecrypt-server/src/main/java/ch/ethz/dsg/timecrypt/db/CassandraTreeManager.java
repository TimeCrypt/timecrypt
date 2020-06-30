/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.db;

import ch.ethz.dsg.timecrypt.exceptions.TimeCryptTreeAlreadyExistsException;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptTreeException;
import ch.ethz.dsg.timecrypt.index.ITreeManager;
import ch.ethz.dsg.timecrypt.index.UserStreamTree;
import ch.ethz.dsg.timecrypt.index.blockindex.BlockTree;
import ch.ethz.dsg.timecrypt.index.blockindex.IBlockTreeFetcher;

public class CassandraTreeManager implements ITreeManager {

    private int k;


    private CassandraBlockTreeManager blockTree;
    private CassandraDatabaseManager db;

    public CassandraTreeManager(CassandraBlockTreeManager blockTree, CassandraDatabaseManager db) {
        this(blockTree, db, 64);
    }

    public CassandraTreeManager(CassandraBlockTreeManager blockTree, CassandraDatabaseManager db, int k) {
        this.blockTree = blockTree;
        this.db = db;
        this.k = k;
    }

    @Override
    public UserStreamTree createTree(long uid, String user) throws TimeCryptTreeException {
        UserStreamTree result = null;
        try {
            if (blockTree.treeExistsInCache(uid, user))
                throw new TimeCryptTreeAlreadyExistsException("Stream already exists.", 1);
            else {
                if (db.checkTreeExists(user, uid))
                    throw new TimeCryptTreeAlreadyExistsException("Stream already exists.", 1);
            }

            BlockTree tree = blockTree.createTree(uid, user, k, 1);
            result = new UserStreamTree(user, uid, tree);

        } catch (Exception e) {
            throw new TimeCryptTreeException(e.getMessage(), 1);
        }
        return result;
    }

    @Override
    public boolean deleteTree(long uid, String user) throws TimeCryptTreeException {
        try {
            db.deleteAllIndexFor(user, uid);
        } catch (Exception e) {
            throw new TimeCryptTreeException(e.getMessage(), 1);
        }
        return true;
    }

    @Override
    public UserStreamTree getTreeForUser(long uid, String user) throws TimeCryptTreeException {
        UserStreamTree result = null;
        try {
            BlockTree tree = blockTree.fetchTree(uid, user);
            result = new UserStreamTree(user, uid, tree);

        } catch (Exception e) {
            throw new TimeCryptTreeException(e.getMessage(), 1);
        }

        return result;
    }

    @Override
    public UserStreamTree getTreeForUser(long uid, String user, int minVersion) throws TimeCryptTreeException {
        UserStreamTree result = null;
        try {
            BlockTree tree = blockTree.fetchTreeMinVersion(uid, user, minVersion);
            result = new UserStreamTree(user, uid, tree);
        } catch (Exception e) {
            throw new TimeCryptTreeException(e.getMessage(), 1);
        }
        return result;
    }

    @Override
    public void invalidateCache() {
        blockTree.invalidateCache();
    }
}
