/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;
import ch.ethz.dsg.timecrypt.protocol.TimeCryptProtocol;

import java.util.List;

public class CryptoContentFactory {

    public static final byte EC_ELGAMAL_TYPE = 1;
    public static final byte CASTELLUCIA_TYPE = 2;
    public static final byte PALLIER_TYPE = 3;
    public static final byte INTERGER_TYPE = 4;
    public static final byte CASTELLUCIA_MAC_TYPE = 5;
    public static final byte NODE_CONTENT_LONG_BIGINT_TYPE = 6;

    public static NodeContent decodeNodeContent(byte[] data) {
        if (data.length < 1)
            throw new RuntimeException("Decode NodeContentFailed");
        switch (data[0]) {
            case CASTELLUCIA_TYPE:
                return BigintNodeContent.decode(data);
            case EC_ELGAMAL_TYPE:
                //return ECElGamalNodeContent.decode(data);
                throw new RuntimeException("Not available in this codebase");
            case INTERGER_TYPE:
                return IntegerContent.decode(data);
            case PALLIER_TYPE:
                throw new RuntimeException("Not available in this codebase");
            case CASTELLUCIA_MAC_TYPE:
                return TwoBigintMACNodeContent.decode(data);
            case NODE_CONTENT_LONG_BIGINT_TYPE:
                return LongBigintContent.decode(data);

        }
        throw new RuntimeException("Decode NodeContent Failed");
    }

    public static NodeContent[] createNodeContentsForRequest(List<TimeCryptProtocol.Metadata> metadatas) {
        NodeContent[] result = new NodeContent[metadatas.size()];
        int count = 0;
        for (TimeCryptProtocol.Metadata metadata : metadatas) {
            result[count] = decodeNodeContent(metadata.getData().toByteArray());
            count++;
        }
        return result;
    }
}
