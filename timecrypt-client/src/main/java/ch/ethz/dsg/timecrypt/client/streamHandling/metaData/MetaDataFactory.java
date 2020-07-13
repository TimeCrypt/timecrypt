/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling.metaData;


import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.crypto.encryption.*;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.CachedKeys;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Collection;

public class MetaDataFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataFactory.class);

    // TODO switch this to reflection
    public static StreamMetaData getMetadataOfType(int id, StreamMetaData.MetadataType type,
                                                   StreamMetaData.MetadataEncryptionScheme scheme) {
        switch (type) {
            case SUM:
                return new SumMetaData(scheme, id);
            case COUNT:
                return new CountMetaData(scheme, id);
            case SQUARE:
                return new SquareMetaData(scheme, id);
            default:
                return null;
        }
    }

    public static long getValueFromEncryptedMetadata(EncryptedMetadata encryptedMetadata, StreamKeyManager
            streamKeyManager, long chunkIdFrom, long chunkIdTo, CachedKeys cachedKeys) throws MACCheckFailed {

        switch (encryptedMetadata.getEncryptionScheme()) {
            case LONG:
                return new TimeCryptEncryptionLong(streamKeyManager.getTreeKeyRegression()).
                        decryptMetadata(encryptedMetadata.getPayloadAsLong(), chunkIdFrom, chunkIdTo - 1,
                                encryptedMetadata.getMetadataId(), cachedKeys);
            case LONG_MAC:
                return new TimeCryptEncryptionLongPlus(streamKeyManager.getTreeKeyRegression(),
                        streamKeyManager.getMacKeyAsBigInteger())
                        .decryptMetadata(new TimeCryptEncryptionLongPlus.TCAuthLongCiphertext(
                                        encryptedMetadata.getPayloadAsLong(), encryptedMetadata.getMacAsBigInteger())
                                , chunkIdFrom, chunkIdTo - 1, encryptedMetadata.getMetadataId(), cachedKeys);
            case BIG_INT_128:
                return new TimeCryptEncryptionBI(streamKeyManager.getTreeKeyRegression(), 128).
                        decryptMetadataLong(encryptedMetadata.getPayloadAsBigInteger()
                                , chunkIdFrom, chunkIdTo - 1, encryptedMetadata.getMetadataId(), cachedKeys);
            case BIG_INT_128_MAC:
                return new TimeCryptEncryptionBIPlus(
                        streamKeyManager.getTreeKeyRegression(), streamKeyManager.getMacKeyAsBigInteger(), 128).
                        decryptMetadataLong(new TimeCryptEncryptionBIPlus.TCAuthBICiphertext(
                                        encryptedMetadata.getPayloadAsBigInteger(), encryptedMetadata.getMacAsBigInteger())
                                , chunkIdFrom, chunkIdTo - 1, encryptedMetadata.getMetadataId(), cachedKeys);
            default:
                throw new RuntimeException("Can not encrypt metadata for unknown metadata encryption scheme " +
                        encryptedMetadata);
        }
    }


    /**
     * Merging Two encrypted Metadata. This should usually not be used in the client since it has access to the plain
     * data but it is needed for testing.
     *
     * @param one An encrypted metadata item.
     * @param two Another encrypted metadata item.
     * @return A encrypted metadata item containing the sum of both.
     */
    public static EncryptedMetadata mergeEncyptedMetadata(EncryptedMetadata one, EncryptedMetadata two) {

        if (!one.getEncryptionScheme().equals(two.getEncryptionScheme())) {
            throw new RuntimeException("Tried to merge two meta data items of different kind! One: " + one + " Two: "
                    + two);
        }

        if (one.getMetadataId() != two.getMetadataId()) {
            throw new RuntimeException("Tried to merge two meta data items of different ID! One: " + one + " Two: "
                    + two);
        }
        switch (one.getEncryptionScheme()) {
            case LONG:
                return new EncryptedMetadata(one.getPayloadAsLong() + two.getPayloadAsLong(),
                        one.getMetadataId(), one.getEncryptionScheme());
            case LONG_MAC:
                return new EncryptedMetadata(one.getPayloadAsLong() + two.getPayloadAsLong(),
                        one.getMacAsBigInteger().add(two.getMacAsBigInteger()),
                        one.getMetadataId(), one.getEncryptionScheme());
            case BIG_INT_128:
                return new EncryptedMetadata(one.getPayloadAsBigInteger().add(two.getPayloadAsBigInteger()),
                        one.getMetadataId(), one.getEncryptionScheme());
            case BIG_INT_128_MAC:
                return new EncryptedMetadata(one.getPayloadAsBigInteger().add(two.getPayloadAsBigInteger()),
                        one.getMacAsBigInteger().add(two.getMacAsBigInteger()),
                        one.getMetadataId(), one.getEncryptionScheme());
            default:
                throw new RuntimeException("Can not encrypt metadata for unknown metadata encryption scheme");
        }

    }


    public static EncryptedMetadata getEncryptedMetadataForValue(StreamMetaData metadata, Collection<DataPoint> value,
                                                                 StreamKeyManager streamKeyManager, long chunkId,
                                                                 CachedKeys cachedkeys) {
        EncryptedMetadata encryptedMetadata;
        LOGGER.debug("starting to encrypt metadata " + metadata.getId() + " in " + chunkId);
        switch (metadata.getEncryptionScheme()) {
            case LONG:
                encryptedMetadata = new EncryptedMetadata(new TimeCryptEncryptionLong(streamKeyManager.getTreeKeyRegression()).
                        encryptMetadata(metadata.calculate(value), chunkId, metadata.getId(), cachedkeys), metadata.getId(),
                        metadata.getEncryptionScheme());
                LOGGER.debug("finished to encrypt metadata " + metadata.getId() + " in " + chunkId);
                return encryptedMetadata;
            case LONG_MAC:
                TimeCryptEncryptionLongPlus.TCAuthLongCiphertext ciphertext = new TimeCryptEncryptionLongPlus(
                        streamKeyManager.getTreeKeyRegression(), streamKeyManager.getMacKeyAsBigInteger())
                        .encryptMetadata(metadata.calculate(value), chunkId, metadata.getId(), cachedkeys);
                encryptedMetadata = new EncryptedMetadata(ciphertext.ciphertext, ciphertext.authCode, metadata.getId(),
                        metadata.getEncryptionScheme());
                LOGGER.debug("finished to encrypt metadata " + metadata.getId() + " in " + chunkId);
                return encryptedMetadata;
            case BIG_INT_128:
                encryptedMetadata = new EncryptedMetadata(new TimeCryptEncryptionBI(streamKeyManager.getTreeKeyRegression(),
                        128).encryptMetadata(BigInteger.valueOf(metadata.calculate(value)), chunkId,
                        metadata.getId(), cachedkeys), metadata.getId(), metadata.getEncryptionScheme());
                LOGGER.debug("finished to encrypt metadata " + metadata.getId() + " in " + chunkId);
                return encryptedMetadata;
            case BIG_INT_128_MAC:
                TimeCryptEncryptionBIPlus.TCAuthBICiphertext ciphertextBi = new TimeCryptEncryptionBIPlus(
                        streamKeyManager.getTreeKeyRegression(), streamKeyManager.getMacKeyAsBigInteger(), 128).
                        encryptMetadata(BigInteger.valueOf(metadata.calculate(value)), chunkId, metadata.getId(), cachedkeys);
                encryptedMetadata = new EncryptedMetadata(ciphertextBi.ciphertext, ciphertextBi.authCode, metadata.getId(),
                        metadata.getEncryptionScheme());
                LOGGER.debug("finished to encrypt metadata " + metadata.getId() + " in " + chunkId);
                return encryptedMetadata;
            default:
                throw new RuntimeException("Can not encrypt metadata for unknown metadata encryption scheme");
        }
    }
}
