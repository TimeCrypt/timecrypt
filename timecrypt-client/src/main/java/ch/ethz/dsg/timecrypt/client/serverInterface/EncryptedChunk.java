/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.codec.binary.Hex;

/**
 * The transport representation of a chunk that summarizes several data points.
 * <p>
 * The chunks are encrypted with standard symmetric encryption while the digests are encrypted with HEAC. However the
 * Encrypted chunk will only handle a byte array and not take care of encryption.
 * <p>
 * Each data chunk also corresponds to an encrypted digest that holds a vector of encoded data that has pre computed
 * values for the chunk.
 */
public class EncryptedChunk {
    private final long streamId;
    private final long chunkId;
    private final byte[] payload;

    /**
     * Constructs a new encrypted chunk with a byte array containing the encrypted values.
     *
     * @param streamId The stream ID of the stream this chunk relates to.
     * @param chunkId  The chunk ID in the stream.
     * @param payload  A byte array that shall contain the encrypted chunk.
     */
    @JsonCreator
    public EncryptedChunk(long streamId, long chunkId, byte[] payload) {
        this.chunkId = chunkId;
        this.streamId = streamId;
        this.payload = payload;
    }

    /**
     * Return the encrypted values stored in this chunk.
     *
     * @return The encrypted transport representation of a chunk.
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Get the ID of this chunk in its corresponding stream.
     *
     * @return The ID of this chunk in the stream.
     */
    public long getChunkId() {
        return chunkId;
    }

    /**
     * Get the ID of the stream that this chunk belongs to.
     *
     * @return The ID of the stream this chunk belongs to.
     */
    public long getStreamId() {
        return streamId;
    }

    @Override
    public String toString() {
        return "EncryptedChunk{" +
                "streamId=" + streamId +
                ", chunkId=" + chunkId +
                ", payload=" + Hex.encodeHexString(payload) +
                '}';
    }

}
