/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class StreamTest {

    @Test
    public void creation() throws IOException {
        Instant someTime = Instant.parse("2020-02-06T10:00:03Z");
        Instant someTimeAtLastMinute = Instant.parse("2020-02-06T10:00:00Z");
        Clock someClock = Clock.fixed(someTime, ZoneOffset.UTC);

        TimeUtil.setClock(someClock);

        StreamMetaData countMetadata = MetaDataFactory.getMetadataOfType(0, StreamMetaData.MetadataType.COUNT,
                StreamMetaData.MetadataEncryptionSchema.LONG);
        StreamMetaData sumMetadata = MetaDataFactory.getMetadataOfType(1, StreamMetaData.MetadataType.SUM,
                StreamMetaData.MetadataEncryptionSchema.LONG);

        Stream stream = new Stream(1L, "Test Stream", "Stream for testing",
                TimeUtil.Precision.ONE_SECOND,
                Arrays.asList(TimeUtil.Precision.TEN_SECONDS, TimeUtil.Precision.ONE_MINUTE), Arrays.asList(
                countMetadata, sumMetadata), null);

        assertEquals(stream.getStartDate(), Date.from(someTimeAtLastMinute));

        assertTrue(stream.getLastWrittenChunkId() < 0);
        assertEquals(stream.getMetaDataAt(0), countMetadata);
        assertEquals(stream.getMetaDataAt(1), sumMetadata);
    }
}