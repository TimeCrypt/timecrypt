/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client;

import ch.ethz.dsg.timecrypt.TimeCryptClient;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.queryInterface.Interval;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.serverInterface.*;
import ch.ethz.dsg.timecrypt.client.state.*;
import ch.ethz.dsg.timecrypt.client.streamHandling.Chunk;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;
import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Class for testing server interfaces can also be used to test new server interface implementations.
 */
public class benchmark {

    // TODO: Tests for broken digests (not the right encryption scheme used when trying to insert a digest)
    // TODO: Tests for out of order inserts of chunks (should not be possible - destroy everything)

    private static final Instant streamStartTime = Instant.parse("2020-02-06T10:00:00Z");
    private static final Clock streamStartClock = Clock.fixed(streamStartTime, ZoneOffset.UTC);
    private static final Logger LOGGER = LoggerFactory.getLogger(benchmark.class);
    private static final String password = "asdfghjklasdfghjkl";
    private static ServerInterface testInterface;
    private static StreamKeyManager streamKeyManager;
    private static TimeCryptClient testClient;
    private static LocalTimeCryptKeystore testKeystore;
    private static LocalTimeCryptProfile testProfile;

    private final static int CHUNK_KEY_STREAM_DEPTH = 20;

    @BeforeAll
    static void setUp() throws Exception {
        // Change the environment variable of the interface provider for different tests

        if (ServerInterfaceFactory.isDefaultProvider()) {
            ServerInterfaceFactory.setInterfaceProvider(
                    ServerInterfaceFactory.InterfaceProvider.IN_MEMORY_MOCK_SERVER_INTERFACE);
        }

        testProfile = new LocalTimeCryptProfile(null, "Test user",
                "Test profile", "127.0.0.1", 15000);

        testInterface = ServerInterfaceFactory.getServerInterface(testProfile);

        streamKeyManager = new StreamKeyManager(KeyGenerator.getInstance("AES").generateKey().getEncoded(),
                CHUNK_KEY_STREAM_DEPTH);

        Instant someTime = Instant.parse("2020-02-06T10:00:03Z");
        Clock testClock = Clock.fixed(someTime, ZoneOffset.UTC);

        TimeUtil.setClock(testClock);

        testKeystore = LocalTimeCryptKeystore.createLocalKeystore(null, password.toCharArray());
        testClient = new TimeCryptClient(testKeystore, testProfile);
    }

    @AfterAll
    static void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    public void benchmarkChunkEncryption() throws Exception {

        // The stream is irrelevant for this benchmark
        Stream stream = new Stream(1, "Test Stream", "Stream for testing",
                TimeUtil.Precision.ONE_MINUTE, Arrays.asList(),
                Arrays.asList(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                        StreamMetaData.MetadataEncryptionScheme.LONG_MAC)), null);

        List<DataPoint> values;

        // Benchmark parameters
        int n = 100;
        int iterations = 1000;

        List<Integer> dpsInChunk = Arrays.asList(0, 10, 100, 1000);

