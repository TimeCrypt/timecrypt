/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface.nettyserver;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.exceptions.UnsupportedOperationException;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Random;

public class NettyServerClient implements ServerInterface {

    // TODO: add real authentication
    public static final String DUMMY_OWNER = "NONE";

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyServerClient.class);
    private final String ip;
    private final int port;
    private BasicClient client;
    private Random rand = new Random();

    public NettyServerClient(String ip, int port) throws IOException {
        this.ip = ip;
        this.port = port;
        createNewConnection();
    }

    private void createNewConnection() throws IOException {
        client = new BasicClient(ip, port);
    }

    @Override
    public long createStream(List<StreamMetaData> metadataConfig) throws CouldNotStoreException {
        //TODO: Generate the uid on the server
        long uid = rand.nextLong();
        try {
            if (!client.createStream(uid, DUMMY_OWNER, metadataConfig.size())) {
                throw new CouldNotStoreException("Create stream failed");
            }
        } catch (IOException e) {
            LOGGER.error("Tried to get create stream got error.", e);
            try {
                createNewConnection();
            } catch (IOException ex) {
                LOGGER.error("Could not create a new connection to server", ex);
            }
            throw new CouldNotStoreException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Tried to get create stream got error.", e);
            throw new CouldNotStoreException(e.getMessage());
        }
        return uid;
    }

    @Override
    public long getLastWrittenChunkId(long streamId) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("The TimeCrypt server currently does not support getting the last " +
                "written Chunk");
    }

    @Override
    public long addChunk(long streamId, EncryptedChunk chunk, EncryptedDigest digest) throws CouldNotStoreException {
        try {
            if (!client.insertChunk(chunk, streamId, DUMMY_OWNER, digest)) {
                throw new CouldNotStoreException("Store failed");
            }
        } catch (IOException e) {
            LOGGER.error("Tried to get chunks from stream id " + streamId + " got error.", e);
            try {
                createNewConnection();
            } catch (IOException ex) {
                LOGGER.error("Could not create a new connection to server", ex);
            }
            throw new CouldNotStoreException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Tried to get chunks from stream id " + streamId + " got error.", e);
            throw new CouldNotStoreException(e.getMessage());
        }
        // Currently cassandra does not really support this. We trust it to be honest.
        return chunk.getChunkId();
    }

    @Override
    public List<EncryptedChunk> getChunks(long streamId, long from, long to) throws CouldNotReceiveException {
        List<EncryptedChunk> result;
        try {
            result = client.getChunks(DUMMY_OWNER, streamId, from, to);
        } catch (IOException e) {
            LOGGER.error("Tried to get chunks from stream id " + streamId + " got error.", e);
            try {
                createNewConnection();
            } catch (IOException ex) {
                LOGGER.error("Could not create a new connection to server", ex);
            }
            throw new CouldNotReceiveException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Tried to get chunks from stream id " + streamId + " got error.", e);
            throw new CouldNotReceiveException(e.getMessage());
        }
        return result;
    }

    @Override
    public void deleteStream(long streamId) throws InvalidQueryException {
        try {
            client.deleteStream(DUMMY_OWNER, streamId);
        } catch (IOException e) {
            LOGGER.error("Tried to delete stream id " + streamId + " got error.", e);
            try {
                createNewConnection();
            } catch (IOException ex) {
                LOGGER.error("Could not create a new connection to server", ex);
            }
            throw new InvalidQueryException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Tried to delete stream id " + streamId + " got error.", e);
            throw new InvalidQueryException(e.getMessage());
        }
    }

    @Override
    public List<EncryptedDigest> getStatisticalData(long streamId, long chunkIdFrom, long chunkIdTo, int granularity, List<StreamMetaData> metaData) throws InvalidQueryException {
        List<EncryptedDigest> result;
        try {
            result = client.getStatistics(streamId, DUMMY_OWNER, chunkIdFrom, chunkIdTo - 1, granularity, metaData);
        } catch (IOException e) {
            LOGGER.error("Tried to get statistics for  stream id " + streamId + " got error.", e);
            try {
                createNewConnection();
            } catch (IOException ex) {
                LOGGER.error("Could not create a new connection to server", ex);
            }
            throw new InvalidQueryException(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Tried to get statistics for  stream id " + streamId + " got error.", e);
            throw new InvalidQueryException(e.getMessage());
        }
        return result;
    }
}
