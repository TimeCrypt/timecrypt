/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;


import ch.ethz.dsg.timecrypt.client.queryInterface.Interval;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.InsertHandler;
import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;

import java.util.*;

/***
 * Example of how to use the Java TimeCrypt api
 */
public class BasicTCUsage {

    private static final String DUMMY_PASSWORD = "asdfghjklasdfghjkl";
    private static final String SERVER_ADDRESS = "172.17.0.1";
    private static final int SERVER_PORT = 15000;
    private static final int TIME_MINUTES_BEFORE_NOW = 10;
    private static final int MIN_TO_MS = 60 * 1000;
    private static final int NUM_ELEMENTS_TO_BACKUP_INSERT = 10000;
    static Random rand = new Random(10);

    public static void basicDemo() throws Exception {
        TimeCryptProfile profile = new LocalTimeCryptProfile(null, "myUser",
                "HeartRateProfile", SERVER_ADDRESS, SERVER_PORT);
        TimeCryptKeystore keystore = LocalTimeCryptKeystore.createLocalKeystore(null, DUMMY_PASSWORD.toCharArray());

        TimeCryptClient tcClient = new TimeCryptClient(keystore, profile);

        long timeRangeMS = (TIME_MINUTES_BEFORE_NOW * MIN_TO_MS);
        long tsDelta = (timeRangeMS / NUM_ELEMENTS_TO_BACKUP_INSERT);


        // Compute the start time of the stream (milliseconds)
        long startDate = TimeUtil.getDateAtLastFullMinute().getTime() - timeRangeMS;

        // Create a new time-series stream
        long streamID = tcClient.createStream(
                "HeartRate Stream",
                "This stream stores my heart rate",
                TimeUtil.Precision.ONE_SECOND,
                Collections.singletonList(TimeUtil.Precision.TEN_SECONDS),
                DefaultConfigs.getDefaultMetaDataConfig(),
                DefaultConfigs.getDefaultEncryptionScheme(),
                null,
                new Date(startDate));


        // Start a backup handler because, we have to insert data from the past
        System.out.format("Insert data from the past %s until now\n", new Date(startDate));
        InsertHandler handler = tcClient.getHandlerForBackupInsert(streamID, new Date(startDate));
        for (long i = 0; i < NUM_ELEMENTS_TO_BACKUP_INSERT; i++) {
            // insert data from the past in-range
            handler.writeDataPointToStream(new DataPoint(new Date(startDate + i * tsDelta), rand.nextInt(200)));
        }
        handler.terminate();

        // For demonstration we perform a live insert a the current timestamp with the chunk handler
        // This api would be used by a sensor or data source that produces data live and on the fly
        System.out.println("Insert data live at the current point in time");
        InsertHandler liveHandler = tcClient.getHandlerForLiveInsert(streamID, 5000);
        liveHandler.writeDataPointToStream(new DataPoint(new Date(), 10));
        Thread.sleep(1000);
        liveHandler.flush();


        // perform simple range aggregation queries
        System.out.println("Perform simple time range aggregations");
        for (long i = 1; i < 10; i++) {
            Interval x = tcClient.performQuery(streamID, new Date(startDate), new Date(startDate + i * TimeUtil.Precision.ONE_SECOND.getMillis()),
                    Query.SupportedOperation.AVG, false);
            System.out.println(String.format("Result [%s, %s] AVG %f,", x.getFrom(), x.getTo(), x.getValue()));
        }

        // perform simple range aggregation queries with more statistics
        System.out.println("Perform simple time range aggregations with multiple statistics");
        for (long i = 1; i < 10; i++) {
            Interval x = tcClient.performQuery(streamID, new Date(startDate), new Date(startDate + i * TimeUtil.Precision.ONE_SECOND.getMillis()),
                    Arrays.asList(Query.SupportedOperation.AVG, Query.SupportedOperation.VAR, Query.SupportedOperation.STD),
                    false);

            System.out.println(String.format("Result [%s, %s] AVG %f, VAR %f, STD %f", x.getFrom(), x.getTo(),
                    x.getValueAt(0), x.getValueAt(1), x.getValueAt(2)));
        }

        System.out.println("Perform different window aggregations over a static time range");
        for (long numVals : new long[]{1, 2, 4, 5, 10}) {
            List<Interval> x = tcClient.performRangeQuery(streamID, new Date(startDate), new Date(startDate + timeRangeMS),
                    Arrays.asList(Query.SupportedOperation.AVG, Query.SupportedOperation.VAR, Query.SupportedOperation.STD),
                    false, timeRangeMS / numVals);
            System.out.println("Window size: " + (timeRangeMS / numVals / 1000 + "s"));
            for (Interval inter : x) {
                System.out.println(String.format("Result [%s, %s] AVG %f, VAR %f, STD %f", inter.getFrom(), inter.getTo(),
                        inter.getValueAt(0), inter.getValueAt(1), inter.getValueAt(2)));
            }

        }

        //terminate the live handler
        liveHandler.terminate();
        // delete the data
        tcClient.deleteStream(streamID);
    }

    public static void main(String[] args) {
        try {
            basicDemo();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
