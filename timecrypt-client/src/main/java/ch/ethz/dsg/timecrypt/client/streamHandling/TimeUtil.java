/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.streamHandling;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Helper Util for chunk and stream related time handling. Basically this class should always be used to translate
 * chunk IDs (the IDs that servers use) to times that can be understood by humans.
 * <p>
 * This also controls the interaction with precisions i.e. intervals of time that streams operate on.
 */
public class TimeUtil {

    static Clock clock = Clock.systemDefaultZone();

    /**
     * Possibility to overwrite the clock for testing the TimeUtil.
     *
     * @param clock A Clock that will be used by the TimeUtil for all stream independent operations.
     */
    public static void setClock(Clock clock) {
        TimeUtil.clock = clock;
    }

    public static long getChunkIdAtTime(Stream associatedStream, Date timestamp) {
        return getChunkIdAtTime(associatedStream, timestamp.getTime());
    }

    public static long getChunkIdAtTime(Stream associatedStream, long timestamp) {
        if (associatedStream.getStartDate().getTime() > timestamp) {
            return -1;
        }
        long timeOffset = timestamp - associatedStream.getStartDate().getTime();
        return (long) Math.floor((float) timeOffset / associatedStream.getChunkSize());
    }

    /**
     * Gets the Date at the last full minute. For example if it is "Mon 13 Jan 2020 05:15:13 PM CET" this function would
     * return "Mon 13 Jan 2020 05:15:00 PM CET"
     * This is used to align streams to always start at a full minute. Therefore enables multi stream aggregations.
     *
     * @return The date at the next full minute.
     */
    public static Date getDateAtLastFullMinute() {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(clock.millis());
        date.set(Calendar.MINUTE, date.get(Calendar.MINUTE));
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        return date.getTime();
    }

    public static long getChunkStartTime(Stream correspondingStream, long chunkId) {
        return correspondingStream.getStartDate().getTime() + (correspondingStream.getChunkSize() * chunkId);
    }

    public static long getChunkEndTime(Stream correspondingStream, long chunkId) {
        return correspondingStream.getStartDate().getTime() + (correspondingStream.getChunkSize() * (chunkId + 1)) - 1;
    }

    public static void resetClock() {
        clock = Clock.systemDefaultZone();
    }

    public enum Precision {
        ONE_HUNDRED_MILLIS(100),
        TWO_HUNDRED_FIFTY_MILLIS(250),
        FIVE_HUNDRED_MILLIS(500),
        ONE_SECOND(1000),
        TEN_SECONDS(10000),
        ONE_MINUTE(60000);

        private final long millis;

        Precision(long s) {
            this.millis = s;
        }

        public static List<Precision> getGreaterPrecisions(Precision precision) {

            List<Precision> precisions = new ArrayList<>();
            for (TimeUtil.Precision otherPrecision : TimeUtil.Precision.values()) {
                if (otherPrecision.getMillis() > precision.getMillis()) {
                    precisions.add(otherPrecision);
                }
            }
            return precisions;
        }

        public long getMillis() {
            return millis;
        }
    }
}
