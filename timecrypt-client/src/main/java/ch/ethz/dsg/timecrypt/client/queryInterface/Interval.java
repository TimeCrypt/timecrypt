/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents the result of a query in a certain time interval.
 * <p>
 * Intervals can be empty if there is no data in it. This corresponds to a null value.
 */
public class Interval {

    private final Date from;
    private final Date to;
    private final List<Double> values;

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
        this.values = new ArrayList<>();
        this.values.add(value);
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
        this.values = new ArrayList<>();
        if (value != null)
            this.values.add(value);
    }

    /**
     * Constructor that operates on epoch millis.
     *
     * @param from  The start of the interval in epoch millis.
     * @param to    The end of the interval in epoch millis.
     * @param values The values in the interval.
     */
    public Interval(long from, long to, List<Double> values) {
        this.from = new Date(from);
        this.to = new Date(to);
        this.values = values;
    }

    /**
     * Constructor that operates on epoch millis.
     *
     * @param from  The start of the interval in epoch millis.
     * @param to    The end of the interval in epoch millis.
     * @param values The values in the interval.
     */
    public Interval(Date from, Date to, List<Double> values) {
        this.from = from;
        this.to = to;;
        this.values = values;
    }


    public Date getFrom() {
        return from;
    }

    public Date getTo() {
        return to;
    }

    public Double getValue() {
        return values.get(0);
    }

    public Double getValueAt(int i) {
        return values.get(i);
    }

    /**
     * Indicates that this interval is empty.
     *
     * @return True if there is no value in this interval. False if there are value.
     */
    public boolean isEmpty() {
        return values.size() == 0;
    }

    @Override
    public String toString() {
        return "Interval{" +
                "from=" + from +
                ", to=" + to +
                ", value=" + values.toString() +
                '}';
    }
}
