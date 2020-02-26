/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface;

import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Transport representation of a metadata item inside a digest.
 * <p>
 * A metadata item is a value of a certain type of metadata over a certain range of
 * This is used to encapsulate the different ways that TimeCrypt can apply its crypto (representation as the Java
 * native 'long' type with 64 Bits or representation as a bigger binary with the Java BigInteger type).
 * <p>
 * However the metadata will only handle a byte array for the encrypted value and optionally the MAC. It will not take
 * care of encryption.
 */
public class EncryptedMetadata {
    private final int numBits;
    private final byte[] payload;
    private final byte[] mac;
    private final int metadataId;
    private final StreamMetaData.MetadataEncryptionSchema encryptionSchema;
    private final boolean hasMac;

    /**
     * Constructor for deserialization. For usage in other contexts usually the specialized constructors supporting
     * Java long or BigInteger.
     *
     * @param numBits          The number of bits of the payload.
     * @param payload          The binary representation of the metadata items value.
     * @param mac              If the chosen encryption schema contains a Message Authentication Code (MAC) this field
     *                         should contain the binary representation of it.
     * @param metadataId       The ID of this metadata item in list of metadata for its associated stream.
     * @param encryptionSchema The encryption schema used for storing the payload and the MAC.
     * @param hasMac           Indicator if the encryption schema contains a MAC or not.
     */
    @JsonCreator
    public EncryptedMetadata(int numBits, byte[] payload, byte[] mac, int metadataId,
                             StreamMetaData.MetadataEncryptionSchema encryptionSchema, boolean hasMac) {
        this.numBits = numBits;
        this.payload = payload;
        this.mac = mac;
        this.metadataId = metadataId;
        this.encryptionSchema = encryptionSchema;
        this.hasMac = hasMac;
    }

    /**
     * Creator for the LONG encryption schema.
     *
     * @param payload                  The Java long representation of the encrypted metadata items value. This class
     *                                 will serialize this value to its transport representation.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionSchema The encryption schema used for storing the payload and the MAC.
     */
    public EncryptedMetadata(long payload, int metadataId,
                             StreamMetaData.MetadataEncryptionSchema metadataEncryptionSchema) {
        this.encryptionSchema = metadataEncryptionSchema;
        this.metadataId = metadataId;

        this.payload = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(payload).array();
        this.numBits = Long.SIZE;
        this.hasMac = false;
        this.mac = new byte[0];
    }

    /**
     * Creator for the LONG_MAC encryption schema.
     *
     * @param payload                  The Java long representation of the encrypted metadata items value. This class
     *                                 will serialize this value to its transport representation.
     * @param mac                      The Java BigInteger representation of the MAC of the data items value.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionSchema The encryption schema used for storing the payload and the MAC.
     */
    public EncryptedMetadata(long payload, BigInteger mac, int metadataId,
                             StreamMetaData.MetadataEncryptionSchema metadataEncryptionSchema) {
        this.encryptionSchema = metadataEncryptionSchema;
        this.metadataId = metadataId;

        this.payload = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(payload).array();
        this.numBits = Long.SIZE;
        this.hasMac = true;
        this.mac = mac.toByteArray();
    }

    /**
     * Creator for the BIG_INTEGER_* encryption schema.
     *
     * @param payload                  The Java BigInteger representation of the encrypted metadata items value. This
     *                                 class will serialize this value to its transport representation.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionSchema The encryption schema used for storing the payload and the MAC.
     */
    public EncryptedMetadata(BigInteger payload, int metadataId,
                             StreamMetaData.MetadataEncryptionSchema metadataEncryptionSchema) {
        this.encryptionSchema = metadataEncryptionSchema;
        this.metadataId = metadataId;

        this.payload = payload.toByteArray();
        this.numBits = payload.bitCount();
        this.hasMac = false;
        this.mac = new byte[0];
    }

    /**
     * Creator for the BIG_INTEGER_*_MAC encryption schema.
     *
     * @param payload                  The Java BigInteger representation of the encrypted metadata items value. This
     *                                 class will serialize this value to its transport representation.
     * @param mac                      The Java BigInteger representation of the MAC of the data items value.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionSchema The encryption schema used for storing the payload and the MAC.
     */
    public EncryptedMetadata(BigInteger payload, BigInteger mac, int metadataId,
                             StreamMetaData.MetadataEncryptionSchema metadataEncryptionSchema) {
        this.encryptionSchema = metadataEncryptionSchema;
        this.metadataId = metadataId;

        this.payload = payload.toByteArray();
        this.numBits = payload.bitCount();
        this.hasMac = true;
        this.mac = mac.toByteArray();
    }

    /**
     * Returns if the chosen encryption schema consists of a mac (and by this its transport representation has a mac.
     *
     * @return Does the transport representation have a mac?
     */
    public boolean isHasMac() {
        return hasMac;
    }

    /**
     * Gets the number of bits of the payload.
     *
     * @return The number of bits of the payload in its binary transport representation.
     */
    public int getNumBits() {
        return numBits;
    }

    /**
     * Get the payload of the encrypted metadata.
     *
     * @return The binary representation of the metadata items value.
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * If the chosen encryption schema contains a Message Authentication Code (MAC) this will return should the binary
     * representation of it.
     *
     * @return The MAC in its transport representation.
     */
    public byte[] getMac() {
        return mac;
    }

    /**
     * Gets metadata id.
     *
     * @return The ID of this metadata item in list of metadata for its associated stream.
     */
    public int getMetadataId() {
        return metadataId;
    }

    /**
     * Gets the encryption schema.
     *
     * @return The encryption schema used for the values in this encrypted metadata.
     */
    public StreamMetaData.MetadataEncryptionSchema getEncryptionSchema() {
        return encryptionSchema;
    }

    @Override
    public String toString() {
        return "EncryptedMetadata{" +
                "numBits=" + numBits +
                ", hasMac=" + hasMac +
                ", payload=" + Hex.encodeHexString(payload) +
                ", mac=" + Hex.encodeHexString(mac) +
                ", metadataId=" + metadataId +
                ", encryptionSchema=" + encryptionSchema +
                '}';
    }

    /**
     * Return the encrypted data as a Java long object.
     *
     * @return The payload as long
     */
    @JsonIgnore
    public long getPayloadAsLong() {
        ByteBuffer buffer = ByteBuffer.allocate(this.numBits);
        buffer.put(this.payload);
        buffer.flip();
        return buffer.getLong();
    }

    /**
     * Return the encrypted data as a Java BigInteger object.
     *
     * @return The payload as BigInteger
     */
    @JsonIgnore
    public BigInteger getPayloadAsBigInteger() {
        return new BigInteger(this.payload);
    }

    @JsonIgnore
    public BigInteger getMacAsBigInteger() {
        return new BigInteger(this.mac);
    }
}
