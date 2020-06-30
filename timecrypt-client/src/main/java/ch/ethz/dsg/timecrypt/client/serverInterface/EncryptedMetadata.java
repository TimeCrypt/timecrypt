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
import java.util.Arrays;
import java.util.Objects;

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
    private final int numPayloadBits;
    private final byte[] payload;
    private final int numMacBits;
    private final byte[] mac;
    private final int metadataId;
    private final StreamMetaData.MetadataEncryptionScheme encryptionScheme;
    private final boolean hasMac;

    /**
     * Constructor for deserialization. For usage in other contexts usually the specialized constructors supporting
     * Java long or BigInteger.
     *
     * @param numPayloadBits   The number of bits of the payload.
     * @param payload          The binary representation of the metadata items value.
     * @param numMacBits       The number of bits of the MAC.
     * @param mac              If the chosen encryption scheme contains a Message Authentication Code (MAC) this field
     *                         should contain the binary representation of it.
     * @param metadataId       The ID of this metadata item in list of metadata for its associated stream.
     * @param encryptionScheme The encryption scheme used for storing the payload and the MAC.
     * @param hasMac           Indicator if the encryption scheme contains a MAC or not.
     */
    @JsonCreator
    public EncryptedMetadata(int numPayloadBits, byte[] payload, int numMacBits, byte[] mac, int metadataId,
                             StreamMetaData.MetadataEncryptionScheme encryptionScheme, boolean hasMac) {
        this.numPayloadBits = numPayloadBits;
        this.payload = payload;
        this.numMacBits = numMacBits;
        this.mac = mac;
        this.metadataId = metadataId;
        this.encryptionScheme = encryptionScheme;
        this.hasMac = hasMac;
    }

    /**
     * Creator for the LONG encryption scheme.
     *
     * @param payload                  The Java long representation of the encrypted metadata items value. This class
     *                                 will serialize this value to its transport representation.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionScheme The encryption scheme used for storing the payload and the MAC.
     */
    public EncryptedMetadata(long payload, int metadataId,
                             StreamMetaData.MetadataEncryptionScheme metadataEncryptionScheme) {
        this.encryptionScheme = metadataEncryptionScheme;
        this.metadataId = metadataId;

        this.payload = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(payload).array();
        this.numPayloadBits = Long.SIZE;
        this.hasMac = false;
        this.mac = new byte[0];
        this.numMacBits = 0;
    }

    /**
     * Creator for the LONG_MAC encryption scheme.
     *
     * @param payload                  The Java long representation of the encrypted metadata items value. This class
     *                                 will serialize this value to its transport representation.
     * @param mac                      The Java BigInteger representation of the MAC of the data items value.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionScheme The encryption scheme used for storing the payload and the MAC.
     */
    public EncryptedMetadata(long payload, BigInteger mac, int metadataId,
                             StreamMetaData.MetadataEncryptionScheme metadataEncryptionScheme) {
        this.encryptionScheme = metadataEncryptionScheme;
        this.metadataId = metadataId;

        this.payload = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(payload).array();
        this.numPayloadBits = Long.SIZE;
        this.hasMac = true;
        this.mac = mac.toByteArray();
        this.numMacBits = mac.bitCount();
    }

    /**
     * Creator for the BIG_INTEGER_* encryption scheme.
     *
     * @param payload                  The Java BigInteger representation of the encrypted metadata items value. This
     *                                 class will serialize this value to its transport representation.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionScheme The encryption scheme used for storing the payload and the MAC.
     */
    public EncryptedMetadata(BigInteger payload, int metadataId,
                             StreamMetaData.MetadataEncryptionScheme metadataEncryptionScheme) {
        this.encryptionScheme = metadataEncryptionScheme;
        this.metadataId = metadataId;

        this.payload = payload.toByteArray();
        this.numPayloadBits = payload.bitCount();
        this.hasMac = false;
        this.mac = new byte[0];
        this.numMacBits = 0;
    }

    /**
     * Creator for the BIG_INTEGER_*_MAC encryption scheme.
     *
     * @param payload                  The Java BigInteger representation of the encrypted metadata items value. This
     *                                 class will serialize this value to its transport representation.
     * @param mac                      The Java BigInteger representation of the MAC of the data items value.
     * @param metadataId               The ID of this metadata item in list of metadata for its associated stream.
     * @param metadataEncryptionScheme The encryption scheme used for storing the payload and the MAC.
     */
    public EncryptedMetadata(BigInteger payload, BigInteger mac, int metadataId,
                             StreamMetaData.MetadataEncryptionScheme metadataEncryptionScheme) {
        this.encryptionScheme = metadataEncryptionScheme;
        this.metadataId = metadataId;

        this.payload = payload.toByteArray();
        this.numPayloadBits = payload.bitCount();
        this.hasMac = true;
        this.mac = mac.toByteArray();
        this.numMacBits = mac.bitCount();
    }

    /**
     * Returns the number of bits in this objects MAC.
     *
     * @return The number of bits in the MAC.
     */
    public int getNumMacBits() {
        return numMacBits;
    }

    /**
     * Returns if the chosen encryption scheme consists of a mac (and by this its transport representation has a mac.
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
    public int getNumPayloadBits() {
        return numPayloadBits;
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
     * If the chosen encryption scheme contains a Message Authentication Code (MAC) this will return should the binary
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
     * Gets the encryption scheme.
     *
     * @return The encryption scheme used for the values in this encrypted metadata.
     */
    public StreamMetaData.MetadataEncryptionScheme getEncryptionScheme() {
        return encryptionScheme;
    }

    @Override
    public String toString() {
        return "EncryptedMetadata{" +
                "numBits=" + numPayloadBits +
                ", hasMac=" + hasMac +
                ", payload=" + Hex.encodeHexString(payload) +
                ", mac=" + Hex.encodeHexString(mac) +
                ", metadataId=" + metadataId +
                ", encryptionScheme=" + encryptionScheme +
                '}';
    }

    /**
     * Return the encrypted data as a Java long object.
     *
     * @return The payload as long
     */
    @JsonIgnore
    public long getPayloadAsLong() {
        ByteBuffer buffer = ByteBuffer.allocate(this.numPayloadBits);
        buffer.put(this.payload);
        buffer.flip();
        return buffer.getLong();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncryptedMetadata that = (EncryptedMetadata) o;
        return numPayloadBits == that.numPayloadBits &&
                numMacBits == that.numMacBits &&
                metadataId == that.metadataId &&
                hasMac == that.hasMac &&
                Arrays.equals(payload, that.payload) &&
                Arrays.equals(mac, that.mac) &&
                encryptionScheme == that.encryptionScheme;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(numPayloadBits, numMacBits, metadataId, encryptionScheme, hasMac);
        result = 31 * result + Arrays.hashCode(payload);
        result = 31 * result + Arrays.hashCode(mac);
        return result;
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
