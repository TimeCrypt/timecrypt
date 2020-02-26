/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index.blockindex.node;

public class BlockIdUtil {

    public static int getFrom(long id) {
        return (int) (id >> 32);
    }

    public static int getTo(long id) {
        return (int) id;
    }

    public static long getID(int from, int to) {
        return (((long) from) << 32) | to;
    }
}
