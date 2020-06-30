/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling.metaData;

import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a bit stupid - it's more to showcase metadata testing.
 */
class CountMetaDataTest {

    @Test
    void calculate() {
        CountMetaData metaData = new CountMetaData(StreamMetaData.MetadataEncryptionScheme.LONG, 0);

        List<DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            dataPoints.add(new DataPoint(new Date(), i));
            assertEquals(dataPoints.size(), metaData.calculate(dataPoints));
        }

    }
}