/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception to be thrown if a TimeCrypt server does not support certain operations.
 */
public class UnsupportedOperationException extends Exception {

    // TODO: This is a workaround for shortcomings of clients and should be removed if possible.
    public UnsupportedOperationException(String message) {
        super(message);
    }
}
