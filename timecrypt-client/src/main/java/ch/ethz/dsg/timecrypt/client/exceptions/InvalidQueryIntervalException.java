/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.exceptions;

import java.util.Date;

/**
 * Exception that indicates that the given interval does not align with the interval of the TimeCrypt chunks and
 * therefore can not be executed at all or can not be executed in a fast index-access only manner.
 * <p>
 * Sometimes this can be fixed by allowing a chunk scan for this query.
 */
public class InvalidQueryIntervalException extends Exception {

    private final boolean startProblematic;
    private final boolean endProblematic;
    private final Date nextEarlierStart;
    private final Date nextLaterStart;
    private final Date nextEarlierEnd;
    private final Date nextLaterEnd;

    public InvalidQueryIntervalException(String reason, boolean start, Date nextEarlier, Date nextLater) {
        super("Interval of the query does not align to given precision intervals. " + reason);
        if (start) {
            this.startProblematic = true;
            this.endProblematic = false;
            this.nextEarlierStart = nextEarlier;
            this.nextLaterStart = nextLater;
            this.nextEarlierEnd = null;
            this.nextLaterEnd = null;
        } else {
            this.startProblematic = false;
            this.endProblematic = true;
            this.nextEarlierStart = null;
            this.nextLaterStart = null;
            this.nextEarlierEnd = nextEarlier;
            this.nextLaterEnd = nextLater;
        }
    }

    public InvalidQueryIntervalException(String reason, Date nextEarlierStart, Date nextLaterStart, Date nextEarlierEnd,
                                         Date nextLaterEnd) {
        super("Either start nor end of the query does not align to given precision intervals." + reason);
        this.startProblematic = true;
        this.endProblematic = true;
        this.nextEarlierStart = nextEarlierStart;
        this.nextLaterStart = nextLaterStart;
        this.nextEarlierEnd = nextEarlierEnd;
        this.nextLaterEnd = nextLaterEnd;

    }
}
