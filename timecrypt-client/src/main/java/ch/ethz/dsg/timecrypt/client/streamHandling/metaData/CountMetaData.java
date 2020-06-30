/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling.metaData;

import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Collection;

class CountMetaData implements StreamMetaData {
    private final MetadataEncryptionScheme encryptionScheme;
    private final int id;

    @JsonCreator
    public CountMetaData(MetadataEncryptionScheme encryptionScheme, int id) {
        this.encryptionScheme = encryptionScheme;
        this.id = id;
    }

    @Override
    public long calculate(Collection<DataPoint> dataPoints) {
        return dataPoints.size();
    }

    @Override
    public MetadataType getType() {
        return MetadataType.COUNT;
    }

    @Override
    public MetadataEncryptionScheme getEncryptionScheme() {
        return encryptionScheme;
    }

    @Override
    public int getId() {
        return id;
    }
}
