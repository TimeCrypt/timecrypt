/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.crypto;

//import ch.ethz.dsg.ecelgamal.ECElGamal;
//import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

import ch.ethz.dsg.timecrypt.index.blockindex.node.NodeContent;

public class ECElGamalNodeContent implements NodeContent {
    @Override
    public NodeContent copy() {
        return null;
    }

    @Override
    public void mergeOther(NodeContent otherContent) {

    }

    @Override
    public NodeContent mergeOtherCopy(NodeContent otherContent) {
        return null;
    }

    @Override
    public byte[] encode() {
        return new byte[0];
    }

    @Override
    public String getStringRepresentation() {
        return null;
    }

    /*ECElGamal.ECElGamalCiphertext ciphertext;

    public ECElGamalNodeContent(ECElGamal.ECElGamalCiphertext ciphertext) {
        this.ciphertext = ciphertext;
    }

    public static ECElGamalNodeContent decode(byte[] data) {
        byte[] tmp = new byte[data.length - 1];
        System.arraycopy(data, 1, tmp, 0, tmp.length);
        return new ECElGamalNodeContent(ECElGamal.ECElGamalCiphertext.decode(tmp));
    }

    public ECElGamal.ECElGamalCiphertext getCiphertext() {
        return ciphertext;
    }

    public NodeContent copy() {
        return new ECElGamalNodeContent(this.ciphertext.copy());
    }

    public void mergeOther(NodeContent otherContent) {
        if (!(otherContent instanceof ECElGamalNodeContent))
            return;
        this.ciphertext = ECElGamal.add(this.ciphertext, ((ECElGamalNodeContent) otherContent).ciphertext);
    }

    public NodeContent mergeOtherCopy(NodeContent otherContent) {
        return new ECElGamalNodeContent(ECElGamal.add(this.ciphertext, ((ECElGamalNodeContent) otherContent).ciphertext));
    }

    public byte[] encode() {
        byte[] tmp = ciphertext.encode();
        byte[] res = new byte[tmp.length + 1];
        res[0] = CryptoContentFactory.EC_ELGAMAL_TYPE;
        System.arraycopy(tmp, 0, res, 1, tmp.length);
        return res;
    }

    public String getStringRepresentation() {
        return ciphertext.toString();
    }*/
}
