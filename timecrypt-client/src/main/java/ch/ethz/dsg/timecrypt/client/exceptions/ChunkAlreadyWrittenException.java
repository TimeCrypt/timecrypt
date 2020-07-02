/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

import java.util.Date;

/**
 * Exception that occurs when it is tried to write a value to a chunk that was already encrypted and is therefore
 * not writable anymore.
 */
public class ChunkAlreadyWrittenException extends TCWriteException {

    private final Date timestamp;
    private final long value;

    /**
     * Create a new exception for a value that was tried to write while the corresponding chunk was already inserted
     *
     * @param timestamp Timestamp of the value that was tried to write.
     * @param value     The value that was tried to write.
     */
    public ChunkAlreadyWrittenException(Date timestamp, long value, String msg) {
        super("Tried to write value " + value + " to a chunk at timestamp " + timestamp.toString() +
                " but an Error occurred:." + msg);
        this.timestamp = timestamp;
        this.value = value;
    }

    /**
     * Create a new exception for a value that was tried to write while the corresponding chunk was already inserted
     *
     * @param timestamp Timestamp of the value that was tried to write.
     * @param value     The value that was tried to write.
     */
    public ChunkAlreadyWrittenException(Date timestamp, long value) {
        super("Tried to write value " + value + " to a chunk at timestamp " + timestamp.toString() +
                " but it was already written.");
        this.timestamp = timestamp;
        this.value = value;
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
     * The value that tried to write.
     *
     * @return The value.
     */
    public long getValue() {
        return value;
    }
}
