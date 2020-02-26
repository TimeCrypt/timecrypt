/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

public class KeyUtil {

    private static final String DELIM = "-";

    public static String deriveKey(long uid, String owner, int key) {
        StringBuilder sb = new StringBuilder(owner.length() + 12 * 2);
        return sb.append(uid)
                .append(DELIM)
                .append(owner)
                .append(DELIM)
                .append(key)
                .toString();
    }

    public static int returnID(String key) {
        String[] splits = key.split(DELIM);
        if (splits.length < 3)
            throw new IllegalArgumentException("Invalid key");
        return Integer.valueOf(splits[2]);
    }

}
