/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptNettyProtocol;

import java.util.List;

public class CryptoContentFactory {

    public static final byte EC_ELGAMAL_TYPE = 1;
    public static final byte CASTELLUCIA_TYPE = 2;
    public static final byte PALLIER_TYPE = 3;
    public static final byte LONG_TYPE = 4;
    public static final byte CASTELLUCIA_MAC_TYPE = 5;
    public static final byte LONG_MAC_TYPE = 6;

    public static NodeContent decodeNodeContent(byte[] data) {
        if (data.length < 1)
            throw new RuntimeException("Decode NodeContentFailed");
        switch (data[0]) {
            case EC_ELGAMAL_TYPE:
                //return ECElGamalNodeContent.decode(data);
                throw new RuntimeException("Not available in this codebase");
            case CASTELLUCIA_TYPE:
                return BigintNodeContent.decode(data);
            case PALLIER_TYPE:
                throw new RuntimeException("Not available in this codebase");
            case LONG_TYPE:
                return LongNodeContent.decode(data);
            case CASTELLUCIA_MAC_TYPE:
                return BigintMacNodeContent.decode(data);
            case LONG_MAC_TYPE:
                return LongMacNodeNodeContent.decode(data);
        }
        throw new RuntimeException("Decode NodeContent Failed");
    }

    public static NodeContent[] createNodeContentsForRequest(List<TimeCryptNettyProtocol.Metadata> metadatas) {
        NodeContent[] result = new NodeContent[metadatas.size()];
        int count = 0;
        for (TimeCryptNettyProtocol.Metadata metadata : metadatas) {
            result[count] = decodeNodeContent(metadata.getData().toByteArray());
            count++;
        }
        return result;
    }
}
