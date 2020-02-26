/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

import java.util.Date;

/**
 * Exception that occurs when some process tried to write to the wrong chunk.
 */
public class WrongChunkException extends Exception {

    /**
     * Create a new exception for a value that was tried to write to a chunk that does not contain its corresponding
     * timestamp .
     *
     * @param valueTimestamp      Timestamp of the value that was tried to write.
     * @param chunkStartTimestamp The start timestamp of the chunk where the value should have been inserted.
     * @param chunkEndTimestamp   The end timestamp of the chunk where the value should have been inserted.
     * @param value               The value that was tried to write.
     */
    public WrongChunkException(Date valueTimestamp, Date chunkStartTimestamp, Date chunkEndTimestamp, long value) {
        super("Tried to write value " + value + "  at timestamp " + valueTimestamp.toString() +
                " / " + valueTimestamp.getTime() + " to a chunk that ranged from " + chunkStartTimestamp + " / " +
                chunkStartTimestamp.getTime() + " (inclusive) to " + chunkEndTimestamp + " / " +
                chunkEndTimestamp.getTime() + " (inclusive).");
    }

    /**
     * Create a new exception with a custom message.
     */
    public WrongChunkException(String msg) {
        super(msg);

    }
}
