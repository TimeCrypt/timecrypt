/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex.node;

public class NodeContentUtil {

    public static NodeContent[] createCopy(NodeContent[] content) {
        NodeContent[] res = new NodeContent[content.length];
        for (int iter = 0; iter < res.length; iter++) {
            res[iter] = content[iter].copy();
        }
        return res;
    }

}