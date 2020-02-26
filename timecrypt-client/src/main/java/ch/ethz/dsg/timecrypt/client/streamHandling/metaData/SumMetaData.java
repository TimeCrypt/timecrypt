/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling.metaData;

import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collection;

/**
 * Implements the SUM Metadata type.
 */
class SumMetaData implements StreamMetaData {
    private final MetadataEncryptionSchema encryptionSchema;
    private final int id;

    @JsonCreator
    public SumMetaData(MetadataEncryptionSchema encryptionSchema, int id) {
        this.encryptionSchema = encryptionSchema;
        this.id = id;
    }

    @Override
    public long calculate(Collection<DataPoint> dataPoints) {
        long val = 0;
        for (DataPoint dataPoint : dataPoints) {
            val += dataPoint.getValue();
        }
        return val;
    }

    @Override
    public MetadataType getType() {
        return MetadataType.SUM;
    }

    @Override
    public MetadataEncryptionSchema getEncryptionSchema() {
        return encryptionSchema;
    }

    @Override
    public int getId() {
        return id;
    }
}
