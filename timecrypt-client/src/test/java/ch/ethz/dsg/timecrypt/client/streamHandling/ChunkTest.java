/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ChunkTest {

    private static Stream stream;

    /* TODO:
        - Encryption / Decryption
        - adding data points
        - only in the given interval allowed
            - all possible values in the interval
        - duplicate inserts in the interval
        - finalized chunks can't be written
     */

    @BeforeAll
    static void beforeAll() throws IOException {

        Instant someTime = Instant.parse("2020-02-06T10:00:03Z");
        Instant someTimeAtNextMinute = Instant.parse("2020-02-06T10:01:00Z");
        Clock someClock = Clock.fixed(someTime, ZoneOffset.UTC);

        TimeUtil.setClock(someClock);

        stream = new Stream(1L, "Test Stream", "Stream for testing", TimeUtil.Precision.ONE_SECOND,
                Arrays.asList(TimeUtil.Precision.TEN_SECONDS, TimeUtil.Precision.ONE_MINUTE), Arrays.asList(
                MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.COUNT,
                        StreamMetaData.MetadataEncryptionSchema.LONG),
                MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.SUM,
                        StreamMetaData.MetadataEncryptionSchema.LONG)), null);
    }

    @AfterAll
    static void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    public void cration() {
        Chunk aChunk = new Chunk(stream, 0);

        assertEquals(aChunk.getStartTime(), stream.getStartDate().getTime());
        assertEquals(aChunk.getEndTime(), stream.getStartDate().getTime() + stream.getChunkSize() - 1);
        assertEquals(aChunk.getChunkID(), 0);
        assertEquals(aChunk.getCorrespondingStream(), stream);
        assertEquals(aChunk.getValues().size(), 0);
    }
}