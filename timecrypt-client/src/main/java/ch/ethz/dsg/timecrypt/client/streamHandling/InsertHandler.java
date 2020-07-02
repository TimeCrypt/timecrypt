/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */


package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.exceptions.*;

public interface InsertHandler {


    /**
     * Store a data point in a stream.
     * <p>
     * Live Handler:
     * Data points should only be in one write window aground the current time (so
     * maximal one write window in the past or one write window in the future or one write window into the past.
     * <p>
     * If it is the first data point that is inserted for this TimeCryptClient the client will also start a chunk
     * handler thread which will ensure that all chunks before the current write window are stored on the server.
     * The chunk handler thread will also write empty chunks to the server if there were no data points in the time
     * interval that is represented by the chunk.
     * The producer thread will run until the clients termination.
     *
     * @param dataPoint The Data point to write to the stream.
     * @throws CouldNotStoreException                 Indicates that it was not possible to store the given data point
     *                                                on the server.
     * @throws StreamNotYetStartedException           Indicates that the data point that should be added is before the
     *                                                start of the stream.
     * @throws ChunkAlreadyWrittenException           Indicates that the data point that should have been added is in
     *                                                a chunk that was already send to the server.
     * @throws DataPointOutsideOfWriteWindowException Indicates that the data point is ahead of the current write
     *                                                window.
     * @throws DuplicateDataPointException            Indicates that there was already a value for this exact point in
     *                                                time (with a granularity of one millisecond (or less if your JVM
     *                                                does not support such fine grained times)).
     */
    void writeDataPointToStream(DataPoint dataPoint) throws TCWriteException;

    /**
     * Flushes all current local data to the server
     */
    void flush();

    /**
     * Stop execution of this handle
     * Stop producing values and finishes all chunks in all the producer threads and sends them
     * blocks until this is done.
     * <p>
     * This method should always be called before exiting the program that uses the InsertHandler.
     */
    void terminate() throws InterruptedException;

    long getStreamID();
}
