/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.queryInterface.Interval;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterfaceFactory;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.*;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeCryptClientTest {

    private static final String password = "asdfghjklasdfghjkl";
    private static Instant streamStartTime = Instant.parse("2020-02-06T10:00:00Z");
    private static Clock streamStartClock = Clock.fixed(streamStartTime, ZoneOffset.UTC);
    private static TimeCryptClient testClient;

    @BeforeAll
    static void setUp() throws Exception {
        // Change the environment variable of the interface provider for different tests

        if (ServerInterfaceFactory.isDefaultProvider()) {
            ServerInterfaceFactory.setInterfaceProvider(
                    ServerInterfaceFactory.InterfaceProvider.IN_MEMORY_MOCK_SERVER_INTERFACE);
        }

        TimeCryptProfile testProfile = new LocalTimeCryptProfile(null, "Test user",
                "Test profile", "127.0.0.1", 15000);

        TimeCryptKeystore testKeystore = LocalTimeCryptKeystore.createLocalKeystore(null, password.toCharArray());

        Instant someTime = Instant.parse("2020-02-06T10:00:03Z");
        Clock testClock = Clock.fixed(someTime, ZoneOffset.UTC);

        TimeUtil.setClock(testClock);

        testClient = new TimeCryptClient(testKeystore, testProfile);
    }

    @AfterAll
    static void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    void e2eTest() throws Exception {
        /*
         * Tests all methods that are exposed by the Client Test
         */
        assertEquals(0, testClient.listStreams().size());

        TimeUtil.Precision streamPrecision = TimeUtil.Precision.ONE_SECOND;
        long id = testClient.createStream("Test", "A test stream", streamPrecision,
                Collections.singletonList(TimeUtil.Precision.TEN_SECONDS),
                Arrays.asList(StreamMetaData.MetadataType.SUM, StreamMetaData.MetadataType.COUNT),
                StreamMetaData.MetadataEncryptionSchema.LONG, null);

        assertEquals(1, testClient.listStreams().size());
        assertTrue(testClient.listStreams().containsKey(id));
        assertEquals(TimeUtil.Precision.ONE_SECOND.getMillis(), testClient.listStreams().get(id).getChunkSize());
        assertEquals(TimeUtil.Precision.ONE_SECOND, testClient.listStreams().get(id).getPrecision());
        assertTrue(0 > testClient.listStreams().get(id).getLastWrittenChunkId());
        assertEquals("Test", testClient.listStreams().get(id).getName());

        Stream testStream = testClient.getStream(id);

        assertEquals(TimeUtil.Precision.ONE_SECOND.getMillis(), testStream.getChunkSize());
        assertEquals(TimeUtil.Precision.ONE_SECOND, testStream.getPrecision());
        assertTrue(0 > testStream.getLastWrittenChunkId());
        assertEquals("Test", testStream.getName());

        ChunkHandler.setClock(streamStartClock);

        Date dataPointDate = new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofMillis(1))).toEpochMilli());
        long dataPointValue = 1;
        testClient.addDataPointToStream(id, new DataPoint(dataPointDate, dataPointValue));

        // Sleep for a second so the chunk handler thread is clearly up and running - otherwise terminating it instantly
        // can lead to a situation that it terminated before ever trying to send chunks
        Thread.sleep(1000);

        // we terminate the client so everything gets written
        testClient.terminate();

        long nrOfWrittenChunks = TimeCryptClient.CHUNK_WRITE_WINDOW.getMillis() / testStream.getChunkSize();
        long maxChunkId = nrOfWrittenChunks - 1;

        // the stream should have one complete written write window now
        //assertEquals(maxChunkId, testStream.getLastWrittenChunkId());

        List<Chunk> chunks = testClient.getChunks(id, 0, 0);

        assertEquals(1, chunks.size());

        Chunk testChunk = chunks.get(0);

        assertEquals(0, testChunk.getChunkID());
        assertEquals(1, testChunk.getValues().size());
        assertEquals(streamStartTime.toEpochMilli(), testChunk.getStartTime());

        DataPoint dataPoint = (DataPoint) testChunk.getValues().toArray()[0];

        assertEquals(dataPointValue, dataPoint.getValue());
        assertEquals(dataPointDate, dataPoint.getTimestamp());

        chunks = testClient.getChunks(id, 1, maxChunkId);
        assertEquals(nrOfWrittenChunks - 1, chunks.size());

        Interval returnInterval = testClient.performQueryForChunkId(id, 0, 1,
                Query.SupportedOperation.AVG, false);

        assertEquals(streamStartTime.toEpochMilli(), returnInterval.getFrom().getTime());
        assertEquals(streamStartTime.plusMillis(streamPrecision.getMillis()).toEpochMilli(),
                returnInterval.getTo().getTime());
        assertEquals(1.0, returnInterval.getValue());

        returnInterval = testClient.performQueryForChunkId(id, 0, maxChunkId + 1,
                Query.SupportedOperation.AVG, false);

        assertEquals(streamStartTime.toEpochMilli(), returnInterval.getFrom().getTime());
        assertEquals(TimeUtil.getChunkStartTime(testStream, maxChunkId + 1),
                returnInterval.getTo().getTime());
        assertEquals(1.0, returnInterval.getValue());

        testClient.deleteStream(id);
        assertEquals(0, testClient.listStreams().size());
    }

    // Forward the clock to the time
//        ChunkHandler.setClock(Clock.offset(streamStartClock, Duration.ofMillis(ChunkHandler.WRITE_WINDOW_MILLIS).
//                plusMillis(testStream.getChunkSize() + 1 )));

}