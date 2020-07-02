/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */


package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.exceptions.TCWriteException;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;

import java.util.List;

public class TCLiveWriteHandler implements InsertHandler {

    private final static int MAX_CHUNK_HANDLER_SHUTDOWN_TIME = 1000;

    private final TCWriteHandler handler;
    private final Thread handlerThread;
    private final List<InsertHandler> openHandlers;

    public TCLiveWriteHandler(Stream associatedStream, StreamKeyManager streamKeyManager,
                              ServerInterface serverInterface, long writeWindowMillis, List<InsertHandler> openHandlers) {
        this.handler = new TCWriteHandler(associatedStream, streamKeyManager, serverInterface, writeWindowMillis);
        this.openHandlers = openHandlers;
        openHandlers.add(this);
        this.handlerThread = new Thread(this.handler);
        this.handlerThread.start();
    }

    @Override
    public void writeDataPointToStream(DataPoint dataPoint) throws TCWriteException {
        handler.putDataPoint(dataPoint);
    }

    @Override
    public void flush() {
        handler.flush();
    }

    @Override
    public void terminate() throws InterruptedException {
        synchronized (this) {
            this.handler.terminate();
        }
        this.handlerThread.join(MAX_CHUNK_HANDLER_SHUTDOWN_TIME);
        openHandlers.remove(this);
    }

    @Override
    public long getStreamID() {
        return this.handler.associatedStream.getId();
    }
}
