/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface;

import ch.ethz.dsg.timecrypt.client.exceptions.*;
import ch.ethz.dsg.timecrypt.client.queryInterface.calculations.*;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedChunk;
import ch.ethz.dsg.timecrypt.client.serverInterface.EncryptedDigest;
import ch.ethz.dsg.timecrypt.client.serverInterface.ServerInterface;
import ch.ethz.dsg.timecrypt.client.streamHandling.*;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import ch.ethz.dsg.timecrypt.crypto.encryption.MACCheckFailed;
import ch.ethz.dsg.timecrypt.crypto.keymanagement.StreamKeyManager;

import java.util.*;

/**
 * TimeCrypt supports a rich set of foundational queries that are widely used in time series workloads, i.e.,
 * statistical queries (e.g., min/max/mean), analytics (e.g., prediction, trend detection). Queries always operate
 * on an given interval inside the a stream.
 * <p>
 * This class translates queries into TimeCrypt operations and deals with the challenges like aligning queries with
 * chunks.
 * <p>
 * There are a few core concepts that are very helpful for understanding this class:
 * <p>
 * "Range queries" vs. "Simple queries": <br/>
 * <strong>Range queries</strong> ask for a certain computation with a certain granularity that devides the return
 * interval into sub-intervals.
 * e.g. give me the DAILY average of the sensor value in the last 5 months
 * <strong>Simple queries</strong> only ask for a certain computation over an interval
 * e.g. give me the OVERALL average of the sensor value in the last 5 months
 * <p>
 * "Chunk scan" <br/>
 * "Chunk scan" means that we can't use the TimeCrupt Digests for computation. For certain computations it is needed
 * to retrieve all chunks of the requested interval in order to perform the computation on them.
 * The reason why this can occur should be twofold: The requested computation is not additive i.e. it can not
 * benefit from The TimeCrypt digests (e.g. min / max values of a stream).
 * The stream does not provide all meta data for the computation e.g. the average of the stream shall be computed
 * but the stream metadata only provide the sum and not the count of the data points. Since chunk scan defeats the
 * whole point of TimeCrypts HEAC indices it has to be explicitly enabled in queries.
 * <p>
 * "Sub chunk queries" <br/>
 * If chunk scan is enabled the queries can select their requested intervals arbitrarily whereas without chunk scan
 * they have to stick to the bounds given by the streams chunk size. Sub-chunk queries have a special case for simple
 * queries where only the sub-chunk intervals at the start and / or end of the requested intervals are retrieved
 * and for the rest the aggregated digests can be used.
 */
public class Query {

    private final Stream stream;
    private long chunkFrom;
    private long digestFrom;
    private long chunkTo;
    private long digestTo;
    // needed if the query handles partial chunks
    private List<StreamMetaData> neededMetaData = new ArrayList<>();
    private long aggregationNumber;
    private List<DataPoint> dataPoints;
    private List<Chunk> chunks;
    private List<Digest> digests;
    private boolean chunkScan;
    private boolean mixedResult;
    private boolean subChunkQuery;

    private Query(Stream stream) {
        this.stream = stream;
        this.mixedResult = false;
        this.subChunkQuery = false;
        this.chunkScan = false;
    }

    /**
     * Perform a query on a stream. Returning the result as one scalar for the complete range.
     *
     * @param stream           The stream that the query should be executed on.
     * @param streamKeyManager The key manager of the stream to query.
     * @param serverInterface  The server to perform the query on.
     * @param from             The start time of the query the query. Results will INCLUDE values at this exact timestamp.
     * @param to               The end time of the query the query. Results will EXCLUDE values at this exact timestamp.
     * @param query            The kind of query to execute.
     * @return An interval object with the query result.
     */
    public static Interval performQuery(Stream stream, StreamKeyManager streamKeyManager, ServerInterface
            serverInterface, Date from, Date to, SupportedOperation query)
            throws InvalidQueryIntervalException, InvalidQueryException, QueryNeedsChunkScanException, QueryFailedException {
        return performQuery(stream, streamKeyManager, serverInterface, from, to, query, false);
    }

