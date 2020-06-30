/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.db;

import ch.ethz.dsg.timecrypt.exceptions.TimeCryptStorageException;
import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.IStorage;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class CassandraStorage implements IStorage {

    private CassandraDatabaseManager databaseManager;

    public CassandraStorage(CassandraDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Chunk getChunk(long uid, String owner, int key) throws TimeCryptStorageException {
        List<Chunk> chunks = getChunks(uid, owner, key, key + 1);
        if (chunks == null || chunks.size() != 1)
            throw new TimeCryptStorageException("No Chunk found", 1);
        return chunks.get(0);
    }

    @Override
    public List<Chunk> getChunks(long uid, String owner, int from, int to) throws TimeCryptStorageException {
        List<Chunk> chunks;
        try {
            CompletionStage<AsyncResultSet> res = databaseManager.loadChunks(owner, uid, from, to);
            chunks = databaseManager.getChunks(res);
        } catch (Exception e) {
            throw new TimeCryptStorageException(e.getMessage(), 1);
        }
        if (chunks == null)
            throw new TimeCryptStorageException("No Chunk found", 1);
        return chunks;
    }


    @Override
    public boolean putChunk(long uid, String owner, Chunk chunk) throws TimeCryptStorageException {
        try {
            // TODO: should check for success or async
            CompletionStage<AsyncResultSet> res = databaseManager.insertChunk(owner, uid, chunk);
        } catch (Exception e) {
            throw new TimeCryptStorageException(e.getMessage(), 1);
        }
        return true;
    }

    @Override
    public boolean deleteChunk(long uid, String owner, int key) throws TimeCryptStorageException {
        // Not Implemented
        return true;
    }

    @Override
    public boolean deleteALL(long uid, String owner) throws TimeCryptStorageException {
        try {
            CompletionStage<AsyncResultSet> res = databaseManager.deleteAllChunksFor(owner, uid);
        } catch (Exception e) {
            throw new TimeCryptStorageException(e.getMessage(), 1);
        }

        return true;
    }
}
