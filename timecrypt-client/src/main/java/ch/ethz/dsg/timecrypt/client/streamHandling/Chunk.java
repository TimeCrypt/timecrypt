/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.exceptions.ChunkAlreadyWrittenException;
import ch.ethz.dsg.timecrypt.client.exceptions.DuplicateDataPointException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;
import ch.ethz.dsg.timecrypt.client.exceptions.WrongChunkException;
import ch.ethz.dsg.timecrypt.crypto.encryption.TimeCryptChunkEncryption;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

/**
 * TimeCrypt stores data points in a stream as time-ordered chunks of predefined time intervals, i.e.,
 * [t_i ,t_(i+1) ) with a fixed ∆ = t_(i+1) − t_i .
 * <p>
 * The chunks CONTAIN the t_i but do NOT CONTAIN the t_(i+1)
 */
public class Chunk {
    private static final Logger LOGGER = LoggerFactory.getLogger(Chunk.class);

    private final Stream correspondingStream;
    private final long chunkID;
    private final long startTime;
    private final long endTime;
    private final HashMap<Long, DataPoint> values;
    private boolean finalized = false;

    public Chunk(Stream correspondingStream, long chunkID, byte[] encryptedData, StreamKeyManager streamKeyManager)
            throws QueryFailedException {
        this.correspondingStream = correspondingStream;

        if (chunkID < 0) {
            throw new RuntimeException("Chunk ID can never be less than zero! ");
        }
        this.chunkID = chunkID;

        this.startTime = TimeUtil.getChunkStartTime(this.correspondingStream, this.chunkID);
        this.endTime = TimeUtil.getChunkEndTime(this.correspondingStream, this.chunkID);
        this.finalized = true;

        byte[] valueBytes;
        try {
            valueBytes = TimeCryptChunkEncryption.decryptAESGcm(streamKeyManager.getChunkKeyRegression()
                    .getSeed(chunkID), encryptedData);
        } catch (InvalidKeyException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException e) {
            LOGGER.error("Could not decrypt chunk.", e);
            throw new QueryFailedException(QueryFailedException.FailReason.COULD_NOT_DECRYPT_CHUNK, e.getMessage());
        }
        this.values = SerializationUtils.deserialize(valueBytes);
    }

    public Chunk(Stream correspondingStream, long chunkID) {
        this.correspondingStream = correspondingStream;

        if (chunkID < 0) {
            throw new RuntimeException("Chunk ID can never be less than zero! ");
        }
        this.chunkID = chunkID;
        this.startTime = TimeUtil.getChunkStartTime(this.correspondingStream, this.chunkID);
        this.endTime = TimeUtil.getChunkEndTime(this.correspondingStream, this.chunkID);
        this.values = new HashMap<>();
    }

    public Stream getCorrespondingStream() {
        return correspondingStream;
    }

    public long getChunkID() {
        return chunkID;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public Collection<DataPoint> getValues() {
        return values.values();
    }

    public void addDataPoint(Date timestamp, long value) throws WrongChunkException, ChunkAlreadyWrittenException,
            DuplicateDataPointException {

        // some sanitation
        if (timestamp.getTime() < startTime || timestamp.getTime() > endTime) {
            throw new WrongChunkException(timestamp, new Date(this.startTime), new Date(this.endTime), value);
        }

        synchronized (this) {
            if (finalized) {
                throw new ChunkAlreadyWrittenException(timestamp, value);
            } else if (values.containsKey(timestamp.getTime())) {
                throw new DuplicateDataPointException(timestamp, values.get(timestamp.getTime()).getValue(), value);
            } else {
                DataPoint dataPoint = new DataPoint(timestamp, value);
                values.put(timestamp.getTime(), dataPoint);
                LOGGER.debug("Added data point " + dataPoint.toString() + " to chunk " + this.chunkID);
            }
        }
    }

    public void finalizeChunk() {
        synchronized (this) {
            finalized = true;
        }
        LOGGER.debug("Chunk " + this.chunkID + " finalized.");
    }

    public byte[] encrypt(StreamKeyManager streamKeyManager) throws Exception {
        byte[] valueBytes = SerializationUtils.serialize(values);
        return TimeCryptChunkEncryption.encryptAESGcm(streamKeyManager.getChunkKeyRegression().getSeed(chunkID), valueBytes);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "correspondingStream=" + correspondingStream +
                ", chunkID=" + chunkID +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", values=" + values +
                '}';
    }
}