    /**
     * Perform a query on a stream. Returning the result as one scalar for the complete range.
     *
     * @param stream           The stream that the query should be executed on.
     * @param streamKeyManager The key manager of the stream to query.
     * @param serverInterface  The server to perform the query on.
     * @param from             The start time of the query the query. Results will INCLUDE values at this exact timestamp.
     * @param to               The end time of the query the query. Results will EXCLUDE values at this exact timestamp.
     * @param queryOp          The kind of query to execute.
     * @param allowChunkScan   Should the query be performed on the individual chunks rather than the aggregation
     *                         indexes if there are no values in the aggregation indexes?
     * @return An interval object with the query result.
     */
    public static Interval performQuery(Stream stream, StreamKeyManager streamKeyManager, ServerInterface serverInterface,
                                        Date from, Date to, SupportedOperation queryOp, boolean allowChunkScan)
            throws InvalidQueryIntervalException, InvalidQueryException, QueryNeedsChunkScanException, QueryFailedException {

        checkFromSmallerThanTo(from, to);
        Query query = checkEndDate(stream, to, allowChunkScan, checkStartDate(stream, from, allowChunkScan, new Query(stream)));

        Calculation calculation = getCalculation(queryOp);
        query = calculation.configureQuery(stream, allowChunkScan, query);

        query.setDigestPrecision(query.digestTo - query.digestFrom);

        query.fetchData(serverInterface, streamKeyManager);

        double result;
        if (query.isChunkScan()) {
            if (query.isSubChunkQuery()) {
                // Assuming that the insertion did not allow duplicate values
                result = calculation.performOnDataPoints(getSubChunkDataPoints(from, to, query));
            } else {
                result = calculation.performOnDataPoints(query.getDataPoints());
            }
        } else if (query.isMixedResult()) {
            if (query.isSubChunkQuery()) {
                result = calculation.performOnMixed(query.getDigests(), getSubChunkDataPoints(from, to, query));
            } else {
                throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Simple query with " +
                        "mixed results but without sub-chunk results should never occur.");
            }
        } else {
            if (query.isSubChunkQuery()) {
                throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Simple query with " +
                        "sub-chunk results but neither chunk scan nor mixed results should never occur.");
            }
            result = calculation.performOnDigests(query.getDigests());
        }
        return new Interval(from, to, result);
    }

    private static List<DataPoint> getSubChunkDataPoints(Date from, Date to, Query query) {

        // Assuming that the insertion did not allow duplicate values
        TreeSet<DataPoint> dataPoints = new TreeSet<>(query.getDataPoints());
        //
        while (dataPoints.first().getTimestamp().getTime() < from.getTime()) {
            dataPoints.pollFirst();
        }
        while (dataPoints.last().getTimestamp().getTime() >= to.getTime()) {
            dataPoints.pollLast();
        }
        return new ArrayList<>(dataPoints);
    }

    /**
     * Perform a query on a stream. Returning the result as vector of results for several partial intervals.
     *
     * @param stream           The stream that the query should be executed on.
     * @param streamKeyManager The key manager of the stream to query.
     * @param serverInterface  The server to perform the query on.
     * @param from             The start time of the query the query. Results will INCLUDE values at this exact timestamp.
     * @param to               The end time of the query the query. Results will EXCLUDE values at this exact timestamp.
     * @param query            The kind of query to execute.
     * @param precision        The size of the partial intervals.
     * @return An interval object with the query result.
     */
    public static List<Interval> performQueryForRange(Stream stream, StreamKeyManager streamKeyManager, ServerInterface
            serverInterface, Date from, Date to, SupportedOperation query, long precision)
            throws InvalidQueryIntervalException, InvalidQueryException, QueryNeedsChunkScanException, QueryFailedException {
        return performQueryForRange(stream, streamKeyManager, serverInterface, from, to, query, precision, false);
    }

    /**
     * Perform a query on a stream. Returning the result as vector of results for several partial intervals.
     * The query flow is roughly:
     * - some sanitation (do the times align with the precision of the stream, etc
     * - ask the calculation what kind of data it needs (raw chunks, meta data in digests)
     * - fetch the requested items from the server
     * - calculate the values on every interval
     *
     * @param stream           The stream that the query should be executed on.
     * @param streamKeyManager The key manager of the stream to query.
     * @param serverInterface  The server to perform the query on.
     * @param from             The start time of the query the query. Results will INCLUDE values at this exact timestamp.
     * @param to               The end time of the query the query. Results will EXCLUDE values at this exact timestamp.
     * @param queryOp          The kind of query to execute.
     * @param precision        The size of the partial intervals.
     * @param allowChunkScan   Should the query be performed on the individual chunks rather than the aggregation
     *                         indexes if there are no values in the aggregation indexes?
     * @return An interval object with the query result.
     */
    public static List<Interval> performQueryForRange(Stream stream, StreamKeyManager streamKeyManager, ServerInterface
            serverInterface, Date from, Date to, SupportedOperation queryOp, long precision, boolean allowChunkScan)
            throws InvalidQueryIntervalException, InvalidQueryException, QueryNeedsChunkScanException, QueryFailedException {

        checkFromSmallerThanTo(from, to);
        Query query = checkEndDate(stream, to, allowChunkScan, checkStartDate(stream, from, allowChunkScan, new Query(stream)));

        Calculation calculation = getCalculation(queryOp);
        query = calculation.configureQuery(stream, allowChunkScan, query);

        if (!allowChunkScan && (precision < stream.getChunkSize())) {
            throw new InvalidQueryException(InvalidQueryException.InvalidReason.PRECISION_HIGHER_THAN_STREAM_PRECISION,
                    "Requested precision is " + precision +
                            " but stream precision is " + stream.getChunkSize());
        } else if (to.getTime() - from.getTime() % precision != 0) {

            // if chunk scans are disallowed it is already guaranteed that the precision is a multiple of the chunk size
            // therefore the calculations will result in valid chunks again.
            long delta = to.getTime() - from.getTime() % precision;
            long next = precision - delta;
            throw new InvalidQueryIntervalException("The requested precision does not partition the requested " +
                    "interval. Change either start or end date", new Date(from.getTime() - delta),
                    new Date(from.getTime() + next), new Date(to.getTime() - delta), new Date(to.getTime() + next));
        } else if (allowChunkScan && (precision < stream.getChunkSize())) {
            // The precision of the query is higher than the streams precision. This means we have to scan all chunks.
            query.activateChunkScan();
        } else {
            query.setDigestPrecision(precision / stream.getChunkSize());
        }

        query.fetchData(serverInterface, streamKeyManager);

        List<Interval> result = new ArrayList<>();
        if (query.isChunkScan()) {
            Queue<DataPoint> dataPoints = new PriorityQueue<>(query.getDataPoints());
            while (query.isSubChunkQuery() && dataPoints.peek() != null && dataPoints.peek().getTimestamp().getTime()
                    < from.getTime()) {
                dataPoints.poll();
            }

            for (long i = from.getTime(); i < to.getTime(); i = i + precision) {
                long top = i + precision;
                List<DataPoint> currentDataPoints = new ArrayList<>();

                // This will automatically remove all data points that should be disregarded at the end of the
                // returned value list for sub chunk queries
                while (dataPoints.peek() != null && dataPoints.peek().getTimestamp().getTime() < top) {
                    currentDataPoints.add(dataPoints.poll());
                }
                result.add(new Interval(i, top - 1, calculation.performOnDataPoints(currentDataPoints)));

            }
        } else if (query.isMixedResult()) {
            throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Range query with " +
                    "mixed result should never occur. ");

        } else {
            if (query.isSubChunkQuery()) {
                throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Range query without " +
                        "chunk scan should never be a subChunkQuery. ");
            }

            Queue<Digest> digests = new PriorityQueue<>(query.getDigests());

            for (long i = query.getDigestFrom(); i < query.getDigestTo() + 1; i = TimeUtil.getChunkIdAtTime(stream,
                    TimeUtil.getChunkStartTime(stream, i) + precision)) {

                // TODO given enough tests this might be removed - for now it's only sanitizing what the server send
                Digest peek = digests.peek();
                if (peek != null) {
                    if (peek.getChunkIdFrom() != i || peek.getChunkIdTo() !=
                            TimeUtil.getChunkIdAtTime(stream, TimeUtil.getChunkStartTime(stream, i) +
                                    precision - 1)) {
                        throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "The digest send" +
                                "by the server does not align with the expected query interval.");
                    }
                }
                result.add(new Interval(TimeUtil.getChunkStartTime(stream, i), TimeUtil.getChunkEndTime(stream, i),
                        calculation.performOnDigests(Collections.singletonList(digests.poll()))));
            }
        }
        return result;
    }

    private static Query checkStartDate(Stream stream, Date from, boolean allowChunkScan, Query query)
            throws InvalidQueryIntervalException {
        long fromTime = from.getTime();
        long chunkId = TimeUtil.getChunkIdAtTime(stream, fromTime);
        long digestId = chunkId;

        if (chunkId < 0) {
            throw new InvalidQueryIntervalException("Interval starts before the stream starts", true,
                    stream.getStartDate(), stream.getStartDate());
        }

        if (chunkId > stream.getLastWrittenChunkId()) {
            throw new InvalidQueryIntervalException("Interval starts after the last inserted chunk.", true,
                    new Date(TimeUtil.getChunkStartTime(stream, stream.getLastWrittenChunkId())),
                    new Date(TimeUtil.getChunkStartTime(stream, stream.getLastWrittenChunkId())));
        }

        long delta = fromTime - TimeUtil.getChunkStartTime(stream, chunkId);

        if (!allowChunkScan && delta > 0) {
            throw new InvalidQueryIntervalException("The requested interval is not aligned with the precision of this" +
                    " stream and chunks scans are disallowed for this query", true,
                    new Date(TimeUtil.getChunkIdAtTime(stream, chunkId)),
                    new Date(TimeUtil.getChunkIdAtTime(stream, chunkId + 1))
            );
        } else if (delta > 0) {
            digestId = chunkId + 1;
            query.activateSubChunkQuery();
        }

        query.setChunkFrom(chunkId);
        query.setDigestFrom(digestId);
        return query;
    }

    private static Query checkEndDate(Stream stream, Date to, boolean allowChunkScan, Query query)
            throws InvalidQueryIntervalException {
        long toTime = to.getTime();
        long chunkId = TimeUtil.getChunkIdAtTime(stream, toTime);
        long digestId = chunkId;

        if (chunkId < 0) {
            throw new InvalidQueryIntervalException("Interval ends before the stream starts", false, stream.getStartDate(), stream.getStartDate()
            );
        }

        // we have to allow last chunk ID + 1 because the interval is EXCLUSIVE the last item
        if (chunkId > stream.getLastWrittenChunkId() + 1) {
            throw new InvalidQueryIntervalException("Interval ends after the last inserted chunk.", false,
                    new Date(TimeUtil.getChunkStartTime(stream, stream.getLastWrittenChunkId())),
                    new Date(TimeUtil.getChunkStartTime(stream, stream.getLastWrittenChunkId())));
        }

        long delta = toTime - TimeUtil.getChunkStartTime(stream, chunkId);

        if (!allowChunkScan && delta > 0) {
            throw new InvalidQueryIntervalException("The requested interval is not aligned with the precision of this stream and chunks scans are " +
                    "disallowed for this query", false, new Date(TimeUtil.getChunkIdAtTime(stream, chunkId)),
                    new Date(TimeUtil.getChunkIdAtTime(stream, chunkId + 1))
            );
        } else if (delta > 0) {
            digestId = chunkId - 1;
            query.activateSubChunkQuery();
        }
        query.setChunkTo(chunkId);
        query.setDigestTo(digestId);
        return query;
    }

    private static void checkFromSmallerThanTo(Date from, Date to) throws InvalidQueryException {

        if (to.getTime() - from.getTime() < 0) {
            throw new InvalidQueryException(InvalidQueryException.InvalidReason.START_TIME_BEFORE_END_TIME,
                    "Start time " + from.toString() + " (" + from.getTime() + "), end time " + to.toString() + "("
                            + to.getTime() + ")");
        } else if (to.getTime() - from.getTime() == 0) {
            // if it is exactly zero the query is just asking for the exact millisecond of 'from' but excluding this
            // millisecond because the 'to' is EXCLUSIVE
            throw new InvalidQueryException(InvalidQueryException.InvalidReason.EMPTY_INTERVAL,
                    "Start time " + from.toString() + " (" + from.getTime() + ") is equel to end time " +
                            to.toString() + "(" + to.getTime() + ") but by definition the end time of the query " +
                            "is excluded from the result.");
        }
    }

    // TODO maybe switch this to reflection
    public static Calculation getCalculation(SupportedOperation operation) throws InvalidQueryException {
        switch (operation) {
            case AVG:
                return new AverageCalculation();
            case NULLABLE_SUM:
                return new NullableSumCalculation();
            case SUM:
                return new SumCalculation();
            case MAX:
                return new MinMaxCalculation(true);
            case MIN:
                return new MinMaxCalculation(false);
            case COUNT:
                return new CountCalculation();
            default:
                throw new InvalidQueryException(InvalidQueryException.InvalidReason.UNSUPPORTED_OPERATION,
                        "Requested Operation was " + operation);
        }

    }

    public boolean isSubChunkQuery() {
        return subChunkQuery;
    }

    private void activateSubChunkQuery() {
        this.subChunkQuery = true;
    }

    private List<Digest> getDigests() {
        return this.digests;

    }

    private boolean isMixedResult() {
        return this.mixedResult;
    }

    private List<DataPoint> getDataPoints() {
        return this.dataPoints;
    }

    private void fetchData(ServerInterface serverInterface, StreamKeyManager streamKeyManager) throws
            QueryFailedException {
        this.chunks = new ArrayList<>();
        this.dataPoints = new ArrayList<>();
        this.digests = new ArrayList<>();

        if (this.chunkScan) {
            List<EncryptedChunk> encryptedChunks;
            try {
                encryptedChunks = serverInterface.getChunks(this.stream.getId(), this.chunkFrom, this.chunkTo);
            } catch (CouldNotReceiveException e) {
                throw new QueryFailedException(QueryFailedException.FailReason.COULD_NOT_RECEIVE_VALUES_FROM_SERVER, e.getMessage());
            }
            decryptChunks(streamKeyManager, encryptedChunks);
        } else {
            List<EncryptedDigest> encryptedDigests;
            try {
                encryptedDigests = serverInterface.getStatisticalData(this.stream.getId(), this.digestFrom,
                        this.digestTo, (int) this.aggregationNumber, this.neededMetaData);
            } catch (InvalidQueryException e) {
                throw new QueryFailedException(QueryFailedException.FailReason.COULD_NOT_RECEIVE_VALUES_FROM_SERVER, e.getMessage());
            }
            for (EncryptedDigest encryptedDigest : encryptedDigests) {
                try {
                    this.digests.add(new Digest(stream, encryptedDigest, streamKeyManager));
                } catch (MACCheckFailed macCheckFailed) {
                    throw new QueryFailedException(QueryFailedException.FailReason.MAC_INVALID, macCheckFailed.getMessage());
                }
            }

            List<EncryptedChunk> encryptedChunks;
            if (this.chunkFrom < this.digestFrom) {
                try {
                    encryptedChunks = serverInterface.getChunks(this.stream.getId(), this.chunkFrom, this.digestFrom);
                } catch (CouldNotReceiveException e) {
                    throw new QueryFailedException(QueryFailedException.FailReason.COULD_NOT_RECEIVE_VALUES_FROM_SERVER, e.getMessage());
                }
                decryptChunks(streamKeyManager, encryptedChunks);
                this.mixedResult = true;
            }
            if (this.digestTo < this.chunkTo) {
                try {
                    encryptedChunks = serverInterface.getChunks(this.stream.getId(), this.digestTo, this.chunkTo);
                } catch (CouldNotReceiveException e) {
                    throw new QueryFailedException(QueryFailedException.FailReason.COULD_NOT_RECEIVE_VALUES_FROM_SERVER, e.getMessage());
                }
                decryptChunks(streamKeyManager, encryptedChunks);
                this.mixedResult = true;
            }
        }
    }

    private void decryptChunks(StreamKeyManager streamKeyManager, List<EncryptedChunk> encryptedChunks) throws
            QueryFailedException {
        for (EncryptedChunk encryptedChunk : encryptedChunks) {
            Chunk chunk;
            chunk = new Chunk(this.stream, encryptedChunk.getChunkId(), encryptedChunk.getPayload(), streamKeyManager);
            this.dataPoints.addAll(chunk.getValues());
            this.chunks.add(chunk);
        }
    }

    private void setDigestPrecision(long l) {
        this.aggregationNumber = l;
    }

    private void activateChunkScan() {
        this.chunkScan = true;
    }

    public void setChunkFrom(long chunkFrom) {
        this.chunkFrom = chunkFrom;
    }

    public long getDigestFrom() {
        return digestFrom;
    }

    public void setDigestFrom(long digestFrom) {
        this.digestFrom = digestFrom;
    }

    public void setChunkTo(long chunkTo) {
        this.chunkTo = chunkTo;
    }

    public long getDigestTo() {
        return digestTo;
    }

    public void setDigestTo(long digestTo) {
        this.digestTo = digestTo;
    }

    public void addNeededMetaData(StreamMetaData neededMetaData) {
        this.neededMetaData.add(neededMetaData);
    }

    private boolean isChunkScan() {
        return this.chunkScan;
    }

    public enum SupportedOperation {
        AVG, COUNT, SUM, NULLABLE_SUM, MIN, MAX
    }
}
