/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.index;

public class UserStreamTree {

    private String owner;
    private long uid;
    private ITree tree;

    public UserStreamTree(String owner, long uid, ITree tree) {
        this.owner = owner;
        this.uid = uid;
        this.tree = tree;
    }

    public String getOwner() {
        return owner;
    }

    public long getUid() {
        return uid;
    }

    public ITree getTree() {
        return tree;
    }
}
