/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import java.nio.ByteBuffer;

public class LongNodeContent implements NodeContent {
    public long i;

    public LongNodeContent(long i) {
        this.i = i;
    }

    public static LongNodeContent decode(byte[] data) {
        byte[] tmp = new byte[data.length - 1];
        System.arraycopy(data, 1, tmp, 0, tmp.length);
        ByteBuffer rsult = ByteBuffer.wrap(tmp);
        return new LongNodeContent(rsult.getLong());
    }

    public long getLong() {
        return i;
    }

    public NodeContent copy() {
        return new LongNodeContent(i);
    }

    public void mergeOther(NodeContent otherContent) {
        if (!(otherContent instanceof LongNodeContent))
            return;
        this.i += ((LongNodeContent) otherContent).i;
    }

    public NodeContent mergeOtherCopy(NodeContent otherContent) {
        NodeContent res = this.copy();
        res.mergeOther(otherContent);
        return res;
    }

    public byte[] encode() {
        ByteBuffer buff = ByteBuffer.allocate(1 + 8);
        buff.put(CryptoContentFactory.LONG_TYPE);
        buff.putLong(this.i);
        return buff.array();
    }

    public NodeContent createEmpty() {
        return new LongNodeContent(0);
    }

    public String getStringRepresentation() {
        return String.valueOf(i);
    }
}
