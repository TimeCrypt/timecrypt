/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt;

import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotReceiveException;
import ch.ethz.dsg.timecrypt.client.exceptions.CouldNotStoreException;
import ch.ethz.dsg.timecrypt.client.exceptions.InvalidQueryException;
import ch.ethz.dsg.timecrypt.client.exceptions.TCWriteException;
import ch.ethz.dsg.timecrypt.client.queryInterface.Interval;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.LocalTimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptKeystore;
import ch.ethz.dsg.timecrypt.client.state.TimeCryptProfile;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.InsertHandler;
import ch.ethz.dsg.timecrypt.client.streamHandling.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class BenchClients {

    public static final String TYPE_LOG_QUERY = "Q";
    public static final String TYPE_LOG_INSERT = "I";
    private static int logGranularity = 100;
    private static int queryStart = 100;
    private static int numElementsPerChunk = 500;
    private static long DEFAULT_START_DATE_MS = 1000;
    private static TimeUtil.Precision DEFAULT_CHUNK_WINDOW = TimeUtil.Precision.TEN_SECONDS;
    private static Date DEFAULT_START_DATE = new Date(DEFAULT_START_DATE_MS);
    private static int[] granularities = new int[]{4, 8, 16, 32, 64, 128, 256, 512, 1024};
    private static Random rangeSelector = new Random(460003178);
    private static Logger logger = LoggerFactory.getLogger(BenchClients.class);
    private static List<Query.SupportedOperation> operationsToQuery = Arrays.asList(Query.SupportedOperation.AVG);

    private static int[][] currentState;

    public static String usernameFromID(int id) {
        return String.format("user%d", id);
    }

    public static void createState(int numUsers, int numStreams) {
        currentState = new int[numUsers][];
        for (int i = 0; i < numUsers; i++) {
            currentState[i] = new int[numStreams];
        }
    }

    public static QueryRequest selectRequest(int userid) {
        int buff = 2;
        QueryRequest req = new QueryRequest();
        req.userID = userid;
        req.queryStream = rangeSelector.nextInt(currentState[req.userID].length);
        int len = currentState[req.userID][req.queryStream] - buff;
        if (len < granularities[0])
            return null;

        do {
            req.granularity = granularities[rangeSelector.nextInt(granularities.length)];
        } while (req.granularity > len);
        int range = len - req.granularity;
        req.from = (range > 0) ? rangeSelector.nextInt(range) : 0;
        req.to = req.from + req.granularity;
        return req;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = new CommandLine(new BenchCommandLine()).execute(args);
        System.exit(exitCode);
    }

    private static class QueryRequest {
        int from;
        int to;
        int granularity;
        int queryStream;
        int userID;
    }

    private static class Client extends Thread {

        public AtomicBoolean ok = new AtomicBoolean(true);
        String username;
        int clientId;
        int numStreams;
        int readWriteRatio;
        private int maxRounds;
        private TimeCryptClient client;
        private int insertCounter = 0;
        private int queryCounter = 0;
        private long[] streamIDs;
        private InsertHandler[] insertHandlers;

        public Client(int clientId, int numStreams, TimeCryptClient client, int readWriteRatio, int maxRounds) {
            this.clientId = clientId;
            this.numStreams = numStreams;
            this.client = client;
            this.readWriteRatio = readWriteRatio;
            this.username = usernameFromID(clientId);
            this.maxRounds = maxRounds;
        }

        private void createStreams() throws CouldNotStoreException, IOException, CouldNotReceiveException, InvalidQueryException {
            streamIDs = new long[numStreams];
            insertHandlers = new InsertHandler[numStreams];
            for (int streamId = 0; streamId < numStreams; streamId++) {
                streamIDs[streamId] = client.createStream(
                        String.format("User:%sStream:%d", username, streamId),
                        "",
                        DEFAULT_CHUNK_WINDOW,
                        Collections.singletonList(TimeUtil.Precision.TEN_SECONDS),
                        DefaultConfigs.getDefaultMetaDataConfig(),
                        DefaultConfigs.getDefaultEncryptionScheme(),
                        null,
                        DEFAULT_START_DATE);
                insertHandlers[streamId] = client.getHandlerForInsertBench(streamIDs[streamId], DEFAULT_START_DATE);
            }
        }

        private void insertToStream(int counter) throws IOException, TCWriteException {
            long gap = DEFAULT_CHUNK_WINDOW.getMillis() / numElementsPerChunk;
            for (int streamId = 0; streamId < numStreams; streamId++) {
                for (int i = 0; i < numElementsPerChunk; i++) {
                    insertHandlers[streamId].writeDataPointToStream(
                            new DataPoint(
                                    new Date(DEFAULT_START_DATE_MS + DEFAULT_CHUNK_WINDOW.getMillis() * counter + i * gap)
                                    , 1));
                }
                long timeBefore = System.nanoTime();
                insertHandlers[streamId].flush();
                if (++insertCounter % logGranularity == 0)
                    logger.info("{},{},{}", this.clientId, TYPE_LOG_INSERT, System.nanoTime() - timeBefore);

                currentState[clientId][streamId]++;
            }
        }

        private void queryStreamsStatistics(int numQueries) throws Exception {
            for (int count = 0; count < numQueries; count++) {
                QueryRequest request = selectRequest(this.clientId);
                if (request == null)
                    continue;
                int from = request.from;
                int to = request.to;
                int granularity = request.granularity;
                long queryStream = request.queryStream;
                long timeBefore = System.nanoTime();
                List<Interval> x = client.performRangeQuery(streamIDs[(int) queryStream],
                        new Date(DEFAULT_START_DATE_MS + from * DEFAULT_CHUNK_WINDOW.getMillis()),
                        new Date(DEFAULT_START_DATE_MS + to * DEFAULT_CHUNK_WINDOW.getMillis()),
                        operationsToQuery,
                        false,
                        granularity * DEFAULT_CHUNK_WINDOW.getMillis());
                if (++queryCounter % logGranularity == 0)
                    logger.info("{},{},{}", this.clientId, TYPE_LOG_QUERY, System.nanoTime() - timeBefore);

            }
        }

        @Override
        public void run() {
            try {
                createStreams();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int counter = 0;
            while (ok.get() && counter < maxRounds) {
                try {
                    insertToStream(counter++);
                    if (counter > queryStart)
                        queryStreamsStatistics(numStreams * readWriteRatio);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @CommandLine.Command(name = "timecrypt-bench-client", mixinStandardHelpOptions = true, description = "Basic Benchmark client for the TC server ",
            version = "1.0")
    private static class BenchCommandLine implements Callable<Integer> {

        @CommandLine.Option(names = {"-c", "--clients"},
                description = "Number of clients ")
        int numberOfClients = 10;

        @CommandLine.Option(names = {"-s", "--streams"},
                description = "Number of streams per client")
        int numStreamsPerClients = 4;

        @CommandLine.Option(names = {"-r", "--rwratio"},
                description = "The chunk insert to Query Ratio")
        int readWriteRatio = 4;

        @CommandLine.Option(names = {"-i", "--ip"},
                description = "The ip address of the server.")
        String connectTo = "127.0.0.1";

        @CommandLine.Option(names = {"-p", "--port"},
                description = "The chunk insert to Query Ratio")
        int connectToPort = 15000;

        @CommandLine.Option(names = {"-mr", "--maxrounds"},
                description = "The maximal number of rounds")
        int maxRounds = 10000;

        @CommandLine.Option(names = {"-md", "--maxduration"},
                description = "The maximal duration of the benchmark")
        int awaitTime = 120000;

        @CommandLine.Option(names = {"-nr", "--recordsperchunk"},
                description = "The number of record per chunk")
        int numberOfRecords = 500;

        @CommandLine.Option(names = {"-lg", "--loggranularity"},
                description = "How many reults to log. eg 100 = every 100th event")
        int logGran = 100;

        @Override
        public Integer call() throws Exception {
            Client[] clients = new Client[numberOfClients];
            createState(numberOfClients, numStreamsPerClients);
            String DUMMY_PASSWORD = "1234";
            numElementsPerChunk = numberOfRecords;
            logGranularity = logGran;
            TimeCryptKeystore keystore = LocalTimeCryptKeystore.createLocalKeystore(null, DUMMY_PASSWORD.toCharArray());
            for (int i = 0; i < numberOfClients; i++) {
                TimeCryptProfile profile = new LocalTimeCryptProfile(null, usernameFromID(i),
                        "Profile" + usernameFromID(i), connectTo, connectToPort);
                TimeCryptClient tcClient = new TimeCryptClient(keystore, profile);
                clients[i] = new Client(i, numStreamsPerClients, tcClient,
                        readWriteRatio, maxRounds);
                clients[i].start();
            }
            Thread.sleep(awaitTime);
            for (int i = 0; i < numberOfClients; i++) {
                clients[i].ok.set(false);
            }

            for (int i = 0; i < numberOfClients; i++) {
                clients[i].join();
            }
            return 0;
        }
    }

}