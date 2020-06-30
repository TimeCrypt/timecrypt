/*
 * Copyright (c) 2020 by ETH Zurich, see AUTHORS file for more
 * Licensed under the Apache License, Version 2.0, see LICENSE file for more details.
 */

package ch.ethz.dsg.timecrypt.client.queryInterface.calculations;

import ch.ethz.dsg.timecrypt.client.exceptions.QueryFailedException;
import ch.ethz.dsg.timecrypt.client.exceptions.QueryNeedsChunkScanException;
import ch.ethz.dsg.timecrypt.client.queryInterface.Query;
import ch.ethz.dsg.timecrypt.client.streamHandling.DataPoint;
import ch.ethz.dsg.timecrypt.client.streamHandling.Digest;
import ch.ethz.dsg.timecrypt.client.streamHandling.Stream;

import java.util.List;

public class ExactMinMaxCalculation implements Calculation {

    boolean max;

    public ExactMinMaxCalculation(boolean max) {
        this.max = max;
    }

    @Override
    public Query configureQuery(Stream stream, boolean chunkScanAllowed, Query query) throws QueryNeedsChunkScanException {

        if (!chunkScanAllowed) {
            throw new QueryNeedsChunkScanException(QueryNeedsChunkScanException.ChunkScanReason.NO_METADATA_SUPPORT
                    , "The calculation of min / max can not be performed on metadata only");
        } else {
            query.activateChunkScan();
        }
        return query;
    }

    @Override
    public Double performOnDigests(List<Digest> digests) throws QueryFailedException {

        throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Tried to perform a min/max" +
                "query on digests - this is not possible and should never happen!");

    }

    @Override
    public Double performOnDataPoints(List<DataPoint> dataPoints) {
        if (dataPoints.size() == 0) {
            return null;
        }
        long optimum = dataPoints.get(0).getValue();
        for (DataPoint dataPoint : dataPoints) {
            if (this.max && dataPoint.getValue() > optimum || !max && dataPoint.getValue() < optimum) {
                optimum = dataPoint.getValue();
            }
        }
        return (double) optimum;
    }

    @Override
    public Double performOnMixed(List<Digest> digests, List<DataPoint> dataPoints) throws QueryFailedException {
        throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Tried to perform a min/max" +
                "query on digests - this is not possible and should never happen!");
    }
}
