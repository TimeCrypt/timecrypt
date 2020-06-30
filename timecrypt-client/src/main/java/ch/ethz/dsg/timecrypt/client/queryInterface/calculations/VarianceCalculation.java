/*
 * Copyright (c) 2020. by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface.calculations;

import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryNeedsChunkScanException;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Digest;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class VarianceCalculation implements Calculation {

    boolean countExisting = false;
    boolean sumExisting = false;
    boolean squareExisting = false;
    long square = 0;
    long sum = 0;
    long count = 0;

    @Override
    public Query configureQuery(Stream stream, boolean chunkScanAllowed, Query query) throws QueryNeedsChunkScanException {

        for (StreamMetaData metaData : stream.getMetaData()) {
            if (metaData.getType().equals(StreamMetaData.MetadataType.COUNT)) {
                countExisting = true;
                query.addNeededMetaData(metaData);
            }
            if (metaData.getType().equals(StreamMetaData.MetadataType.SQUARE)) {
                squareExisting = true;
                query.addNeededMetaData(metaData);
            }
            if (metaData.getType().equals(StreamMetaData.MetadataType.SUM)) {
                sumExisting = true;
                query.addNeededMetaData(metaData);
            }
        }

        if (!(squareExisting && countExisting && sumExisting) && !chunkScanAllowed) {
            throw new QueryNeedsChunkScanException(QueryNeedsChunkScanException.ChunkScanReason.
                    NOT_ENOUGH_METADATA_IN_STREAM, "Count in metadata: " + countExisting + " square in metadata: " +
                    squareExisting);
        } else if(! squareExisting || ! countExisting  || ! sumExisting) {
            query.activateChunkScan();
        }
        return query;
    }

    @Override
    public Double performOnDigests(List<Digest> digests) throws QueryFailedException {
        square = 0;
        count = 0;
        sum = 0;

        extractFromDigest(digests);

        if (count == 0) {
            return null;
        }

        return (square / (double) count) - (sum / (double) count);
    }

    @Override
    public Double performOnDataPoints(List<DataPoint> dataPoints) {
        square = 0;
        count = 0;

        extractFromDataPoints(dataPoints);

        if (count == 0) {
            return null;
        }

        return (square / (double) count) - (sum / (double) count);
    }

    @Override
    public Double performOnMixed(List<Digest> digests, List<DataPoint> dataPoints) throws QueryFailedException {
        square = 0;
        count = 0;

        extractFromDigest(digests);
        extractFromDataPoints(dataPoints);

        if (count == 0) {
            return null;
        }

        return (square / (double) count) - (sum / (double) count);
    }

    private void extractFromDataPoints(List<DataPoint> dataPoints) {
        for (DataPoint dataPoint : dataPoints) {
            count++;
            square += dataPoint.getValue() * dataPoint.getValue();
            sum += dataPoint.getValue();
        }
    }

    private void extractFromDigest(List<Digest> digests) throws QueryFailedException {
        if (!squareExisting || !countExisting || !sumExisting) {
            throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Either square or count are " +
                    "not in the MetaData but it was asked to perform this calculation on Digests which is not " +
                    "possible. Count in metadata: " + countExisting + " square in metadata: "
                    + squareExisting + " sum in metadata: " + sumExisting);
        }

        for (Digest digest : digests) {
            for (Pair<StreamMetaData, Long> value : digest.getValues()) {
                if (value.getLeft().getType().equals(StreamMetaData.MetadataType.COUNT)) {
                    count += value.getRight();
                }
                if (value.getLeft().getType().equals(StreamMetaData.MetadataType.SQUARE)) {
                    square += value.getRight();
                }
                if (value.getLeft().getType().equals(StreamMetaData.MetadataType.SUM)) {
                    sum += value.getRight();
                }
            }
        }
    }
}
