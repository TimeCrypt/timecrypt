/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex.node;

import ch.ethz.dsg.timecrypt.index.blockindex.encoding.BlockNodeEncoding.BinBlockNodeContent;
import ch.ethz.dsg.timecrypt.index.blockindex.encoding.BlockNodeEncoding.BinMetaNode;
import ch.ethz.dsg.timecrypt.crypto.CryptoContentFactory;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

public class BlockNode {

    public int from;
    public int to;
    public MetaInnerNode[] metaInnerNode;
    public int metaInterval;
    private int version;

    public BlockNode(int version, int from, int to, MetaInnerNode[] metaInnerNode) {
        this.from = from;
        this.to = to;
        this.metaInnerNode = metaInnerNode;
        this.metaInterval = (to - from) / metaInnerNode.length;
        this.version = version;
    }

    public BlockNode(int version, int from, int to, int k) {
        this.from = from;
        this.to = to;
        this.metaInterval = to - from;
        this.metaInnerNode = new MetaInnerNode[k];
        this.metaInterval = (to - from) / k;
        this.version = version;
    }

    private static long computeID(int from, int to) {
        return BlockIdUtil.getID(from, to);
    }

    public static MetaInnerNode[] decodeNodeContent(byte[] data) throws InvalidProtocolBufferException {
        BinBlockNodeContent content = BinBlockNodeContent.parseFrom(data);
        int numMeta = content.getContentsCount();
        MetaInnerNode[] res = new MetaInnerNode[numMeta];
        for (int iter = 0; iter < res.length; iter++) {
            BinMetaNode node = content.getContents(iter);
            if (!node.getIsNull()) {
                NodeContent[] nodeContents = new NodeContent[node.getContentCount()];
                for (int i = 0; i < nodeContents.length; i++) {
                    nodeContents[i] = CryptoContentFactory.decodeNodeContent(node.getContent(i).toByteArray());
                }
                res[iter] = new MetaInnerNode(nodeContents);
            }
        }
        return res;
    }

    public int getVersion() {
        return version;
    }

    public int getNextEmpty() {
        int count = 0;
        for (MetaInnerNode meta : metaInnerNode) {
            if (meta == null)
                break;
            count++;
        }
        return count;
    }

    public void setNewVersion(int version) {
        this.version = version;
    }

    public int getNumberOfChildren() {
        return getNextEmpty();
    }

    public boolean hasNoChilds() {
        return getNextEmpty() == 0;
    }

    public MetaInnerNode getMetaAtIndex(int i) {
        return metaInnerNode[i];
    }

    public void insert(MetaInnerNode newMeta, int from, int to) {
        assert (to - from == metaInterval);
        int next = ((from - this.from) / metaInterval);
        if (next >= metaInnerNode.length)
            throw new RuntimeException("Already Full");
        metaInnerNode[next] = newMeta;
    }

    public BlockNode createCopy() {
        MetaInnerNode[] tmp = new MetaInnerNode[metaInnerNode.length];
        for (int iter = 0; iter < tmp.length; iter++)
            tmp[iter] = metaInnerNode[iter].createCopy();
        return new BlockNode(this.version, this.from, this.to, tmp);
    }

    public boolean hasSpace() {
        return getNextEmpty() < metaInnerNode.length;
    }

    public NodeContent[] getAggregatedNodeContent() {
        int nextEmpty = getNextEmpty();
        NodeContent[] res = null;
        if (nextEmpty == 0) {
            return null;
        }

        boolean isFirst = true;
        for (MetaInnerNode meta : metaInnerNode) {
            if (meta == null)
                continue;
            if (isFirst) {
                res = new NodeContent[meta.metaInformation.length];
            }
            for (int i = 0; i < res.length; i++) {
                if (isFirst) {
                    res[i] = meta.metaInformation[i].copy();
                } else {
                    res[i].mergeOther(meta.metaInformation[i]);
                }
            }
            isFirst = false;
        }
        return res;
    }

