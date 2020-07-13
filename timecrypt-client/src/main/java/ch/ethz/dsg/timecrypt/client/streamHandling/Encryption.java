/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.MetaDataFactory;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.CachedKeys;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Encryption {

    public static EncryptedDigest encryptMetadata(List<StreamMetaData> metaDataItems, StreamKeyManager streamKeyManager,
                                                  Collection<DataPoint> dataPoints, long streamId, long chunkId) {
        List<EncryptedMetadata> encryptedMetaData = new ArrayList<>();

        CachedKeys keys = new CachedKeys();
        for (StreamMetaData metadata : metaDataItems) {
            encryptedMetaData.add(MetaDataFactory.getEncryptedMetadataForValue(metadata,
                    dataPoints, streamKeyManager, chunkId, keys));
        }
        return new EncryptedDigest(streamId, chunkId, chunkId + 1,
                encryptedMetaData);
    }

    public static EncryptedChunk encryptChunk(Chunk chunk, StreamKeyManager streamKeyManager,
                                              long streamId, long chunkId) throws Exception {

        return new EncryptedChunk(streamId, chunkId, chunk.encrypt(streamKeyManager));
    }

    public static CiphertextPair encryptMetadataAndChunk(List<StreamMetaData> metaDataItems, Chunk chunk, StreamKeyManager streamKeyManager,
                                                  long streamId, long chunkId) throws Exception {
        List<EncryptedMetadata> encryptedMetaData = new ArrayList<>();
        CiphertextPair pair = new CiphertextPair();

        CachedKeys keys = new CachedKeys();
        for (StreamMetaData metadata : metaDataItems) {
            encryptedMetaData.add(MetaDataFactory.getEncryptedMetadataForValue(metadata,
                    chunk.getValues(), streamKeyManager, chunkId, keys));
        }
        pair.metadata = new EncryptedDigest(streamId, chunkId, chunkId + 1,
                encryptedMetaData);

        pair.chunk = new EncryptedChunk(streamId, chunkId, chunk.encrypt(streamKeyManager, keys));
        return pair;
    }

    public static class CiphertextPair {
        public EncryptedDigest metadata;
        public EncryptedChunk chunk;
    }
}

