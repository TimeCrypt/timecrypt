/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import java.math.BigInteger;

public class BigintNodeContent implements NodeContent {

    protected BigInteger content;

    public BigintNodeContent(BigInteger content) {
        this.content = content;
    }

    public static BigintNodeContent decode(byte[] data) {
        byte[] tmp = new byte[data.length - 1];
        System.arraycopy(data, 1, tmp, 0, tmp.length);
        return new BigintNodeContent(new BigInteger(1, tmp));
    }

    public BigInteger getContent() {
        return content;
    }

    public NodeContent copy() {
        return new BigintNodeContent(this.content);
    }

    public void mergeOther(NodeContent otherContent) {
        if (!(otherContent instanceof BigintNodeContent))
            return;
        this.content = content.add(((BigintNodeContent) otherContent).content);
    }

    public NodeContent mergeOtherCopy(NodeContent otherContent) {
        NodeContent res = this.copy();
        res.mergeOther(otherContent);
        return res;
    }

    public byte[] encode() {
        byte[] tmp = content.toByteArray();
        byte[] res = new byte[tmp.length + 1];
        res[0] = CryptoContentFactory.CASTELLUCIA_TYPE;
        System.arraycopy(tmp, 0, res, 1, tmp.length);
        return res;
    }

    public NodeContent createEmpty() {
        return new BigintNodeContent(BigInteger.ZERO);
    }

    public String getStringRepresentation() {
        return content.toString();
    }
}
