/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */


package ch.ethz.dsg.timecrypt.crypto.keyRegression;

import ch.ethz.dsg.timecrypt.crypto.keymanagement.KeyUtil;
import ch.ethz.dsg.timecrypt.crypto.prf.IPRF;

import java.math.BigInteger;
import java.util.*;

public class LabelTreeKeyRegression implements IKeyRegression {

    private final IPRF prf;
    private final byte labelBits;
    private ArrayList<TreeNode> nodes = new ArrayList<>();

    public LabelTreeKeyRegression(IPRF prf, int labelBits, byte[] masterKey) {
        this.prf = prf;
        this.labelBits = (byte) labelBits;
        nodes.add(new TreeNode(Label.empty(), masterKey));
    }

    public LabelTreeKeyRegression(IPRF prf, int labelBits, List<SeedNode> nodes) {
        this.prf = prf;
        this.labelBits = (byte) labelBits;
        for (SeedNode sn : nodes) {
            this.nodes.add(new TreeNode(Label.newLabel((byte) sn.getDepth(), sn.getNodeNr()), sn.getSeed()));
        }
    }

    private TreeNode findNode(Label label) {
        for (TreeNode n : this.nodes) {
            if (n.label.isPrefix(label)) {
                return n;
            }
        }
        return null;
    }

    private TreeNode deriveNode(Label label) {
        TreeNode node = this.findNode(label);
        if (node != null) {
            return node.computeNode(label);
        }
        return null;
    }


    @Override
    public BigInteger getKey(long id, int keyBits) throws InvalidKeyDerivation {
        return KeyUtil.deriveKey(prf, this.getSeed(id), keyBits);
    }

    @Override
    public byte[] getSeed(long id) throws InvalidKeyDerivation {
        Label label = Label.newLabel(this.labelBits, id);
        TreeNode node = this.findNode(label);
        if (node == null) {
            throw new InvalidKeyDerivation("The key with label " + id + " cannot be computed");
        } else {
            return node.computeKey(label);
        }
    }

    @Override
    public byte[][] getSeeds(long from, long to) throws InvalidKeyDerivation {
        byte[][] result = new byte[(int) (to - from + 1)][];
        for (int i = (int) from; i <= to; i++) {
            result[i] = this.getSeed(i);
        }
        return result;
    }

    @Override
    public BigInteger[] getKeys(long from, long to, int keyBits) throws InvalidKeyDerivation {
        BigInteger[] result = new BigInteger[(int) (to - from + 1)];
        for (int i = (int) from; i <= to; i++) {
            result[i] = this.getKey(i, keyBits);
        }
        return result;
    }

    @Override
    public BigInteger getKeySum(long from, long to, int keyBits) throws InvalidKeyDerivation {
        BigInteger sum = BigInteger.ZERO;
        for (int i = (int) from; i <= to; i++) {
            sum = sum.add(this.getKey(i, keyBits));
        }
        return sum;
    }

    @Override
    public IPRF getPRF() {
        return this.prf;
    }

    public List<SeedNode> constrainNodes(long from, long to) {
        ArrayList<SeedNode> results = new ArrayList<SeedNode>();
        Label labelFrom = Label.newLabel(this.labelBits, from);
        Label labelTo = Label.newLabel(this.labelBits, to - 1);
        long pow = 0;
        while (labelFrom.compareTo(labelTo) < 0) {
            // FROM
            int bit = labelFrom.getBit((int) (this.labelBits - pow - 1));
            if (bit == 1) {
                TreeNode n = this.deriveNode(labelFrom);
                if (n != null) {
                    results.add(n);
                } else {
                    throw new RuntimeException("Constrain failed");
                }
                labelFrom.reduceAdd();
            } else {
                labelFrom.reduceLen((byte) 1);
            }
            // TO
            //println!("BITS {}", self.label_bits - pow - 1);
            bit = labelTo.getBit((int) (this.labelBits - pow - 1));
            if (bit == 1) {
                labelTo.reduceLen((byte) 1);
            } else {
                TreeNode n = this.deriveNode(labelTo);
                if (n != null) {
                    results.add(n);
                } else {
                    throw new RuntimeException("Constrain failed");
                }
                labelTo.reduceSub();
            }
            pow += 1;
        }
        if (labelFrom.equals(labelTo)) {
            TreeNode n = this.deriveNode(labelTo);
            if (n != null) {
                results.add(n);
            } else {
                throw new RuntimeException("Constrain failed");
            }
        }
        results.sort(new Comparator<SeedNode>() {
            @Override
            public int compare(SeedNode o1, SeedNode o2) {
                if (o1 instanceof TreeNode && o2 instanceof TreeNode) {
                    TreeNode n1 = (TreeNode) o1;
                    TreeNode n2 = (TreeNode) o2;
                    return n1.label.compareTo(n2.label);
                }
                return -1;
            }
        });
        return results;
    }

