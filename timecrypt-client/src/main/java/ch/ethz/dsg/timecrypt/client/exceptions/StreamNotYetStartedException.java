/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

import java.util.Date;

/**
 * Exception that occurs when it is tried to write a value to a stream that has its start date after the timestamp of
 * the value.
 * <p>
 * Streams always start at the next ful minute - therefore it might occur that they start after it is tried to
 * write the first value
 */
public class StreamNotYetStartedException extends Exception {

    private final Date dataTimestamp;
    private final Date streamTimestamp;
    private final long value;

    /**
     * Create a new exception for a value that was tried to write while the corresponding stream was not yet ready.
     *
     * @param dataTimestamp   The timestamp of the data point that was tried to write.
     * @param streamTimestamp The timestamp at which the stream starts.
     * @param value           The value that was tried to write.
     */
    public StreamNotYetStartedException(Date dataTimestamp, Date streamTimestamp, long value) {
        super("Tried to write value " + value + " to a chunk at timestamp " + dataTimestamp.toString() +
                " but it stream only starts at " + streamTimestamp.toString());
        this.dataTimestamp = dataTimestamp;
        this.streamTimestamp = streamTimestamp;
        this.value = value;
    }

    /**
     * The start timestamp of the stream where the data should have been inserted.
     *
     * @return The timestamp.
     */
    public Date getStreamTimestamp() {
        return streamTimestamp;
    }

    /**
     * The timestamp at which the value was tried to put.
     *
     * @return The timestamp.
     */
    public Date getDataTimestamp() {
        return dataTimestamp;
    }

    /**
     * The value that tried to write.
     *
     * @return The value.
     */
    public long getValue() {
        return value;
    }
}
