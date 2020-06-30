/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.exceptions;

public class TimeCryptTreeAlreadyExistsException extends TimeCryptTreeException {
    public TimeCryptTreeAlreadyExistsException(String message, int id) {
        super(message, id);
    }
}
