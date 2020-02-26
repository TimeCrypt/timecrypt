/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex.node;

public class MetaInnerNode {

    public NodeContent[] metaInformation;

    public MetaInnerNode(NodeContent[] metaInformation) {
        this.metaInformation = metaInformation;
    }

    public NodeContent[] getContentCopy() {
        return NodeContentUtil.createCopy(metaInformation);
    }

    public MetaInnerNode createCopy() {
        return new MetaInnerNode(NodeContentUtil.createCopy(metaInformation));
    }
}
