/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception that gets raised if something went wrong in the server communication and an asset
 * could not be stored or in general if a operation could not be persisted.
 */
public class CouldNotStoreException extends Exception {

    public CouldNotStoreException(String reason) {
        super(reason);
    }

}
