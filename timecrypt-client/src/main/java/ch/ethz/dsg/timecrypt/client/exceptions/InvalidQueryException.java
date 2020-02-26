/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception that indicates that the issuer of the query tried an unsupported operation with this query.
 * If the query will be retried without a change it will always fail.
 */
public class InvalidQueryException extends Exception {

    public InvalidQueryException(String message) {
        super(message);
    }

    public InvalidQueryException(InvalidReason reason, String message) {
        super(reason.getExplanation() + " " + message);
    }

    public enum InvalidReason {
        GENERAL("An invalid query occurred."),
        EMPTY_INTERVAL("The requested query start time is before the query end time."),
        START_TIME_BEFORE_END_TIME("The requested query start time is before the query end time."),
        UNSUPPORTED_OPERATION("The requested operation is unknown to this TimeCrypt client."),
        PRECISION_HIGHER_THAN_STREAM_PRECISION("The requested precision is higher than the precision of the stream itself. This Can be mitigated by a chunk scan"),
        ;

        private final String explanation;

        InvalidReason(String s) {
            this.explanation = s;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
