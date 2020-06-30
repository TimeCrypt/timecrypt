/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface;

import ch.ethz.dsg.timecrypt.client.exceptions.*;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterfaceFactory;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;
import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import javax.crypto.KeyGenerator;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

class QueryTest {

    // TODO: Chunk scan vs non chunk scan
    // TODO: all error scenarios regarding the from and to
    // - not enough meta data but no chunk scan
    // - Precision greater than stream precision
    // - invalid precisions for range queries
    // Sub-Chunk queries
    // chunk only queries

    private static Stream stream;
    private static ServerInterface testInterface;
    private static StreamKeyManager streamKeyManager;
    private static Instant streamStartTime = Instant.parse("2020-02-06T10:01:00Z");
    private static Clock streamStartClock = Clock.fixed(streamStartTime, ZoneOffset.UTC);

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

    //   @Test
    void sunshineTest() throws CouldNotStoreException, InvalidQueryException, IOException, QueryNeedsChunkScanException,
            QueryFailedException, InvalidQueryIntervalException {

        List<StreamMetaData> metaData = new ArrayList<>();
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionScheme.LONG_MAC));
        metaData.add(MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionScheme.BIG_INT_128_MAC));

        long id = testInterface.createStream(metaData);

        Stream stream = new Stream(id, "Test Stream", "Stream for testing",
                TimeUtil.Precision.ONE_SECOND,
                Arrays.asList(TimeUtil.Precision.TEN_SECONDS, TimeUtil.Precision.ONE_MINUTE), metaData,
                null);

        for (long i = 0L; i < 100; i++) {
            DataPoint dataPoint = new DataPoint(new Date(Instant.now(Clock.offset(streamStartClock, Duration.ofSeconds(i)))
                    .toEpochMilli()), i);
//            values.add(dataPoint);

            //          assertEquals(i, testInterface.addChunk(id, getEncryptedChunk(metaData, id, i,
            //                   Collections.singletonList(dataPoint)), getEncryptedDigest(metaData, id, i,
            //                 Collections.singletonList(dataPoint))));
        }

        Interval result = Query.performQuery(stream, streamKeyManager, testInterface,
                new Date(streamStartTime.toEpochMilli()),
                new Date(streamStartTime.plusSeconds(5).toEpochMilli()), Query.SupportedOperation.AVG, false);

        testInterface.deleteStream(id);
    }
}