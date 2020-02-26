/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex.node;

public interface NodeContent {

    NodeContent copy();

    void mergeOther(NodeContent otherContent);

    NodeContent mergeOtherCopy(NodeContent otherContent);

    byte[] encode();

    String getStringRepresentation();

}
