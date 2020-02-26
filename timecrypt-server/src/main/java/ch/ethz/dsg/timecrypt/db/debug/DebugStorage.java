/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.db.debug;

import ch.ethz.dsg.timecrypt.index.Chunk;
import ch.ethz.dsg.timecrypt.index.IStorage;
import ch.ethz.dsg.timecrypt.index.KeyUtil;
import ch.ethz.dsg.timecrypt.exceptions.TimeCryptStorageException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugStorage implements IStorage {

    public Map<String, byte[]> keys = new HashMap<String, byte[]>();

    public Chunk getChunk(String key) {
        return new Chunk(KeyUtil.returnID(key), keys.get(key));
    }

    public Chunk getChunk(long uid, String owner, int key) {
        return getChunk(KeyUtil.deriveKey(uid, owner, key));
    }

    @Override
    public List<Chunk> getChunks(long uid, String owner, int from, int to) throws TimeCryptStorageException {
        return null;
    }

    public boolean putChunk(String key, Chunk chunk) {
        keys.put(key, chunk.getData());
        return true;
    }

    public boolean putChunk(long uid, String owner, Chunk chunk) {
        return putChunk(KeyUtil.deriveKey(uid, owner, chunk.getStorageKey()), chunk);
    }

    @Override
    public boolean deleteChunk(long uid, String owner, int key) throws TimeCryptStorageException {
        byte[] before = keys.remove(KeyUtil.deriveKey(uid, owner, key));
        return before != null;
    }

    @Override
    public boolean deleteALL(long uid, String owner) throws TimeCryptStorageException {
        return true;
    }
}
