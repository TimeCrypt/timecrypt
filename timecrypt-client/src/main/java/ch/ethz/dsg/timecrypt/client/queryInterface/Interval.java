/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface;

import java.util.Date;

/**
 * Represents the result of a query in a certain time interval.
 * <p>
 * Intervals can be empty if there is no data in it. This corresponds to a null value.
 */
public class Interval {

    private final Date from;
    private final Date to;
    private final Double value;

    /**
     * Constructor that operates on dates.
     *
     * @param from  The start of the interval.
     * @param to    The end of the interval.
     * @param value The value in the interval.
     */
    public Interval(Date from, Date to, double value) {
        this.from = from;
        this.to = to;
        this.value = value;
    }

    /**
     * Constructor that operates on epoch millis.
     *
     * @param from  The start of the interval in epoch millis.
     * @param to    The end of the interval in epoch millis.
     * @param value The value in the interval.
     */
    public Interval(long from, long to, Double value) {
        this.from = new Date(from);
        this.to = new Date(to);
        this.value = value;
    }

    public Date getFrom() {
        return from;
    }

    public Date getTo() {
        return to;
    }

    public Double getValue() {
        return value;
    }

    /**
     * Indicates that this interval is empty.
     *
     * @return True if there is no value in this interval. False if there are value.
     */
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public String toString() {
        return "Interval{" +
                "from=" + from +
                ", to=" + to +
                ", value=" + value +
                '}';
    }
}
