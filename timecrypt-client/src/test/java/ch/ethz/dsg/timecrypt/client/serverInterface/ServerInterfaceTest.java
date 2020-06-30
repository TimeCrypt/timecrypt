/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.*;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Class for testing server interfaces can also be used to test new server interface implementations.
 */
public class ServerInterfaceTest {

    // TODO: Tests for broken digests (not the right encryption scheme used when trying to insert a digest)
    // TODO: Tests for out of order inserts of chunks (should not be possible - destroy everything)

    private static ServerInterface testInterface;
    private static StreamKeyManager streamKeyManager;
    private static final Instant streamStartTime = Instant.parse("2020-02-06T10:00:00Z");
    private static final Clock streamStartClock = Clock.fixed(streamStartTime, ZoneOffset.UTC);

    @BeforeAll
    static void setUp() throws Exception {
        // Change the environment variable of the interface provider for different tests

        if (ServerInterfaceFactory.isDefaultProvider()) {
            ServerInterfaceFactory.setInterfaceProvider(
                    ServerInterfaceFactory.InterfaceProvider.IN_MEMORY_MOCK_SERVER_INTERFACE);
        }

        TimeCryptProfile testProfile = new LocalTimeCryptProfile(null, "Test user",
                "Test profile", "127.0.0.1", 15000);

        testInterface = ServerInterfaceFactory.getServerInterface(testProfile);

        streamKeyManager = new StreamKeyManager(KeyGenerator.getInstance("AES").generateKey().getEncoded(),
                20);

        Instant someTime = Instant.parse("2020-02-06T10:00:03Z");
        Clock testClock = Clock.fixed(someTime, ZoneOffset.UTC);

        TimeUtil.setClock(testClock);
    }

    @AfterAll
    static void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    public void nonExistingStream() {

        // Assuming that the server was empty before there should be no stream with this ID
        assertThrows(InvalidQueryException.class, () -> testInterface.deleteStream(13));

        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(13, 1, 2));

        assertThrows(InvalidQueryException.class, () -> testInterface.getLastWrittenChunkId(13));

        assertThrows(CouldNotStoreException.class, () -> testInterface.addChunk(13,
                getEncryptedChunk(new ArrayList<>(), 13, 0, new ArrayList<>()),
                getEncryptedDigest(new ArrayList<>(), 13, 0, new ArrayList<>())));

