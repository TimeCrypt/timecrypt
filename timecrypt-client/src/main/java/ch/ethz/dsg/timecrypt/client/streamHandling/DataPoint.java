/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import java.io.Serializable;
import java.util.Date;

/**
 * Representation of a data point in time. The data point is a timestamp associated with a specific value. It is
 * immutable since once it is recorded the observation can not be undone.
 */
public class DataPoint implements Serializable, Comparable<DataPoint> {
    private final Date timestamp;
    private final long value;

    /**
     * Create a new data point representing a value at a certain timestamp.
     *
     * @param timestamp The timestamp of the data point.
     * @param value     The value of the data point.
     */
    public DataPoint(Date timestamp, long value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    /**
     * Get the time stamp of this data point.
     *
     * @return The time stamp at which the value was recorded.
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Get the value of the data point.
     *
     * @return The value at the time where the time stamp was recorded.
     */
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "DataPoint{" +
                "timestamp=" + timestamp +
                ", value=" + value +
                '}';
    }

    @Override
    public int compareTo(DataPoint dataPoint) {
        long timeDelta = this.timestamp.getTime() - dataPoint.getTimestamp().getTime();

        if (timeDelta == 0) {
            long valueDelta = this.value - dataPoint.getValue();
            if (valueDelta > Integer.MAX_VALUE) {
                return +1;
            } else if (valueDelta < Integer.MIN_VALUE) {
                return -1;
            } else {
                return (int) valueDelta;
            }
        }

        if (timeDelta > Integer.MAX_VALUE) {
            return +1;
        } else if (timeDelta < Integer.MIN_VALUE) {
            return -1;
        } else {
            return (int) timeDelta;
        }
    }
}
