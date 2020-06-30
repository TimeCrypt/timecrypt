/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.state.TimeCryptLocalChunkStore;
import ch.ethz.dsg.timecrypt.client.state.YamlTimeCryptLocalChunkStore;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class Stream {

    private final String name;
    private final String description;
    private final Date startDate;

    // The size of the chunks in milliseconds (i.e. the delta of time between every chunk).
    // This affects the precision of the analytics since fast analytical queries can only be computed with this
    // granularity (for all other queries a full scan of all data point would be needed.
    // The highest resolution for queries and access control is defined by the chunk size
    private final long chunkSize;
    private final TimeUtil.Precision precision;
    private final List<TimeUtil.Precision> resolutionLevels;
    private final List<StreamMetaData> metaData;
    private final long id;
    private final String localChunkStorePath;
    @JsonIgnore
    private final TimeCryptLocalChunkStore localChunkStore;

    @JsonCreator
    public Stream(String name, String description, Date startDate, long chunkSize, TimeUtil.Precision precision,
                  List<TimeUtil.Precision> resolutionLevels, List<StreamMetaData> metaData, long id,
                  String localChunkStorePath) throws Exception {
        this.name = name;
        this.description = description;
        this.chunkSize = chunkSize;
        this.precision = precision;
        this.resolutionLevels = resolutionLevels;
        this.id = id;
        this.metaData = metaData;
        this.startDate = startDate;
        this.localChunkStorePath = localChunkStorePath;
        this.localChunkStore = YamlTimeCryptLocalChunkStore.loadYamlLocalChunkStore(this.localChunkStorePath);
    }

    /**
     * Creates a new TimeCrypt stream and accepts all user defined arguments.
     *
     * @param id                  The stream ID that was defined by the server.
     * @param name                A human readable name - not used for anything inside TimeCrypt
     * @param description         A human readable description - not used for anything inside TimeCrypt
     * @param chunkSize           The size of the chunks defines the maximum precision of aggregations.
     * @param resolutionLevels    The resolution levels define the granularity of sharing Streams with others.
     * @param metaData            The requested meta data types define the kind of computations that get supported.
     * @param localChunkStorePath The path for the chunk store to use when saving unwritten chunks before transmission.
     * @param streamStartDate     The start Date of the stream.
     * @throws IOException Exception that is thrown if the local chunk store could not be created.
     */
    public Stream(long id, String name, String description, TimeUtil.Precision chunkSize, List<TimeUtil.Precision> resolutionLevels,
                  List<StreamMetaData> metaData, String localChunkStorePath, Date streamStartDate) throws IOException {
        this.name = name;
        this.description = description;
        this.precision = chunkSize;
        this.chunkSize = chunkSize.getMillis();
        this.resolutionLevels = resolutionLevels;
        this.id = id;
        this.metaData = metaData;
        this.localChunkStorePath = localChunkStorePath;
        this.localChunkStore = new YamlTimeCryptLocalChunkStore(this.localChunkStorePath);
        this.startDate = streamStartDate;
    }

    /**
     * Creates a new TimeCrypt stream and accepts all user defined arguments.
     *
     * @param id                  The stream ID that was defined by the server.
     * @param name                A human readable name - not used for anything inside TimeCrypt
     * @param description         A human readable description - not used for anything inside TimeCrypt
     * @param chunkSize           The size of the chunks defines the maximum precision of aggregations.
     * @param resolutionLevels    The resolution levels define the granularity of sharing Streams with others.
     * @param metaData            The requested meta data types define the kind of computations that get supported.
     * @param localChunkStorePath The path for the chunk store to use when saving unwritten chunks before transmission.
     * @throws IOException Exception that is thrown if the local chunk store could not be created.
     */
    public Stream(long id, String name, String description, TimeUtil.Precision chunkSize, List<TimeUtil.Precision> resolutionLevels,
                  List<StreamMetaData> metaData, String localChunkStorePath) throws IOException {
        this(id, name, description, chunkSize, resolutionLevels, metaData, localChunkStorePath, TimeUtil.getDateAtLastFullMinute());
    }

    public TimeUtil.Precision getPrecision() {
        return precision;
    }

    public String getLocalChunkStorePath() {
        return localChunkStorePath;
    }

    public List<StreamMetaData> getMetaData() {
        return metaData;
    }

    @JsonIgnore
    public long getLastWrittenChunkId() {
        return localChunkStore.getLastWrittenChunkId();
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public List<TimeUtil.Precision> getResolutionLevels() {
        return resolutionLevels;
    }

    public Date getStartDate() {
        return startDate;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TimeCryptLocalChunkStore getLocalChunkStore() {
        return localChunkStore;
    }

    public StreamMetaData getMetaDataAt(int metadataId) {
        for (StreamMetaData item : metaData) {
            if (item.getId() == metadataId) {
                return item;
            }
        }
        return null;
    }
}
