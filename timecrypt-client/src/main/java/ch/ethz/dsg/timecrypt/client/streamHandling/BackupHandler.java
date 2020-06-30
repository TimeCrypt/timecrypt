/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.exceptions.ChunkAlreadyWrittenException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.DuplicateDataPointException;
import ch.ethz.dsg.timecrypt.client.exceptions.WrongChunkException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class BackupHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkHandler.class);

    private Stream associatedStream;
    private StreamKeyManager streamKeyManager;
    private ServerInterface serverInterface;

    private Chunk curChunk;

    public BackupHandler(Stream associatedStream, StreamKeyManager streamKeyManager, ServerInterface serverInterface, Date backupStartDate) {
        this.associatedStream = associatedStream;
        this.streamKeyManager = streamKeyManager;
        this.serverInterface = serverInterface;
        this.curChunk = new Chunk(associatedStream, TimeUtil.getChunkIdAtTime(associatedStream, backupStartDate.getTime()));

    }

    public void putDataPoint(DataPoint dataPoint) throws ChunkAlreadyWrittenException, WrongChunkException, DuplicateDataPointException {
        long chunkId = TimeUtil.getChunkIdAtTime(associatedStream, dataPoint.getTimestamp().getTime());
        if (chunkId < curChunk.getChunkID()) {
            throw new ChunkAlreadyWrittenException(dataPoint.getTimestamp(), dataPoint.getValue(), "Backup Chunk already written");
        } else if (chunkId == curChunk.getChunkID()) {
            curChunk.addDataPoint(dataPoint.getTimestamp(), dataPoint.getValue());
        } else {
            sendChunk(curChunk);
            for (long id = curChunk.getChunkID() + 1; id < chunkId; id++) {
                sendChunk(new Chunk(associatedStream, id));
            }
            curChunk = new Chunk(associatedStream, chunkId);
        }
    }

    public void flush() {
        sendChunk(curChunk);
        curChunk = new Chunk(associatedStream, curChunk.getChunkID() + 1);;
    }

    private void sendChunk(Chunk curChunk) {
        long chunkId = curChunk.getChunkID();
        LOGGER.debug("Finalizing chunk " + chunkId);
        curChunk.finalizeChunk();
        List<EncryptedMetadata> encryptedMetaData = new ArrayList<>();
        LOGGER.debug("Encrypting metadata for chunk " + chunkId);
        for (StreamMetaData metadata : associatedStream.getMetaData()) {
            encryptedMetaData.add(MetaDataFactory.getEncryptedMetadataForValue(metadata,
                    curChunk.getValues(), streamKeyManager, chunkId));
        }
        EncryptedDigest digest = new EncryptedDigest(associatedStream.getId(), chunkId, chunkId + 1,
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

        long serverChunkId = -1;
        try {
            LOGGER.debug("Sending chunk " + chunkId + " to server.");
            serverChunkId = serverInterface.addChunk(associatedStream.getId(), encryptedChunk, digest);
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
        associatedStream.getLocalChunkStore().setLastWrittenChunkId(serverChunkId);
    }

}
