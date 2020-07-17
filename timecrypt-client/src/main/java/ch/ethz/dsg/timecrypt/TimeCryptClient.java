/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.exceptions.*;
import ch.ethz.dsg.timecrypt.client.queryInterface.Interval;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterfaceFactory;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.*;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * The Client API for interacting with TimeCrypt, a system that provides scalable and real-time analytics over large
 * volumes of encrypted time series data.
 * <p>
 * The client takes care of the encryption / decryption of any values that are stored on the TimeCrypt server and
 * provides a query interface for interaction.
 */
public class TimeCryptClient {

    /**
     * The write window defines an interval in which data points can be inserted to the stream. While inside of the
     * write window data points are only stored on the client. If the data point is at least one write window in the
     * past they get encrypted and stored on the server.
     * <p>
     * The write window also defines how much in the future data points are accepted. This is needed since all data
     * points will be send to the server before terminating the client and since the chunks in TimeCrypt have to be
     * continuous and can only be written once a very data point in the very distant future would result in all
     * chunks until that time being written to the server.
     */
    public static final TimeUtil.Precision CHUNK_WRITE_WINDOW = TimeUtil.Precision.TEN_SECONDS;

    // TODO: These values could be made configurable in something like an advanced stream creation.
    private final static int CHUNK_KEY_STREAM_DEPTH = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeCryptClient.class);

    private final TimeCryptKeystore keyStore;
    private final TimeCryptProfile profile;

    private final List<InsertHandler> openInsertHandlers = Collections.synchronizedList(new ArrayList<InsertHandler>());
    private final ServerInterface serverInterface;
    public ServerInterface getServerInterface() {
        return serverInterface;
    }

    /**
     * Create a new TimeCrypt client. The server a profile and a keyStore have to be provided.
     * <p>
     * The profile will be used to store all private metadata about streams (e.g. their start timestamp or the
     * meaning of their aggregatable meta data (digests). It also provides login information for the TimeCrypt server.
     * <p>
     * The key store is used for secure storing all TimeCrypt related keys.
     *
     * @param keyStore A TimeCrypt Keystore for key handling.
     * @param profile  A profile for this client.
     */
    public TimeCryptClient(TimeCryptKeystore keyStore, TimeCryptProfile profile) {
        this.keyStore = keyStore;
        this.profile = profile;

        try {
            this.serverInterface = ServerInterfaceFactory.getServerInterface(profile);
        } catch (IOException e) {
            LOGGER.error("Could not initiate ServerInterface", e);
            // TODO: Better error
            throw new RuntimeException("Could not initiate ServerInterface " + e.getMessage());
        }
    }

    /**
     * Create a new stream.
     *
     * @param name                A name for the stream - this will not be exposed to the server and only be used for
     *                            display purposes.
     * @param description         A description for the stream - this will not be exposed to the server and only be used
     *                            for display purposes.
     * @param chunkSize           The size of the raw data chunks. This determines also the minimal size of fast
     *                            statistical queries to the server.
     * @param resolutionLevels    The aggregation levels for stream sharing.
     * @param metaDataTypes       The kind of meta data that should be stored on the server (encrypted). The meta data
     *                            that is stored determines which statistical queries can be executed without scanning
     *                            all raw data.
     * @param encryptionScheme    The metadata storage algorithm defines the security but also the performance of
     *                            the metadata encryption wit HEAC.
     * @param localChunkStorePath The path to the local chunk store. In this local chunk store all chunks will be stored
     *                            before sending them to the server. This shall prevent that chunks will be send twice
     *                            with different payload and by this expose their keys.
     * @return The ID that was assigned to the stream by the server
     * @throws CouldNotStoreException The server did not allow to store the stream or there were issues with key
     *                                creation on the client.
     * @throws IOException            Exception that is thrown if the local chunk store could not be created.
     */
    public long createStream(String name, String description, TimeUtil.Precision chunkSize,
                             List<TimeUtil.Precision> resolutionLevels,
                             List<StreamMetaData.MetadataType> metaDataTypes,
                             StreamMetaData.MetadataEncryptionScheme encryptionScheme,
                             String localChunkStorePath,
                             Date startDate) throws CouldNotStoreException, IOException {

        // TODO: This could be based on random so the access pattern is not so obvious.
        List<StreamMetaData> metaData = new ArrayList<>();
        int metadataId = 0;
        for (StreamMetaData.MetadataType type : metaDataTypes) {
            metaData.add(MetaDataFactory.getMetadataOfType(metadataId, type, encryptionScheme));
            metadataId++;
        }

        SecretKey streamMasterKey;
        try {
            streamMasterKey = KeyGenerator.getInstance("AES").generateKey();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Could not generate the stream key.", e);
            throw new CouldNotStoreException("Could not generate the stream key.");
        }

        long streamId = serverInterface.createStream(metaData);

        try {
            keyStore.storeStreamKey(profile.getProfileName() + streamId, streamMasterKey);
            keyStore.syncKeystore(true);
        } catch (Exception e) {
            LOGGER.error("Could not store the stream key in the keystore", e);
            try {
                serverInterface.deleteStream(streamId);
            } catch (InvalidQueryException ex) {
                LOGGER.error("Could delete stream on server after error during key creation on client", ex);
            }
            throw new CouldNotStoreException("Could not store the stream key in the keystore");
        }

        Stream stream = null;
        if (startDate == null)
            stream = new Stream(streamId, name, description, chunkSize, resolutionLevels, metaData,
                    localChunkStorePath);
        else
            stream = new Stream(streamId, name, description, chunkSize, resolutionLevels, metaData,
                    localChunkStorePath, startDate);

        profile.addStream(stream);
        try {
            if (!profile.syncProfile(false)) {
                throw new CouldNotStoreException("Could not store the new stream in profile");
            }
        } catch (Exception e) {
            LOGGER.error("Error storing new stream in profile", e);
            try {
                serverInterface.deleteStream(streamId);
            } catch (InvalidQueryException ex) {
                LOGGER.error("Could delete stream on server after error during storing profile on client", ex);
            }
            throw new CouldNotStoreException("Could not store the new stream in profile");
        }

        return streamId;
    }

    /**
     * Create a new stream.
     *
     * @param name                A name for the stream - this will not be exposed to the server and only be used for
     *                            display purposes.
     * @param description         A description for the stream - this will not be exposed to the server and only be used
     *                            for display purposes.
     * @param chunkSize           The size of the raw data chunks. This determines also the minimal size of fast
     *                            statistical queries to the server.
     * @param resolutionLevels    The aggregation levels for stream sharing.
     * @param metaDataTypes       The kind of meta data that should be stored on the server (encrypted). The meta data
     *                            that is stored determines which statistical queries can be executed without scanning
     *                            all raw data.
     * @param encryptionScheme    The metadata storage algorithm defines the security but also the performance of
     *                            the metadata encryption wit HEAC.
     * @param localChunkStorePath The path to the local chunk store. In this local chunk store all chunks will be stored
     *                            before sending them to the server. This shall prevent that chunks will be send twice
     *                            with different payload and by this expose their keys.
     * @return The ID that was assigned to the stream by the server
     * @throws CouldNotStoreException The server did not allow to store the stream or there were issues with key
     *                                creation on the client.
     * @throws IOException            Exception that is thrown if the local chunk store could not be created.
     */
    public long createStream(String name, String description, TimeUtil.Precision chunkSize,
                             List<TimeUtil.Precision> resolutionLevels,
                             List<StreamMetaData.MetadataType> metaDataTypes,
                             StreamMetaData.MetadataEncryptionScheme encryptionScheme,
                             String localChunkStorePath) throws CouldNotStoreException, IOException {

        return this.createStream(name, description, chunkSize, resolutionLevels,
                metaDataTypes, encryptionScheme, localChunkStorePath, null);
    }

    public long createStream(String name, String description, TimeUtil.Precision chunkSize,
                             List<TimeUtil.Precision> resolutionLevels,
                             StreamMetaData.MetadataEncryptionScheme encryptionScheme,
                             String localChunkStorePath) throws CouldNotStoreException, IOException {

        return this.createStream(name, description, chunkSize, resolutionLevels,
                DefaultConfigs.getDefaultMetaDataConfig(), encryptionScheme, localChunkStorePath, null);
    }

    /**
     * Get all streams that are known to this client.
     *
     * @return A map of stream IDs with their corresponding streams.
     */
    public Map<Long, Stream> listStreams() {
        return profile.getStreams();
    }

    /**
     * Get a specific stream.
     *
     * @param streamId The ID of the stream to receive.
     * @return The stream for the given ID.
     * @throws CouldNotReceiveException Exception if there is no stream for the given ID.
     */
    public Stream getStream(long streamId) throws CouldNotReceiveException {
        if (profile.getStreams().containsKey(streamId)) {
            return profile.getStreams().get(streamId);
        }
        throw new CouldNotReceiveException("No known stream with the given ID");
    }

    /**
     * Store a data point in a stream. Data points should only be in one write window aground the current time (so
     * maximal one write window in the past or one write window in the future or one write window into the past.
     * <p>
     * If it is the first data point that is inserted for this TimeCryptClient the client will also start a chunk
     * handler thread which will ensure that all chunks before the current write window are stored on the server.
     * The chunk handler thread will also write empty chunks to the server if there were no data points in the time
     * interval that is represented by the chunk.
     * The producer thread will run until the clients termination.
     *
     * @param streamId  The ID of the stream to write the values to.
     * @param dataPoint The Data point to write to the stream.
     * @throws CouldNotStoreException                 Indicates that it was not possible to store the given data point
     *                                                on the server.
     * @throws StreamNotYetStartedException           Indicates that the data point that should be added is before the
     *                                                start of the stream.
     * @throws ChunkAlreadyWrittenException           Indicates that the data point that should have been added is in
     *                                                a chunk that was already send to the server.
     * @throws DataPointOutsideOfWriteWindowException Indicates that the data point is ahead of the current write
     *                                                window.
     * @throws DuplicateDataPointException            Indicates that there was already a value for this exact point in
     *                                                time (with a granularity of one millisecond (or less if your JVM
     *                                                does not support such fine grained times)).
     */
    public void addDataPointLiveToStream(long streamId, DataPoint dataPoint) throws TCWriteException {
        synchronized (this) {
            InsertHandler handler = getHandlerForLiveInsert(streamId);
            handler.writeDataPointToStream(dataPoint);
        }
    }

    private boolean checkHandlerExists(long streamID) {
        for (InsertHandler handler : openInsertHandlers) {
            if (handler.getStreamID() == streamID && handler instanceof TCLiveWriteHandler)
                return true;
        }
        return false;
    }

    private InsertHandler getHandlerWith(long streamID) {
        for (InsertHandler handler : openInsertHandlers) {
            if (handler.getStreamID() == streamID && handler instanceof TCLiveWriteHandler)
                return handler;
        }
        return null;
    }

    /**
     * Creates a insert stream handler for live inserts.
     * This handler can be used to perform on the fly inserts from e.g. a sensor.
     * The handler serializes the stream into fixed time windows and takes care of encryption.
     *
     * @param streamId the stream id the handler should insert to.
     * @return a TC insert handler for live inserts.
     */
    public InsertHandler getHandlerForLiveInsert(long streamId) {
        return this.getHandlerForLiveInsert(streamId, CHUNK_WRITE_WINDOW.getMillis());
    }

    /**
     * Creates a insert stream handler for live inserts.
     * This handler can be used to perform on the fly inserts from e.g. a sensor.
     * The handler serializes the stream into fixed time windows and takes care of encryption.
     *
     * @param streamId      the stream id the handler should insert to.
     * @param writeWindowMS data within the write window (milliseconds) is kept local for out of order inserts
     * @return a TC insert handler for live inserts.
     */
    public InsertHandler getHandlerForLiveInsert(long streamId, long writeWindowMS) {
        if (checkHandlerExists(streamId))
            return getHandlerWith(streamId);
        TCLiveWriteHandler streamHandler = null;
        try {
            streamHandler = new TCLiveWriteHandler(profile.getStream(streamId),
                    new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                            streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface, writeWindowMS, openInsertHandlers);
        } catch (CouldNotReceiveException e) {
            e.printStackTrace();
        } catch (InvalidQueryException e) {
            e.printStackTrace();
        }
        return streamHandler;
    }

    /**
     * Creates a insert stream handler for backup inserts.
     * This handler can be used to insert data from the past into the stream.
     *
     * @param streamId        the stream id the handler should insert to.
     * @param backupStartTime the start time of the data that is to be inserted.
     * @return a TC insert handler for backup inserts.
     */
    public InsertHandler getHandlerForBackupInsert(long streamId, Date backupStartTime) throws CouldNotReceiveException, InvalidQueryException, IOException {
        return new BackupHandler(profile.getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), this.serverInterface, backupStartTime, openInsertHandlers);
    }

    /**
     * Creates a insert stream handler for bench inserts.
     * @param streamId        the stream id the handler should insert to.
     * @param backupStartTime the start time of the data that is to be inserted.
     * @return a TC insert handler for backup inserts.
     */
    public InsertHandler getHandlerForInsertBench(long streamId, Date backupStartTime) throws CouldNotReceiveException, InvalidQueryException, IOException {
        return new BenchInsertHandler(profile.getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), this.serverInterface, backupStartTime, openInsertHandlers);
    }

    /**
     * Terminates all open insertHandlers created by this client
     *
     * @throws InterruptedException
     */
    public void terminateAllHandlers() {
        for (InsertHandler handler : openInsertHandlers) {
            try {
                handler.terminate();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Retrieve a chunk from a stream on the server and decrypt it.
     *
     * @param streamId    The stream ID of the stream that the chunk belongs to.
     * @param chunkIdFrom The chunk ID of the first chunk that shall be retrieved (INCLUSIVE).
     * @param chunkIdTo   The chunk ID of the last chunk that shall be retrieved (INCLUSIVE).
     * @return A list of decrypted chunks.
     * @throws CouldNotReceiveException Something went wrong with the retrieval of the chunk.
     * @throws InvalidQueryException    Could not receive the key to decrypt the chunks.
     * @throws QueryFailedException     Something went wrong with the decryption of the chunk.
     */
    public List<Chunk> getChunks(long streamId, long chunkIdFrom, long chunkIdTo) throws CouldNotReceiveException,
            InvalidQueryException, QueryFailedException {
        Stream stream = getStream(streamId);
        List<Chunk> chunks = new ArrayList<>();

        for (EncryptedChunk encryptedChunk : serverInterface.getChunks(stream.getId(), chunkIdFrom, chunkIdTo + 1))
            chunks.add(new Chunk(stream, encryptedChunk.getChunkId(), encryptedChunk.getPayload(),
                    new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                            streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH)));
        return chunks;
    }

    /**
     * Delete a stream on the server.
     *
     * @param streamId The stream to delete.
     * @throws InvalidQueryException There is no stream with the given ID.
     */
    public void deleteStream(long streamId) throws InvalidQueryException {
        serverInterface.deleteStream(streamId);
        Map<Long, Stream> streams = profile.getStreams();
        for (long stream : streams.keySet()) {
            streams.get(stream).getLocalChunkStore().deleteChunkStore();
        }
        profile.deleteStream(streamId);
    }

    /**
     * Perform a statistical query on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param from           The start time of the query. Data points at exactly this time will be INCLUDED in the
     *                       result of the query.
     * @param to             The end time of the query. Data points at exactly this time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOp        The operation that should be performed on the stream in the selected interval.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @return An interval representing the result of the query.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public Interval performQuery(long streamId, Date from, Date to, Query.SupportedOperation queryOp, boolean
            allowChunkScan) throws CouldNotReceiveException, InvalidQueryException, QueryNeedsChunkScanException,
            QueryFailedException, InvalidQueryIntervalException {
        return Query.performQuery(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface, from, to, queryOp, allowChunkScan);
    }

    /**
     * Perform a statistical query on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param from           The start time of the query. Data points at exactly this time will be INCLUDED in the
     *                       result of the query.
     * @param to             The end time of the query. Data points at exactly this time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOps       The operations that should be performed on the stream in the selected interval.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @return A list of interval representing the results of the query in order.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public Interval performQuery(long streamId, Date from, Date to, List<Query.SupportedOperation> queryOps, boolean
            allowChunkScan) throws CouldNotReceiveException, InvalidQueryException, QueryNeedsChunkScanException,
            QueryFailedException, InvalidQueryIntervalException {
        return Query.performQuery(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface, from, to, queryOps, allowChunkScan);
    }

    /**
     * Perform a statistical query on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param chunkIdFrom    The start chunk ID of the query. Data points in this chunk time will be INCLUDED in the
     *                       result of the query.
     * @param chunkIdTo      The end chunk ID of the query. Data points in this chunk time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOp        The operation that should be performed on the stream in the selected interval.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @return An interval representing the result of the query.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public Interval performQueryForChunkId(long streamId, long chunkIdFrom, long chunkIdTo, Query.SupportedOperation queryOp, boolean
            allowChunkScan) throws CouldNotReceiveException, InvalidQueryException, QueryNeedsChunkScanException,
            QueryFailedException, InvalidQueryIntervalException {
        return Query.performQuery(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                        streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface,
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdFrom)),
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdTo)), queryOp, allowChunkScan);
    }

    /**
     * Perform a statistical query on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param chunkIdFrom    The start chunk ID of the query. Data points in this chunk time will be INCLUDED in the
     *                       result of the query.
     * @param chunkIdTo      The end chunk ID of the query. Data points in this chunk time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOps       The operations that should be performed on the stream in the selected interval.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @return An interval representing the result of the query.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public Interval performQueryForChunkId(long streamId, long chunkIdFrom, long chunkIdTo, List<Query.SupportedOperation> queryOps, boolean
            allowChunkScan) throws CouldNotReceiveException, InvalidQueryException, QueryNeedsChunkScanException,
            QueryFailedException, InvalidQueryIntervalException {
        return Query.performQuery(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                        streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface,
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdFrom)),
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdTo)), queryOps, allowChunkScan);
    }

    /**
     * Perform a statistical query with a certain granularity on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param from           The start time of the query. Data points at exactly this time will be INCLUDED in the
     *                       result of the query.
     * @param to             The end time of the query. Data points at exactly this time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOp        The operation that should be performed on the stream in the intervals.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @param precision      The size of the sub intervals that shall be computed.
     * @return A list of intervals representing the result of the query in the requested intervals.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public List<Interval> performRangeQuery(long streamId, Date from, Date to, Query.SupportedOperation queryOp, boolean
            allowChunkScan, long precision) throws CouldNotReceiveException, InvalidQueryException,
            QueryNeedsChunkScanException, QueryFailedException, InvalidQueryIntervalException {
        return Query.performQueryForRange(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface, from, to, queryOp, precision, allowChunkScan);
    }

    /**
     * Perform a statistical query with a certain granularity on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param from           The start time of the query. Data points at exactly this time will be INCLUDED in the
     *                       result of the query.
     * @param to             The end time of the query. Data points at exactly this time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOps       The operations that should be performed on the stream in the intervals.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @param precision      The size of the sub intervals that shall be computed.
     * @return A list of intervals representing the result of the query in the requested intervals.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public List<Interval> performRangeQuery(long streamId, Date from, Date to, List<Query.SupportedOperation> queryOps, boolean
            allowChunkScan, long precision) throws CouldNotReceiveException, InvalidQueryException,
            QueryNeedsChunkScanException, QueryFailedException, InvalidQueryIntervalException {
        return Query.performQueryForRange(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface, from, to, queryOps, precision, allowChunkScan);
    }


    /**
     * Perform a statistical query with a certain granularity on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param chunkIdFrom    The start chunk ID of the query. Data points in this chunk time will be INCLUDED in the
     *                       result of the query.
     * @param chunkIdTo      The end chunk ID of the query. Data points in this chunk time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOp        The operation that should be performed on the stream in the intervals.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @param precision      The size of the sub intervals that shall be computed.
     * @return A list of intervals representing the result of the query in the requested intervals.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public List<Interval> performRangeQueryForChunkId(long streamId, long chunkIdFrom, long chunkIdTo, Query.SupportedOperation queryOp, boolean
            allowChunkScan, long precision) throws CouldNotReceiveException, InvalidQueryException,
            QueryNeedsChunkScanException, QueryFailedException, InvalidQueryIntervalException {
        return Query.performQueryForRange(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                        streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface,
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdFrom)),
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdTo)), queryOp, precision, allowChunkScan);
    }

    /**
     * Perform a statistical query with a certain granularity on a stream.
     *
     * @param streamId       The stream to perform the operation on.
     * @param chunkIdFrom    The start chunk ID of the query. Data points in this chunk time will be INCLUDED in the
     *                       result of the query.
     * @param chunkIdTo      The end chunk ID of the query. Data points in this chunk time will be EXCLUDED in the
     *                       result of the query.
     * @param queryOps       The operations that should be performed on the stream in the intervals.
     * @param allowChunkScan Should it be allowed to compute the query on the raw data inside the chunks. Usually this
     *                       is not intended because it takes a lot of time and ressources, to fetch the data, decrypt
     *                       it and perform the query on the raw data.
     * @param precision      The size of the sub intervals that shall be computed.
     * @return A list of intervals representing the result of the query in the requested intervals.
     * @throws CouldNotReceiveException      Something went wrong with the retrieval of the data from the server.
     * @throws InvalidQueryException         Exception that indicates that the issuer of the query tried an unsupported
     *                                       operation with this query. If the query will be retried without a change
     *                                       it will always fail.
     * @throws QueryNeedsChunkScanException  Exception that indicates that a valid query can not be executed without
     *                                       accessing the Chunks that store the actual data points.
     * @throws QueryFailedException          Exception that indicates that a problem occurred during the execution of
     *                                       a valid query. A retry of the query might help.
     * @throws InvalidQueryIntervalException Exception that indicates that the given interval does not align with the
     *                                       interval of the TimeCrypt chunks and therefore can not be executed at all
     *                                       or can not be executed in a fast index-access only manner.
     */
    public List<Interval> performRangeQueryForChunkId(long streamId, long chunkIdFrom, long chunkIdTo, List<Query.SupportedOperation> queryOps, boolean
            allowChunkScan, long precision) throws CouldNotReceiveException, InvalidQueryException,
            QueryNeedsChunkScanException, QueryFailedException, InvalidQueryIntervalException {
        return Query.performQueryForRange(getStream(streamId), new StreamKeyManager(keyStore.receiveStreamKey(profile.getProfileName() +
                        streamId).getEncoded(), CHUNK_KEY_STREAM_DEPTH), serverInterface,
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdFrom)),
                new Date(TimeUtil.getChunkStartTime(getStream(streamId), chunkIdTo)), queryOps, precision, allowChunkScan);
    }
}
