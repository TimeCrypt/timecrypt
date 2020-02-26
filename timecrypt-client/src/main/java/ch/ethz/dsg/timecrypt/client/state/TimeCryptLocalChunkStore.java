/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.state;

import ch.ethz.dsg.timecrypt.client.exceptions.ChunkOutOfOrderException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import org.apache.commons.lang3.tuple.Pair;

import java.util.SortedMap;

/**
 * The local chunk store is used to store information about chunks that have not been send to the server. It shall
 * prevent chunks from being send again twice with different content and by this revealing their secret keys.
 * <p>
 * It also stores the ID of the last written Chunk locally so it can react to unwritten chunks in case of a restart
 * of a producer. The last written chunk ID is the ID of the chunk that the server last acknowledged to be received.
 */
public interface TimeCryptLocalChunkStore {

    /**
     * Retrive the ID of the last chunk that the server acknowledged to be received.
     *
     * @return The chunk ID. Of the last written stream or a negative number if no chunk was written so far.
     */
    long getLastWrittenChunkId();

    /**
     * Add an unwritten Chunk. Unwritten means that the chunk was not yet send to the server. It is important that the
     * chunk gets stored in the Local Chunk store before being send so in case of a power outage or similar reasons
     * there will never be two different chunks that are send to the server with the same encryption key.
     *
     * @param chunk  The encrypted chunk that is about to be send.
     * @param digest The encrypted digest that is about to be send.
     * @throws CouldNotStoreException   Exceptions that might occur are problems that come up while saving
     * @throws ChunkOutOfOrderException Exceptions that indicate that the chunk that shall be written to the chunk
     *                                  store has an ID that is smaller than the ID of the last written Chunk.
     */
    void addUnwrittenChunk(EncryptedChunk chunk, EncryptedDigest digest) throws CouldNotStoreException,
            ChunkOutOfOrderException;

    /**
     * Return all chunks that are not yet acknowledged by the server.
     *
     * @return A map of encrypted chunks / digests for writing to the server.
     */
    SortedMap<Long, Pair<EncryptedChunk, EncryptedDigest>> getUnwrittenChunks();

    /**
     * Mark a chunk as acknowledged by the server. This means the last written chunk ID will incremented and the chunk
     * will be removed from the list of unwritten chunks.
     *
     * @param chunkId The chunk ID.
     * @throws ChunkOutOfOrderException Throws an exception if the chunks are not acknowledged in order or if the
     *                                  chunk Id is unknown to the chunk storage.
     * @throws CouldNotStoreException   Exceptions that might occur are problems that come up while saving
     */
    void markChunkAsWritten(long chunkId) throws ChunkOutOfOrderException, CouldNotStoreException;

    /**
     * Delete this chunk store
     */
    void deleteChunkStore();
}