    public static class Label implements Comparable<Label> {
        public byte len;
        public long label;

        Label(byte len, long label) {
            this.len = len;
            this.label = label;
        }

        public static Label from(long label) {
            return new Label((byte) 64, label);
        }

        public static Label empty() {
            return new Label((byte) 0, 0);
        }

        public static Label newLabel(byte len, long label) {
            long shifted = label << (64 - len);
            return new Label(len, shifted);
        }

        public long toLong() {
            return this.label >> (64 - this.len);
        }

        public int getBit(int index) {
            return (int) ((this.label >> (63 - index)) & 1);
        }

        public void setBit(int index) {
            this.label = this.label | (1L << (63 - index));
        }

        public boolean isPrefix(Label other) {
            if (this.len > other.len) {
                return false;
            } else {
                return this.len == 0 || (this.label ^ (other.label)) >> (64 - this.len) == 0;
            }
        }

        public void reduceLen(byte len) {
            if (this.len - len <= 0) {
                this.len = 0;
                this.label = 0;
            } else {
                this.len -= len;
                this.label = this.toLong() << (64 - this.len);
            }
        }

        public void reduceSub() {
            this.reduceLen((byte) 1);
            if (this.len > 0) {
                this.label -= 1L << (64 - this.len);
            }
        }

        public void reduceAdd() {
            this.reduceLen((byte) 1);
            if (this.len > 0) {
                this.label += 1L << (64 - this.len);
            }
        }

        public int[] getPathVector(int from) {
            int[] out = new int[this.len - from];
            for (int i = from; i < this.len; i++)
                out[i - from] = this.getBit(i);
            return out;
        }

        public Label getPrefix(int prefixLen) {
            return Label.newLabel((byte) prefixLen, this.label >> (64 - prefixLen));
        }

        public Label copy() {
            return new Label(this.len, this.label);
        }

        @Override
        public String toString() {
            char[] out = new char[this.len];
            for (int i = 0; i < this.len; i++) {
                out[i] = this.getBit(i) == 0 ? '0' : '1';
            }
            return new String(out);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Label label1 = (Label) o;
            return len == label1.len &&
                    label == label1.label;
        }

        @Override
        public int hashCode() {
            return Objects.hash(len, label);
        }

        @Override
        public int compareTo(Label o) {
            if (this.equals(o))
                return 0;
            if (this.len > o.len)
                return 1;
            if (this.len < o.len)
                return -1;
            return Long.compareUnsigned(this.label, o.label);
        }
    }

    public class TreeNode implements SeedNode {
        public Label label;
        public byte[] key;

        public TreeNode(Label label, byte[] key) {
            this.label = label;
            this.key = key;
        }

        public byte[] computeKey(Label label) {
            if (this.label.isPrefix(label)) {
                return prf.muliApply(this.key, label.getPathVector(this.label.len));
            }
            return null;
        }

        public TreeNode computeNode(Label label) {
            if (this.label.isPrefix(label)) {
                return new TreeNode(label.copy(), this.computeKey(label));
            }
            return null;
        }

        @Override
        public String toString() {
            return "TreeNode{" +
                    "label=" + label.toString() +
                    ", key=" + Arrays.toString(key) +
                    '}';
        }

        @Override
        public long getNodeNr() {
            return label.toLong();
        }

        @Override
        public byte[] getSeed() {
            return this.key;
        }

        @Override
        public int getDepth() {
            return label.len;
        }

        @Override
        public void printNode() {
            System.out.println("[Seed: " + this.getSeed().toString() + ", " + this.label.toString() + "]");
        }

        @Override
        public int compareTo(SeedNode node) {
            if (node instanceof TreeNode) {
                TreeNode other = (TreeNode) node;
                return this.label.compareTo(other.label);
            }
            return -1;
        }
    }
}
