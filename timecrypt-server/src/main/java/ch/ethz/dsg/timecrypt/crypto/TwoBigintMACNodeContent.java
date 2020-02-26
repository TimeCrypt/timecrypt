/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import java.math.BigInteger;

public class TwoBigintMACNodeContent extends BigintNodeContent {

    public static final BigInteger PRIME = new BigInteger("340282366920938463463374607431768211297");

    private BigInteger mac;


    public TwoBigintMACNodeContent(BigInteger content, BigInteger mac) {
        super(content);
        this.mac = mac;
    }

    public static BigintNodeContent decode(byte[] data) {
        byte[] contentBytes = new byte[data[1]];
        byte[] macBytes = new byte[data.length - contentBytes.length - 2];
        System.arraycopy(data, 2, contentBytes, 0, contentBytes.length);
        System.arraycopy(data, 2 + contentBytes.length, macBytes, 0, macBytes.length);
        return new TwoBigintMACNodeContent(new BigInteger(1, contentBytes), new BigInteger(1, macBytes));
    }

    public BigInteger getMac() {
        return mac;
    }

    @Override
    public NodeContent copy() {
        return new TwoBigintMACNodeContent(this.content, this.mac);
    }

    @Override
    public void mergeOther(NodeContent otherContent) {
        super.mergeOther(otherContent);
        if (!(otherContent instanceof TwoBigintMACNodeContent))
            throw new RuntimeException("Merge Failed, inconsistent Node Contents");
        //this.mac = this.mac.add(((CastellucciaMACNodeContent) otherContent).mac).mod(PRIME);
        this.mac = this.mac.add(((TwoBigintMACNodeContent) otherContent).mac);
    }

    @Override
    public byte[] encode() {
        byte[] contentBytes = content.toByteArray();
        byte[] macBytes = mac.toByteArray();
        byte[] res = new byte[contentBytes.length + macBytes.length + 2];
        res[0] = CryptoContentFactory.CASTELLUCIA_MAC_TYPE;
        res[1] = (byte) contentBytes.length;
        System.arraycopy(contentBytes, 0, res, 2, contentBytes.length);
        System.arraycopy(macBytes, 0, res, 2 + contentBytes.length, macBytes.length);
        return res;
    }

    @Override
    public NodeContent createEmpty() {
        return new TwoBigintMACNodeContent(BigInteger.ZERO, BigInteger.ZERO);
    }

    @Override
    public String getStringRepresentation() {
        return super.getStringRepresentation() + "|" + this.mac.toString();
    }
}
