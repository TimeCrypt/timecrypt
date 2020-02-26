/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

import ch.ethz.dsg.timecrypt.exceptions.TimeCryptTreeException;

/**
 * manages trees (tree metadata) and allows to get it
 */
public interface ITreeManager {

    UserStreamTree createTree(long uid, String user) throws TimeCryptTreeException;

    boolean deleteTree(long uid, String user) throws TimeCryptTreeException;

    UserStreamTree getTreeForUser(long uid, String user) throws TimeCryptTreeException;

    UserStreamTree getTreeForUser(long uid, String user, int minVersion) throws TimeCryptTreeException;

    void invalidateCache();

}