    public boolean hasMetaForInterval(int from, int to) {
        assert (containsInterval(from, to));
        int id = (from - this.from) / metaInterval;
        return metaInnerNode[id] != null;
    }

    public BlockNode createChildNodeForInterval(int from, int k) {
        int id = (from - this.from) / metaInterval;
        return new BlockNode(this.version, this.from + id * metaInterval, this.from + (id + 1) * metaInterval, k);
    }

    public long getId() {
        return computeID(this.from, this.to);
    }

    public boolean containsInterval(int from, int to) {
        return this.from <= from && this.to >= to;
    }

    public long getPointerMetaInfoIndexForLeaf(int from, int to) {
        assert (containsInterval(from, to));
        int id = ((from - this.from) / metaInterval);
        return computeID(this.from + metaInterval * id, this.from + metaInterval * (id + 1));
    }

    public long getPointerMetaInfoIndex(int index) {
        return computeID(this.from + metaInterval * index, this.from + metaInterval * (index + 1));
    }

    public boolean isLeaf(int interval) {
        return metaInterval == interval;
    }

    public List<MetaInnerNode> getMetaFullyContained(int from, int to) {
        ArrayList<MetaInnerNode> results = new ArrayList<>(metaInnerNode.length);
        if (from >= this.to)
            return results;
        int fromInd, toInd;
        if (from <= this.from) {
            fromInd = 0;
        } else {
            fromInd = (from - this.from) / metaInterval;
            fromInd = ((from - this.from) % metaInterval == 0) ? fromInd : ++fromInd;
        }

        if (to >= this.to) {
            toInd = metaInnerNode.length;
        } else {
            toInd = (to - this.from) / metaInterval;
        }

        for (int i = fromInd; i < toInd; i++) {
            if (this.metaInnerNode[i] != null) {
                results.add(metaInnerNode[i]);
            }
        }
        return results;
    }

    public int getLeftNodeDepper(int from) {
        if (from >= this.to)
            return -1;
        int leftIndex = -1;
        if (!(from <= this.from)) {
            leftIndex = (from - this.from) / metaInterval;
            leftIndex = ((from - this.from) % metaInterval == 0) ? -1 : leftIndex;
        }

        return leftIndex;
    }

    public int getRigthNodeDepper(int to) {
        if (from >= this.to)
            return -1;
        int rightIndex = -1;
        if (!(to >= this.to)) {
            rightIndex = (to - this.from) / metaInterval;
            rightIndex = ((to - this.from) % metaInterval == 0) ? -1 : rightIndex;
        }
        return rightIndex;
    }

    public boolean metaAtIndexContains(int index, int val) {
        int fromLocal = this.from + metaInterval * index, toLocal = this.from + metaInterval * (index + 1);
        return val <= toLocal && fromLocal <= val;
    }

    public String toString() {
        StringBuilder buff = new StringBuilder(this.metaInnerNode.length * 2);
        buff.append("|").append(from).append("-").append(to).append("|");
        for (MetaInnerNode child : this.metaInnerNode) {
            if (child == null)
                buff.append("O|");
            else
                buff.append("X|");
        }
        buff.append("V:")
                .append(this.version)
                .append("|");
        return buff.toString();
    }

    public byte[] encodeContent() {
        BinBlockNodeContent.Builder binBuild = BinBlockNodeContent.newBuilder();
        for (MetaInnerNode meta : metaInnerNode) {
            if (meta == null) {
                binBuild.addContents(BinMetaNode.newBuilder()
                        .setIsNull(true));
            } else {
                BinMetaNode.Builder binMeta = BinMetaNode.newBuilder();
                binMeta.setIsNull(false);
                for (NodeContent content : meta.metaInformation) {
                    binMeta.addContent(ByteString.copyFrom(content.encode()));
                }
                binBuild.addContents(binMeta);
            }
        }
        return binBuild.build().toByteArray();
    }
}
