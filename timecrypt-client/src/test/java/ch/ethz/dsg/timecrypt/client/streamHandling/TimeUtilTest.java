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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interaction with time is essential for TimeCrypt - good tests of the TimeUtil are therefore very needed.
 */
public class TimeUtilTest {

    private static Stream stream;
    private static Instant streamTime = Instant.parse("2020-02-06T10:00:03Z");
    private static Instant streamStartTime = Instant.parse("2020-02-06T10:00:00Z");
    private static Instant someTimeBeforeStream = Instant.parse("2020-02-06T09:59:00Z");
    private static Clock streamClock = Clock.fixed(streamTime, ZoneOffset.UTC);

    @BeforeAll
    static void before() throws IOException {

        TimeUtil.setClock(streamClock);

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
    public void testTimeMocking() {
        // Test the time mocking of the TimeUtil because it is essential for all other tests.

        Instant someTime = Instant.parse("2020-02-06T10:00:03Z");
        Instant someTimeAtLastMinute = Instant.parse("2020-02-06T10:00:00Z");
        Clock someClock = Clock.fixed(someTime, ZoneOffset.UTC);

        assertTrue(TimeUtil.getDateAtLastFullMinute().before(Date.from(someTime)));

        TimeUtil.setClock(someClock);

        assertEquals(Date.from(someTimeAtLastMinute), TimeUtil.getDateAtLastFullMinute());
    }

    @Test
    public void testPrecisions() throws IOException {

        for (TimeUtil.Precision precision : TimeUtil.Precision.values()) {

            TimeUtil.setClock(streamClock);

            stream = new Stream(1L, "Test Stream", "Stream for testing", precision,
                    new ArrayList<>(), new ArrayList<>(), null);

            assertEquals(stream.getStartDate().getTime(), streamStartTime.toEpochMilli());

            // Chunk start
            assertEquals(TimeUtil.getChunkStartTime(stream, 0L), stream.getStartDate().getTime());

            // Chunk end
            assertEquals(TimeUtil.getChunkEndTime(stream, 0L),
                    stream.getStartDate().getTime() + precision.getMillis() - 1);

            // get ChunkID from millis
            assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate().getTime()), 0L);
            assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate().getTime() +
                    precision.getMillis() - 1), 0L);

            // get ChunkID from date
            assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate()), 0L);
            assertEquals(TimeUtil.getChunkIdAtTime(stream, new Date(stream.getStartDate().getTime() +
                    precision.getMillis() - 1)), 0L);

            // next chunk is one milli after this chunk
            assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate().getTime() +
                    precision.getMillis()), 1L);
            assertEquals(TimeUtil.getChunkIdAtTime(stream, new Date(stream.getStartDate().getTime() +
                    precision.getMillis())), 1L);

            for (long i = 0; i < 100L; i++) {
                // Chunk start
                assertEquals(stream.getStartDate().getTime() + (i * precision.getMillis()),
                        TimeUtil.getChunkStartTime(stream, i));

                // Chunk end
                assertEquals(stream.getStartDate().getTime() + ((i + 1) * precision.getMillis()) - 1,
                        TimeUtil.getChunkEndTime(stream, i));

                // get ChunkID from date
                assertEquals(TimeUtil.getChunkIdAtTime(stream, new Date(stream.getStartDate().getTime() +
                        (precision.getMillis() * i))), i);
                assertEquals(TimeUtil.getChunkIdAtTime(stream, new Date(stream.getStartDate().getTime() +
                        (precision.getMillis() * (i + 1)) - 1)), i);

                // get ChunkID from millis
                assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate().getTime() +
                        (precision.getMillis() * i)), i);
                assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate().getTime() +
                        (precision.getMillis() * (i + 1)) - 1), i);

                // next chunk is one milli after this chunk
                assertEquals(TimeUtil.getChunkIdAtTime(stream, new Date(stream.getStartDate().getTime() +
                        (precision.getMillis() * (i + 1)))), i + 1);
                assertEquals(TimeUtil.getChunkIdAtTime(stream, stream.getStartDate().getTime() +
                        (precision.getMillis() * (i + 1))), i + 1);
            }
        }
    }

    @Test
    public void testChunksBeforeStart() {
        assertTrue(TimeUtil.getChunkIdAtTime(stream, someTimeBeforeStream.toEpochMilli()) < 0);
        assertTrue(TimeUtil.getChunkIdAtTime(stream, new Date(someTimeBeforeStream.toEpochMilli())) < 0);
    }

}