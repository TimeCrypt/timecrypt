/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

import java.util.Date;

/**
 * Exception that occurs when a value at a given time in a chunk is already written.
 */
public class DuplicateDataPointException extends TCWriteException {

    private final Date timestamp;
    private final long oldValue;
    private final long newValue;

    /**
     * Create a new exception for a value that was tried to write while another value was already present.
     *
     * @param timestamp Timestamp of the value that was tried to write.
     * @param oldValue  The already existing value.
     * @param newValue  The value that was tried to write.
     */
    public DuplicateDataPointException(Date timestamp, long oldValue, long newValue) {
        super("Tried to write value " + newValue + " to a chunk at timestamp " + timestamp.toString() +
                " but it alredy had value " + newValue);
        this.timestamp = timestamp;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /**
     * The timestamp at which the value was tried to put.
     *
     * @return The timestamp.
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * The value that was already there when the write was tried.
     *
     * @return The value.
     */
    public long getOldValue() {
        return oldValue;
    }

    /**
     * The value that tried to write.
     *
     * @return The value.
     */
    public long getNewValue() {
        return newValue;
    }
}
