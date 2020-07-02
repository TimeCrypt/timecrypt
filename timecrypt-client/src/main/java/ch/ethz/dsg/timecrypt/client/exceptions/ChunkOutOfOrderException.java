/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception that occurs when it is tried to write a value to a chunk that was already encrypted and is therefore
 * not writable anymore.
 */
public class ChunkOutOfOrderException extends TCWriteException {

    private final long chunkID;
    private final long nextChunkId;

    /**
     * Create a new exception for a value that was tried to write while the corresponding chunk was already inserted
     *
     * @param chunkID     The chunk ID of the chunk that should have been added to the chunk store.
     * @param nextChunkId The next chunk ID that the chunk store expected.
     */
    public ChunkOutOfOrderException(long chunkID, long nextChunkId) {
        super("Tried to add chunk ID " + chunkID + " to the chunk store but next expected chunk ID was " + nextChunkId);
        this.chunkID = chunkID;
        this.nextChunkId = nextChunkId;
    }

    /**
     * Get the chunk ID of the chunk that should have been added to the chunk store.
     *
     * @return The chunk ID of the chunk that should have been added to the chunk store.
     */
    public long getChunkID() {
        return chunkID;
    }

    /**
     * Get the chunk ID of the next expected chunk in the chunk store.
     *
     * @return The chunk ID of the next expected chunk in the chunk store.
     */
    public long getNextChunkId() {
        return nextChunkId;
    }
}