        // Assuming that the server was empty before there should be no stream with this ID
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(13,
                1, 2, 1, Collections.singletonList(
                        MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                                StreamMetaData.MetadataEncryptionScheme.LONG))));
    }

    @Test
    public void createAndDeleteStream() throws CouldNotStoreException, InvalidQueryException {

        List<Long> ids = new ArrayList<>();
        List<StreamMetaData> metaData = new ArrayList<>();

        // with empty metadata config
        ids.add(testInterface.createStream(metaData));

        // test all possible of encryption and metadata types
        for (StreamMetaData.MetadataEncryptionScheme scheme : StreamMetaData.MetadataEncryptionScheme.values()) {
            metaData = new ArrayList<>();

            for (StreamMetaData.MetadataType type : StreamMetaData.MetadataType.values()) {
                metaData.add(MetaDataFactory.getMetadataOfType(metaData.size(), type, scheme));
                ids.add(testInterface.createStream(metaData));
            }
        }

        // test mixed encryption
        StreamMetaData.MetadataType type = StreamMetaData.MetadataType.COUNT;
        metaData = new ArrayList<>();

        for (StreamMetaData.MetadataEncryptionScheme scheme : StreamMetaData.MetadataEncryptionScheme.values()) {
            metaData.add(MetaDataFactory.getMetadataOfType(metaData.size(), type, scheme));
            ids.add(testInterface.createStream(metaData));
        }

        List<Long> listWithoutDuplicates = new ArrayList<>(new HashSet<>(ids));

        // The stream IDs have to be unique
        assertEquals(ids.size(), listWithoutDuplicates.size());

        // Delete all streams
        for (long id : ids) {
            testInterface.deleteStream(id);
        }
    }

    @Test
    public void creatStreamWithBadMetadata() {
        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128));
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128_MAC));

        assertThrows(CouldNotStoreException.class, () -> testInterface.createStream(metaData));

        // TODO: This should not be possible, should it?
        List<StreamMetaData> otherMetaData = new ArrayList<>();
        otherMetaData.add(MetaDataFactory.getMetadataOfType(7, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128));
        otherMetaData.add(MetaDataFactory.getMetadataOfType(13, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128_MAC));

        assertThrows(CouldNotStoreException.class, () -> testInterface.createStream(otherMetaData));

        List<StreamMetaData> negativeMetaData = new ArrayList<>();
        negativeMetaData.add(MetaDataFactory.getMetadataOfType(-1, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128));

        assertThrows(CouldNotStoreException.class, () -> testInterface.createStream(negativeMetaData));
    }

    @Test
    public void testChunkHandling() throws Exception {
        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.LONG_MAC));
        metaData.add(MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.LONG));

        long id = testInterface.createStream(metaData);

        // The stream is empty - it should not be possible to retrieve any data points
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, 0, 0));
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, 0, 1));
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, 1, 100));


        // Test some nonsense ranges
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, -100, 0));
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, -1, -1));
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, 1, -1));
        assertThrows(CouldNotReceiveException.class, () -> testInterface.getChunks(id, 10, 1));


        // Actually test insertion
        List<EncryptedChunk> insertedChunks = new ArrayList<>();
        List<DataPoint> values;

        // go up to 1000 because our test stream has an interval size of 1 second.
        for (long i = 0L; i < 100; i++) {

            values = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                // we also test that the server can handle the growth of metadata
                values.add(new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)
                        .plusMillis(j))).toEpochMilli()), j));
            }

            EncryptedChunk aChunk = getEncryptedChunk(metaData, id, i, values);
            insertedChunks.add((int) i, aChunk);

            assertEquals(i, testInterface.addChunk(id, aChunk, getEncryptedDigest(metaData, id, i, values)));

            List<EncryptedChunk> result = testInterface.getChunks(id, 0L, (i + 1));
            assertThat(insertedChunks, IsIterableContainingInAnyOrder.containsInAnyOrder(result.toArray()));

            // Test arbitrary  sub ranges
            for (int lowerBound = 0; lowerBound < (i + 1); lowerBound++) {
                assertThat(insertedChunks.subList(lowerBound, (int) (i + 1)),
                        IsIterableContainingInAnyOrder.containsInAnyOrder(
                                testInterface.getChunks(id, lowerBound, (i + 1)).toArray()));
            }
        }

        testInterface.deleteStream(id);
    }

    @Test
    public void getLastWrittenChunkId() throws Exception {
        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128));
        metaData.add(MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128_MAC));

        long id = testInterface.createStream(metaData);

        assertTrue(testInterface.getLastWrittenChunkId(id) < 0);

        List<DataPoint> values = new ArrayList<>();
        for (long i = 0L; i < 100; i++) {

            assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i, values),
                    getEncryptedDigest(metaData, id, i, values)));

            assertEquals(i, testInterface.getLastWrittenChunkId(id));
        }

        testInterface.deleteStream(id);
    }

    @Test
    public void getOperationsOnStreamWithoutMetadata() throws Exception {
        List<StreamMetaData> metaData = new ArrayList<>();
        long id = testInterface.createStream(metaData);

        List<DataPoint> values;
        List<EncryptedChunk> insertedChunks = new ArrayList<>();

        for (long i = 0L; i < 100; i++) {

            values = new ArrayList<>();
            for (int j = 0; j <= i; j++) {
                // we also test that the server can handle the growth of metadata
                values.add(new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)
                        .plusMillis(j))).toEpochMilli()), j));
            }

            EncryptedChunk aChunk = getEncryptedChunk(metaData, id, i, values);
            insertedChunks.add((int) i, aChunk);

            assertEquals(i, testInterface.addChunk(id, aChunk, getEncryptedDigest(metaData, id, i, values)));

            assertThat(insertedChunks, IsIterableContainingInAnyOrder.containsInAnyOrder(
                    testInterface.getChunks(id, 0L, (i + 1)).toArray()));

        }
        // Should always return an error since there are no meta data
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 5L, 1, new ArrayList<>()));

        testInterface.deleteStream(id);
    }

    @Test
    public void testInvalidStatisticRequests() throws Exception {

        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.LONG));
        long id = testInterface.createStream(metaData);


        // Invalid ranges - there are no chunks yet
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 1, 1, metaData));
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 0, 1, metaData));

        for (long i = 0L; i < 10; i++) {
            List<DataPoint> value = Collections.singletonList(new DataPoint(new Date(Instant.now(Clock.offset(
                    streamStartClock, Duration.ofSeconds(i))).toEpochMilli()), i));

            EncryptedChunk aChunk = getEncryptedChunk(metaData, id, i, value);
            assertEquals(i, testInterface.addChunk(id, aChunk, getEncryptedDigest(metaData, id, i, value)));
        }

        // Not asking for any data
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 5L, 1, new ArrayList<>()));


        // Invalid ranges
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                -5L, 0, 1, metaData));
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                -5L, 10, 1, metaData));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                5L, 1, 1, metaData));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                5L, 1, 1, metaData));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                1, 1, 1, metaData));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                1, 101, 1, metaData));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                10, 101, 1, metaData));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                100, 101, 1, metaData));


        // Invalid meta data

        // Less than zero ID
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 1, 1, Collections.singletonList(MetaDataFactory.getMetadataOfType(
                        -1, StreamMetaData.MetadataType.COUNT, StreamMetaData.MetadataEncryptionScheme.LONG))));

        // This stream has no values with ID 2
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 1, 1, Collections.singletonList(MetaDataFactory.getMetadataOfType(
                        2, StreamMetaData.MetadataType.COUNT, StreamMetaData.MetadataEncryptionScheme.LONG))));

        // TODO: Should the clients realize that it is the wrong requested encryption?
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, 1, 1, Collections.singletonList(MetaDataFactory.getMetadataOfType(
                        0, StreamMetaData.MetadataType.COUNT, StreamMetaData.MetadataEncryptionScheme.BIG_INT_128))));

        testInterface.deleteStream(id);
    }

    @Test
    public void getStatisticalDataForAllEncryptionSchemes() throws Exception {
        List<Long> ids = new ArrayList<>();
        List<StreamMetaData> metaData;

        // test all possible of encryption and metadata types
        for (StreamMetaData.MetadataEncryptionScheme scheme : StreamMetaData.MetadataEncryptionScheme.values()) {
            metaData = new ArrayList<>();

            metaData.add(MetaDataFactory.getMetadataOfType(metaData.size(), StreamMetaData.MetadataType.COUNT, scheme));
            long id = testInterface.createStream(metaData);
            ids.add(id);

            List<DataPoint> values = new ArrayList<>();

            for (long i = 0L; i < 100; i++) {
                DataPoint dataPoint = new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)))
                        .toEpochMilli()), i);
                values.add(dataPoint);

                assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i,
                        Collections.singletonList(dataPoint)), getEncryptedDigest(metaData, id, i,
                        Collections.singletonList(dataPoint))));

                // Test arbitrary  sub ranges
                for (long lowerBound = 0; lowerBound < (i + 1); lowerBound++) {
                    List<EncryptedDigest> result = testInterface.getStatisticalData(id, lowerBound, (i + 1), (int)
                            ((i - lowerBound) + 1), metaData);

                    assertEquals(1, result.size());
                    assertEquals(1, result.get(0).getPayload().size());

                    assertEquals(lowerBound, result.get(0).getChunkIdFrom());
                    assertEquals((i + 1), result.get(0).getChunkIdTo());

                    List<Pair<StreamMetaData, Long>> resultValues = new Digest(id, result.get(0), streamKeyManager,
                            metaData).getValues();

                    assertEquals(1, resultValues.size());
                    assertEquals(metaData.get(0).getType(), resultValues.get(0).getLeft().getType());

                    assertEquals(metaData.get(0).getEncryptionScheme(),
                            resultValues.get(0).getLeft().getEncryptionScheme());
                    assertEquals(metaData.get(0).getId(), resultValues.get(0).getLeft().getId());

                    assertEquals(metaData.get(0).calculate(values.subList((int) lowerBound, (int) (i + 1))),
                            resultValues.get(0).getRight());
                }
            }
        }

        // Delete all streams
        for (long id : ids) {
            testInterface.deleteStream(id);
        }
    }

    @Test
    public void getStatisticalDataForDifferentGranularities() throws Exception {
        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128_MAC));

        long id = testInterface.createStream(metaData);
        int max = 1000;

        for (long i = 0L; i < max; i++) {
            DataPoint dataPoint = new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)))
                    .toEpochMilli()), i);

            assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i,
                    Collections.singletonList(dataPoint)), getEncryptedDigest(metaData, id, i,
                    Collections.singletonList(dataPoint))));
        }

        List<Integer> granularities = new ArrayList<>();
        granularities.add(1);
        granularities.add(2);
        granularities.add(4);
        granularities.add(5);
        granularities.add(10);
        granularities.add(20);
        granularities.add(25);
        granularities.add(50);
        granularities.add(100);

        for (Integer granularity : granularities) {

            long div = max / granularity;
            List<EncryptedDigest> result = testInterface.getStatisticalData(id, 0, max,
                    granularity, metaData);

            assertEquals(div, result.size());
            assertEquals(1, result.get(0).getPayload().size());

            for (int j = 0; j < div; j++) {
                List<Pair<StreamMetaData, Long>> resultValues = new Digest(id, result.get(j), streamKeyManager,
                        metaData).getValues();

                // default checks
                assertEquals(1, resultValues.size());

                assertEquals((long) granularity, resultValues.get(0).getRight());
            }
        }

        // Test a few useless granularities
        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, (max - 1), 0, new ArrayList<>()));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, (max - 1), 13, new ArrayList<>()));

        assertThrows(InvalidQueryException.class, () -> testInterface.getStatisticalData(id,
                0, (max - 1), 17, new ArrayList<>()));

        testInterface.deleteStream(id);
    }

    @Test
    public void getStatisticalDataForPositiveAndNegativeValues() throws Exception {
        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.LONG_MAC));

        long id = testInterface.createStream(metaData);

        List<DataPoint> values = new ArrayList<>();

        List<Long> dataPointValues = new ArrayList<>();
        dataPointValues.add(1L);
        dataPointValues.add(-1L);
        dataPointValues.add(-10L);
        dataPointValues.add(11L);
        dataPointValues.add(-111L);
        dataPointValues.add(0L);

        for (long i = 0L; i < dataPointValues.size(); i++) {
            DataPoint dataPoint = new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock,
                    Duration.ofSeconds(i))).toEpochMilli()), dataPointValues.get((int) i));
            values.add(dataPoint);

            assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i,
                    Collections.singletonList(dataPoint)), getEncryptedDigest(metaData, id, i,
                    Collections.singletonList(dataPoint))));

            List<EncryptedDigest> result = testInterface.getStatisticalData(id, 0, (i + 1), (int)
                    (i + 1), metaData);

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getPayload().size());

            List<Pair<StreamMetaData, Long>> resultValues = new Digest(id, result.get(0), streamKeyManager,
                    metaData).getValues();

            // default checks
            assertEquals(1, resultValues.size());
            assertEquals(metaData.get(0).getType(), resultValues.get(0).getLeft().getType());
            assertEquals(metaData.get(0).getEncryptionScheme(),
                    resultValues.get(0).getLeft().getEncryptionScheme());
            assertEquals(metaData.get(0).getId(), resultValues.get(0).getLeft().getId());

            assertEquals(metaData.get(0).calculate(values), resultValues.get(0).getRight());
        }

        testInterface.deleteStream(id);
    }

    @Test
    public void getStatisticalDataForMultipleMetaData() throws Exception {

        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.LONG_MAC));
        metaData.add(MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.LONG));

        long id = testInterface.createStream(metaData);

        List<DataPoint> values = new ArrayList<>();
        for (long i = 0L; i < 10; i++) {
            DataPoint dataPoint = new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)))
                    .toEpochMilli()), i);
            values.add(dataPoint);

            assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i,
                    Collections.singletonList(dataPoint)), getEncryptedDigest(metaData, id, i,
                    Collections.singletonList(dataPoint))));

            // Test arbitrary  sub ranges
            for (long lowerBound = 0; lowerBound < (i + 1); lowerBound++) {
                List<EncryptedDigest> result = testInterface.getStatisticalData(id, lowerBound, (i + 1), (int)
                        ((i - lowerBound) + 1), metaData);

                assertEquals(1, result.size());
                assertEquals(2, result.get(0).getPayload().size());

                List<Pair<StreamMetaData, Long>> resultValues = new Digest(id, result.get(0), streamKeyManager,
                        metaData).getValues();

                assertEquals(2, resultValues.size());
                assertEquals(metaData.get(0).getType(), resultValues.get(0).getLeft().getType());
                assertEquals(metaData.get(1).getType(), resultValues.get(1).getLeft().getType());

                assertEquals(metaData.get(0).getEncryptionScheme(),
                        resultValues.get(0).getLeft().getEncryptionScheme());
                assertEquals(metaData.get(1).getEncryptionScheme(),
                        resultValues.get(1).getLeft().getEncryptionScheme());

                assertEquals(metaData.get(0).getId(), resultValues.get(0).getLeft().getId());
                assertEquals(metaData.get(1).getId(), resultValues.get(1).getLeft().getId());

                assertEquals(metaData.get(0).calculate(values.subList((int) lowerBound, (int) (i + 1))),
                        resultValues.get(0).getRight());
                assertEquals(metaData.get(1).calculate(values.subList((int) lowerBound, (int) (i + 1))),
                        resultValues.get(1).getRight());
            }
        }

        testInterface.deleteStream(id);
    }


    @Test
    public void getStatisticalDataForSpecialMetaData() throws Exception {

        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.LONG_MAC));
        metaData.add(MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.LONG));

        List<StreamMetaData> requestMetaData = new ArrayList<>();
        requestMetaData.add(MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.LONG));

        long id = testInterface.createStream(metaData);

        List<DataPoint> values = new ArrayList<>();
        for (long i = 0L; i < 10; i++) {
            DataPoint dataPoint = new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)))
                    .toEpochMilli()), i);
            values.add(dataPoint);

            assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i,
                    Collections.singletonList(dataPoint)), getEncryptedDigest(metaData, id, i,
                    Collections.singletonList(dataPoint))));

            // Test arbitrary  sub ranges
            for (long lowerBound = 0; lowerBound < (i + 1); lowerBound++) {
                List<EncryptedDigest> result = testInterface.getStatisticalData(id, lowerBound, (i + 1), (int)
                        ((i - lowerBound) + 1), requestMetaData);

                assertEquals(1, result.size());

                List<Pair<StreamMetaData, Long>> resultValues = new Digest(id, result.get(0), streamKeyManager,
                        metaData).getValues();

                assertEquals(1, resultValues.size());
                assertEquals(metaData.get(1).getType(), resultValues.get(0).getLeft().getType());

                assertEquals(metaData.get(1).getEncryptionScheme(),
                        resultValues.get(0).getLeft().getEncryptionScheme());

                assertEquals(metaData.get(1).getId(), resultValues.get(0).getLeft().getId());

                assertEquals(metaData.get(1).calculate(values.subList((int) lowerBound, (int) (i + 1))),
                        resultValues.get(0).getRight());
            }
        }
        testInterface.deleteStream(id);
    }

    private EncryptedChunk getEncryptedChunk(List<StreamMetaData> metaData, long streamId, long chunkId,
                                             List<DataPoint> values) throws Exception {

        Stream stream = new Stream(streamId, "Test Stream", "Stream for testing",
                TimeUtil.Precision.ONE_SECOND,
                Arrays.asList(TimeUtil.Precision.TEN_SECONDS, TimeUtil.Precision.ONE_MINUTE), metaData,
                null);
        Chunk aChunk = new Chunk(stream, chunkId);

        for (DataPoint dataPoint : values) {
            aChunk.addDataPoint(dataPoint.getTimestamp(), dataPoint.getValue());
        }

        return new EncryptedChunk(streamId, chunkId, aChunk.encrypt(streamKeyManager));
    }

    private EncryptedDigest getEncryptedDigest(List<StreamMetaData> metaData, long streamId, long chunkId,
                                               List<DataPoint> values) {

        List<EncryptedMetadata> encryptedMetaData = new ArrayList<>();

        for (StreamMetaData metadata : metaData) {
            encryptedMetaData.add(MetaDataFactory.getEncryptedMetadataForValue(metadata, values, streamKeyManager,
                    chunkId));
        }
        return new EncryptedDigest(streamId, chunkId, chunkId + 1, encryptedMetaData);
    }
}