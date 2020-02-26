/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception that indicates that a valid query can not be executed without accessing the Chunks that store the
 * actual data points.
 * <p>
 * This means the query can not be executed in a fast index-access only manner. Because the stream does not support
 * all needed types of meta data.
 */
public class QueryNeedsChunkScanException extends Exception {
    public QueryNeedsChunkScanException(ChunkScanReason reason, String message) {
        super(reason.getExplanation() + " " + message);
    }

    public enum ChunkScanReason {
        NOT_ENOUGH_METADATA_IN_STREAM("The stream does not provide enough meta data for the requested query."),
        NO_METADATA_SUPPORT("There is currently no meta data in TimeCrypt that support this query."),
        ;

        private final String explanation;

        ChunkScanReason(String s) {
            this.explanation = s;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
