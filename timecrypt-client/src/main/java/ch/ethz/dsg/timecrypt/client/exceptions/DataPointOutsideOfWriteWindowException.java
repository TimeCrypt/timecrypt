/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Error that indicates that the provided data point can not be written to the stream because
 * it is not in the current write window.
 */
public class DataPointOutsideOfWriteWindowException extends TCWriteException {
    public DataPointOutsideOfWriteWindowException(String message) {
        super(message);
    }
}
