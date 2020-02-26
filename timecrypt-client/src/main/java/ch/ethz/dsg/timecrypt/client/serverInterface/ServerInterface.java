/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.exceptions.UnsupportedOperationException;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;

import java.util.List;

/**
 * Abstraction for the server interface. This allows mocking and different implementations of the TimeCrypt protocol.
 */
public interface ServerInterface {
    /**
     * Creates a new stream on the server. The server needs to know the meta data configuration of the stream and will
     * assign a stream ID.
     *
     * @param metadataConfig A list of meta data containing their encryption schema as well as the ID of the meta data
     *                       in the stream. Watch out: Meta data also store their type but the server does not
     *                       necessarily need it.
     * @return The ID that was assigned to the stream by the server.
     * @throws CouldNotStoreException Exception that is raised if the server could not store the stream or something
     *                                went wrong in the communication with the server.
     */
    long createStream(List<StreamMetaData> metadataConfig) throws CouldNotStoreException;

    /**
     * Returns the servers understanding of the last written chunk. This is important because TimeCrypt can not
     * operate with missing values inside its data stream.
     *
     * @param streamId The stream Id whose last written chunk is queried.
     * @return The ID of the last written chunk.
     * @throws InvalidQueryException Exception that is raised if the last written chunk can not be queried.
     */
    long getLastWrittenChunkId(long streamId) throws InvalidQueryException, UnsupportedOperationException;

    /**
     * Add a new chunk to the stream on the server.
     *
     * @param streamId The ID of the stream for which the chunk shall be inserted.
     * @param chunk    The already encrypted chunk.
     * @param digest   The digest associated with this chunk.
     * @return The chunk ID that the server assigned to this chunk after insertion. It should match the chunk ID inside
     * the provided chunk otherwise there is an inconsistency between the server and the client.
     * @throws CouldNotStoreException Exception that is raised if the chunk could not be stored on the server.
     */
    long addChunk(long streamId, EncryptedChunk chunk, EncryptedDigest digest) throws CouldNotStoreException;

    /**
     * Receive raw chunks from the server.
     *
     * @param streamId    The ID of the  stream for which the chunk shall be fetched.
     * @param chunkIdFrom The start of the interval over which this request is performed. The chunk whose ID marks
     *                    the start of the query range shall be INCLUDED into the result.
     * @param chunkIdTo   The end of the interval over which this request is performed. The chunk whose ID marks
     *                    the end of the query range shall be EXCLUDED from the result.
     * @return The encrypted chunk that was returned by the server.
     * @throws CouldNotReceiveException Exception that is raised if the chunk could not be received from the server.
     */
    List<EncryptedChunk> getChunks(long streamId, long chunkIdFrom, long chunkIdTo) throws CouldNotReceiveException;

    /**
     * Delete a stream on the server.
     *
     * @param streamId The ID of the stream to delete.
     */
    void deleteStream(long streamId) throws InvalidQueryException;

    /**
     * Receive statistical data from the server. The statistical data is stored in encrypted digests on the server
     * and can be aggregated there using since it is using a partially homomorphic-encryption-based access control
     * scheme (HEAC). This request allows to define a certain granularity that should be returned by the server (number
     * of chunks that are aggregated into one specific digest that is returned.
     * <p>
     * Also the request can contain only a subset of all stream meta data if the client only needs certain values
     * for its aggregations.
     *
     * @param streamId    The ID of the stream to query.
     * @param chunkIdFrom The start of the interval over which this request is performed. The chunk whose ID marks
     *                    the start of the query range shall be INCLUDED into the result.
     * @param chunkIdTo   The end of the interval over which this request is performed. The chunk whose ID marks
     *                    the end of the query range shall be EXCLUDED from the result.
     * @param granularity The number of chunks that shall be aggregated into one digest returned by the server.
     * @param metaData    A list of stream meta data that should be included in the server response containing
     *                    the expected meta data encryption schema as well as the ID of the meta data
     *                    in the stream. Watch out: Meta data also store their type but the server does not
     *                    necessarily need it.
     * @return A List of Encrypted digests that represent the servers response.
     * @throws InvalidQueryException Exception that is raised if the server did not understand this request.
     */
    List<EncryptedDigest> getStatisticalData(long streamId, long chunkIdFrom, long chunkIdTo, int granularity,
                                             List<StreamMetaData> metaData) throws InvalidQueryException;
}
