/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex;

import ch.ethz.dsg.timecrypt.index.blockindex.node.BlockNode;

public interface INodeManager {

    BlockNode loadNode(long id) throws Exception;

    void pushUpdates(UpdateSummary summary) throws Exception;

    void updateToLatest(BlockTree tree) throws Exception;

    BlockNode loadNodeWithMinVersion(long blockid, int version) throws Exception;

    BlockNode loadNodeWithVersionForInsert(long blockid, int version) throws Exception;

    void updateToLatest(BlockTree tree, int minVersion) throws Exception;


}
