/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling.metaData;

import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collection;

/**
 * Abstraction of stream meta data. Meta data are calculated on a bunch of data points and later stored on the
 * TimeCrypt server. Meta data get aggregated by the server for faster query processing while the data points (stored
 * in chunks that represent a certain time interval) will not be needed anymore.
 * <p>
 * Implement new meta data types to allow the fast computation of new types of queries.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public interface StreamMetaData {

    /**
     * Calculate the value of a meta data item.
     *
     * @param dataPoints A bunch of data points.
     * @return The aggregation of the points according to the type of meta data represented by the concrete instance.
     */
    long calculate(Collection<DataPoint> dataPoints);

    /**
     * Return the type of meta data stored in this item.
     *
     * @return The type of the item.
     */
    @JsonIgnore
    MetadataType getType();

    /**
     * Return the encryption schema used by this meta data item.
     *
     * @return The encryption schema of the item.
     */
    MetadataEncryptionSchema getEncryptionSchema();

    /**
     * Return the ID for this meta data item inside the list of the meta data of the corresponding stream. This must be
     * unique for every meta data item of a stream.
     *
     * @return The unique ID of the meta data item inside the stream.
     */
    int getId();

    /**
     * The supported types of stream meta data. They should be types of values that still make sense when aggregated
     * by the server.
     */
    enum MetadataType {
        SUM, COUNT
    }

    /**
     * The different encryption schemas that are currently supported by TimeCrypt. They optionally also contain MAC
     * for authentication of the data.
     */
    enum MetadataEncryptionSchema {
        LONG, LONG_MAC, BIG_INT_128, BIG_INT_128_MAC
    }
}
