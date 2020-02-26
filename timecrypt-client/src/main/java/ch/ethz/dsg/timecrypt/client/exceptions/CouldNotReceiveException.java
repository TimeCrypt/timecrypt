/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception for problems in the interaction with a TimeCrypt server. Throwing an exception like this should indicate
 * that fetching the requested resource failed.
 */
public class CouldNotReceiveException extends Exception {
    public CouldNotReceiveException(String reason) {
        super(reason);
    }
}
