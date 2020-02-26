/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex;

public interface IBlockTreeFetcher {

    BlockTree createTree(long uid, String user, int k, int interval) throws Exception;

    BlockTree fetchNewestTreeAndAwait(long uid, String user) throws Exception;

    BlockTree fetchTreeMinVersion(long uid, String user, int minVersion) throws Exception;

    BlockTree fetchTree(long uid, String user) throws Exception;

    void invalidateCache();

}