        // The benchmark
        for (int numberOfDataPoints : dpsInChunk) {
            Chunk aChunk = new Chunk(stream, 0);

            values = new ArrayList<>();
            for (int j = 0; j <= numberOfDataPoints; j++) {
                aChunk.addDataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(0)
                        .plusMillis(j))).toEpochMilli()), j);
            }

            LOGGER.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            LOGGER.info("Preparation finished - start inserting");
            LOGGER.info("Encryption iterations: " + iterations);
            LOGGER.info("nr of datapoints: " + numberOfDataPoints);
            LOGGER.info("");
            LOGGER.info("");

            for (int j = 0; j <= n + 2; j++) {
                LOGGER.info("Start " + j + " / " + n + " iteration encrypting with " + numberOfDataPoints);

                for (long i = 0L; i < iterations; i++) {
                    new EncryptedChunk(1, 1, aChunk.encrypt(streamKeyManager));
                }
            }
            LOGGER.info("Finished iterations encrypting with " + numberOfDataPoints);
        }
    }


    @Test
    public void benchmarkInsertion() throws Exception {

        List<Chunk> unwrittenChunks;
        List<StreamMetaData> metaData;

        // Parameter of the Benchmark
        int n = 100;
        List<Integer> dpsInChunk = Arrays.asList(100);
        List<Integer> nrOfChunks = Arrays.asList(100);
        List<String> chunkStores = Arrays.asList(null, "/tmp/masterarbeit_do_not_touch");
        List<List<StreamMetaData.MetadataType>> types = Arrays.asList(
                Arrays.asList(StreamMetaData.MetadataType.SUM),
                Arrays.asList(StreamMetaData.MetadataType.COUNT),
                Arrays.asList(StreamMetaData.MetadataType.COUNT, StreamMetaData.MetadataType.SUM));

        int streamId = 42;
        int chunkCounter;
        int id;

        // test all possible of encryption and metadata types
        for (StreamMetaData.MetadataEncryptionScheme scheme : StreamMetaData.MetadataEncryptionScheme.values()) {
            id = 0;

            for (List<StreamMetaData.MetadataType> type : types) {

                metaData = new ArrayList<>();
                id++;
                for (StreamMetaData.MetadataType type1 : type) {
                    metaData.add(MetaDataFactory.getMetadataOfType(id, type1, scheme));
                }

                for (String chunkStore : chunkStores) {
                    Stream stream = new Stream(streamId, "Test Stream", "Stream for testing",
                            TimeUtil.Precision.ONE_MINUTE, Arrays.asList(), metaData, chunkStore);

                    for (int numberOfDps : dpsInChunk) {
                        unwrittenChunks = new ArrayList<>();
                        chunkCounter = 0;

                        for (int nrOfChunk : nrOfChunks) {

                            for (int i = 0; i < nrOfChunk; i++) {

                                Chunk aChunk = new Chunk(stream, chunkCounter);

                                for (int j = 0; j <= numberOfDps; j++) {
                                    aChunk.addDataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofMinutes(chunkCounter)
                                            .plusMillis(j))).toEpochMilli()), j);
                                }
                                unwrittenChunks.add(aChunk);
                                chunkCounter++;
                            }
                            LOGGER.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                            LOGGER.info("Preparation finished - start inserting");

                            for (StreamMetaData metaDatum : metaData) {
                                LOGGER.info("Schema: " + metaDatum.getEncryptionScheme());
                                LOGGER.info("Type: " + metaDatum.getType());
                            }

                            LOGGER.info("nr of Chunks: " + nrOfChunk);
                            LOGGER.info("nr of DPs: " + numberOfDps);
                            LOGGER.info("Chunkstore: " + chunkStore);
                            LOGGER.info(" ");
                            LOGGER.info(" ");

                            for (int i = 0; i < n; i++) {
                                TimeCryptLocalChunkStore asdf = new YamlTimeCryptLocalChunkStore(chunkStore);
                                LOGGER.info("Start iteration " + i);

                                for (Chunk chunk : unwrittenChunks) {
                                    sendChunk(asdf, chunk.getChunkID(), chunk, stream, false, testInterface, streamKeyManager);
                                }
                            }
                            LOGGER.info("Finished iterations for insertion");
                        }
                    }
                }
            }
        }
    }

    @Test
    public void benchmarkQueries() throws Exception {

        List<Chunk> unwrittenChunks;
        List<StreamMetaData> metaData;

        // Parameter of the Benchmark
        int n = 100;
        List<Integer> dpsInChunk = Arrays.asList(1000);
        List<Integer> nrOfChunks = Arrays.asList(100);
        List<List<StreamMetaData.MetadataType>> types = Arrays.asList(Arrays.asList(),
                Arrays.asList(StreamMetaData.MetadataType.COUNT, StreamMetaData.MetadataType.SUM));
        // how big is the query range (in chunks)
        List<Integer> range = Arrays.asList(10, 100);

        int chunkCounter;

        // test all possible of encryption and metadata types
        for (StreamMetaData.MetadataEncryptionScheme scheme : StreamMetaData.MetadataEncryptionScheme.values()) {

            for (List<StreamMetaData.MetadataType> type : types) {

                long streamID = testClient.createStream("Test", "A test stream",
                        TimeUtil.Precision.ONE_MINUTE, Arrays.asList(), type, scheme, null);
                Stream stream = testClient.getStream(streamID);
                StreamKeyManager asdf = new StreamKeyManager(testKeystore.receiveStreamKey(testProfile.getProfileName() +
                        streamID).getEncoded(), CHUNK_KEY_STREAM_DEPTH);

                for (int numberOfDps : dpsInChunk) {
                    unwrittenChunks = new ArrayList<>();
                    chunkCounter = 0;

                    for (int nrOfChunk : nrOfChunks) {

                        int sumInserted = 0;
                        for (int i = 0; i < nrOfChunk; i++) {

                            Chunk aChunk = new Chunk(stream, chunkCounter);

                            for (int j = 0; j <= numberOfDps; j++) {
                                aChunk.addDataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofMinutes(chunkCounter)
                                        .plusMillis(j))).toEpochMilli()), j);
                                sumInserted += j;
                            }
                            unwrittenChunks.add(aChunk);
                            chunkCounter++;
                        }

                        for (Chunk chunk : unwrittenChunks) {
                            sendChunk(stream.getLocalChunkStore(), chunk.getChunkID(), chunk, stream, true
                                    , testClient.getServerInterface(), asdf);

                        }

                        for (int curRange : range) {
                            LOGGER.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                            LOGGER.info("Preparation finished - start querying");
                            LOGGER.info("Scheme: " + scheme);
                            for (StreamMetaData.MetadataType metaDatum : type) {
                                LOGGER.info("Type: " + metaDatum);
                            }
                            LOGGER.info("nr of Chunks: " + nrOfChunk);
                            LOGGER.info("nr of DPs: " + numberOfDps);
                            LOGGER.info("Query range: " + curRange);
                            LOGGER.info(" ");
                            LOGGER.info(" ");

                            for (int i = 0; i < n + 2 ; i++) {
                                LOGGER.info("Start iteration " + i);

                                Interval returnInterval = testClient.performQueryForChunkId(streamID, 0,
                                        curRange, Query.SupportedOperation.AVG, true);

                                assertEquals(streamStartTime.toEpochMilli(), returnInterval.getFrom().getTime());
//                                assertEquals(streamStartTime.plusMillis(curRange * TimeUtil.Precision.ONE_SECOND.getMillis()).toEpochMilli(),
 //                                       returnInterval.getTo().getTime());
//                                assertEquals(sumInserted / numberOfDps, returnInterval.getValue());

                            }
                            LOGGER.info("Finished iterations for query");
                        }
                    }
                }
            }
        }
    }

    private void sendChunk(TimeCryptLocalChunkStore chunkStore, long chunkId, Chunk curChunk, Stream associatedStream,
                           boolean reallySend, ServerInterface testInterface, StreamKeyManager streamKeyManager) {
        curChunk.finalizeChunk();
        List<EncryptedMetadata> encryptedMetaData = new ArrayList<>();

        for (StreamMetaData metadata : associatedStream.getMetaData()) {
            encryptedMetaData.add(MetaDataFactory.getEncryptedMetadataForValue(metadata,
                    curChunk.getValues(), streamKeyManager, chunkId));
        }
        EncryptedDigest digest = new EncryptedDigest(associatedStream.getId(), chunkId, chunkId,
                encryptedMetaData);

        EncryptedChunk encryptedChunk;
        try {
            encryptedChunk = new EncryptedChunk(associatedStream.getId(), chunkId,
                    curChunk.encrypt(streamKeyManager));
        } catch (Exception e) {
            LOGGER.error("Could not encrypt chunk.", e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Could encrypt chunk " + chunkId + " for stream " + associatedStream.getId() +
                    ". Message:" + e.getMessage());
        }

        try {
            chunkStore.addUnwrittenChunk(encryptedChunk, digest);
        } catch (Exception e) {
            LOGGER.error("Could store chunk " + chunkId + " for stream " + associatedStream.getId() +
                    " in the chunk store before writing - terminating.", e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Could store chunk " + chunkId + " for stream " + associatedStream.getId() +
                    " in the chunk store before writing. Message:" + e.getMessage());
        }

        if (reallySend) {
            try {
                long serverChunkId = testInterface.addChunk(associatedStream.getId(), encryptedChunk, digest);
                if (serverChunkId != chunkId) {
                    LOGGER.error("Server reported a different chunkId than we expected for stream " + associatedStream.getId() +
                            "Expected " + curChunk + " got " + serverChunkId + ". This means the understanding of the " +
                            "stream got inconsistent between server and client - can't handle that");
                    // TODO: raise a useful exception.
                    throw new RuntimeException("Server reported a different chunkId than we expected. For stream " +
                            associatedStream.getId());
                }
            } catch (CouldNotStoreException e) {
                LOGGER.error("Could not store chunk with chunk ID " + chunkId + " on the server", e);
                // TODO: raise a useful exception.
                System.exit(1);
            }
        }


        try {
            chunkStore.markChunkAsWritten(chunkId);
        } catch (Exception e) {
            LOGGER.error("Could mark chunk " + chunkId + " as written to stream " +
                    associatedStream.getId() + "in the chunk store - terminating.", e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Could mark chunk " + chunkId + " as written to " +
                    "stream " + associatedStream.getId() + "in the chunk store. Message:" + e.getMessage());
        }
    }
}