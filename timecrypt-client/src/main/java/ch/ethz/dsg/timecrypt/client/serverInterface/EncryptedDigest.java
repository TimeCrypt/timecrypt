/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

/**
 * The transport representation of a digest.
 * <p>
 * The digest that holds a vector of encoded data that has pre computed values for the chunk or higher aggregations..
 * Since the homomorphic-encryption-based access control scheme (HEAC) is additively homomorphic, it
 * supports secure aggregation of cipher texts. However the encrypted digest will only handle a byte array and not take
 * care of encryption.
 */
public class EncryptedDigest {

    final long streamId;
    final long chunkIdFrom;
    final long chunkIdTo;
    final List<EncryptedMetadata> payload;

    /**
     * Create a new encrypted digest.
     *
     * @param streamId    The stream ID of the stream this digest relates to.
     * @param chunkIdFrom The start of the interval over which this chunk is aggregating. The chunk whose ID marks
     *                    the start of the query range will be INCLUDED into the result is.
     * @param chunkIdTo   The end of the interval over which this this chunk is aggregating . The chunk whose ID marks
     *                    the end of the query range be be INCLUDED from the result.
     * @param payload     A list of transport representations of the metadata item values.
     */
    @JsonCreator
    public EncryptedDigest(long streamId, long chunkIdFrom, long chunkIdTo, List<EncryptedMetadata> payload) {
        this.streamId = streamId;
        this.chunkIdFrom = chunkIdFrom;
        this.chunkIdTo = chunkIdTo;
        this.payload = payload;
    }

    /**
     * Gets the ID of the first chunk that is included in the aggregations inside of this digest.
     *
     * @return The first chunks chunk ID.
     */
    public long getChunkIdFrom() {
        return chunkIdFrom;
    }

    /**
     * Gets the ID of the last chunk that is included in the aggregations inside of this digest.
     *
     * @return The last chunks chunk ID.
     */
    public long getChunkIdTo() {
        return chunkIdTo;
    }

    /**
     * Gets the list of metadata items (values of certain meta data in this range) in their transport representation.
     *
     * @return A list of encrypted metadata.
     */
    public List<EncryptedMetadata> getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "EncryptedDigest{" +
                "streamId=" + streamId +
                ", chunkIdFrom=" + chunkIdFrom +
                ", chunkIdTo=" + chunkIdTo +
                ", payload=" + payload +
                '}';
    }
}
