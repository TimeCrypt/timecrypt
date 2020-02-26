/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

import ch.ethz.dsg.timecrypt.index.ITree;

public interface ITreeFactory {
    public ITree createTree(long uid, String user, int k, int interval);
}
