/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.exceptions;

import ch.ethz.dsg.timecrypt.protocol.TimeCryptNettyProtocol.ErrorResponse;

public class TimeCryptRequestException extends RuntimeException {

    int id = 0;

    public TimeCryptRequestException(String message, int id) {
        super(message);
        this.id = id;
    }

    public ErrorResponse getErrorRespons() {
        return ErrorResponse.newBuilder().setId(id).setMessage(this.getMessage()).build();
    }

}
