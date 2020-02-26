/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

import ch.ethz.dsg.timecrypt.exceptions.TimeCryptStorageException;

import java.util.List;

public interface IStorage {

    Chunk getChunk(long uid, String owner, int key) throws TimeCryptStorageException;

    List<Chunk> getChunks(long uid, String owner, int from, int to) throws TimeCryptStorageException;

    boolean putChunk(long uid, String owner, Chunk chunk) throws TimeCryptStorageException;

    boolean deleteChunk(long uid, String owner, int key) throws TimeCryptStorageException;

    boolean deleteALL(long uid, String owner) throws TimeCryptStorageException;

}
