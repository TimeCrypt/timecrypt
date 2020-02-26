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
import ch.ethz.dsg.timecrypt.client.streamHandling.metaData.StreamMetaData;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class CountCalculation implements Calculation {

    boolean countExisting = false;
    long count = 0;

    @Override
    public Query configureQuery(Stream stream, boolean chunkScanAllowed, Query query) throws QueryNeedsChunkScanException {

        for (StreamMetaData metaData : stream.getMetaData()) {
            if (metaData.getType().equals(StreamMetaData.MetadataType.COUNT)) {
                countExisting = true;
                query.addNeededMetaData(metaData);
            }
        }

        if (!countExisting && !chunkScanAllowed) {
            throw new QueryNeedsChunkScanException(QueryNeedsChunkScanException.ChunkScanReason.
                    NOT_ENOUGH_METADATA_IN_STREAM, "Count is not in metadata.");
        }
        return query;
    }

    @Override
    public Double performOnDigests(List<Digest> digests) throws QueryFailedException {
        count = 0;

        extractFromDigest(digests);

        return (double) count;
    }

    @Override
    public Double performOnDataPoints(List<DataPoint> dataPoints) {
        return (double) dataPoints.size();
    }

    @Override
    public Double performOnMixed(List<Digest> digests, List<DataPoint> dataPoints) throws QueryFailedException {
        count = 0;

        extractFromDigest(digests);
        count += dataPoints.size();

        return (double) count;
    }

    private void extractFromDigest(List<Digest> digests) throws QueryFailedException {
        if (!countExisting) {
            throw new QueryFailedException(QueryFailedException.FailReason.INTERNAL_ERROR, "Either sum or count are " +
                    "not in the MetaData but it was asked to perform this calculation on Digests which is not " +
                    "possible. Count not in metadata. ");
        }

        for (Digest digest : digests) {
            for (Pair<StreamMetaData, Long> value : digest.getValues()) {
                if (value.getLeft().getType().equals(StreamMetaData.MetadataType.COUNT)) {
                    count += value.getRight();
                }
            }
        }
    }

}
