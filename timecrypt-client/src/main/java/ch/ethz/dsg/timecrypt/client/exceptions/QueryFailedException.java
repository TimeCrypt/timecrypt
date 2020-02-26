/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

/**
 * Exception that indicates that a problem occurred during the execution of a valid query / task.
 * <p>
 * A retry of the query might help.
 */
public class QueryFailedException extends Exception {

    public QueryFailedException(FailReason failReason, String message) {
        super(failReason.getExplanation() + " " + message);
    }

    public enum FailReason {
        COULD_NOT_RECEIVE_VALUES_FROM_SERVER("Could not fetch stuff from the server."),
        FILE_ALREADY_EXISTING("The file that should be used is already existing."),
        CORRESPONDING_CHUNKS_COULD_NOT_BE_FOUND("Server deleted (some of) the chunks that would have been needed " +
                "for the chunk scan."),
        COULD_NOT_DECRYPT_CHUNK("Server deleted (some of) the chunks that would have been."),
        MAC_INVALID("The MAC check failed during the decryption of a Digest."),
        INTERNAL_ERROR("An internal error in the query engine occurred. Please file a bug."),
        ;

        private final String explanation;

        FailReason(String s) {
            this.explanation = s;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
