/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.serverInterface.nettyserver;

import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedMetadata;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData.MetadataEncryptionSchema.*;

public class NodeContentSerialization {

    public static final byte NODE_CONTENT_EC_ELGAMAL_TYPE = 1;
    public static final byte NODE_CONTENT_BIGINT_TYPE = 2;
    public static final byte NODE_CONTENT_LONG_TYPE = 4;
    public static final byte NODE_CONTENT_TWO_BIGINT_TYPE = 5;
    public static final byte NODE_CONTENT_LONG_BIGINT_TYPE = 6;

    public static final HashMap<StreamMetaData.MetadataEncryptionSchema, Byte> schemeToNodeContent = new HashMap<>();

    static {
        schemeToNodeContent.put(LONG, NODE_CONTENT_LONG_TYPE);
        schemeToNodeContent.put(LONG_MAC, NODE_CONTENT_LONG_BIGINT_TYPE);
        schemeToNodeContent.put(BIG_INT_128, NODE_CONTENT_BIGINT_TYPE);
        schemeToNodeContent.put(BIG_INT_128_MAC, NODE_CONTENT_TWO_BIGINT_TYPE);
    }

    public static EncryptedMetadata decodeNodeContent(byte[] encodedContent, int id, StreamMetaData.MetadataEncryptionSchema expectedScheme) {
        if (encodedContent.length < 1)
            throw new RuntimeException("Decode NodeContentFailed");
        switch (expectedScheme) {
            case LONG:
                if ((encodedContent[0] != schemeToNodeContent.get(LONG)))
                    throw new RuntimeException("Encryption scheme does not match the node content");
                byte[] tmp = new byte[encodedContent.length - 1];
                System.arraycopy(encodedContent, 1, tmp, 0, tmp.length);
                ByteBuffer result = ByteBuffer.wrap(tmp);
                return new EncryptedMetadata(result.getLong(), id, expectedScheme);
            case LONG_MAC:
                byte[] macBytes = new byte[encodedContent.length - Long.BYTES - 2];
                ByteBuffer buff = ByteBuffer.wrap(encodedContent, 1, Long.BYTES);
                System.arraycopy(encodedContent, 1 + Long.BYTES, macBytes, 0, macBytes.length);
                return new EncryptedMetadata(buff.getLong(), new BigInteger(1, macBytes), id, expectedScheme);
            case BIG_INT_128:
                if ((encodedContent[0] != schemeToNodeContent.get(BIG_INT_128)))
                    throw new RuntimeException("Encryption scheme does not match the node content");
                tmp = new byte[encodedContent.length - 1];
                System.arraycopy(encodedContent, 1, tmp, 0, tmp.length);
                return new EncryptedMetadata(new BigInteger(1, tmp), id, expectedScheme);
            case BIG_INT_128_MAC:
                if ((encodedContent[0] != schemeToNodeContent.get(BIG_INT_128_MAC)))
                    throw new RuntimeException("Encryption scheme does not match the node content");
                byte[] contentBytes = new byte[encodedContent[1]];
                macBytes = new byte[encodedContent.length - contentBytes.length - 2];
                System.arraycopy(encodedContent, 2, contentBytes, 0, contentBytes.length);
                System.arraycopy(encodedContent, 2 + contentBytes.length, macBytes, 0, macBytes.length);
                return new EncryptedMetadata(new BigInteger(1, contentBytes), new BigInteger(1, macBytes), id, expectedScheme);
        }
        throw new RuntimeException("Decode Encrypted Metadata Failed");
    }

    public static byte[] encodeToNodeContent(EncryptedMetadata metadata) {
        switch (metadata.getEncryptionSchema()) {
            case LONG:
                byte[] temp = new byte[metadata.getPayload().length + 1];
                temp[0] = schemeToNodeContent.get(LONG);
                System.arraycopy(metadata.getPayload(), 0, temp, 1, metadata.getPayload().length);
                return temp;
            case LONG_MAC:
                byte[] contentBytes = metadata.getPayload();
                byte[] macBytes = metadata.getMac();
                byte[] res = new byte[contentBytes.length + macBytes.length + 1];
                res[0] = schemeToNodeContent.get(LONG_MAC);
                System.arraycopy(contentBytes, 0, res, 1, contentBytes.length);
                System.arraycopy(macBytes, 0, res, 1 + contentBytes.length, macBytes.length);
                return res;
            case BIG_INT_128:
                temp = new byte[metadata.getPayload().length + 1];
                temp[0] = schemeToNodeContent.get(BIG_INT_128);
                System.arraycopy(metadata.getPayload(), 0, temp, 1, metadata.getPayload().length);
                return temp;
            case BIG_INT_128_MAC:
                contentBytes = metadata.getPayload();
                macBytes = metadata.getMac();
                res = new byte[contentBytes.length + macBytes.length + 2];
                res[0] = schemeToNodeContent.get(BIG_INT_128_MAC);
                res[1] = (byte) contentBytes.length;
                System.arraycopy(contentBytes, 0, res, 2, contentBytes.length);
                System.arraycopy(macBytes, 0, res, 2 + contentBytes.length, macBytes.length);
                return res;
        }
        throw new RuntimeException("Encode Encrypted Metadata Failed");
    }
}
