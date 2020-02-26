/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.exceptions.UnsupportedOperationException;
import ch.ethz.dsg.timecrypt.client.exceptions.*;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptLocalChunkStore;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.*;

import static ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil.getChunkIdAtTime;

/**
 * Handles chunks
 */
public class ChunkHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkHandler.class);
    private static Clock clock = Clock.systemDefaultZone();
    private final long writeWindowMillis;
    private final Stream associatedStream;
    private final StreamKeyManager streamKeyManager;
    private final ServerInterface serverInterface;
    private final SortedMap<Long, Chunk> unwrittenChunks;
    private boolean terminating = false;

    public ChunkHandler(Stream associatedStream, StreamKeyManager streamKeyManager, ServerInterface serverInterface, long write_window_millis) {
        this.associatedStream = associatedStream;
        this.streamKeyManager = streamKeyManager;
        writeWindowMillis = write_window_millis;
        this.unwrittenChunks = new TreeMap<>();
        this.serverInterface = serverInterface;
    }

    public static void setClock(Clock clock) {
        ChunkHandler.clock = clock;
    }

    private Chunk getChunkAtTime(DataPoint dataPoint) throws StreamNotYetStartedException,
            ChunkAlreadyWrittenException {
        long chunkId = getChunkIdAtTime(associatedStream, dataPoint.getTimestamp().getTime());

        if (chunkId < 0) {
            throw new StreamNotYetStartedException(dataPoint.getTimestamp(), associatedStream.getStartDate(),
                    dataPoint.getValue());
        }

        Chunk theChunk;
        synchronized (unwrittenChunks) {
            if (chunkId <= associatedStream.getLocalChunkStore().getLastWrittenChunkId()) {
                throw new ChunkAlreadyWrittenException(dataPoint.getTimestamp(), dataPoint.getValue());
            }

            if (unwrittenChunks.containsKey(chunkId)) {
                theChunk = unwrittenChunks.get(chunkId);
            } else {
                theChunk = new Chunk(associatedStream, chunkId);
                unwrittenChunks.put(chunkId, theChunk);
            }
        }
        return theChunk;
    }

    public void putDataPoint(DataPoint dataPoint) throws ChunkAlreadyWrittenException,
            DataPointOutsideOfWriteWindowException, StreamNotYetStartedException,
            DuplicateDataPointException {
        synchronized (this) {
            if (terminating) {
                throw new ChunkAlreadyWrittenException(dataPoint.getTimestamp(), dataPoint.getValue(), "Can not write " +
                        "chunks right now - ChunkHandler is terminating");
            }
            Date writeWindow = new Date(clock.millis() + writeWindowMillis);
            if (dataPoint.getTimestamp().getTime() > writeWindow.getTime()) {
                throw new DataPointOutsideOfWriteWindowException("Data point timestamp of " + dataPoint.toString() +
                        " is outside of the write window that is open until " + writeWindow);
            }
            try {
                getChunkAtTime(dataPoint).addDataPoint(dataPoint.getTimestamp(), dataPoint.getValue());
            } catch (WrongChunkException e) {
                LOGGER.error("Placed the data point in the wrong chunk", e);
                throw new RuntimeException("Placed the data point in the wrong chunk - implementation error. Please " +
                        "report this bug");
            }
        }
    }

    /**
     * Write all remaining chunks.
     */
    public void terminate() {
        synchronized (this) {
            terminating = true;
        }
    }

    /**
     * write chunks periodically
     * synchronize chunk inserts with chunk writes
     */
    @Override
    public void run() {
        LOGGER.info("Started chunk sender thread.");
        TimeCryptLocalChunkStore chunkStore = associatedStream.getLocalChunkStore();

        long serverChunkId;
        long lastChunkId = chunkStore.getLastWrittenChunkId();
        try {
            serverChunkId = serverInterface.getLastWrittenChunkId(associatedStream.getId());
        } catch (InvalidQueryException e) {
            LOGGER.error("Server reported that he does not know stream " + associatedStream.getId() + " name: " +
                    associatedStream.getName(), e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Server does not know stream.  " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            LOGGER.debug("The server currently does not support receiving the last written chunk ID.");
            serverChunkId = lastChunkId;
        }
        if (serverChunkId != lastChunkId) {
            LOGGER.error("Server reported a different chunkId than we expected for stream " + associatedStream.getId() +
                    "Expected " + lastChunkId + " got " + serverChunkId + ". This means the understanding of the " +
                    "stream got inconsistent between server and client - can't handle that");
            // TODO: raise a useful exception.
            throw new RuntimeException("Server reported a different chunkId than we expected. For stream " +
                    associatedStream.getId());
        }

        // Check if there are non-written chunks from a former execution for sending - chunk store guarantees that
        // they are the next chunk ID to write.
        SortedMap<Long, Pair<EncryptedChunk, EncryptedDigest>> openChunks = chunkStore.getUnwrittenChunks();

        LOGGER.debug("Start writing unwritten chunk.");
        for (long chunkId : openChunks.keySet()) {
            Pair<EncryptedChunk, EncryptedDigest> unwritten = openChunks.get(chunkId);

            try {
                LOGGER.debug("Adding chunk" + unwritten.getLeft().getChunkId());
                serverChunkId = serverInterface.addChunk(associatedStream.getId(), unwritten.getLeft(), unwritten.getRight());
                if (serverChunkId != chunkId) {
                    LOGGER.error("Server reported a different chunkId than we expected for stream " +
                            associatedStream.getId() + "Expected " + lastChunkId + " got " + serverChunkId + ". This " +
                            "means the understanding of the stream got inconsistent between server and client - " +
                            "can't handle that");
                    // TODO: raise a useful exception.
                    throw new RuntimeException("Server reported a different chunkId than we expected. For stream " +
                            associatedStream.getId());
                }
            } catch (CouldNotStoreException e) {
                LOGGER.error("Could not store chunk with chunk ID " + chunkId + " for stream " +
                        associatedStream.getId() + " on the server", e);
                // TODO: raise a useful exception.
                throw new RuntimeException(e);
            }

            try {
                LOGGER.debug("Mark chunk" + unwritten.getLeft().getChunkId() + " as written.");
                chunkStore.markChunkAsWritten(chunkId);
            } catch (Exception e) {
                LOGGER.error("Could mark chunk " + unwritten.getLeft().getChunkId() + " as written to stream " +
                        associatedStream.getId() + "in the chunk store - terminating.", e);
                // TODO: raise a useful exception.
                throw new RuntimeException("Could mark chunk " + unwritten.getLeft().getChunkId() + " as written to " +
                        "stream " + associatedStream.getId() + "in the chunk store. Message:" + e.getMessage());
            }
        }
        LOGGER.debug("Finished writing unwritten chunk.");

        while (!terminating) {
            LOGGER.debug("Create chunks for next write window.");

            // create all chunks for the next writeWindow if they are not yet there
            long now = clock.millis();
            long lastWindow = now - writeWindowMillis;
            long nextWindow = now + writeWindowMillis;

            long maxSendChunkId = getChunkIdAtTime(associatedStream, lastWindow);
            long maxCreateChunkId = getChunkIdAtTime(associatedStream, nextWindow);

            synchronized (unwrittenChunks) {
                for (long chunkId = (associatedStream.getLastWrittenChunkId() + 1);
                     chunkId <= maxCreateChunkId; chunkId++) {
                    if (!unwrittenChunks.containsKey(chunkId)) {
                        LOGGER.debug("Creating chunk " + chunkId);
                        Chunk theChunk = new Chunk(associatedStream, chunkId);
                        unwrittenChunks.put(chunkId, theChunk);
                    }
                }
            }

            LOGGER.debug("Taking care of unwritten chunks.");
            for (long chunkId = (associatedStream.getLastWrittenChunkId() + 1); chunkId < maxSendChunkId; chunkId++) {
                Chunk curChunk = unwrittenChunks.get(chunkId);
                if ((curChunk.getEndTime() + writeWindowMillis) < now) {

                    // sanity check
                    if (chunkId != curChunk.getChunkID()) {
                        throw new RuntimeException("ChunkID got inconsistent in ChunkHandler. Assuming chunk has ID " +
                                chunkId + " but chunk assumes its ID to be " + curChunk.getChunkID());
                    }
                    sendChunk(chunkStore, chunkId, curChunk);
                }
            }
            try {
                LOGGER.debug("Going to sleep.");
                Thread.sleep(associatedStream.getChunkSize());
                LOGGER.debug("Awake again.");
            } catch (InterruptedException e) {
                LOGGER.info("Chunk sender thread was waken up - continuing execution");
            }
        }
        LOGGER.info("Chunk sender thread is terminating. Sending all unwritten chunks that exist.");

        long now = clock.millis();
        long nextWindow = now + writeWindowMillis;
        long maxFinishChunk = getChunkIdAtTime(associatedStream, nextWindow);

        if (unwrittenChunks.lastKey() > maxFinishChunk) {
            LOGGER.error("Unwritten chunks contain a chunk (chunk ID " + unwrittenChunks.lastKey() +
                    ") that is outside of the write window (" + maxFinishChunk + ") - ignoring it while termination!" +
                    "This means the data in all those chunks will be lost.");
        } else {
            maxFinishChunk = unwrittenChunks.lastKey();
        }

        for (long chunkId = (associatedStream.getLastWrittenChunkId() + 1); chunkId < maxFinishChunk; chunkId++) {
            synchronized (unwrittenChunks) {
                if (!unwrittenChunks.containsKey(chunkId)) {
                    LOGGER.debug("Creating chunk " + chunkId);
                    Chunk theChunk = new Chunk(associatedStream, chunkId);
                    unwrittenChunks.put(chunkId, theChunk);
                }
            }
            sendChunk(chunkStore, chunkId, unwrittenChunks.get(chunkId));
        }
        LOGGER.info("Finished chunk sender thread.");
    }

    private void sendChunk(TimeCryptLocalChunkStore chunkStore, long chunkId, Chunk curChunk) {
        LOGGER.debug("Finalizing chunk " + chunkId);
        curChunk.finalizeChunk();
        List<EncryptedMetadata> encryptedMetaData = new ArrayList<>();
        LOGGER.debug("Encrypting metadata for chunk " + chunkId);

        for (StreamMetaData metadata : associatedStream.getMetaData()) {
            encryptedMetaData.add(MetaDataFactory.getEncryptedMetadataForValue(metadata,
                    curChunk.getValues(), streamKeyManager, chunkId));
        }
        EncryptedDigest digest = new EncryptedDigest(associatedStream.getId(), chunkId, chunkId,
                encryptedMetaData);

        EncryptedChunk encryptedChunk;
        try {
            LOGGER.debug("Encrypting chunk " + chunkId);
            encryptedChunk = new EncryptedChunk(associatedStream.getId(), chunkId,
                    curChunk.encrypt(streamKeyManager));
        } catch (Exception e) {
            LOGGER.error("Could not encrypt chunk.", e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Could encrypt chunk " + chunkId + " for stream " + associatedStream.getId() +
                    ". Message:" + e.getMessage());
        }

        try {
            LOGGER.debug("Store unwritten chunk " + chunkId);
            chunkStore.addUnwrittenChunk(encryptedChunk, digest);
        } catch (Exception e) {
            LOGGER.error("Could store chunk " + chunkId + " for stream " + associatedStream.getId() +
                    " in the chunk store before writing - terminating.", e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Could store chunk " + chunkId + " for stream " + associatedStream.getId() +
                    " in the chunk store before writing. Message:" + e.getMessage());
        }

        try {
            LOGGER.debug("Sending chunk " + chunkId + " to server.");
            long serverChunkId = serverInterface.addChunk(associatedStream.getId(), encryptedChunk, digest);
            if (serverChunkId != chunkId) {
                LOGGER.error("Server reported a different chunkId than we expected for stream " + associatedStream.getId() +
                        "Expected " + curChunk + " got " + serverChunkId + ". This means the understanding of the " +
                        "stream got inconsistent between server and client - can't handle that");
                // TODO: raise a useful exception.
                throw new RuntimeException("Server reported a different chunkId than we expected. For stream " +
                        associatedStream.getId());
            }
        } catch (CouldNotStoreException e) {
            LOGGER.error("Could not store chunk with chunk ID " + chunkId + " on the server", e);
            // TODO: raise a useful exception.
            System.exit(1);
        }

        try {
            LOGGER.debug("Marking chunk " + chunkId + " as written.");
            synchronized (unwrittenChunks) {
                chunkStore.markChunkAsWritten(chunkId);
                unwrittenChunks.remove(chunkId);
            }
        } catch (Exception e) {
            LOGGER.error("Could mark chunk " + chunkId + " as written to stream " +
                    associatedStream.getId() + "in the chunk store - terminating.", e);
            // TODO: raise a useful exception.
            throw new RuntimeException("Could mark chunk " + chunkId + " as written to " +
                    "stream " + associatedStream.getId() + "in the chunk store. Message:" + e.getMessage());
        }
    }
}
