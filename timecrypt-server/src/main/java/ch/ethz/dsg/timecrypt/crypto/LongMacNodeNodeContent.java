/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class LongMacNodeNodeContent extends LongNodeContent {
    public static final BigInteger PRIME = new BigInteger("340282366920938463463374607431768211297");

    private BigInteger mac;

    public LongMacNodeNodeContent(long content, BigInteger mac) {
        super(content);
        this.mac = mac;
    }

    public static LongMacNodeNodeContent decode(byte[] data) {
        byte[] macBytes = new byte[data.length - Long.BYTES - 2];
        ByteBuffer buff = ByteBuffer.wrap(data, 1, Long.BYTES);
        System.arraycopy(data, 1 + Long.BYTES, macBytes, 0, macBytes.length);
        return new LongMacNodeNodeContent(buff.getLong(), new BigInteger(1, macBytes));
    }

    public BigInteger getMac() {
        return mac;
    }

    @Override
    public NodeContent copy() {
        return new LongMacNodeNodeContent(this.i, this.mac);
    }

    @Override
    public void mergeOther(NodeContent otherContent) {
        super.mergeOther(otherContent);
        if (!(otherContent instanceof LongMacNodeNodeContent))
            throw new RuntimeException("Merge Failed, inconsistent Node Contents");
        //this.mac = this.mac.add(((LongBigintContent) otherContent).mac);
        this.mac = this.mac.add(((LongMacNodeNodeContent) otherContent).mac);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buff = ByteBuffer.allocate(Long.BYTES);
        byte[] contentBytes = buff.putLong(this.i).array();
        byte[] macBytes = mac.toByteArray();
        byte[] res = new byte[contentBytes.length + macBytes.length + 1];
        res[0] = CryptoContentFactory.CASTELLUCIA_MAC_TYPE;
        System.arraycopy(contentBytes, 0, res, 1, contentBytes.length);
        System.arraycopy(macBytes, 0, res, 1 + contentBytes.length, macBytes.length);
        return res;
    }

    @Override
    public NodeContent createEmpty() {
        return new LongMacNodeNodeContent(0, BigInteger.ZERO);
    }

    @Override
    public String getStringRepresentation() {
        return super.getStringRepresentation() + "|" + this.mac.toString();
    }
}
