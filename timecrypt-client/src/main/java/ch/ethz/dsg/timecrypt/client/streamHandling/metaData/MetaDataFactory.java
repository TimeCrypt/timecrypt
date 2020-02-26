/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling.metaData;


import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.crypto.encryption.*;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;

import java.math.BigInteger;
import java.util.Collection;

public class MetaDataFactory {

    // TODO switch this to reflection
    public static StreamMetaData getMetadataOfType(int id, StreamMetaData.MetadataType type,
                                                   StreamMetaData.MetadataEncryptionSchema schema) {
        switch (type) {
            case SUM:
                return new SumMetaData(schema, id);
            case COUNT:
                return new CountMetaData(schema, id);
            default:
                return null;
        }
    }

    public static long getValueFromEncryptedMetadata(EncryptedMetadata encryptedMetadata, StreamKeyManager
            streamKeyManager, long chunkIdFrom, long chunkIdTo) throws MACCheckFailed {

        switch (encryptedMetadata.getEncryptionSchema()) {
            case LONG:
                return new TimeCryptEncryptionLong(streamKeyManager.getChunkKeyRegression()).
                        decryptMetadata(encryptedMetadata.getPayloadAsLong(), chunkIdFrom, chunkIdTo,
                                encryptedMetadata.getMetadataId());
            case LONG_MAC:
                return new TimeCryptEncryptionLongPlus(streamKeyManager.getChunkKeyRegression(),
                        streamKeyManager.getMacKeyAsBigInteger())
                        .decryptMetadata(new TimeCryptEncryptionLongPlus.TCAuthLongCiphertext(
                                        encryptedMetadata.getPayloadAsLong(), encryptedMetadata.getMacAsBigInteger())
                                , chunkIdFrom, chunkIdTo, encryptedMetadata.getMetadataId());
            case BIG_INT_128:
                return new TimeCryptEncryptionBI(streamKeyManager.getChunkKeyRegression(), 128).
                        decryptMetadataLong(encryptedMetadata.getPayloadAsBigInteger()
                                , chunkIdFrom, chunkIdTo, encryptedMetadata.getMetadataId());
            case BIG_INT_128_MAC:
                return new TimeCryptEncryptionBIPlus(
                        streamKeyManager.getChunkKeyRegression(), streamKeyManager.getMacKeyAsBigInteger(), 128).
                        decryptMetadataLong(new TimeCryptEncryptionBIPlus.TCAuthBICiphertext(
                                        encryptedMetadata.getPayloadAsBigInteger(), encryptedMetadata.getMacAsBigInteger())
                                , chunkIdFrom, chunkIdTo, encryptedMetadata.getMetadataId());
            default:
                throw new RuntimeException("Can not encrypt metadata for unknown metadata encryption schema " +
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

        if (!one.getEncryptionSchema().equals(two.getEncryptionSchema())) {
            throw new RuntimeException("Tried to merge two meta data items of different kind! One: " + one + " Two: "
                    + two);
        }

        if (one.getMetadataId() != two.getMetadataId()) {
            throw new RuntimeException("Tried to merge two meta data items of different ID! One: " + one + " Two: "
                    + two);
        }
        switch (one.getEncryptionSchema()) {
            case LONG:
                return new EncryptedMetadata(one.getPayloadAsLong() + two.getPayloadAsLong(),
                        one.getMetadataId(), one.getEncryptionSchema());
            case LONG_MAC:
                return new EncryptedMetadata(one.getPayloadAsLong() + two.getPayloadAsLong(),
                        one.getMacAsBigInteger().add(two.getMacAsBigInteger()),
                        one.getMetadataId(), one.getEncryptionSchema());
            case BIG_INT_128:
                return new EncryptedMetadata(one.getPayloadAsBigInteger().add(two.getPayloadAsBigInteger()),
                        one.getMetadataId(), one.getEncryptionSchema());
            case BIG_INT_128_MAC:
                return new EncryptedMetadata(one.getPayloadAsBigInteger().add(two.getPayloadAsBigInteger()),
                        one.getMacAsBigInteger().add(two.getMacAsBigInteger()),
                        one.getMetadataId(), one.getEncryptionSchema());
            default:
                throw new RuntimeException("Can not encrypt metadata for unknown metadata encryption schema");
        }

    }


    public static EncryptedMetadata getEncryptedMetadataForValue(StreamMetaData metadata, Collection<DataPoint> value,
                                                                 StreamKeyManager streamKeyManager, long chunkId) {

        switch (metadata.getEncryptionSchema()) {
            case LONG:
                return new EncryptedMetadata(new TimeCryptEncryptionLong(streamKeyManager.getChunkKeyRegression()).
                        encryptMetadata(metadata.calculate(value), chunkId, metadata.getId()), metadata.getId(),
                        metadata.getEncryptionSchema());
            case LONG_MAC:
                TimeCryptEncryptionLongPlus.TCAuthLongCiphertext ciphertext = new TimeCryptEncryptionLongPlus(
                        streamKeyManager.getChunkKeyRegression(), streamKeyManager.getMacKeyAsBigInteger())
                        .encryptMetadata(metadata.calculate(value), chunkId, metadata.getId());
                return new EncryptedMetadata(ciphertext.ciphertext, ciphertext.authCode, metadata.getId(),
                        metadata.getEncryptionSchema());
            case BIG_INT_128:
                return new EncryptedMetadata(new TimeCryptEncryptionBI(streamKeyManager.getChunkKeyRegression(),
                        128).encryptMetadata(BigInteger.valueOf(metadata.calculate(value)), chunkId,
                        metadata.getId()), metadata.getId(), metadata.getEncryptionSchema());
            case BIG_INT_128_MAC:
                TimeCryptEncryptionBIPlus.TCAuthBICiphertext ciphertextBi = new TimeCryptEncryptionBIPlus(
                        streamKeyManager.getChunkKeyRegression(), streamKeyManager.getMacKeyAsBigInteger(), 128).
                        encryptMetadata(BigInteger.valueOf(metadata.calculate(value)), chunkId, metadata.getId());
                return new EncryptedMetadata(ciphertextBi.ciphertext, ciphertextBi.authCode, metadata.getId(),
                        metadata.getEncryptionSchema());
            default:
                throw new RuntimeException("Can not encrypt metadata for unknown metadata encryption schema");
        }
    }
}
