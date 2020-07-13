/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.encryption.MACCheckFailed;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.CachedKeys;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class Digest implements Comparable<Digest> {

    /**
     * First chunk ID that is part of the aggregated metadata in this digest.
     */
    private final long chunkIdFrom;
    /**
     * Last chunk ID that is part of the aggregated metadata in this digest.
     */
    private final long chunkIdTo;

    private final List<Pair<StreamMetaData, Long>> values;
    private final long correspondingStreamId;

    public Digest(Stream correspondingStream, long chunkIdFrom, long chunkIdTo, List<Pair<StreamMetaData, Long>> values) {
        this.chunkIdFrom = chunkIdFrom;
        this.chunkIdTo = chunkIdTo;
        this.values = values;
        this.correspondingStreamId = correspondingStream.getId();
    }

    public Digest(Stream correspondingStream, EncryptedDigest encryptedDigest, StreamKeyManager streamKeyManager)
            throws MACCheckFailed {
        this.correspondingStreamId = correspondingStream.getId();
        this.values = new ArrayList<>();

        this.chunkIdFrom = encryptedDigest.getChunkIdFrom();
        this.chunkIdTo = encryptedDigest.getChunkIdTo();
        List<EncryptedMetadata> encryptedMetadata = encryptedDigest.getPayload();

        if (chunkIdFrom < 0) {
            // TODO Throw better exception
            throw new RuntimeException("Chunk ID can never be less than zero! ");
        }
        if (chunkIdTo < chunkIdFrom) {
            // TODO Throw better exception
            throw new RuntimeException("ChunkIdTo has to be greater than chunkIdFrom for a digest.");
        }
        CachedKeys cachedKeys = new CachedKeys();
        for (EncryptedMetadata curMetadataItem : encryptedMetadata) {
            values.add(new ImmutablePair<>(correspondingStream.getMetaDataAt(curMetadataItem.getMetadataId()),
                    MetaDataFactory.getValueFromEncryptedMetadata(curMetadataItem, streamKeyManager,
                            chunkIdFrom, chunkIdTo, cachedKeys)));
        }
    }

    public Digest(long correspondingStreamId, EncryptedDigest encryptedDigest, StreamKeyManager streamKeyManager,
                  List<StreamMetaData> metaData)
            throws MACCheckFailed {
        this.correspondingStreamId = correspondingStreamId;
        this.values = new ArrayList<>();

        this.chunkIdFrom = encryptedDigest.getChunkIdFrom();
        this.chunkIdTo = encryptedDigest.getChunkIdTo();
        List<EncryptedMetadata> encryptedMetadata = encryptedDigest.getPayload();

        if (chunkIdFrom < 0) {
            throw new RuntimeException("Chunk ID can never be less than zero! ");
        }
        if (chunkIdTo < chunkIdFrom) {
            throw new RuntimeException("ChunkIdTo has to be greater than chunkIdFrom for a digest.");
        }

        CachedKeys cachedKeys = new CachedKeys();
        for (EncryptedMetadata curMetadataItem : encryptedMetadata) {
            StreamMetaData correspondingMetaData = null;

            // Yes this inefficient but this constructor is currently only used for testing
            for (StreamMetaData streamMetaData : metaData) {
                if (streamMetaData.getId() == curMetadataItem.getMetadataId()) {
                    correspondingMetaData = streamMetaData;
                }
            }

            values.add(new ImmutablePair<>(correspondingMetaData,
                    MetaDataFactory.getValueFromEncryptedMetadata(curMetadataItem, streamKeyManager,
                            chunkIdFrom, chunkIdTo, cachedKeys)));
        }
    }

    public long getChunkIdFrom() {
        return chunkIdFrom;
    }

    public long getChunkIdTo() {
        return chunkIdTo;
    }

    public List<Pair<StreamMetaData, Long>> getValues() {
        return values;
    }

    public long getCorrespondingStreamId() {
        return correspondingStreamId;
    }

    @Override
    public int compareTo(Digest digest) {
        if (this.correspondingStreamId != digest.correspondingStreamId) {
            return getComparableDiffFromLong(this.correspondingStreamId -
                    digest.correspondingStreamId);
        }
        long idDelta = this.getChunkIdFrom() - digest.getChunkIdFrom();

        if (idDelta != 0) {
            return getComparableDiffFromLong(idDelta);
        }

        idDelta = this.getChunkIdTo() - digest.getChunkIdTo();

        if (idDelta != 0) {
            return getComparableDiffFromLong(idDelta);
        }

        return 0;
    }

    private int getComparableDiffFromLong(long delta) {
        if (delta > Integer.MAX_VALUE) {
            return +1;
        } else if (delta < Integer.MIN_VALUE) {
            return -1;
        } else {
            return (int) delta;
        }
    }

}
