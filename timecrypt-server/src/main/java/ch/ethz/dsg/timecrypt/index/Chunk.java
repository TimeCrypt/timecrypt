/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

public class Chunk {

    private int storageKey;

    private byte[] data;

    public Chunk(int storageKey, byte[] data) {
        this.storageKey = storageKey;
        this.data = data;
    }

    public int getStorageKey() {
        return storageKey;
    }

    public byte[] getData() {
        return data;
    }
}
